package com.aezer0.initialization.service.ai.protocol;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

public class AnthropicCompatibleStreamingChatModel implements StreamingChatModel {

    private static final int CHUNK_SIZE = 24;

    private final String baseUrl;
    private final String apiKey;
    private final String modelName;

    public AnthropicCompatibleStreamingChatModel(String baseUrl, String apiKey, String modelName) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        try {
            ChatResponse response = AnthropicCompatibleClient.chat(
                    baseUrl,
                    apiKey,
                    modelName,
                    chatRequest.messages(),
                    chatRequest.maxOutputTokens()
            );

            String text = response.aiMessage() != null ? response.aiMessage().text() : "";
            if (text != null && !text.isEmpty()) {
                for (int i = 0; i < text.length(); i += CHUNK_SIZE) {
                    int end = Math.min(i + CHUNK_SIZE, text.length());
                    handler.onPartialResponse(text.substring(i, end));
                }
            }
            handler.onCompleteResponse(response);
        } catch (Exception e) {
            handler.onError(e);
        }
    }
}

