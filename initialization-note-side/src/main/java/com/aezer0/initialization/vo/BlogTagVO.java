package com.aezer0.initialization.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BlogTagVO {

    private Long id;

    private String name;

    private LocalDateTime createTime;
}
