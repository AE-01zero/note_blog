package com.aezer0.initialization.service.ai.protocol;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;

public class GeminiCompatibleEmbeddingModel implements EmbeddingModel {

    private final String baseUrl;
    private final String apiKey;
    private final String modelName;

    public GeminiCompatibleEmbeddingModel(String baseUrl, String apiKey, String modelName) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        List<Embedding> embeddings = new ArrayList<>();
        if (segments != null) {
            for (TextSegment segment : segments) {
                String text = segment != null && segment.text() != null ? segment.text() : "";
                embeddings.add(GeminiCompatibleEmbeddingClient.embed(baseUrl, apiKey, modelName, text));
            }
        }
        return Response.from(embeddings);
    }
}

