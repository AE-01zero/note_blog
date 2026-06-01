package com.aezer0.initialization.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("t_ai_model_config")
@Data
public class AiModelConfig implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** chat / streaming / embedding */
    @TableField("model_type")
    private String modelType;

    @TableField("base_url")
    private String baseUrl;

    @TableField("api_key")
    private String apiKey;

    @TableField("model_name")
    private String modelName;

    @TableField("log_requests")
    private Boolean logRequests;

    @TableField("log_responses")
    private Boolean logResponses;

    /** 仅 embedding 使用 */
    @TableField("max_segments_per_batch")
    private Integer maxSegmentsPerBatch;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
