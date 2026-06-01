package com.aezer0.initialization.service.ai.protocol;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

public class OpenAiProtocolAdapter implements ChatProtocolAdapter {

    @Override
    public String protocol() {
        return "openai";
    }

    @Override
    public void probe(String baseUrl, String apiKey, String modelName) {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .logRequests(false)
                .logResponses(false)
                .build();
        model.chat("ping");
    }

    @Override
    public ChatModel buildChatModel(String baseUrl,
                                    String apiKey,
                                    String modelName,
                                    Boolean logRequests,
                                    Boolean logResponses) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }

    @Override
    public StreamingChatModel buildStreamingChatModel(String baseUrl,
                                                      String apiKey,
                                                      String modelName,
                                                      Boolean logRequests,
                                                      Boolean logResponses) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}

