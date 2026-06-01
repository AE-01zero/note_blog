package com.aezer0.initialization.service.ai.protocol;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public class GeminiCompatibleChatModel implements ChatModel {

    private final String baseUrl;
    private final String apiKey;
    private final String modelName;

    public GeminiCompatibleChatModel(String baseUrl, String apiKey, String modelName) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return GeminiCompatibleClient.chat(
                baseUrl,
                apiKey,
                modelName,
                chatRequest.messages(),
                chatRequest.maxOutputTokens()
        );
    }
}

