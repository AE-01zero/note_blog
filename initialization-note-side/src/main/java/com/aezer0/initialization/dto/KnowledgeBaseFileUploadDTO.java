package com.aezer0.initialization.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 知识库文件上传DTO
 */
@Data
public class KnowledgeBaseFileUploadDTO {

    /**
     * 知识库ID
     */
    @NotNull(message = "知识库ID不能为空")
    private Long knowledgeBaseId;

    /**
     * 文件ID列表（从个人知识库复制）
     */
    private List<Long> fileIds;

    /**
     * 来源类型：1-本地上传 2-个人知识库复制
     */
    private Integer sourceType = 1;
} 