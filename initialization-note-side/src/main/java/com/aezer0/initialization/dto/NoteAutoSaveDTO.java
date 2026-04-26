package com.aezer0.initialization.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 笔记自动保存DTO
 */
@Data
public class NoteAutoSaveDTO {

    /**
     * 笔记ID
     */
    @NotNull(message = "笔记ID不能为空")
    private Long noteId;

    /**
     * Markdown 格式的笔记内容
     */
    private String contentMd;
} 