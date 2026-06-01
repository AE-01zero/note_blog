package com.aezer0.initialization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BlogPostUpdateDTO {

    @NotBlank(message = "博客标题不能为空")
    @Size(max = 255, message = "博客标题不能超过255个字符")
    private String title;

    @Size(max = 1000, message = "摘要不能超过1000个字符")
    private String summary;

    private String contentMd;

    @Size(max = 255, message = "封面URL不能超过255个字符")
    private String coverUrl;

    private Long categoryId;

    private List<Long> tagIds;

    private Boolean isTop;
}
