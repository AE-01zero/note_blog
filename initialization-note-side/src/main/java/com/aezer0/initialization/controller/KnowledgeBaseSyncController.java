package com.aezer0.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.aezer0.initialization.result.Result;
import com.aezer0.initialization.service.KnowledgeBaseSyncService;
import com.aezer0.initialization.utils.UserUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 知识库同步控制器
 * 提供从 knowledge-base 目录自动同步文档到知识库的 API
 */
@RestController
@RequestMapping("/api/knowledge-base-sync")
@Slf4j
public class KnowledgeBaseSyncController {

    @Autowired
    private KnowledgeBaseSyncService knowledgeBaseSyncService;

    /**
     * 同步所有知识库文档
     * 将 knowledge-base 目录下的所有文档同步到个人知识库和共享知识库
     *
     * @return 同步结果
     */
    @PostMapping("/sync-all")
    @SaCheckLogin
    public Result<Map<String, Object>> syncAllDocuments() {
        // 仅允许默认管理员操作
        UserUtils.requireDefaultAdmin();

        log.info("用户 {} 触发知识库全量同步", UserUtils.getCurrentUserId());
        Map<String, Object> result = knowledgeBaseSyncService.syncAllDocuments();

        if ((Boolean) result.get("success")) {
            return Result.success(result);
        } else {
            return Result.error(result.get("message").toString());
        }
    }

    /**
     * 同步指定分类
     *
     * @param categoryName 分类名称（解析后的名称，如 "AI安全"）
     * @return 同步结果
     */
    @PostMapping("/sync-category")
    @SaCheckLogin
    public Result<Map<String, Object>> syncCategory(@RequestParam String categoryName) {
        // 仅允许默认管理员操作
        UserUtils.requireDefaultAdmin();

        log.info("用户 {} 触发分类 '{}' 的同步", UserUtils.getCurrentUserId(), categoryName);
        Map<String, Object> result = knowledgeBaseSyncService.syncCategory(categoryName);

        if ((Boolean) result.get("success")) {
            return Result.success(result);
        } else {
            return Result.error(result.get("message").toString());
        }
    }

    /**
     * 同步单个文件
     *
     * @param filePath 文件完整路径
     * @param category 分类名称
     * @return 同步结果
     */
    @PostMapping("/sync-file")
    @SaCheckLogin
    public Result<Map<String, Object>> syncSingleFile(
            @RequestParam String filePath,
            @RequestParam(required = false) String category) {
        // 仅允许默认管理员操作
        UserUtils.requireDefaultAdmin();

        log.info("用户 {} 触发文件 '{}' 的同步，分类: {}", UserUtils.getCurrentUserId(), filePath, category);
        Map<String, Object> result = knowledgeBaseSyncService.syncSingleFile(filePath, category);

        if ((Boolean) result.get("success")) {
            return Result.success(result);
        } else {
            return Result.error(result.get("message").toString());
        }
    }

    /**
     * 获取同步预览（不实际执行同步）
     * 显示将要同步的分类和文件信息
     *
     * @return 预览信息
     */
    @GetMapping("/preview")
    @SaCheckLogin
    public Result<Map<String, Object>> getSyncPreview() {
        // 仅允许默认管理员操作
        UserUtils.requireDefaultAdmin();

        log.info("用户 {} 获取知识库同步预览", UserUtils.getCurrentUserId());
        Map<String, Object> result = knowledgeBaseSyncService.getSyncPreview();

        if ((Boolean) result.get("success")) {
            return Result.success(result);
        } else {
            return Result.error(result.get("message").toString());
        }
    }

    /**
     * 解析文件夹名称
     * 测试接口：验证解析逻辑是否正确
     *
     * @param folderName 文件夹原始名称
     * @return 解析后的名称
     */
    @GetMapping("/parse-folder-name")
    @SaCheckLogin
    public Result<String> parseFolderName(@RequestParam String folderName) {
        String parsed = knowledgeBaseSyncService.parseFolderName(folderName);
        return Result.success(parsed);
    }

    /**
     * 解析文件名
     * 测试接口：验证文件名解析逻辑是否正确
     *
     * @param fileName 文件原始名称
     * @return 解析后的名称
     */
    @GetMapping("/parse-file-name")
    @SaCheckLogin
    public Result<String> parseFileName(@RequestParam String fileName) {
        String parsed = knowledgeBaseSyncService.parseFileName(fileName);
        return Result.success(parsed);
    }
}
