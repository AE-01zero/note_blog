package com.ldd.initialization.service.ai.guided;

import com.ldd.initialization.service.ai.ApkAnalysisService;
import com.ldd.initialization.service.ai.ProtocolAnalysisService;
import com.ldd.initialization.service.ai.SoAnalysisService;
import com.ldd.initialization.service.ai.guided.model.AnalysisSession;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@Slf4j
public class StepExecutorRegistry {

    @Autowired
    private ApkAnalysisService apkAnalysisService;
    @Autowired
    private SoAnalysisService soAnalysisService;
    @Autowired
    private ProtocolAnalysisService protocolAnalysisService;

    private final Map<String, Function<AnalysisSession, Map<String, Object>>> executors = new HashMap<>();

    @PostConstruct
    public void init() {
        // APK步骤
        executors.put("apk.extractInfo", session -> {
            byte[] bytes = (byte[]) session.getContext().get("fileBytes");
            return apkAnalysisService.stepExtractApkInfo(
                    Path.of(session.getFilePath()), bytes, session.getFileName());
        });
        executors.put("apk.extractFeatures", session ->
                apkAnalysisService.stepExtractFeatures(Path.of(session.getFilePath())));
        executors.put("apk.analyzePermissions", session -> {
            Map<String, Object> features = getFromContext(session, "apk.extractFeatures");
            List<Map<String, Object>> perms = apkAnalysisService.stepAnalyzePermissions(features);
            return Map.of("permissions", perms);
        });
        executors.put("apk.extractDomains", session -> {
            List<String> domains = apkAnalysisService.stepExtractNetworkDomains(Path.of(session.getFilePath()));
            return Map.of("domains", domains);
        });
        executors.put("apk.detectPacker", session ->
                apkAnalysisService.stepDetectPacker(Path.of(session.getFilePath())));
        executors.put("apk.malwareDetection", session -> {
            Map<String, Object> features = getFromContext(session, "apk.extractFeatures");
            List<Map<String, Object>> perms = (List<Map<String, Object>>)
                    getFromContext(session, "apk.analyzePermissions").get("permissions");
            List<String> domains = (List<String>)
                    getFromContext(session, "apk.extractDomains").get("domains");
            return apkAnalysisService.stepMalwareDetection(features, perms, domains);
        });
        executors.put("apk.recommendations", session -> {
            Map<String, Object> detection = getFromContext(session, "apk.malwareDetection");
            List<Map<String, Object>> recs = apkAnalysisService.stepGenerateRecommendations(detection);
            return Map.of("recommendations", recs);
        });

        // SO步骤
        executors.put("so.extractInfo", session -> {
            byte[] bytes = (byte[]) session.getContext().get("fileBytes");
            return soAnalysisService.stepExtractSoInfo(bytes, session.getFileName());
        });
        executors.put("so.extractFunctions", session -> {
            byte[] bytes = (byte[]) session.getContext().get("fileBytes");
            List<Map<String, Object>> funcs = soAnalysisService.stepExtractFunctions(bytes);
            return Map.of("functions", funcs);
        });
        executors.put("so.identifyCrypto", session -> {
            byte[] bytes = (byte[]) session.getContext().get("fileBytes");
            List<Map<String, Object>> funcs = (List<Map<String, Object>>)
                    getFromContext(session, "so.extractFunctions").get("functions");
            List<Map<String, Object>> algos = soAnalysisService.stepIdentifyCrypto(bytes, funcs);
            return Map.of("algorithms", algos);
        });
        executors.put("so.detectObfuscation", session -> {
            byte[] bytes = (byte[]) session.getContext().get("fileBytes");
            List<Map<String, Object>> funcs = (List<Map<String, Object>>)
                    getFromContext(session, "so.extractFunctions").get("functions");
            return soAnalysisService.stepDetectObfuscation(bytes, funcs);
        });
        executors.put("so.analyzeStrings", session -> {
            byte[] bytes = (byte[]) session.getContext().get("fileBytes");
            List<Map<String, Object>> strings = soAnalysisService.stepAnalyzeStrings(bytes);
            return Map.of("strings", strings);
        });

        // Protocol步骤
        executors.put("protocol.validate", session -> {
            byte[] bytes = (byte[]) session.getContext().get("fileBytes");
            return protocolAnalysisService.stepValidateAndBasicInfo(bytes, session.getFileName());
        });
        executors.put("protocol.parseProtocol", session ->
                protocolAnalysisService.stepParseProtocol(Path.of(session.getFilePath())));
        executors.put("protocol.calculateStats", session ->
                protocolAnalysisService.stepCalculateStats(Path.of(session.getFilePath())));
        executors.put("protocol.analyzeEncryption", session -> {
            byte[] bytes = (byte[]) session.getContext().get("fileBytes");
            return protocolAnalysisService.stepAnalyzeEncryption(Path.of(session.getFilePath()), bytes);
        });
        executors.put("protocol.analyzeDataFormat", session ->
                protocolAnalysisService.stepAnalyzeDataFormat(Path.of(session.getFilePath())));
    }

    public Map<String, Object> execute(String executorMethod, AnalysisSession session) {
        Function<AnalysisSession, Map<String, Object>> executor = executors.get(executorMethod);
        if (executor == null) {
            log.warn("未知的执行方法: {}", executorMethod);
            return Map.of("error", "未知的分析步骤: " + executorMethod);
        }
        return executor.apply(session);
    }

    public boolean hasExecutor(String executorMethod) {
        return executors.containsKey(executorMethod);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getFromContext(AnalysisSession session, String stepMethod) {
        Object result = session.getContext().get("step_result_" + stepMethod);
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        return Map.of();
    }
}
