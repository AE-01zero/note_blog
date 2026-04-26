package com.aezer0.initialization.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库成员表
 * 
 * @TableName t_knowledge_base_member
 */
@TableName(value = "t_knowledge_base_member")
@Data
public class KnowledgeBaseMember implements Serializable {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 知识库ID
     */
    @TableField(value = "knowledge_base_id")
    private Long knowledgeBaseId;

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 角色：1-创建者 2-成员
     */
    private Integer role;

    /**
     * 加入时间
     */
    @TableField(value = "join_time", fill = FieldFill.INSERT)
    private LocalDateTime joinTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /**
     * 用户信息（不存储在数据库中）
     */
    @TableField(exist = false)
    private User user;

    /**
     * 知识库信息（不存储在数据库中）
     */
    @TableField(exist = false)
    private SharedKnowledgeBase knowledgeBase;
} 