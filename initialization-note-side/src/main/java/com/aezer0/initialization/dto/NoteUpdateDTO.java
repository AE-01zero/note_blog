package com.aezer0.initialization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 笔记更新DTO
 */
@Data
public class NoteUpdateDTO {

    /**
     * 笔记ID
     */
    @NotNull(message = "笔记ID不能为空")
    private Long noteId;

    /**
     * 笔记标题
     */
    @NotBlank(message = "笔记标题不能为空")
    @Size(max = 255, message = "笔记标题长度不能超过255个字符")
    private String title;

    /**
     * Markdown 格式的笔记内容
     */
    private String contentMd;

    /**
     * 笔记状态（1-已发布 0-草稿）
     */
    private Integer status;

    /**
     * 是否置顶
     */
    private Boolean isPinned;

    /**
     * 标签列表
     */
    private List<String> tags;
} 