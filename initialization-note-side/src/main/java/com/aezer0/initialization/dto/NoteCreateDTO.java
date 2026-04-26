package com.aezer0.initialization.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 笔记创建DTO
 */
@Data
public class NoteCreateDTO {

    /**
     * 所属笔记本ID
     */
    @NotNull(message = "笔记本ID不能为空")
    private Long notebookId;

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