package com.aezer0.initialization.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
/**
 * 笔记本创建DTO
 */
@Data
public class NotebookCreateDTO {

    /**
     * 笔记本名称
     */
    @NotBlank(message = "笔记本名称不能为空")
    @Size(max = 128, message = "笔记本名称长度不能超过128个字符")
    private String name;

    /**
     * 封面图片URL
     */
    @Size(max = 255, message = "封面图片URL长度不能超过255个字符")
    private String cover;

    /**
     * 笔记本描述
     */
    @Size(max = 512, message = "笔记本描述长度不能超过512个字符")
    private String description;

    /**
     * 排序字段
     */
    private Integer sortOrder;

    /**
     * 分类ID（复用博客分类体系）
     */
    private Long categoryId;
} 