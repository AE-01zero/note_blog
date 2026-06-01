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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Anthropic-compatible protocol client (/v1/messages).
 */
public final class AnthropicCompatibleClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private AnthropicCompatibleClient() {
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
        String payload = buildPayload(modelName, messages, maxTokens);
        String url = normalizeBaseUrl(baseUrl) + "/messages";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Anthropic protocol request failed: " + e.getMessage(), e);
        }

        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + safeBody(response.body()));
        }

        return parseChatResponse(response.body(), modelName);
    }

    private static String buildPayload(String modelName, List<ChatMessage> messages, Integer maxTokens) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("max_tokens", maxTokens != null && maxTokens > 0 ? maxTokens : 1024);

        StringBuilder systemBuilder = new StringBuilder();
        List<Map<String, Object>> anthropicMessages = new ArrayList<>();

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
                    anthropicMessages.add(buildMessage("user", text));
                }
                continue;
            }

            if (message instanceof AiMessage) {
                AiMessage aiMessage = (AiMessage) message;
                String text = aiMessage.text();
                if (text != null && !text.isBlank()) {
                    anthropicMessages.add(buildMessage("assistant", text));
                }
            }
        }

        if (anthropicMessages.isEmpty()) {
            anthropicMessages.add(buildMessage("user", "ping"));
        }

        payload.put("messages", anthropicMessages);
        if (!systemBuilder.isEmpty()) {
            payload.put("system", systemBuilder.toString());
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (IOException e) {
            throw new RuntimeException("Failed to build Anthropic payload: " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> buildMessage(String role, String text) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);

        List<Map<String, Object>> contentBlocks = new ArrayList<>();
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "text");
        block.put("text", text);
        contentBlocks.add(block);

        message.put("content", contentBlocks);
        return message;
    }

    private static ChatResponse parseChatResponse(String responseBody, String fallbackModelName) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            String modelName = root.path("model").asText(fallbackModelName);
            String text = extractText(root.path("content"));

            Integer inputTokens = readNullableInt(root.path("usage").path("input_tokens"));
            Integer outputTokens = readNullableInt(root.path("usage").path("output_tokens"));
            TokenUsage tokenUsage = (inputTokens != null || outputTokens != null)
                    ? new TokenUsage(inputTokens, outputTokens)
                    : null;

            ChatResponse.Builder builder = ChatResponse.builder()
                    .aiMessage(AiMessage.from(text))
                    .modelName(modelName);
            if (tokenUsage != null) {
                builder.tokenUsage(tokenUsage);
            }
            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Anthropic response: " + e.getMessage(), e);
        }
    }

    private static String extractText(JsonNode contentNode) {
        if (!contentNode.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : contentNode) {
            if (item.path("type").asText("").equals("text")) {
                String text = item.path("text").asText("");
                if (!text.isEmpty()) {
                    sb.append(text);
                }
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
