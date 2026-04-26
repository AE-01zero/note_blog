package com.aezer0.initialization.service.ai.protocol;

import dev.langchain4j.model.embedding.EmbeddingModel;

public interface EmbeddingProtocolAdapter {

    String protocol();

    void probe(String baseUrl, String apiKey, String modelName, Integer maxSegmentsPerBatch);

    EmbeddingModel buildEmbeddingModel(String baseUrl,
                                       String apiKey,
                                       String modelName,
                                       Boolean logRequests,
                                       Boolean logResponses,
                                       Integer maxSegmentsPerBatch);
}

