package com.aezer0.initialization.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class BlogPostListVO {

    private Long id;

    private Long userId;

    private String title;

    private String summary;

    private String coverUrl;

    private Long categoryId;

    private String categoryName;

    private List<BlogTagVO> tags;

    private Integer status;

    private Boolean isTop;

    private Integer viewCount;

    private LocalDateTime publishTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
