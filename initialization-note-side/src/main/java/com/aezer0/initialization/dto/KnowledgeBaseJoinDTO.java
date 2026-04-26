package com.aezer0.initialization.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 加入知识库DTO
 */
@Data
public class KnowledgeBaseJoinDTO {

    /**
     * 知识库ID
     */
    @NotNull(message = "知识库ID不能为空")
    private Long knowledgeBaseId;

    /**
     * 访问密码（如果知识库设置了密码）
     */
    private String password;
} 