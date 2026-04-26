package com.aezer0.initialization.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文件展示VO
 */
@Data
public class KnowledgeBaseFileVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 知识库ID
     */
    private Long knowledgeBaseId;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件扩展名
     */
    private String extension;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 文件URL
     */
    private String fileUrl;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 上传者ID
     */
    private Long uploaderId;

    /**
     * 上传者用户名
     */
    private String uploaderName;

    /**
     * 上传者头像
     */
    private String uploaderAvatar;

    /**
     * 来源类型：1-本地上传 2-个人知识库复制
     */
    private Integer sourceType;

    /**
     * 来源类型名称
     */
    private String sourceTypeName;

    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;

    /**
     * 处理状态（向量化处理状态）
     */
    private String processingStatus;

    /**
     * 文档片段数量
     */
    private Integer documentCount;

    /**
     * 文件分类
     */
    private String category;

    /**
     * 向量片段总数
     */
    private Integer chunkCount;

    /**
     * 预览摘要
     */
    private String summary;
} 