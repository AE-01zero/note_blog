package com.aezer0.initialization.service.ai.adapter;

import com.aezer0.initialization.retention.ChatServiceProvider;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,//手动装配
        chatModel = "localChatModel",//指定模型
        streamingChatModel = "openAiStreamingChatModel",
        chatMemoryProvider = "chatMemoryProvider"//配置会话记忆提供者对象
        //chatMemory = "chatMemory",//配置会话记忆对象
//        chatMemoryProvider = "chatMemoryProvider",//配置会话记忆提供者对象
//        contentRetriever = "userContentRetriever"//配置用户隔离的向量数据库检索对象
//        tools = "reservationTool"
)
@ChatServiceProvider("qwen1.5-7b-chat")
public interface LocalConsultantService {

    @SystemMessage("你是小助手!")
    public Flux<String> streamChat(@MemoryId String memoryId, @UserMessage String message);

    @SystemMessage("你是小助手!")
    public String simpleChat( @MemoryId String memoryId,@UserMessage String message);
}
