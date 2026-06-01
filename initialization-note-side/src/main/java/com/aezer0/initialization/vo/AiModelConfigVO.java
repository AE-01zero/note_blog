package com.aezer0.initialization.vo;

import lombok.Data;

@Data
public class AiModelConfigVO {

    private Long id;
    private String modelType;
    private String baseUrl;
    /** 脱敏后的 api-key，如 sk-abc...xyz */
    private String apiKey;
    private String modelName;
    private Boolean logRequests;
    private Boolean logResponses;
    private Integer maxSegmentsPerBatch;

    public static AiModelConfigVO from(com.aezer0.initialization.domain.AiModelConfig config) {
        AiModelConfigVO vo = new AiModelConfigVO();
        vo.setId(config.getId());
        vo.setModelType(config.getModelType());
        vo.setBaseUrl(config.getBaseUrl());
        vo.setModelName(config.getModelName());
        vo.setLogRequests(config.getLogRequests());
        vo.setLogResponses(config.getLogResponses());
        vo.setMaxSegmentsPerBatch(config.getMaxSegmentsPerBatch());
        // api-key 脱敏
        String key = config.getApiKey();
        if (key != null && key.length() > 8) {
            vo.setApiKey(key.substring(0, 6) + "..." + key.substring(key.length() - 4));
        } else {
            vo.setApiKey("******");
        }
        return vo;
    }
}
