package com.ldd.initialization.service;

import java.util.Map;

/**
 * 知识库同步服务
 * 用于从 knowledge-base 目录自动同步文档到个人知识库和共享知识库
 */
public interface KnowledgeBaseSyncService {

    /**
     * 同步 knowledge-base 目录下的所有文档
     * @return 同步结果统计
     */
    Map<String, Object> syncAllDocuments();

    /**
     * 同步指定分类下的文档
     * @param categoryName 分类名称（解析后的名称，如 "AI安全"）
     * @return 同步结果
     */
    Map<String, Object> syncCategory(String categoryName);

    /**
     * 获取同步预览（不实际执行同步）
     * @return 预览信息
     */
    Map<String, Object> getSyncPreview();

    /**
     * 解析文件夹名称，去掉序号前缀
     * 例如: "01-AI安全" -> "AI安全"
     *      "02-Android安全" -> "Android安全"
     *      "1_AI安全" -> "AI安全"
     *      "1、ai安全" -> "AI安全"
     * @param folderName 原始文件夹名称
     * @return 解析后的名称
     */
    String parseFolderName(String folderName);

    /**
     * 解析文件名，移除序号后缀
     * 例如: "01-AI安全入门.md" -> "AI安全入门.md"
     *      "ai安全-1.md" -> "ai安全.md"
     *      "ai安全_1.md" -> "ai安全.md"
     * @param fileName 原始文件名
     * @return 解析后的文件名
     */
    String parseFileName(String fileName);

    /**
     * 同步单个文件到知识库
     * @param filePath 文件路径
     * @param category 分类名称
     * @return 同步结果
     */
    Map<String, Object> syncSingleFile(String filePath, String category);
}
