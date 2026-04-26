package com.aezer0.initialization.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 笔记表
 * 
 * @TableName t_note
 */
@TableName(value = "t_note")
@Data
public class Note implements Serializable {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属笔记本ID
     */
    @TableField(value = "notebook_id")
    private Long notebookId;

    /**
     * 所属用户ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 笔记标题
     */
    private String title;

    /**
     * Markdown 格式的笔记内容
     */
    @TableField(value = "content_md")
    private String contentMd;

    /**
     * 笔记状态（1-已发布 0-草稿）
     */
    private Integer status;

    /**
     * 是否置顶
     */
    @TableField(value = "is_pinned")
    private Boolean isPinned;

    /**
     * 查看次数
     */
    @TableField(value = "view_count")
    private Integer viewCount;

    /**
     * 字数统计
     */
    @TableField(value = "word_count")
    private Integer wordCount;

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

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /**
     * 笔记本名称（不存储在数据库中）
     */
    @TableField(exist = false)
    private String notebookName;

    /**
     * HTML 格式的笔记内容（不存储在数据库中）
     */
    @TableField(exist = false)
    private String contentHtml;
} 