package com.aezer0.initialization.vo;

import lombok.Data;

@Data
public class AiModelReloadResultVO {

    /**
     * 1: 热更新通过, 2: 热更新失败
     */
    private Integer status;

    private String message;

    private String modelType;

    private String baseUrl;

    private String modelName;

    /**
     * openai / anthropic
     */
    private String protocol;

    private Long checkCostMs;
}
