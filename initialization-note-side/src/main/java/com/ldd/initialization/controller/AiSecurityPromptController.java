package com.ldd.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.ldd.initialization.result.Result;
import com.ldd.initialization.service.ai.AiSecurityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI安全检测控制器
 * 提供Prompt注入检测等安全分析功能
 */
@RestController
@RequestMapping("/api/ai-security")
@Slf4j
public class AiSecurityPromptController {

    @Autowired
    private AiSecurityService aiSecurityService;

    /**
     * Prompt注入检测
     */
    @SaCheckLogin
    @PostMapping("/detect-injection")
    public Result<Map<String, Object>> detectPromptInjection(@RequestBody Map<String, String> request) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        String prompt = request.get("prompt");
        String detectionLevel = request.getOrDefault("detectionLevel", "normal");

        log.info("用户 {} 发起Prompt注入检测, level={}", userId, detectionLevel);

        try {
            Map<String, Object> result = aiSecurityService.detectPromptInjection(prompt, detectionLevel);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Prompt注入检测失败", e);
            return Result.error("检测失败: " + e.getMessage());
        }
    }

    /**
     * AI增强Prompt注入检测 — 规则引擎 + 大模型深度语义分析
     */
    @SaCheckLogin
    @PostMapping("/detect-injection/enhanced")
    public Result<Map<String, Object>> detectPromptInjectionEnhanced(@RequestBody Map<String, String> request) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        String prompt = request.get("prompt");
        String detectionLevel = request.getOrDefault("detectionLevel", "normal");

        log.info("用户 {} 发起AI增强Prompt注入检测, level={}", userId, detectionLevel);

        try {
            Map<String, Object> result = aiSecurityService.detectPromptInjectionEnhanced(prompt, detectionLevel);
            return Result.success(result);
        } catch (Exception e) {
            log.error("AI增强Prompt注入检测失败", e);
            return Result.error("检测失败: " + e.getMessage());
        }
    }

    /**
     * AI增强Prompt注入检测 — SSE流式输出
     * 实时输出：规则引擎结果 → AI思考过程 → AI分析结论
     */
    @SaCheckLogin
    @PostMapping(value = "/detect-injection/enhanced/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> detectPromptInjectionEnhancedStream(@RequestBody Map<String, String> request) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        String prompt = request.get("prompt");
        String detectionLevel = request.getOrDefault("detectionLevel", "normal");

        log.info("用户 {} 发起AI增强Prompt注入流式检测, level={}", userId, detectionLevel);

        return aiSecurityService.detectPromptInjectionEnhancedStream(prompt, detectionLevel);
    }

    /**
     * 获取AI增强分析开关状态
     */
    @SaCheckLogin
    @GetMapping("/ai-toggle")
    public Result<Map<String, Object>> getAiToggle() {
        try {
            Map<String, Object> status = aiSecurityService.getAiToggleStatus();
            return Result.success(status);
        } catch (Exception e) {
            log.error("获取AI开关状态失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 切换AI增强分析开关
     */
    @SaCheckLogin
    @PostMapping("/ai-toggle")
    public Result<Map<String, Object>> updateAiToggle(@RequestBody Map<String, Boolean> request) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        boolean enabled = request.getOrDefault("enabled", false);

        log.info("用户 {} 切换AI增强分析开关: {}", userId, enabled);

        try {
            Map<String, Object> result = aiSecurityService.updateAiToggle(enabled);
            return Result.success(result);
        } catch (Exception e) {
            log.error("切换AI开关失败", e);
            return Result.error("切换失败: " + e.getMessage());
        }
    }

    /**
     * AI辅助安全分析
     */
    @SaCheckLogin
    @PostMapping("/analyze")
    public Result<Map<String, Object>> analyzeWithAI(@RequestBody Map<String, String> request) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        String target = request.get("analysisTarget");
        String analysisType = request.get("analysisType");

        log.info("用户 {} 发起AI安全分析: type={}, target={}", userId, analysisType, target);

        try {
            Map<String, Object> result = aiSecurityService.analyzeWithAI(target, analysisType);
            return Result.success(result);
        } catch (Exception e) {
            log.error("AI安全分析失败", e);
            return Result.error("分析失败: " + e.getMessage());
        }
    }

    /**
     * 获取安全知识库
     */
    @SaCheckLogin
    @GetMapping("/knowledge")
    public Result<Map<String, Object>> getSecurityKnowledge(
            @RequestParam(value = "category", defaultValue = "all") String category,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {

        try {
            Map<String, Object> knowledge = aiSecurityService.getSecurityKnowledge(category, page, limit);
            return Result.success(knowledge);
        } catch (Exception e) {
            log.error("获取安全知识库失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取攻击案例
     */
    @SaCheckLogin
    @GetMapping("/cases")
    public Result<Map<String, Object>> getAttackCases(
            @RequestParam(value = "type", defaultValue = "all") String type) {

        try {
            Map<String, Object> cases = aiSecurityService.getAttackCases(type);
            return Result.success(cases);
        } catch (Exception e) {
            log.error("获取攻击案例失败", e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }
}