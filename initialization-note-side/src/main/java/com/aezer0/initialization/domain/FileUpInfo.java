package com.aezer0.initialization.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@TableName("t_file_info")
public class FileUpInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String originalFilename;

    private String fileName;

    private String fileExtension;

    private Long fileSize;

    private String filePath;

    private String fileUrl;

    private Long userId;

    private String fileMd5;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private String fileType;

    private String mimeType;

    private String fileHash;

    // 文件来源类型（1: 本地上传, 2: 个人知识库复制, 3: 笔记转换PDF）
    private Integer sourceType;

    // 来源笔记ID（如果是从笔记转换的）
    private Long sourceNoteId;

    // 来源笔记标题（如果是从笔记转换的）
    private String sourceNoteTitle;

    // 知识库分类
    private String category;
}