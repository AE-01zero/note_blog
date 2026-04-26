package com.aezer0.initialization.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 共享知识库展示VO
 */
@Data
public class SharedKnowledgeBaseVO {

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
     * 是否公开展示
     */
    private Boolean isPublic;

    /**
     * 成员数量
     */
    private Integer memberCount;

    /**
     * 文件数量
     */
    private Integer fileCount;

    /**
     * 状态：1-正常 0-禁用
     */
    private Integer status;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 当前用户是否为成员
     */
    private Boolean isMember;

    /**
     * 当前用户在知识库中的角色
     */
    private Integer userRole;

    /**
     * 成员列表（详情页面使用）
     */
    private List<KnowledgeBaseMemberVO> members;

    /**
     * 文件列表（详情页面使用）
     */
    private List<KnowledgeBaseFileVO> files;
} 