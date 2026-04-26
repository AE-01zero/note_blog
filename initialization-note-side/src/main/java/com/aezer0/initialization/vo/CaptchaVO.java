package com.aezer0.initialization.vo;

import lombok.Data;

/**
 * 图形验证码响应DTO
 */
@Data
public class CaptchaVO {
    private String captchaId; // 验证码唯一ID
    private String imageBase64; // Base64编码的验证码图片
}