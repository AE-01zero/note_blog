package com.aezer0.initialization.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_blog_post")
public class BlogPost implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String title;

    private String summary;

    @TableField("content_md")
    private String contentMd;

    @TableField("content_html")
    private String contentHtml;

    @TableField("cover_url")
    private String coverUrl;

    @TableField("category_id")
    private Long categoryId;

    private Integer status;

    @TableField("is_top")
    private Boolean isTop;

    @TableField("view_count")
    private Integer viewCount;

    @TableField("source_note_id")
    private Long sourceNoteId;

    @TableField("publish_time")
    private LocalDateTime publishTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
