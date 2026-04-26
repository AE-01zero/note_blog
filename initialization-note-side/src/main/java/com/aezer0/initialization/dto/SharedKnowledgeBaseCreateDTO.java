package com.aezer0.initialization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建共享知识库DTO
 */
@Data
public class SharedKnowledgeBaseCreateDTO {

    /**
     * 知识库名称
     */
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 128, message = "知识库名称长度不能超过128个字符")
    private String name;

    /**
     * 知识库描述
     */
    @Size(max = 2000, message = "知识库描述长度不能超过2000个字符")
    private String description;

    /**
     * 封面图片URL
     */
    @Size(max = 255, message = "封面图片URL长度不能超过255个字符")
    private String coverUrl;

    /**
     * 访问密码（可选）
     */
    @Size(max = 20, message = "访问密码长度不能超过20个字符")
    private String password;

    /**
     * 是否公开展示
     */
    private Boolean isPublic = true;

    /**
     * 分类ID（复用博客分类体系）
     */
    private Long categoryId;

    /**
     * 初始文件ID列表（从个人知识库复制）
     */
    private List<Long> initialFileIds;
} 