package com.aezer0.initialization.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("t_register_setting")
public class RegisterSetting implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("register_enabled")
    private Boolean registerEnabled;

    @TableField("invite_code")
    private String inviteCode;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
