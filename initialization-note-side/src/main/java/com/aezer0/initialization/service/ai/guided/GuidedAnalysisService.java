package com.aezer0.initialization.service.ai.guided;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aezer0.initialization.config.ai.AiAnalysisConfig;
import com.aezer0.initialization.service.ai.guided.model.AnalysisSession;
import com.aezer0.initialization.service.ai.guided.model.AnalysisSession.SessionStatus;
import com.aezer0.initialization.service.ai.guided.model.AnalysisStep;
import com.aezer0.initialization.service.ai.guided.model.AnalysisStep.StepStatus;
import com.aezer0.initialization.service.tool.ToolManagementService;
import com.aezer0.initialization.vo.GuidedAnalysisSessionVO;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class GuidedAnalysisService {

    @Autowired
    private GuidedAnalysisSessionManager sessionManager;
    @Autowired
    private StepExecutorRegistry executorRegistry;
    @Autowired
    private AiAnalysisConfig config;
    @Autowired
    private ToolManagementService toolService;
    @Autowired
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;
    @Autowired
    @Qualifier("openAiStreamingChatModel")
    private StreamingChatModel streamingChatModel;
    @Autowired
    @Qualifier("streamingExecutor")
    private ThreadPoolTaskExecutor streamingExecutor;

    private final Map<String, Sinks.Many<String>> sessionSinks = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GuidedAnalysisSessionVO createSession(byte[] fileBytes, String fileName,
                                                  String moduleType, String focusAreas,
                                                  String userId) throws IOException {
        if (sessionManager.countByUser(userId) >= config.getGuided().getMaxSessionsPerUser()) {
            throw new IllegalStateException("已达到最大并发分析会话数: " + config.getGuided().getMaxSessionsPerUser());
        }

        // 检查所需工具是否可用
        String requiredTool = getRequiredTool(moduleType);
        if (requiredTool != null && !toolService.isToolAvailable(requiredTool)) {
            String toolDisplayName = switch (requiredTool) {
                case "apktool" -> "ApkTool";
                case "tshark" -> "TShark";
                default -> requiredTool;
            };
            throw new IllegalStateException("插件未安装: " + toolDisplayName + " 尚未配置或不可用，请先在管理后台上传并验证该工具后再启动分析。");
        }

        Path tempFile = Files.createTempFile("guided_analysis_", getSuffix(moduleType));
        Files.write(tempFile, fileBytes);

        AnalysisSession session = new AnalysisSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setModuleType(moduleType);
        session.setFileName(fileName);
        session.setFilePath(tempFile.toString());
        session.setCreatedAt(Instant.now());
        session.setLastActiveAt(Instant.now());
        session.getContext().put("fileBytes", fileBytes);

        List<AnalysisStep> steps = generatePlan(moduleType, fileName, fileBytes.length, focusAreas);
        session.setSteps(steps);
        session.setStatus(SessionStatus.WAITING);

        sessionManager.save(session);

        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer(256);
        sessionSinks.put(session.getSessionId(), sink);

        emitEvent(session.getSessionId(), Map.of(
                "type", "plan",
                "steps", steps.stream().map(s -> Map.of(
                        "index", s.getIndex(),
                        "title", s.getTitle(),
                        "description", s.getDescription()
                )).toList()
        ));

        return GuidedAnalysisSessionVO.from(session);
    }

    public Flux<String> getSessionStream(String sessionId) {
        AnalysisSession session = sessionManager.get(sessionId);
        if (session == null) {
            return Flux.just(toJson(Map.of("type", "error", "message", "会话不存在")));
        }

        Sinks.Many<String> sink = sessionSinks.computeIfAbsent(sessionId,
                k -> Sinks.many().multicast().onBackpressureBuffer(256));

        int heartbeatInterval = config.getGuided().getHeartbeatIntervalSeconds();
        Flux<String> heartbeat = Flux.interval(Duration.ofSeconds(heartbeatInterval))
                .map(i -> ":ping\n");

        return Flux.merge(sink.asFlux(), heartbeat)
                .timeout(Duration.ofMinutes(30), Flux.just(toJson(Map.of("type", "timeout"))));
    }

    public void advanceStep(String sessionId, String userInstruction) {
        AnalysisSession session = sessionManager.get(sessionId);
        if (session == null) throw new IllegalArgumentException("会话不存在");
        if (session.getStatus() != SessionStatus.WAITING) {
            throw new IllegalStateException("当前状态不允许执行下一步: " + session.getStatus());
        }

        if (userInstruction != null && !userInstruction.isBlank()) {
            adaptPlan(session, userInstruction);
        }

        int nextIndex = session.getCurrentStepIndex();
        if (nextIndex >= session.getSteps().size()) {
            completeSession(session);
            return;
        }

        session.setStatus(SessionStatus.EXECUTING);
        sessionManager.save(session);

        streamingExecutor.execute(() -> executeStep(session, nextIndex));
    }

    public void skipStep(String sessionId) {
        AnalysisSession session = sessionManager.get(sessionId);
        if (session == null) throw new IllegalArgumentException("会话不存在");
        if (session.getStatus() != SessionStatus.WAITING) return;

        AnalysisStep current = session.getCurrentStep();
        if (current != null) {
            current.setStatus(StepStatus.SKIPPED);
            emitEvent(sessionId, Map.of(
                    "type", "step_skipped", "stepIndex", current.getIndex()));
        }

        session.setCurrentStepIndex(session.getCurrentStepIndex() + 1);
        if (session.getCurrentStepIndex() >= session.getSteps().size()) {
            completeSession(session);
        } else {
            session.setStatus(SessionStatus.WAITING);
            sessionManager.save(session);
        }
    }

    public void terminateSession(String sessionId) {
        AnalysisSession session = sessionManager.get(sessionId);
        if (session == null) return;

        session.setStatus(SessionStatus.TERMINATED);
        sessionManager.save(session);
        emitEvent(sessionId, Map.of("type", "session_terminated"));
        cleanup(session);
    }

    public GuidedAnalysisSessionVO getSessionState(String sessionId) {
        AnalysisSession session = sessionManager.get(sessionId);
        if (session == null) return null;
        return GuidedAnalysisSessionVO.from(session);
    }

    // ========== 内部方法 ==========

    private void executeStep(AnalysisSession session, int stepIndex) {
        AnalysisStep step = session.getSteps().get(stepIndex);
        String sessionId = session.getSessionId();

        try {
            step.setStatus(StepStatus.RUNNING);
            step.setStartedAt(Instant.now());
            emitEvent(sessionId, Map.of(
                    "type", "step_start",
                    "stepIndex", stepIndex,
                    "title", step.getTitle()));

            Map<String, Object> result = executorRegistry.execute(step.getExecutorMethod(), session);
            step.setResult(result);
            session.getContext().put("step_result_" + step.getExecutorMethod(), result);

            emitEvent(sessionId, Map.of(
                    "type", "step_result",
                    "stepIndex", stepIndex,
                    "data", result));

            streamAiInterpretation(session, step);

            step.setStatus(StepStatus.COMPLETED);
            step.setCompletedAt(Instant.now());
            session.setCurrentStepIndex(stepIndex + 1);

            if (session.getCurrentStepIndex() >= session.getSteps().size()) {
                completeSession(session);
            } else {
                session.setStatus(SessionStatus.WAITING);
                sessionManager.save(session);
                emitEvent(sessionId, Map.of(
                        "type", "step_complete",
                        "stepIndex", stepIndex,
                        "status", "WAITING"));
            }

        } catch (Exception e) {
            log.error("步骤执行失败: session={}, step={}", sessionId, stepIndex, e);
            step.setStatus(StepStatus.FAILED);
            step.setCompletedAt(Instant.now());
            session.setCurrentStepIndex(stepIndex + 1);
            session.setStatus(SessionStatus.WAITING);
            sessionManager.save(session);
            emitEvent(sessionId, Map.of(
                    "type", "step_error",
                    "stepIndex", stepIndex,
                    "error", e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
    }

    private void streamAiInterpretation(AnalysisSession session, AnalysisStep step) {
        String systemPrompt = getInterpretationPrompt(session.getModuleType());
        String userPrompt = buildStepContext(session, step);
        String sessionId = session.getSessionId();
        int stepIndex = step.getIndex();

        StringBuilder fullText = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(userPrompt)))
                    .build();

            streamingChatModel.doChat(request, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    fullText.append(token);
                    emitEvent(sessionId, Map.of(
                            "type", "ai_chunk",
                            "stepIndex", stepIndex,
                            "content", token));
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    step.setAiInterpretation(fullText.toString());
                    latch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    log.warn("AI解读流式输出异常", error);
                    step.setAiInterpretation(fullText.toString());
                    latch.countDown();
                }
            });

            latch.await(config.getGuided().getStepTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("AI解读失败", e);
            step.setAiInterpretation("AI解读暂时不可用");
        }
    }

    private void completeSession(AnalysisSession session) {
        session.setStatus(SessionStatus.COMPLETED);
        sessionManager.save(session);
        emitEvent(session.getSessionId(), Map.of(
                "type", "session_complete",
                "summary", "分析完成，共执行 " + session.getSteps().stream()
                        .filter(s -> s.getStatus() == StepStatus.COMPLETED).count() + " 个步骤"));
        cleanup(session);
    }

    private void cleanup(AnalysisSession session) {
        session.getContext().remove("fileBytes");
        if (session.getFilePath() != null) {
            try {
                Files.deleteIfExists(Path.of(session.getFilePath()));
            } catch (IOException ignored) {}
        }
    }

    // ========== 计划生成 ==========

    private List<AnalysisStep> generatePlan(String moduleType, String fileName, int fileSize, String focusAreas) {
        try {
            String prompt = buildPlanPrompt(moduleType, fileName, fileSize, focusAreas);
            String response = chatModel.chat(List.of(
                    SystemMessage.from(PLAN_SYSTEM_PROMPT),
                    UserMessage.from(prompt)
            )).aiMessage().text();
            return parsePlanResponse(response, moduleType);
        } catch (Exception e) {
            log.warn("AI计划生成失败，使用默认计划", e);
            return getDefaultPlan(moduleType);
        }
    }

    private void adaptPlan(AnalysisSession session, String userInstruction) {
        try {
            StringBuilder context = new StringBuilder();
            context.append("当前模块: ").append(session.getModuleType()).append("\n");
            context.append("已完成步骤:\n");
            for (AnalysisStep step : session.getSteps()) {
                if (step.getStatus() == StepStatus.COMPLETED) {
                    context.append("- ").append(step.getTitle()).append("\n");
                }
            }
            context.append("\n用户指令: ").append(userInstruction).append("\n");
            context.append("\n可用方法:\n");
            context.append(getAvailableMethods(session.getModuleType()));

            String response = chatModel.chat(List.of(
                    SystemMessage.from(ADAPT_SYSTEM_PROMPT),
                    UserMessage.from(context.toString())
            )).aiMessage().text();

            List<AnalysisStep> newSteps = parsePlanResponse(response, session.getModuleType());
            if (!newSteps.isEmpty()) {
                List<AnalysisStep> completed = new ArrayList<>(
                        session.getSteps().subList(0, session.getCurrentStepIndex()));
                for (int i = 0; i < newSteps.size(); i++) {
                    newSteps.get(i).setIndex(completed.size() + i);
                }
                completed.addAll(newSteps);
                session.setSteps(completed);
                sessionManager.save(session);

                emitEvent(session.getSessionId(), Map.of(
                        "type", "plan_updated",
                        "steps", newSteps.stream().map(s -> Map.of(
                                "index", s.getIndex(),
                                "title", s.getTitle(),
                                "description", s.getDescription()
                        )).toList()));
            }
        } catch (Exception e) {
            log.warn("计划调整失败，继续原计划", e);
        }
    }

    private List<AnalysisStep> parsePlanResponse(String response, String moduleType) {
        try {
            String json = response;
            int start = response.indexOf('[');
            int end = response.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = response.substring(start, end + 1);
            }
            List<Map<String, String>> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            List<AnalysisStep> steps = new ArrayList<>();
            for (int i = 0; i < parsed.size(); i++) {
                Map<String, String> item = parsed.get(i);
                String method = item.getOrDefault("executorMethod", "");
                if (!executorRegistry.hasExecutor(method)) continue;
                AnalysisStep step = new AnalysisStep();
                step.setIndex(i);
                step.setTitle(item.getOrDefault("title", "步骤" + (i + 1)));
                step.setDescription(item.getOrDefault("description", ""));
                step.setExecutorMethod(method);
                steps.add(step);
            }
            return steps.isEmpty() ? getDefaultPlan(moduleType) : steps;
        } catch (Exception e) {
            log.warn("解析AI计划响应失败: {}", e.getMessage());
            return getDefaultPlan(moduleType);
        }
    }

    // ========== 默认计划 ==========

    private List<AnalysisStep> getDefaultPlan(String moduleType) {
        return switch (moduleType.toUpperCase()) {
            case "APK" -> buildDefaultApkPlan();
            case "SO" -> buildDefaultSoPlan();
            case "PROTOCOL" -> buildDefaultProtocolPlan();
            default -> List.of();
        };
    }

    private List<AnalysisStep> buildDefaultApkPlan() {
        return List.of(
                buildStep(0, "解析APK基本信息", "提取包名、版本、SDK版本等元数据", "apk.extractInfo"),
                buildStep(1, "提取APK特征", "分析权限、组件、原生库", "apk.extractFeatures"),
                buildStep(2, "权限风险分析", "评估权限请求的安全风险", "apk.analyzePermissions"),
                buildStep(3, "提取网络域名", "从DEX中提取URL和域名", "apk.extractDomains"),
                buildStep(4, "检测加壳/加固", "识别APK保护方案", "apk.detectPacker"),
                buildStep(5, "恶意行为检测", "综合评估恶意行为特征", "apk.malwareDetection"),
                buildStep(6, "生成处置建议", "基于分析结果给出建议", "apk.recommendations"));
    }

    private List<AnalysisStep> buildDefaultSoPlan() {
        return List.of(
                buildStep(0, "解析ELF文件头", "提取架构、段信息、依赖库", "so.extractInfo"),
                buildStep(1, "提取函数列表", "解析符号表获取导出函数", "so.extractFunctions"),
                buildStep(2, "识别加密算法", "通过常量和函数名匹配加密实现", "so.identifyCrypto"),
                buildStep(3, "检测代码混淆", "分析符号混淆、OLLVM等保护", "so.detectObfuscation"),
                buildStep(4, "分析敏感字符串", "提取并分类可打印字符串", "so.analyzeStrings"));
    }

    private List<AnalysisStep> buildDefaultProtocolPlan() {
        return List.of(
                buildStep(0, "验证PCAP文件", "检查文件格式有效性", "protocol.validate"),
                buildStep(1, "解析协议层次", "识别通信协议类型", "protocol.parseProtocol"),
                buildStep(2, "统计通信信息", "分析连接数、流量分布", "protocol.calculateStats"),
                buildStep(3, "分析加密特征", "检测TLS和自定义加密", "protocol.analyzeEncryption"),
                buildStep(4, "分析数据格式", "识别二进制协议结构", "protocol.analyzeDataFormat"));
    }

    private AnalysisStep buildStep(int index, String title, String description, String executorMethod) {
        AnalysisStep step = new AnalysisStep();
        step.setIndex(index);
        step.setTitle(title);
        step.setDescription(description);
        step.setExecutorMethod(executorMethod);
        return step;
    }

    // ========== 工具方法 ==========

    private void emitEvent(String sessionId, Map<String, Object> event) {
        Sinks.Many<String> sink = sessionSinks.get(sessionId);
        if (sink != null) {
            sink.tryEmitNext(toJson(event));
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"序列化失败\"}";
        }
    }

    private String getSuffix(String moduleType) {
        return switch (moduleType.toUpperCase()) {
            case "APK" -> ".apk";
            case "SO" -> ".so";
            case "PROTOCOL" -> ".pcap";
            default -> ".bin";
        };
    }

    private String getRequiredTool(String moduleType) {
        return switch (moduleType.toUpperCase()) {
            case "APK" -> ToolManagementService.TOOL_APKTOOL;
            case "PROTOCOL" -> ToolManagementService.TOOL_TSHARK;
            default -> null; // SO uses in-process jelf library, no external tool needed
        };
    }

    private String buildPlanPrompt(String moduleType, String fileName, int fileSize, String focusAreas) {
        StringBuilder sb = new StringBuilder();
        sb.append("文件类型: ").append(moduleType).append("\n");
        sb.append("文件名: ").append(fileName).append("\n");
        sb.append("文件大小: ").append(fileSize).append(" bytes\n");
        if (focusAreas != null && !focusAreas.isBlank()) {
            sb.append("用户关注方向: ").append(focusAreas).append("\n");
        }
        sb.append("\n可用方法:\n").append(getAvailableMethods(moduleType));
        sb.append("\n请根据以上信息生成分析计划。");
        return sb.toString();
    }

    private String getAvailableMethods(String moduleType) {
        return switch (moduleType.toUpperCase()) {
            case "APK" -> "apk.extractInfo, apk.extractFeatures, apk.analyzePermissions, apk.extractDomains, apk.detectPacker, apk.malwareDetection, apk.recommendations";
            case "SO" -> "so.extractInfo, so.extractFunctions, so.identifyCrypto, so.detectObfuscation, so.analyzeStrings";
            case "PROTOCOL" -> "protocol.validate, protocol.parseProtocol, protocol.calculateStats, protocol.analyzeEncryption, protocol.analyzeDataFormat";
            default -> "";
        };
    }

    private String buildStepContext(AnalysisSession session, AnalysisStep step) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前步骤: ").append(step.getTitle()).append("\n");
        sb.append("步骤描述: ").append(step.getDescription()).append("\n\n");
        sb.append("执行结果:\n");
        try {
            sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(step.getResult()));
        } catch (Exception e) {
            sb.append(step.getResult());
        }
        sb.append("\n\n请对以上结果进行安全分析解读，指出关键发现和下一步建议。");
        return sb.toString();
    }

    private String getInterpretationPrompt(String moduleType) {
        return switch (moduleType.toUpperCase()) {
            case "APK" -> "你是一位资深Android安全分析师。请对当前分析步骤的结果进行专业解读，指出安全风险、可疑行为和关键发现。语言简洁，重点突出。如果发现需要深入分析的方向，请在最后给出建议。";
            case "SO" -> "你是一位资深逆向工程师。请对当前ELF分析步骤的结果进行专业解读，关注加密实现、反调试手段、混淆技术和潜在漏洞。语言简洁，重点突出。";
            case "PROTOCOL" -> "你是一位网络协议分析专家。请对当前协议分析步骤的结果进行专业解读，关注加密通信、异常流量模式、协议特征和潜在的C2通信。语言简洁，重点突出。";
            default -> "你是一位安全分析专家，请对分析结果进行专业解读。";
        };
    }

    private static final String PLAN_SYSTEM_PROMPT = """
            你是一位安全分析规划师。根据文件信息生成分步分析计划。
            每一步应该是一个独立的分析动作，按照从浅到深的顺序排列。

            输出格式（严格JSON数组，不要包含其他文字）：
            [{"title": "步骤标题", "description": "步骤描述", "executorMethod": "方法标识"}]

            根据文件特征和用户关注方向选择合适的步骤并排序。不需要包含所有步骤。""";

    private static final String ADAPT_SYSTEM_PROMPT = """
            你是一位安全分析规划师。用户希望调整分析方向。
            根据已完成的步骤和用户的新指令，重新规划剩余步骤。
            输出格式（严格JSON数组，不要包含其他文字）：
            [{"title": "步骤标题", "description": "步骤描述", "executorMethod": "方法标识"}]""";
}
