package com.aezer0.initialization.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库成员展示VO
 */
@Data
public class KnowledgeBaseMemberVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 知识库ID
     */
    private Long knowledgeBaseId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 头像URL
     */
    private String avatarUrl;

    /**
     * 角色：1-创建者 2-成员
     */
    private Integer role;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 加入时间
     */
    private LocalDateTime joinTime;

    /**
     * 是否在线（可扩展功能）
     */
    private Boolean online;
} 