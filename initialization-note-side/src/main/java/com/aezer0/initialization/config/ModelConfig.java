package com.aezer0.initialization.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ModelConfig {

    // ---Local---

    @Bean
    @Qualifier("localChatModel")
    public OpenAiChatModel localChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl("http://127.0.0.1:1234/v1/")
                .modelName("qwen1.5-7b-chat")
                .timeout(Duration.ofMinutes(1))
                .temperature(3.0).logRequests(true).logResponses(true)
                .build();
    }

//    @Bean
//    @Qualifier("remoteEmbeddingModel")
//    public EmbeddingModel remoteEmbeddingModel(
//            @Value("${langchain4j.open-ai.api-key}") String apiKey,
//            @Value("${langchain4j.open-ai.embedding-model.model-name}") String modelName,
//            @Value("${langchain4j.open-ai.timeout}") Duration timeout) {
//        return OpenAiEmbeddingModel.builder()
//                .apiKey(apiKey)
//                .modelName(modelName)
//                .timeout(timeout)
//                .build();
//    }

}
