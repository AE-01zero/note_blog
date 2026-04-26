package com.aezer0.initialization.service.ai.protocol;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

public class OpenAiEmbeddingProtocolAdapter implements EmbeddingProtocolAdapter {

    @Override
    public String protocol() {
        return "openai";
    }

    @Override
    public void probe(String baseUrl, String apiKey, String modelName, Integer maxSegmentsPerBatch) {
        OpenAiEmbeddingModel model = OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .logRequests(false)
                .logResponses(false)
                .maxSegmentsPerBatch(maxSegmentsPerBatch != null ? maxSegmentsPerBatch : 10)
                .build();
        model.embed("ping");
    }

    @Override
    public EmbeddingModel buildEmbeddingModel(String baseUrl,
                                              String apiKey,
                                              String modelName,
                                              Boolean logRequests,
                                              Boolean logResponses,
                                              Integer maxSegmentsPerBatch) {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .maxSegmentsPerBatch(maxSegmentsPerBatch)
                .build();
    }
}

