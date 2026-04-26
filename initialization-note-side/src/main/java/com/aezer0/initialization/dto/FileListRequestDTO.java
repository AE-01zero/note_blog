package com.aezer0.initialization.dto;

import lombok.Data;

/**
 * 文件列表查询请求DTO
 */
@Data
public class FileListRequestDTO {
    
    /**
     * 页码（从1开始）
     */
    private int page = 1;
    
    /**
     * 每页大小
     */
    private int size = 10;
    
    /**
     * 搜索关键词（可选）
     */
    private String keyword;
    
    /**
     * 文件类型（可选）
     */
    private String fileType;

    /**
     * 分类（可选）
     */
    private String category;
    
    /**
     * 排序字段（可选，默认按创建时间）
     */
    private String sortBy = "create_time";
    
    /**
     * 排序方向（可选，默认倒序）
     */
    private String sortOrder = "desc";
    
    /**
     * 获取有效的排序字段
     */
    public String getSortBy() {
        if (this.sortBy == null || this.sortBy.trim().isEmpty()) {
            return "create_time";
        }
        return this.sortBy;
    }
    
    /**
     * 获取有效的排序方向
     */
    public String getSortOrder() {
        if (this.sortOrder == null || this.sortOrder.trim().isEmpty()) {
            return "desc";
        }
        return this.sortOrder.toLowerCase();
    }
} 