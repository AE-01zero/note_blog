package com.aezer0.initialization.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class CurrentUserVO {

    private Long id;

    private String phone;

    private Integer roleType;

    private String username;

    private String avatarUrl;

    private String realName;

    private Integer gender;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date birthDate;

    private Integer workYears;

    private Integer education;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastLoginTime;
}
