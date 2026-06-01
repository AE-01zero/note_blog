package com.aezer0.initialization.service.ai.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Gemini native protocol client (/models/{model}:generateContent).
 */
public final class GeminiCompatibleClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private GeminiCompatibleClient() {
    }

    public static void probe(String baseUrl, String apiKey, String modelName) {
        List<ChatMessage> messages = List.of(UserMessage.from("ping"));
        chat(baseUrl, apiKey, modelName, messages, 1);
    }

    public static ChatResponse chat(String baseUrl,
                                    String apiKey,
                                    String modelName,
                                    List<ChatMessage> messages,
                                    Integer maxTokens) {
        String payload = buildPayload(messages, maxTokens);
        String encodedModel = URLEncoder.encode(modelName, StandardCharsets.UTF_8);
        String url = normalizeBaseUrl(baseUrl) + "/models/" + encodedModel + ":generateContent";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gemini protocol request failed: " + e.getMessage(), e);
        }

        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + safeBody(response.body()));
        }

        return parseChatResponse(response.body(), modelName);
    }

    private static String buildPayload(List<ChatMessage> messages, Integer maxTokens) {
        Map<String, Object> payload = new LinkedHashMap<>();

        StringBuilder systemBuilder = new StringBuilder();
        List<Map<String, Object>> contents = new ArrayList<>();

        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage) {
                SystemMessage systemMessage = (SystemMessage) message;
                if (!systemMessage.text().isBlank()) {
                    if (!systemBuilder.isEmpty()) {
                        systemBuilder.append('\n');
                    }
                    systemBuilder.append(systemMessage.text());
                }
                continue;
            }

            if (message instanceof UserMessage) {
                UserMessage userMessage = (UserMessage) message;
                String text = userMessage.hasSingleText() ? userMessage.singleText() : "";
                if (!text.isBlank()) {
                    contents.add(buildContent("user", text));
                }
                continue;
            }

            if (message instanceof AiMessage) {
                AiMessage aiMessage = (AiMessage) message;
                String text = aiMessage.text();
                if (text != null && !text.isBlank()) {
                    contents.add(buildContent("model", text));
                }
            }
        }

        if (contents.isEmpty()) {
            contents.add(buildContent("user", "ping"));
        }

        payload.put("contents", contents);

        if (!systemBuilder.isEmpty()) {
            Map<String, Object> systemInstruction = new LinkedHashMap<>();
            systemInstruction.put("parts", List.of(Map.of("text", systemBuilder.toString())));
            payload.put("systemInstruction", systemInstruction);
        }

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("maxOutputTokens", maxTokens != null && maxTokens > 0 ? maxTokens : 1024);
        payload.put("generationConfig", generationConfig);

        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (IOException e) {
            throw new RuntimeException("Failed to build Gemini payload: " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> buildContent(String role, String text) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", role);
        content.put("parts", List.of(Map.of("text", text)));
        return content;
    }

    private static ChatResponse parseChatResponse(String responseBody, String fallbackModelName) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);

            JsonNode candidate = root.path("candidates").isArray() && root.path("candidates").size() > 0
                    ? root.path("candidates").get(0)
                    : null;
            String text = candidate == null ? "" : extractText(candidate.path("content").path("parts"));

            Integer inputTokens = readNullableInt(root.path("usageMetadata").path("promptTokenCount"));
            Integer outputTokens = readNullableInt(root.path("usageMetadata").path("candidatesTokenCount"));
            Integer totalTokens = readNullableInt(root.path("usageMetadata").path("totalTokenCount"));
            TokenUsage tokenUsage = (inputTokens != null || outputTokens != null || totalTokens != null)
                    ? new TokenUsage(inputTokens, outputTokens, totalTokens)
                    : null;

            ChatResponse.Builder builder = ChatResponse.builder()
                    .aiMessage(AiMessage.from(text))
                    .modelName(fallbackModelName);
            if (tokenUsage != null) {
                builder.tokenUsage(tokenUsage);
            }
            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini response: " + e.getMessage(), e);
        }
    }

    private static String extractText(JsonNode partsNode) {
        if (!partsNode.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : partsNode) {
            String text = part.path("text").asText("");
            if (!text.isEmpty()) {
                sb.append(text);
            }
        }
        return sb.toString();
    }

    private static Integer readNullableInt(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String safeBody(String body) {
        if (body == null) {
            return "";
        }
        String compact = body.replaceAll("[\\r\\n]+", " ").trim();
        if (compact.length() > 500) {
            return compact.substring(0, 500) + "...";
        }
        return compact;
    }
}

