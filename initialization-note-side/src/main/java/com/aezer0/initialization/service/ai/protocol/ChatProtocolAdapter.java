package com.aezer0.initialization.service.ai.protocol;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

public interface ChatProtocolAdapter {

    String protocol();

    void probe(String baseUrl, String apiKey, String modelName);

    ChatModel buildChatModel(String baseUrl,
                             String apiKey,
                             String modelName,
                             Boolean logRequests,
                             Boolean logResponses);

    StreamingChatModel buildStreamingChatModel(String baseUrl,
                                               String apiKey,
                                               String modelName,
                                               Boolean logRequests,
                                               Boolean logResponses);
}

