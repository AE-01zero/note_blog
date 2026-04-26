package com.aezer0.initialization.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库广场展示VO
 */
@Data
public class KnowledgeBaseSquareVO {

    /**
     * 主键ID
     */
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
    private String coverUrl;

    /**
     * 是否有访问密码
     */
    private Boolean hasPassword;

    /**
     * 创建者ID
     */
    private Long creatorId;

    /**
     * 创建者用户名
     */
    private String creatorName;

    /**
     * 创建者头像
     */
    private String creatorAvatar;

    /**
     * 成员数量
     */
    private Integer memberCount;

    /**
     * 文件数量
     */
    private Integer fileCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 当前用户是否已加入
     */
    private Boolean isJoined;

    /**
     * 热度评分（基于成员数量、文件数量、活跃度等计算）
     */
    private Double hotScore;

    /**
     * 标签列表（基于文件内容提取，可选功能）
     */
    private String tags;
} 