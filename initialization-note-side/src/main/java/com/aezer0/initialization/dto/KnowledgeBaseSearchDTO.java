package com.aezer0.initialization.dto;

import lombok.Data;

/**
 * 知识库搜索DTO
 */
@Data
public class KnowledgeBaseSearchDTO {

    /**
     * 搜索关键词
     */
    private String keyword;

    /**
     * 排序字段：create_time, member_count, file_count
     */
    private String sortBy = "create_time";

    /**
     * 排序顺序：asc, desc
     */
    private String sortOrder = "desc";

    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页大小
     */
    private Integer size = 10;

    /**
     * 是否只显示公开的知识库
     */
    private Boolean onlyPublic = true;
} 