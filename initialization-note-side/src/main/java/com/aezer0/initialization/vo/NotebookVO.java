package com.aezer0.initialization.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 笔记本VO
 */
@Data
public class NotebookVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 笔记本名称
     */
    private String name;

    /**
     * 封面图片URL
     */
    private String cover;

    /**
     * 笔记本描述
     */
    private String description;

    /**
     * 排序字段
     */
    private Integer sortOrder;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 笔记数量
     */
    private Integer noteCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
} 