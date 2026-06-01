package com.aezer0.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.aezer0.initialization.domain.AnalysisHistory;
import com.aezer0.initialization.domain.DecompileRecord;
import com.aezer0.initialization.result.Result;
import com.aezer0.initialization.service.ai.AiSecurityService;
import com.aezer0.initialization.service.ai.ApkAnalysisService;
import com.aezer0.initialization.service.ai.SoAnalysisService;
import com.aezer0.initialization.service.ai.ProtocolAnalysisService;
import com.aezer0.initialization.service.ai.StreamingAnalysisService;
import com.aezer0.initialization.service.apktool.ApkToolService;
import com.aezer0.initialization.service.ai.SecurityAnalysisLlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI安全分析控制器
 * 提供APK分析、SO分析、协议分析、Prompt注入检测等功能
 */
@RestController
@RequestMapping("/api/ai-analysis")
@Slf4j
public class AiSecurityController {

    @Autowired
    private ApkAnalysisService apkAnalysisService;

    @Autowired
    private SoAnalysisService soAnalysisService;

    @Autowired
    private ProtocolAnalysisService protocolAnalysisService;

    @Autowired
    private AiSecurityService aiSecurityService;

    @Autowired
    private ApkToolService apkToolService;

    @Autowired
    private SecurityAnalysisLlmService llmService;

    @Autowired
    private StreamingAnalysisService streamingAnalysisService;

    @Autowired
    private com.aezer0.initialization.service.AnalysisHistoryService analysisHistoryService;

    private static final int MAX_FILE_SIZE = 200 * 1024 * 1024; // 200MB

    // ========== ApkTool逆向分析API ==========

    /**
     * APK逆向分析（使用ApkTool）
     */
    @SaCheckLogin
    @PostMapping("/apk/decompile")
    public Result<Map<String, Object>> decompileApk(@RequestParam("file") MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小超过限制 (最大200MB)");
        }
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        log.info("用户 {} 发起APK逆向请求: {}", userId, file.getOriginalFilename());

