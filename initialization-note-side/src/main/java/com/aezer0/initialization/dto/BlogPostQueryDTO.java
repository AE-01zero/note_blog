package com.aezer0.initialization.dto;

import lombok.Data;

@Data
public class BlogPostQueryDTO {

    private Integer page = 1;

    private Integer size = 10;

    private String keyword;

    private Integer status;

    private Long userId;

    private Long categoryId;

    private Long tagId;
}
