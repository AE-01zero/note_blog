package com.aezer0.initialization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
public class ChatRequestDTO {


    private String userId;

    @NotBlank(message = "会话ID不能为空")
    private String memoryId;

    @NotBlank(message = "消息内容不能为空")
    private String message;

    /** 可选：限定检索的知识库分类 */
    private String categoryFilter;

    /** 模式：strict(严格-仅知识库) / relaxed(宽松-可扩展) */
    private String mode = "relaxed";
}
