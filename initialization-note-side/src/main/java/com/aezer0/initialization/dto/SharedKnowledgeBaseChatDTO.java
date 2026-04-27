package com.aezer0.initialization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 共享知识库聊天DTO
 */
@Data
public class SharedKnowledgeBaseChatDTO {

    /**
     * 知识库ID
     */
    @NotNull(message = "知识库ID不能为空")
    private Long knowledgeBaseId;

    /**
     * 用户消息
     */
    @NotBlank(message = "用户消息不能为空")
    private String message;

    /**
     * 会话ID（用于维持对话上下文）
     */
    private String memoryId;

    /**
     * 分类过滤（可选，按分类名称限定检索范围）
     */
    private String categoryFilter;

    /**
     * 可选：回答模式，strict_kb / kb_hybrid_reasoning
     */
    private String answerMode;
} 
