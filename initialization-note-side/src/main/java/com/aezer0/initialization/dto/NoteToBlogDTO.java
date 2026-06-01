package com.aezer0.initialization.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class NoteToBlogDTO {

    @NotNull(message = "笔记ID不能为空")
    private Long noteId;

    private String summary;

    private String coverUrl;

    private Long categoryId;

    private List<Long> tagIds;

    private Boolean isTop;
}
