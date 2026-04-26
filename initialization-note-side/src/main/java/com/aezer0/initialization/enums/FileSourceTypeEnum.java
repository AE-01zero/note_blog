package com.aezer0.initialization.enums;

import lombok.Getter;

/**
 * 文件来源类型枚举
 */
@Getter
public enum FileSourceTypeEnum {
    
    LOCAL_UPLOAD(1, "本地上传"),
    PERSONAL_COPY(2, "个人知识库复制"),
    NOTE_TO_PDF(3, "笔记转换PDF");

    private final int value;
    private final String desc;

    FileSourceTypeEnum(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    /**
     * 根据 value 获取对应的枚举
     */
    public static FileSourceTypeEnum getByValue(int value) {
        for (FileSourceTypeEnum type : FileSourceTypeEnum.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }

    /**
     * 根据 value 获取对应的 desc
     */
    public static String getDescByValue(int value) {
        FileSourceTypeEnum type = getByValue(value);
        return type != null ? type.getDesc() : null;
    }
} 