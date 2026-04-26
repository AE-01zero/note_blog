package com.aezer0.initialization.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterSettingsUpdateDTO {

    @NotNull(message = "注册开关不能为空")
    private Boolean registerEnabled;

    @Size(max = 64, message = "邀请码最大64位")
    private String inviteCode;
}
