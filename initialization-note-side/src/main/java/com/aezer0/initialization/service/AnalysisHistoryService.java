package com.aezer0.initialization.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aezer0.initialization.domain.AnalysisHistory;
import com.aezer0.initialization.domain.DecompileRecord;
import com.aezer0.initialization.mapper.AnalysisHistoryMapper;
import com.aezer0.initialization.mapper.DecompileRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class AnalysisHistoryService extends ServiceImpl<AnalysisHistoryMapper, AnalysisHistory> {

    @Autowired
    private DecompileRecordMapper decompileRecordMapper;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ===================== 分析历史管理 =====================

    /** 保存分析结果 */
    @Transactional
    public AnalysisHistory saveAnalysis(Long userId, String moduleType, String fileName,
                                         Long fileSize, String workDir, Map<String, Object> result,
                                         String verdict, String riskLevel) {
        AnalysisHistory history = new AnalysisHistory();
        history.setUserId(userId);
        history.setModuleType(moduleType);
        history.setFileName(fileName);
        history.setFileSize(fileSize);
        history.setWorkDir(workDir);
        history.setVerdict(verdict);
        history.setRiskLevel(riskLevel);
        history.setSummary(buildSummary(moduleType, result, fileName));
        history.setExtraInfo(buildExtraInfo(moduleType, result));
        history.setCreatedAt(LocalDateTime.now());
        history.setUpdatedAt(LocalDateTime.now());

        try {
            // 精简result, 去掉超大字段避免JSONB过大
            Map<String, Object> slimResult = slimResultForStorage(result);
            history.setAnalysisResult(objectMapper.writeValueAsString(slimResult));
        } catch (Exception e) {
            log.warn("序列化分析结果失败: {}", e.getMessage());
            history.setAnalysisResult("{}");
        }

        save(history);
        log.info("保存分析历史: userId={}, module={}, fileName={}, id={}", userId, moduleType, fileName, history.getId());
        return history;
    }

    /** 分页查询用户分析历史 */
    public Map<String, Object> getHistoryByUser(Long userId, String moduleType, int page, int size) {
        LambdaQueryWrapper<AnalysisHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnalysisHistory::getUserId, userId);
        if (moduleType != null && !moduleType.isEmpty() && !"all".equals(moduleType)) {
            wrapper.eq(AnalysisHistory::getModuleType, moduleType.toUpperCase());
        }
        wrapper.orderByDesc(AnalysisHistory::getCreatedAt);

        Page<AnalysisHistory> pageResult = page(new Page<>(page, size), wrapper);
        List<Map<String, Object>> list = new ArrayList<>();
        for (AnalysisHistory h : pageResult.getRecords()) {
            list.add(toHistoryMap(h));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", pageResult.getTotal());
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /** 删除分析历史(同时清理关联的反编译文件) */
    @Transactional
    public Map<String, Object> deleteHistory(Long historyId, Long userId) {
        Map<String, Object> result = new HashMap<>();
        AnalysisHistory history = getById(historyId);
        if (history == null || !history.getUserId().equals(userId)) {
            result.put("success", false);
            result.put("message", "记录不存在或无权限");
            return result;
        }

        // 如果有关联的反编译记录，标记删除并清理文件
        LambdaQueryWrapper<DecompileRecord> dw = new LambdaQueryWrapper<>();
        dw.eq(DecompileRecord::getAnalysisHistoryId, historyId).eq(DecompileRecord::getUserId, userId);
        List<DecompileRecord> decompileRecords = decompileRecordMapper.selectList(dw);
        for (DecompileRecord dr : decompileRecords) {
            deleteDecompileDir(dr.getWorkDir());
            dr.setStatus("DELETED");
            dr.setUpdatedAt(LocalDateTime.now());
            decompileRecordMapper.updateById(dr);
        }

        removeById(historyId);
        log.info("删除分析历史: id={}, userId={}", historyId, userId);
        result.put("success", true);
        return result;
    }

    /** 清空用户所有历史 */
    @Transactional
    public Map<String, Object> clearAllHistory(Long userId) {
        // 清理所有反编译文件
        LambdaQueryWrapper<DecompileRecord> dw = new LambdaQueryWrapper<>();
        dw.eq(DecompileRecord::getUserId, userId).eq(DecompileRecord::getStatus, "ACTIVE");
        List<DecompileRecord> records = decompileRecordMapper.selectList(dw);
        for (DecompileRecord dr : records) {
            deleteDecompileDir(dr.getWorkDir());
            dr.setStatus("DELETED");
            decompileRecordMapper.updateById(dr);
        }

        LambdaQueryWrapper<AnalysisHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnalysisHistory::getUserId, userId);
        long count = count(wrapper);
        remove(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("deletedCount", count);
        log.info("清空分析历史: userId={}, count={}", userId, count);
        return result;
    }

    // ===================== 反编译文件管理 =====================

    /** 保存反编译记录 */
    public DecompileRecord saveDecompileRecord(Long userId, Long analysisHistoryId,
                                                String apkFileName, String packageName,
                                                String workDir, int fileCount, long totalSize) {
        DecompileRecord record = new DecompileRecord();
        record.setUserId(userId);
        record.setAnalysisHistoryId(analysisHistoryId);
        record.setApkFileName(apkFileName);
        record.setPackageName(packageName);
        record.setWorkDir(workDir);
        record.setFileCount(fileCount);
        record.setTotalSize(totalSize);
        record.setStatus("ACTIVE");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        decompileRecordMapper.insert(record);
        log.info("保存反编译记录: userId={}, apk={}, workDir={}", userId, apkFileName, workDir);
        return record;
    }

    /** 获取用户的反编译记录列表 */
    public List<DecompileRecord> getDecompileRecords(Long userId) {
        LambdaQueryWrapper<DecompileRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DecompileRecord::getUserId, userId)
               .eq(DecompileRecord::getStatus, "ACTIVE")
               .orderByDesc(DecompileRecord::getCreatedAt);
        return decompileRecordMapper.selectList(wrapper);
    }

    /** 删除反编译记录并清理磁盘文件 */
    @Transactional
    public Map<String, Object> deleteDecompileRecord(Long recordId, Long userId) {
        Map<String, Object> result = new HashMap<>();
        DecompileRecord record = decompileRecordMapper.selectById(recordId);
        if (record == null || !record.getUserId().equals(userId)) {
            result.put("success", false);
            result.put("message", "记录不存在或无权限");
            return result;
        }

        deleteDecompileDir(record.getWorkDir());
        record.setStatus("DELETED");
        record.setUpdatedAt(LocalDateTime.now());
        decompileRecordMapper.updateById(record);

        result.put("success", true);
        result.put("message", "已删除反编译文件");
        log.info("删除反编译记录: id={}, workDir={}", recordId, record.getWorkDir());
        return result;
    }

    /** 清理磁盘上的反编译目录 */
    private void deleteDecompileDir(String workDir) {
        if (workDir == null || workDir.isEmpty()) return;
        try {
            Path path = Path.of(workDir);
            if (Files.exists(path)) {
                Files.walk(path)
                     .sorted(Comparator.reverseOrder())
                     .map(Path::toFile)
                     .forEach(File::delete);
                log.info("已清理反编译目录: {}", workDir);
            }
        } catch (Exception e) {
            log.warn("清理反编译目录失败: {}, error: {}", workDir, e.getMessage());
        }
    }

    // ===================== 辅助方法 =====================

    /** 生成简短摘要 */
    private String buildSummary(String moduleType, Map<String, Object> result, String fileName) {
        try {
            switch (moduleType) {
                case "APK":
                    Map<String, Object> detection = getMap(result, "detection");
                    String verdict = detection != null ? (String) detection.getOrDefault("verdict", "UNKNOWN") : "UNKNOWN";
                    String pkg = getPackageName(result);
                    return String.format("[%s] %s - %s", verdict, fileName, pkg);
                case "APK_REVERSE":
                    return String.format("反编译: %s", fileName);
                case "SO":
                    Map<String, Object> soInfo = getMap(result, "soInfo");
                    String arch = soInfo != null ? (String) soInfo.getOrDefault("architecture", "unknown") : "unknown";
                    return String.format("SO分析: %s (%s)", fileName, arch);
                case "PROTOCOL":
                    Map<String, Object> protocol = getMap(result, "protocol");
                    String protoType = protocol != null ? (String) protocol.getOrDefault("type", "unknown") : "unknown";
                    return String.format("协议分析: %s (%s)", fileName, protoType);
                default:
                    return fileName;
            }
        } catch (Exception e) {
            return fileName;
        }
    }

    /** 提取附加信息 */
    private String buildExtraInfo(String moduleType, Map<String, Object> result) {
        try {
            Map<String, Object> extra = new HashMap<>();
            switch (moduleType) {
                case "APK":
                    extra.put("packageName", getPackageName(result));
                    Map<String, Object> detection = getMap(result, "detection");
                    if (detection != null) extra.put("malwareType", detection.getOrDefault("malwareType", ""));
                    break;
                case "SO":
                    Map<String, Object> soInfo = getMap(result, "soInfo");
                    if (soInfo != null) extra.put("architecture", soInfo.getOrDefault("architecture", ""));
                    Map<String, Object> algorithms = getMap(result, "algorithms");
                    if (algorithms != null) extra.put("algoCount", algorithms.size());
                    break;
                case "PROTOCOL":
                    Map<String, Object> protocol = getMap(result, "protocol");
                    if (protocol != null) extra.put("type", protocol.getOrDefault("type", ""));
                    Map<String, Object> stats = getMap(result, "stats");
                    if (stats != null) extra.put("packetCount", stats.getOrDefault("totalPackets", 0));
                    break;
            }
            return objectMapper.writeValueAsString(extra);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> result, String key) {
        Object val = result.get(key);
        return val instanceof Map ? (Map<String, Object>) val : null;
    }

    private String getPackageName(Map<String, Object> result) {
        try {
            Map<String, Object> apkInfo = getMap(result, "apkInfo");
            if (apkInfo != null) {
                Object pkg = apkInfo.get("packageName");
                return pkg != null ? pkg.toString() : "unknown";
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    /** 精简结果，去掉超大字段 */
    private Map<String, Object> slimResultForStorage(Map<String, Object> result) {
        Map<String, Object> slim = new HashMap<>(result);
        // 去掉二进制/编码视图等超大字段
        slim.remove("hexView");
        slim.remove("smaliContent");
        slim.remove("rawBytes");
        slim.remove("base64Content");
        // 截断超长字符串数组
        slim.replaceAll((k, v) -> {
            if (v instanceof List && ((List<?>) v).size() > 200) {
                List<?> list = (List<?>) v;
                Map<String, Object> trunc = new HashMap<>();
                trunc.put("totalCount", list.size());
                trunc.put("preview", list.subList(0, Math.min(50, list.size())));
                return trunc;
            }
            return v;
        });
        return slim;
    }

    /** 转换为前端友好的Map */
    private Map<String, Object> toHistoryMap(AnalysisHistory h) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", h.getId());
        map.put("userId", h.getUserId());
        map.put("moduleType", h.getModuleType());
        map.put("fileName", h.getFileName());
        map.put("fileSize", h.getFileSize());
        map.put("workDir", h.getWorkDir());
        map.put("verdict", h.getVerdict());
        map.put("riskLevel", h.getRiskLevel());
        map.put("summary", h.getSummary());
        map.put("createdAt", h.getCreatedAt() != null ? h.getCreatedAt().toString() : null);

        // 解析JSON字段
        try {
            if (h.getAnalysisResult() != null) {
                map.put("analysisResult", objectMapper.readValue(h.getAnalysisResult(), Map.class));
            }
        } catch (Exception ignored) {}
        try {
            if (h.getExtraInfo() != null) {
                map.put("extraInfo", objectMapper.readValue(h.getExtraInfo(), Map.class));
            }
        } catch (Exception ignored) {}

        return map;
    }
}