        try {
            Map<String, Object> result = apkToolService.decompile(
                    file.getBytes(),
                    file.getOriginalFilename()
            );
            String workDir = (String) result.get("workDir");
            boolean success = Boolean.TRUE.equals(result.get("success"));

            // 持久化分析历史
            String verdict = success ? "SUCCESS" : "FAILED";
            AnalysisHistory history = analysisHistoryService.saveAnalysis(
                    userId, "APK_REVERSE", file.getOriginalFilename(),
                    file.getSize(), workDir, result, verdict, "INFO");

            // 持久化反编译文件记录
            if (success && workDir != null) {
                int fileCount = result.get("fileCount") instanceof Number ? ((Number) result.get("fileCount")).intValue() : 0;
                analysisHistoryService.saveDecompileRecord(
                        userId, history.getId(), file.getOriginalFilename(),
                        null, workDir, fileCount, 0);
            }

            return Result.success(result);
        } catch (Exception e) {
            // 失败时也记录
            analysisHistoryService.saveAnalysis(userId, "APK_REVERSE", file.getOriginalFilename(),
                    file.getSize(), null, Map.of("error", e.getMessage()), "FAILED", "INFO");
            log.error("APK逆向失败", e);
            return Result.error("APK逆向失败: " + e.getMessage());
        }
    }

    /**
     * 获取源码文件列表
     */
    @SaCheckLogin
    @GetMapping("/apk/files")
    public Result<List<Map<String, Object>>> listSourceFiles(
            @RequestParam("workDir") String workDir) {
        try {
            Path decompiledDir = Path.of(workDir, "input");
            List<Map<String, Object>> files = apkToolService.listSourceFiles(decompiledDir);
            return Result.success(files);
        } catch (Exception e) {
            log.error("获取文件列表失败", e);
            return Result.error("获取文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 读取源码文件内容
     */
    @SaCheckLogin
    @GetMapping("/apk/file/content")
    public Result<Map<String, Object>> readFileContent(
            @RequestParam("workDir") String workDir,
            @RequestParam("path") String path) {
        try {
            Path decompiledDir = Path.of(workDir, "input");
            Map<String, Object> result = apkToolService.readFile(decompiledDir, path);
            return Result.success(result);
        } catch (Exception e) {
            log.error("读取文件失败", e);
            return Result.error("读取文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取静态交叉引用 (Xref Tree)
     */
    @SaCheckLogin
    @GetMapping("/apk/file/xref")
    public Result<Map<String, Object>> getFileXrefs(
            @RequestParam("workDir") String workDir,
            @RequestParam("path") String path) {
        try {
            Path decompiledDir = Path.of(workDir, "input");
            Map<String, Object> xrefs = apkToolService.calculateXrefs(decompiledDir, path);
            return Result.success(xrefs);
        } catch (Exception e) {
            log.error("获取交叉引用失败", e);
            return Result.error("获取交叉引用失败: " + e.getMessage());
        }
    }

    @SaCheckLogin
    @GetMapping("/apk/file/search")
    public Result<List<Map<String, Object>>> searchCodebase(
            @RequestParam("workDir") String workDir,
            @RequestParam("q") String query,
            @RequestParam(value = "isRegex", defaultValue = "false") boolean isRegex,
            @RequestParam(value = "caseSensitive", defaultValue = "false") boolean caseSensitive,
            @RequestParam(value = "fileType", defaultValue = "all") String fileType,
            @RequestParam(value = "excludeThirdParty", defaultValue = "false") boolean excludeThirdParty) {
        try {
            Path decompiledDir = Path.of(workDir, "input");
            List<Map<String, Object>> results = apkToolService.searchInCodebase(
                    decompiledDir, query, isRegex, caseSensitive, fileType, excludeThirdParty);
            return Result.success(results);
        } catch (Exception e) {
            log.error("静态检索失败", e);
            return Result.error("静态检索失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件树结构
     */
    @SaCheckLogin
    @GetMapping("/apk/file-tree")
    public Result<Map<String, Object>> getFileTree(
            @RequestParam("workDir") String workDir) {
        try {
            Path decompiledDir = Path.of(workDir, "input");
            Map<String, Object> tree = apkToolService.getFileTree(decompiledDir);
            return Result.success(tree);
        } catch (Exception e) {
            log.error("获取文件树失败", e);
            return Result.error("获取文件树失败: " + e.getMessage());
        }
    }

    /**
     * AI分析指定文件
     */
    @SaCheckLogin
    @PostMapping("/apk/file/analyze")
    public Result<Map<String, Object>> analyzeFile(
            @RequestParam("workDir") String workDir,
            @RequestParam("path") String path,
            @RequestParam("fileType") String fileType,
            @RequestParam(value = "aiAssist", defaultValue = "true") boolean aiAssist) {
        try {
            Path decompiledDir = Path.of(workDir, "input");
            Map<String, Object> fileContent = apkToolService.readFile(decompiledDir, path);

            if (!(boolean) fileContent.getOrDefault("success", false)) {
                return Result.error((String) fileContent.get("error"));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("fileInfo", fileContent);

            if (aiAssist) {
                // 根据文件类型调用不同的AI分析
                Map<String, Object> aiAnalysis = performFileAiAnalysis(fileContent, fileType);
                result.put("aiAnalysis", aiAnalysis);
            }

            return Result.success(result);
        } catch (Exception e) {
            log.error("AI分析失败", e);
            return Result.error("AI分析失败: " + e.getMessage());
        }
    }

    /**
     * AI辅助分析SO文件
     */
    @SaCheckLogin
    @PostMapping("/apk/so/analyze")
    public Result<Map<String, Object>> analyzeSoFromApk(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "aiAssist", defaultValue = "false") boolean aiAssist) {
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小超过限制 (最大200MB)");
        }
        try {
            Map<String, Object> result = soAnalysisService.analyzeSo(file.getBytes(), file.getOriginalFilename(), aiAssist);
            return Result.success(result);
        } catch (Exception e) {
            log.error("SO分析失败", e);
            return Result.error("SO分析失败: " + e.getMessage());
        }
    }

    /**
     * AI辅助分析解包工作区中的指定SO文件
     */
    @SaCheckLogin
    @PostMapping("/apk/so/analyze-workspace")
    public Result<Map<String, Object>> analyzeSoFromWorkspace(
            @RequestParam("workDir") String workDir,
            @RequestParam("path") String path,
            @RequestParam(value = "aiAssist", defaultValue = "false") boolean aiAssist) {
        try {
            Path decompiledDir = Path.of(workDir, "input");
            Path soFilePath = decompiledDir.resolve(path);
            if (!Files.exists(soFilePath)) {
                return Result.error("SO文件不存在: " + path);
            }
            byte[] soBytes = Files.readAllBytes(soFilePath);
            String fileName = soFilePath.getFileName().toString();
            Map<String, Object> result = soAnalysisService.analyzeSo(soBytes, fileName, aiAssist);
            return Result.success(result);
        } catch (Exception e) {
            log.error("工作区SO分析失败", e);
            return Result.error("SO分析失败: " + e.getMessage());
        }
    }

    /**
     * 获取源码还原评估
     */
    @SaCheckLogin
    @GetMapping("/apk/reconstruction-assessment")
    public Result<Map<String, Object>> getReconstructionAssessment(
            @RequestParam("workDir") String workDir) {
        try {
            Path decompiledDir = Path.of(workDir, "input");
            Map<String, Object> analysis = apkToolService.analyzeDecompiledOutput(decompiledDir);

            @SuppressWarnings("unchecked")
            Map<String, Object> reconstruction = (Map<String, Object>) analysis.getOrDefault("reconstruction", new HashMap<>());
            return Result.success(reconstruction);
        } catch (Exception e) {
            log.error("获取评估失败", e);
            return Result.error("获取评估失败: " + e.getMessage());
        }
    }

    /**
     * AI辅助源码分析
     */
    @SaCheckLogin
    @PostMapping("/apk/source-analyze")
    public Result<Map<String, Object>> analyzeSourceCode(
            @RequestParam("workDir") String workDir,
            @RequestParam(value = "focusAreas", required = false) String focusAreas) {
        try {
            Path decompiledDir = Path.of(workDir, "input");
            Map<String, Object> analysis = apkToolService.analyzeDecompiledOutput(decompiledDir);

            // 执行AI辅助分析
            Map<String, Object> aiAnalysis = performSourceCodeAiAnalysis(analysis, focusAreas);
            analysis.put("aiAnalysis", aiAnalysis);

            return Result.success(analysis);
        } catch (Exception e) {
            log.error("源码分析失败", e);
            return Result.error("源码分析失败: " + e.getMessage());
        }
    }

    // ========== AI分析辅助方法 ==========

    private Map<String, Object> performFileAiAnalysis(Map<String, Object> fileContent, String fileType) {
        Map<String, Object> aiAnalysis = new HashMap<>();
        String content = (String) fileContent.get("content");
        String path = (String) fileContent.get("path");

        log.info("开始调用真实大模型对文件进行单代码逆向审计: {}", path);

        // 构建深度上下文
        String prompt = String.format("""
                请对以下反编译得到的文件进行深度安全审计与漏洞扫描：
                文件路径: %s
                文件类型: %s

                文件内容:
                ```%s
                %s
                ```
                """, path, fileType, fileType, content);

        String analysisResult = llmService.analyzeFileCode(prompt);

        aiAnalysis.put("analysis", analysisResult);
        aiAnalysis.put("model", "qwen-plus");
        aiAnalysis.put("timestamp", System.currentTimeMillis());
        aiAnalysis.put("confidence", 0.95);
        aiAnalysis.put("moduleType", "FILE");

        return aiAnalysis;
    }

    private Map<String, Object> performSourceCodeAiAnalysis(Map<String, Object> analysis, String focusAreas) {
        Map<String, Object> aiAnalysis = new HashMap<>();

        log.info("开始调用真实大模型对整个APK进行全局源码级合规安全评估...");

        // 提取并序列化反编译元数据，提供大模型宏观感知
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = (Map<String, Object>) analysis.getOrDefault("manifest", new HashMap<>());
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) manifest.getOrDefault("permissions", List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> smali = (Map<String, Object>) analysis.getOrDefault("smali", new HashMap<>());
        @SuppressWarnings("unchecked")
        Map<String, Object> nativeLibs = (Map<String, Object>) analysis.getOrDefault("nativeLibraries", new HashMap<>());
        @SuppressWarnings("unchecked")
        Map<String, Object> reconstruction = (Map<String, Object>) analysis.getOrDefault("reconstruction", new HashMap<>());

        StringBuilder summary = new StringBuilder();
        summary.append("【反编译APK全局安全扫描元数据】\n\n");
        summary.append("- 核心组件统计:\n");
        summary.append("  * Activity数量: ").append(manifest.getOrDefault("activityCount", 0)).append("\n");
        summary.append("  * Service数量: ").append(manifest.getOrDefault("serviceCount", 0)).append("\n");
        summary.append("  * Receiver数量: ").append(manifest.getOrDefault("receiverCount", 0)).append("\n");
        summary.append("- 申请敏感权限列表:\n");
        if (permissions.isEmpty()) {
            summary.append("  * (无特殊高危权限申请)\n");
        } else {
            for (String perm : permissions) {
                summary.append("  * ").append(perm).append("\n");
            }
        }
        summary.append("- 代码库物理规模:\n");
        summary.append("  * 包含Smali类总数: ").append(smali.getOrDefault("classCount", 0)).append("\n");
        summary.append("  * 声明方法总数: ").append(smali.getOrDefault("methodCount", 0)).append("\n");
        summary.append("  * 包层级结构总数: ").append(smali.getOrDefault("packageCount", 0)).append("\n");
        summary.append("- 原生共享库(Native SO)统计:\n");
        int libCount = (int) nativeLibs.getOrDefault("count", 0);
        summary.append("  * 依赖 Native SO 库数量: ").append(libCount).append("\n");
        if (libCount > 0) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> libs = (List<Map<String, Object>>) nativeLibs.getOrDefault("libraries", List.of());
            for (Map<String, Object> lib : libs) {
                summary.append("    - SO名称: ").append(lib.get("name")).append(" (架构: ").append(lib.get("arch")).append(")\n");
            }
        }
        summary.append("- 源码可重构还原评估:\n");
        summary.append("  * 评估还原等级: ").append(reconstruction.getOrDefault("level", "UNKNOWN")).append("\n");
        summary.append("  * 还原可能性描述: ").append(reconstruction.getOrDefault("description", "")).append("\n");
        @SuppressWarnings("unchecked")
        List<String> limitations = (List<String>) reconstruction.getOrDefault("limitations", List.of());
        if (!limitations.isEmpty()) {
            summary.append("  * 源码还原主要限制因子:\n");
            for (String limit : limitations) {
                summary.append("    - ").append(limit).append("\n");
            }
        }

        if (focusAreas != null && !focusAreas.trim().isEmpty()) {
            summary.append("\n【用户审计重点关注方向（AI优先核验）】:\n");
            summary.append(focusAreas.trim()).append("\n");
        }

        // 调用真实LLM进行架构级审计
        String reportResult = llmService.analyzeGlobalMetadata(summary.toString());

        aiAnalysis.put("report", reportResult);
        aiAnalysis.put("model", "qwen-plus");
        aiAnalysis.put("timestamp", System.currentTimeMillis());
        aiAnalysis.put("confidence", 0.96);
        aiAnalysis.put("moduleType", "GLOBAL");

        return aiAnalysis;
    }

    /**
     * APK静态分析
     */
    @SaCheckLogin
    @PostMapping("/apk")
    public Result<Map<String, Object>> analyzeApk(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "depth", defaultValue = "standard") String depth,
            @RequestParam(value = "aiEnhance", defaultValue = "true") boolean aiEnhance,
            @RequestParam(value = "sandboxMode", defaultValue = "false") boolean sandboxMode,
            @RequestParam(value = "sandboxConfig", defaultValue = "android13") String sandboxConfig) {

        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小超过限制 (最大200MB)");
        }
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        log.info("用户 {} 发起APK分析请求: {}", userId, file.getOriginalFilename());

        try {
            Map<String, Object> result = apkAnalysisService.analyzeApk(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    depth,
                    aiEnhance,
                    sandboxMode,
                    sandboxConfig
            );
            return Result.success(result);
        } catch (Exception e) {
            log.error("APK分析失败", e);
            return Result.error("APK分析失败: " + e.getMessage());
        }
    }

    /**
     * SO文件分析
     */
    @SaCheckLogin
    @PostMapping("/so")
    public Result<Map<String, Object>> analyzeSo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "aiAssist", defaultValue = "false") boolean aiAssist) {
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小超过限制 (最大200MB)");
        }
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        log.info("用户 {} 发起SO分析请求: {}, aiAssist={}", userId, file.getOriginalFilename(), aiAssist);

        try {
            Map<String, Object> result = soAnalysisService.analyzeSo(file.getBytes(), file.getOriginalFilename(), aiAssist);
            return Result.success(result);
        } catch (Exception e) {
            log.error("SO分析失败", e);
            return Result.error("SO分析失败: " + e.getMessage());
        }
    }

    /**
     * 协议分析
     */
    @SaCheckLogin
    @PostMapping("/protocol")
    public Result<Map<String, Object>> analyzeProtocol(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "aiAssist", defaultValue = "false") boolean aiAssist) {
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小超过限制 (最大200MB)");
        }
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        log.info("用户 {} 发起协议分析请求: {}, AI辅助: {}", userId, file.getOriginalFilename(), aiAssist);

        try {
            Map<String, Object> result = protocolAnalysisService.analyzeProtocol(file.getBytes(), aiAssist);
            return Result.success(result);
        } catch (Exception e) {
            log.error("协议分析失败", e);
            return Result.error("协议分析失败: " + e.getMessage());
        }
    }

    /**
     * 知识库检索
     */
    @SaCheckLogin
    @GetMapping("/search")
    public Result<Map<String, Object>> searchKnowledge(
            @RequestParam("q") String query,
            @RequestParam(value = "type", defaultValue = "all") String type,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {

        try {
            Map<String, Object> results = aiSecurityService.searchKnowledge(query, type, limit);
            return Result.success(results);
        } catch (Exception e) {
            log.error("知识检索失败", e);
            return Result.error("知识检索失败: " + e.getMessage());
        }
    }

    /**
     * 获取分析历史（支持模块筛选和分页）
     */
    @SaCheckLogin
    @GetMapping("/history")
    public Result<Map<String, Object>> getAnalysisHistory(
            @RequestParam(value = "moduleType", required = false) String moduleType,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        try {
            Map<String, Object> history = aiSecurityService.getAnalysisHistory(userId, moduleType, page, size);
            return Result.success(history);
        } catch (Exception e) {
            log.error("获取分析历史失败", e);
            return Result.error("获取分析历史失败: " + e.getMessage());
        }
    }

    /** 保存分析历史（前端主动持久化） */
    @SaCheckLogin
    @PostMapping("/history")
    public Result<Map<String, Object>> saveAnalysisHistory(@RequestBody Map<String, Object> data) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        try {
            String moduleType = (String) data.get("moduleType");
            String fileName = (String) data.get("fileName");
            Long fileSize = data.get("fileSize") instanceof Number ? ((Number) data.get("fileSize")).longValue() : 0L;
            String workDir = (String) data.get("workDir");
            String verdict = (String) data.getOrDefault("verdict", "UNKNOWN");
            String riskLevel = (String) data.getOrDefault("riskLevel", "INFO");

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) data.get("result");
            if (result == null) result = new HashMap<>();

            com.aezer0.initialization.domain.AnalysisHistory history =
                    aiSecurityService.saveAnalysisHistory(userId, moduleType, fileName, fileSize, workDir, result, verdict, riskLevel);

            Map<String, Object> resp = new HashMap<>();
            resp.put("id", history.getId());
            resp.put("success", true);
            return Result.success(resp);
        } catch (Exception e) {
            log.error("保存分析历史失败", e);
            return Result.error("保存失败: " + e.getMessage());
        }
    }

    /** 删除单条分析历史 */
    @SaCheckLogin
    @DeleteMapping("/history/{historyId}")
    public Result<Map<String, Object>> deleteAnalysisHistory(@PathVariable Long historyId) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        try {
            Map<String, Object> result = aiSecurityService.deleteAnalysisHistory(historyId, userId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("删除分析历史失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /** 清空所有分析历史 */
    @SaCheckLogin
    @DeleteMapping("/history")
    public Result<Map<String, Object>> clearAnalysisHistory() {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        try {
            Map<String, Object> result = aiSecurityService.clearAnalysisHistory(userId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("清空分析历史失败", e);
            return Result.error("清空失败: " + e.getMessage());
        }
    }

    // ========== 反编译文件管理API ==========

    /** 获取用户的反编译记录列表 */
    @SaCheckLogin
    @GetMapping("/decompile-records")
    public Result<List<DecompileRecord>> getDecompileRecords() {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        try {
            List<DecompileRecord> records = aiSecurityService.getDecompileRecords(userId);
            return Result.success(records);
        } catch (Exception e) {
            log.error("获取反编译记录失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /** 删除反编译记录并清理磁盘文件 */
    @SaCheckLogin
    @DeleteMapping("/decompile-records/{recordId}")
    public Result<Map<String, Object>> deleteDecompileRecord(@PathVariable Long recordId) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        try {
            Map<String, Object> result = aiSecurityService.deleteDecompileRecord(recordId, userId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("删除反编译记录失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取分析状态
     */
    @SaCheckLogin
    @GetMapping("/status/{taskId}")
    public Result<Map<String, Object>> getAnalysisStatus(@PathVariable String taskId) {
        try {
            Map<String, Object> status = aiSecurityService.getAnalysisStatus(taskId);
            return Result.success(status);
        } catch (Exception e) {
            log.error("获取分析状态失败", e);
            return Result.error("获取分析状态失败: " + e.getMessage());
        }
    }

    // ========== 流式分析 API (SSE 进度管线) ==========

    /**
     * 启动 APK 流式分析 — 上传文件，返回 taskId
     */
    @SaCheckLogin
    @PostMapping("/apk/start")
    public Result<Map<String, Object>> startApkAnalysis(@RequestParam("file") MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小超过限制 (最大200MB)");
        }
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        log.info("用户 {} 发起APK流式分析: {}", userId, file.getOriginalFilename());
        try {
            String taskId = streamingAnalysisService.startApkAnalysis(file.getBytes(), file.getOriginalFilename());
            return Result.success(Map.of("taskId", taskId));
        } catch (Exception e) {
            log.error("启动APK流式分析失败", e);
            return Result.error("启动分析失败: " + e.getMessage());
        }
    }

    /**
     * 订阅 APK 分析进度 (SSE)
     */
    @SaCheckLogin
    @GetMapping(value = "/apk/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamApkAnalysis(@PathVariable String taskId) {
        log.info("SSE 订阅 APK 分析: {}", taskId);
        return streamingAnalysisService.getTaskStream(taskId);
    }

    /**
     * 启动 SO 流式分析
     */
    @SaCheckLogin
    @PostMapping("/so/start")
    public Result<Map<String, Object>> startSoAnalysis(@RequestParam("file") MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小超过限制 (最大200MB)");
        }
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        log.info("用户 {} 发起SO流式分析: {}", userId, file.getOriginalFilename());
        try {
            String taskId = streamingAnalysisService.startSoAnalysis(file.getBytes(), file.getOriginalFilename());
            return Result.success(Map.of("taskId", taskId));
        } catch (Exception e) {
            log.error("启动SO流式分析失败", e);
            return Result.error("启动分析失败: " + e.getMessage());
        }
    }

    /**
     * 订阅 SO 分析进度 (SSE)
     */
    @SaCheckLogin
    @GetMapping(value = "/so/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamSoAnalysis(@PathVariable String taskId) {
        log.info("SSE 订阅 SO 分析: {}", taskId);
        return streamingAnalysisService.getTaskStream(taskId);
    }

    /**
     * 启动 Protocol 流式分析
     */
    @SaCheckLogin
    @PostMapping("/protocol/start")
    public Result<Map<String, Object>> startProtocolAnalysis(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "aiAssist", defaultValue = "false") boolean aiAssist) {
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小超过限制 (最大200MB)");
        }
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        log.info("用户 {} 发起协议流式分析: {}, AI辅助: {}", userId, file.getOriginalFilename(), aiAssist);
        try {
            String taskId = streamingAnalysisService.startProtocolAnalysis(file.getBytes(), file.getOriginalFilename(), aiAssist);
            return Result.success(Map.of("taskId", taskId));
        } catch (Exception e) {
            log.error("启动协议流式分析失败", e);
            return Result.error("启动分析失败: " + e.getMessage());
        }
    }

    /**
     * 订阅 Protocol 分析进度 (SSE)
     */
    @SaCheckLogin
    @GetMapping(value = "/protocol/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamProtocolAnalysis(@PathVariable String taskId) {
        log.info("SSE 订阅 Protocol 分析: {}", taskId);
        return streamingAnalysisService.getTaskStream(taskId);
    }

    /**
     * 启动 APK 反编译流式分析
     */
    @SaCheckLogin
    @PostMapping("/apk/decompile/start")
    public Result<Map<String, Object>> startDecompileAnalysis(@RequestParam("file") MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小超过限制 (最大200MB)");
        }
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        log.info("用户 {} 发起APK反编译流式分析: {}", userId, file.getOriginalFilename());
        try {
            String taskId = streamingAnalysisService.startDecompileAnalysis(file.getBytes(), file.getOriginalFilename());
            return Result.success(Map.of("taskId", taskId));
        } catch (Exception e) {
            log.error("启动反编译流式分析失败", e);
            return Result.error("启动分析失败: " + e.getMessage());
        }
    }

    /**
     * 订阅 APK 反编译进度 (SSE)
     */
    @SaCheckLogin
    @GetMapping(value = "/apk/decompile/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamDecompileAnalysis(@PathVariable String taskId) {
        log.info("SSE 订阅反编译: {}", taskId);
        return streamingAnalysisService.getTaskStream(taskId);
    }
}
