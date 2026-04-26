package com.aezer0.initialization.enums;

import lombok.Getter;

/**
 * 知识库角色枚举
 */
@Getter
public enum KnowledgeBaseRoleEnum {
    
    CREATOR(1, "创建者"),
    MEMBER(2, "成员");

    private final int value;
    private final String desc;

    KnowledgeBaseRoleEnum(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    /**
     * 根据 value 获取对应的枚举
     */
    public static KnowledgeBaseRoleEnum getByValue(int value) {
        for (KnowledgeBaseRoleEnum role : KnowledgeBaseRoleEnum.values()) {
            if (role.getValue() == value) {
                return role;
            }
        }
        return null;
    }

    /**
     * 根据 value 获取对应的 desc
     */
    public static String getDescByValue(int value) {
        KnowledgeBaseRoleEnum role = getByValue(value);
        return role != null ? role.getDesc() : null;
    }
} 