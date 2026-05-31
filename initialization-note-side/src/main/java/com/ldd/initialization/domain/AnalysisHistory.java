package com.ldd.initialization.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("t_analysis_history")
@Data
public class AnalysisHistory implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("module_type")
    private String moduleType;

    @TableField("file_name")
    private String fileName;

    @TableField("file_size")
    private Long fileSize;

    @TableField("work_dir")
    private String workDir;

    @TableField("analysis_result")
    private String analysisResult;  // JSONB string

    @TableField("verdict")
    private String verdict;

    @TableField("risk_level")
    private String riskLevel;

    @TableField("summary")
    private String summary;

    @TableField("extra_info")
    private String extraInfo;  // JSONB string

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
