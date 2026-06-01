package com.aezer0.initialization.service.ai.protocol;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

public class GeminiProtocolAdapter implements ChatProtocolAdapter {

    @Override
    public String protocol() {
        return "gemini";
    }

    @Override
    public void probe(String baseUrl, String apiKey, String modelName) {
        GeminiCompatibleClient.probe(baseUrl, apiKey, modelName);
    }

    @Override
    public ChatModel buildChatModel(String baseUrl,
                                    String apiKey,
                                    String modelName,
                                    Boolean logRequests,
                                    Boolean logResponses) {
        return new GeminiCompatibleChatModel(baseUrl, apiKey, modelName);
    }

    @Override
    public StreamingChatModel buildStreamingChatModel(String baseUrl,
                                                      String apiKey,
                                                      String modelName,
                                                      Boolean logRequests,
                                                      Boolean logResponses) {
        return new GeminiCompatibleStreamingChatModel(baseUrl, apiKey, modelName);
    }
}

