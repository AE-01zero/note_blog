package com.aezer0.initialization.service.ai.protocol;

import dev.langchain4j.model.embedding.EmbeddingModel;

public class GeminiEmbeddingProtocolAdapter implements EmbeddingProtocolAdapter {

    @Override
    public String protocol() {
        return "gemini";
    }

    @Override
    public void probe(String baseUrl, String apiKey, String modelName, Integer maxSegmentsPerBatch) {
        GeminiCompatibleEmbeddingClient.probe(baseUrl, apiKey, modelName);
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(String baseUrl,
                                              String apiKey,
                                              String modelName,
                                              Boolean logRequests,
                                              Boolean logResponses,
                                              Integer maxSegmentsPerBatch) {
        return new GeminiCompatibleEmbeddingModel(baseUrl, apiKey, modelName);
    }
}

