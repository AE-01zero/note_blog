package com.aezer0.initialization.enums;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
public enum FileTypeEnum {
    IMAGE("图片", Arrays.asList("image/jpeg", "image/png", "image/gif", "image/bmp")),
    VIDEO("视频", Arrays.asList("video/mp4", "video/avi", "video/mpeg")),
    DOCUMENT("文档", Arrays.asList("application/pdf", 
                                "application/msword", 
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
    OTHER("其他", new ArrayList<>());

    private final String description;
    private final List<String> mimeTypes;

    FileTypeEnum(String description, List<String> mimeTypes) {
        this.description = description;
        this.mimeTypes = mimeTypes;
    }

    public static FileTypeEnum getByMimeType(String mimeType) {
        for (FileTypeEnum type : FileTypeEnum.values()) {
            if (type.getMimeTypes().contains(mimeType)) {
                return type;
            }
        }
        return OTHER;
    }

    // getters...
}