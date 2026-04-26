package com.aezer0.initialization.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 状态枚举类
 */
@Getter
public enum StatusEnum {
    DISABLED(0, "禁用"),
    ENABLED(1, "启用"),
    PENDING(2, "待审核");

    private final Integer code;
    private final String description;

    StatusEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取对应的 StatusEnum
     * @param code 状态码
     * @return StatusEnum 枚举值，如果不存在返回 null
     */
    public static StatusEnum getStatusEnumByCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(StatusEnum.values())
                .filter(status -> status.getCode().equals(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据 code 获取状态描述
     * @param code 状态码
     * @return 状态描述，如果不存在返回 null
     */
    public static String getDescriptionByCode(Integer code) {
        StatusEnum statusEnum = getStatusEnumByCode(code);
        return statusEnum != null ? statusEnum.getDescription() : null;
    }
}