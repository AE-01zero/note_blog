package com.aezer0.initialization.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 共享知识库表
 * 
 * @TableName t_shared_knowledge_base
 */
@TableName(value = "t_shared_knowledge_base")
@Data
public class SharedKnowledgeBase implements Serializable {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 知识库名称
     */
    private String name;

    /**
     * 知识库描述
     */
    private String description;

    /**
     * 封面图片URL
     */
    @TableField(value = "cover_url")
    private String coverUrl;

    /**
     * 访问密码（可选）
     */
    private String password;

    /**
     * 创建者ID
     */
    @TableField(value = "creator_id")
    private Long creatorId;

    /**
     * 是否公开展示
     */
    @TableField(value = "is_public")
    private Boolean isPublic;

    /**
     * 成员数量
     */
    @TableField(value = "member_count")
    private Integer memberCount;

    /**
     * 文件数量
     */
    @TableField(value = "file_count")
    private Integer fileCount;

    /**
     * 状态：1-正常 0-禁用
     */
    private Integer status;

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
     * 创建者用户名（不存储在数据库中）
     */
    @TableField(exist = false)
    private String creatorName;

    /**
     * 创建者头像（不存储在数据库中）
     */
    @TableField(exist = false)
    private String creatorAvatar;

    /**
     * 当前用户是否为成员（不存储在数据库中）
     */
    @TableField(exist = false)
    private Boolean isMember;

    /**
     * 当前用户在知识库中的角色（不存储在数据库中）
     */
    @TableField(exist = false)
    private Integer userRole;
} 