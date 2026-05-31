package com.ldd.initialization.vo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RegisterSettingsVO {

    private Boolean registerEnabled;

    private String inviteCode;
}
