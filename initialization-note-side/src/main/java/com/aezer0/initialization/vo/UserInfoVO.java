package com.aezer0.initialization.vo;


import lombok.Data;

import java.io.Serializable;

@Data
public class UserInfoVO implements Serializable {

    /**
     * 用户ID
     */
    private Long id;


    /**
     * 用户角色 1-求职者 2-招聘者 3-管理员
     */
    private Integer role_type;

    /**
     * 用户名
     */
    private String username;

    /**
     * 头像URL
     */
    private String avatar_url;



    /**
     * 性别 0-未设置 1-男 2-女
     */
    private Integer gender;





}
