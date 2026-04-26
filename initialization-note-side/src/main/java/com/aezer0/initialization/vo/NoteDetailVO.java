package com.aezer0.initialization.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记详情VO
 */
@Data
public class NoteDetailVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 所属笔记本ID
     */
    private Long notebookId;

    /**
     * 笔记标题
     */
    private String title;

    /**
     * Markdown 格式的笔记内容
     */
    private String contentMd;

    /**
     * HTML 格式的笔记内容
     */
    private String contentHtml;

    /**
     * 笔记状态（1-已发布 0-草稿）
     */
    private Integer status;

    /**
     * 是否置顶
     */
    private Boolean isPinned;

    /**
     * 查看次数
     */
    private Integer viewCount;

    /**
     * 字数统计
     */
    private Integer wordCount;

    /**
     * 笔记本名称
     */
    private String notebookName;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
} 