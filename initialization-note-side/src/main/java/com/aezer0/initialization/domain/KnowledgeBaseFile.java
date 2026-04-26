package com.aezer0.initialization.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文件表
 * 
 * @TableName t_knowledge_base_file
 */
@TableName(value = "t_knowledge_base_file")
@Data
public class KnowledgeBaseFile implements Serializable {
    
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
     * 文件ID
     */
    @TableField(value = "file_id")
    private Long fileId;

    /**
     * 上传者ID
     */
    @TableField(value = "uploader_id")
    private Long uploaderId;

    /**
     * 来源类型：1-本地上传 2-个人知识库复制
     */
    @TableField(value = "source_type")
    private Integer sourceType;

    /**
     * 上传时间
     */
    @TableField(value = "upload_time", fill = FieldFill.INSERT)
    private LocalDateTime uploadTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /**
     * 文件信息（不存储在数据库中）
     */
    @TableField(exist = false)
    private FileUpInfo fileInfo;

    /**
     * 上传者信息（不存储在数据库中）
     */
    @TableField(exist = false)
    private User uploader;

    /**
     * 知识库信息（不存储在数据库中）
     */
    @TableField(exist = false)
    private SharedKnowledgeBase knowledgeBase;
} 