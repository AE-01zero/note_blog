package com.aezer0.initialization.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BlogCategoryVO {

    private Long id;

    private String name;

    private String description;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
