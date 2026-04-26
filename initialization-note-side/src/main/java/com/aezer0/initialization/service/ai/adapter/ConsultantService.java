package com.aezer0.initialization.service.ai.adapter;

import com.aezer0.initialization.retention.ChatServiceProvider;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import org.springframework.util.RouteMatcher;
import reactor.core.publisher.Flux;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,//手动装配
        chatModel = "openAiChatModel",//指定模型
        streamingChatModel = "openAiStreamingChatModel",
        //chatMemory = "chatMemory",//配置会话记忆对象
        chatMemoryProvider = "chatMemoryProvider"//配置会话记忆提供者对象
//        contentRetriever = "userContentRetriever"//配置用户隔离的向量数据库检索对象
//        tools = "reservationTool"
)
@ChatServiceProvider("qwen-plus")
public interface ConsultantService {

    @SystemMessage("你是小助手!")
    public Flux<String> streamChat(@MemoryId String memoryId, @UserMessage String message);

    @SystemMessage("你是小助手!")
    public String simpleChat( @MemoryId String memoryId,@UserMessage String message);

    /**
     * 一个AiService，专门用于将长文本内容提炼成精简的摘要。
     */

        @SystemMessage("""
        你是一个专业的文档摘要专家。
        你的任务是根据用户提供的文本内容，生成一段不超过150字的、精炼的、中性的摘要。
        摘要必须准确地概括文档的核心主题、主要观点和关键信息。
        请直接返回摘要内容，不要添加任何额外的解释、标题或开场白（例如，不要说“好的，这是摘要：”）。
        """)
        String summarize(@UserMessage String text);
    }


