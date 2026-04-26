package com.aezer0.initialization.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 笔记本表
 * 
 * @TableName t_notebook
 */
@TableName(value = "t_notebook")
@Data
public class Notebook implements Serializable {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(value = "user_id")
    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 笔记本名称
     */
    private String name;

    /**
     * 封面图片URL
     */
    @TableField(value = "color")
    private String cover;

    /**
     * 笔记本描述
     */
    private String description;
    @TableField(value = "sort_order")
    /**
     * 排序字段
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 分类ID（复用博客分类体系）
     */
    @TableField(value = "category_id")
    private Long categoryId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /**
     * 笔记数量（不存储在数据库中）
     */
    @TableField(exist = false)
    private Integer noteCount;
} 