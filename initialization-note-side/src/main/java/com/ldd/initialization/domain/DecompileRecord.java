package com.ldd.initialization.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("t_decompile_record")
@Data
public class DecompileRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("analysis_history_id")
    private Long analysisHistoryId;

    @TableField("apk_file_name")
    private String apkFileName;

    @TableField("package_name")
    private String packageName;

    @TableField("work_dir")
    private String workDir;

    @TableField("file_count")
    private Integer fileCount;

    @TableField("total_size")
    private Long totalSize;

    @TableField("status")
    private String status;  // ACTIVE / DELETED

    @TableField("error_msg")
    private String errorMsg;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
