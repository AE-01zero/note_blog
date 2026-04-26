package com.aezer0.initialization.service.ai.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal Gemini native embedding client (/models/{model}:embedContent).
 */
public final class GeminiCompatibleEmbeddingClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private GeminiCompatibleEmbeddingClient() {
    }

    public static void probe(String baseUrl, String apiKey, String modelName) {
        embed(baseUrl, apiKey, modelName, "ping");
    }

    public static Embedding embed(String baseUrl, String apiKey, String modelName, String text) {
        String encodedModel = URLEncoder.encode(modelName, StandardCharsets.UTF_8);
        String url = normalizeBaseUrl(baseUrl) + "/models/" + encodedModel + ":embedContent";

        String payload = buildPayload(text);
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
            throw new RuntimeException("Gemini embedding request failed: " + e.getMessage(), e);
        }

        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + safeBody(response.body()));
        }

        return parseEmbedding(response.body());
    }

    private static String buildPayload(String text) {
        Map<String, Object> payload = Map.of(
                "content", Map.of(
                        "parts", List.of(Map.of("text", text))
                )
        );
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (IOException e) {
            throw new RuntimeException("Failed to build Gemini embedding payload: " + e.getMessage(), e);
        }
    }

    private static Embedding parseEmbedding(String responseBody) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode valuesNode = root.path("embedding").path("values");
            if (!valuesNode.isArray() || valuesNode.size() == 0) {
                throw new RuntimeException("Gemini embedding response missing values");
            }
            List<Float> values = new ArrayList<>();
            for (JsonNode node : valuesNode) {
                values.add((float) node.asDouble());
            }
            return Embedding.from(values);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini embedding response: " + e.getMessage(), e);
        }
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

