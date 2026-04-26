package com.aezer0.initialization.enums;

import lombok.Getter;

/**
 * 用户角色枚举
 */
@Getter
public enum RoleType {
    ADMIN(1, "ADMIN"),
    USER(2, "USER");

    private final int value;
    private final String desc;

    RoleType(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    /**
     * 根据 value 获取对应的 desc
     *
     * @param value 要查找的值
     * @return 对应的 desc，如果未找到则返回 null
     */
    public static String getDescByValue(int value) {
        for (RoleType roleType : RoleType.values()) {
            if (roleType.getValue() == value) {
                return roleType.getDesc();
            }
        }
        return null;
    }
}