package com.aezer0.initialization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiModelConfigUpdateDTO {

    @NotBlank(message = "API地址不能为空")
    private String baseUrl;

    /** 为空时保留原值 */
    private String apiKey;

    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    private Boolean logRequests;
    private Boolean logResponses;
    private Integer maxSegmentsPerBatch;
}
