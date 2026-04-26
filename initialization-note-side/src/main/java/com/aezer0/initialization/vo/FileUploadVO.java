package com.aezer0.initialization.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileUploadVO {
    
    private Long id;
    
    private String originalFilename;
    
    private String fileName;
    
    private String extension;
    
    private Long fileSize;
    
    private String filePath;
    
    private String fileUrl;
    
    private Long userId;
    
    private String fileMd5;
    
    private LocalDateTime createTime;
    
    private String fileType;
    
    private String mimeType;
    
    // 文档处理相关信息
    private Integer documentCount;
    
    private String processingStatus;
    
    private String processingMessage;
} 