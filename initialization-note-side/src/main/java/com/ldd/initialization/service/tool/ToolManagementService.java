package com.ldd.initialization.service.tool;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 工具管理服务
 * 管理安全分析工具（apktool、jadx等）的上传、配置和状态检查
 */
@Service
@Slf4j
public class ToolManagementService {

    @Value("${tool.storage-path:/tmp/tools}")
    private String toolStoragePath;

    @Value("${tool.enabled-auto-check:true}")
    private boolean autoCheckEnabled;

    // 工具定义
    public static final String TOOL_APKTOOL = "apktool";
    public static final String TOOL_JADX = "jadx";
    public static final String TOOL_FRIDA = "frida-server";
    public static final String TOOL_ANDROID_SDK = "android-sdk";
    public static final String TOOL_TSHARK = "tshark";

    // 工具状态
    public enum ToolStatus {
        NOT_CONFIGURED,    // 未配置
        UPLOADED,          // 已上传
        AVAILABLE,         // 可用（验证通过）
        UNAVAILABLE        // 不可用（验证失败）
    }

    // 工具信息
    @Data
    public static class ToolInfo {
        private String name;
        private String displayName;
        private String version;
        private String path;
        private ToolStatus status;
        private String description;
        private boolean requiresAdmin;
        private Date uploadedAt;
        private Date lastVerified;
    }

    // 工具注册表
    private final Map<String, ToolInfo> toolRegistry = new HashMap<>();

    public ToolManagementService() {
        initToolRegistry();
    }

    private void initToolRegistry() {
        // APKTool
        ToolInfo apktool = new ToolInfo();
        apktool.setName(TOOL_APKTOOL);
        apktool.setDisplayName("ApkTool");
        apktool.setDescription("APK反编译和重编译工具");
        apktool.setRequiresAdmin(true);
        toolRegistry.put(TOOL_APKTOOL, apktool);

        // Jadx
        ToolInfo jadx = new ToolInfo();
        jadx.setName(TOOL_JADX);
        jadx.setDisplayName("Jadx");
        jadx.setDescription("Java反编译器，将Smali转为Java源码");
        jadx.setRequiresAdmin(true);
        toolRegistry.put(TOOL_JADX, jadx);

        // Frida
        ToolInfo frida = new ToolInfo();
        frida.setName(TOOL_FRIDA);
        frida.setDisplayName("Frida Server");
        frida.setDescription("动态 instrumentation 工具");
        frida.setRequiresAdmin(true);
        toolRegistry.put(TOOL_FRIDA, frida);

        // Android SDK
        ToolInfo sdk = new ToolInfo();
        sdk.setName(TOOL_ANDROID_SDK);
        sdk.setDisplayName("Android SDK");
        sdk.setDescription("Android开发工具包，包含ADB和模拟器");
        sdk.setRequiresAdmin(true);
        toolRegistry.put(TOOL_ANDROID_SDK, sdk);

        // Tshark
        ToolInfo tshark = new ToolInfo();
        tshark.setName(TOOL_TSHARK);
        tshark.setDisplayName("TShark");
        tshark.setDescription("Wireshark命令行工具，用于PCAP协议分析");
        tshark.setRequiresAdmin(false);
        toolRegistry.put(TOOL_TSHARK, tshark);
    }

    // 允许上传的工具列表（提示用）
    private static final Set<String> ALLOWED_TOOLS = Set.of(TOOL_APKTOOL, TOOL_JADX, TOOL_TSHARK);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jar", ".exe", ".bat", ".sh", "");

    /**
     * 获取允许上传的工具列表
     */
    public Set<String> getAllowedTools() {
        return ALLOWED_TOOLS;
    }

    /**
     * 获取允许的文件扩展名
     */
    public Set<String> getAllowedExtensions() {
        return ALLOWED_EXTENSIONS;
    }

    /**
     * 上传工具文件（需要管理员权限）
     */
    public Map<String, Object> uploadTool(String toolName, MultipartFile file, String version) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 检查工具名称是否有效
            if (!ALLOWED_TOOLS.contains(toolName)) {
                result.put("success", false);
                result.put("error", "只允许上传 apktool、jadx 和 tshark 插件");
                return result;
            }

            // 创建工具存储目录
            Path toolDir = Paths.get(toolStoragePath, toolName);
            Files.createDirectories(toolDir);

            // 保存文件
            String originalFilename = file.getOriginalFilename();
            Path toolPath = toolDir.resolve(originalFilename != null ? originalFilename : toolName);
            Files.write(toolPath, file.getBytes());

            // 更新工具信息
            ToolInfo tool = toolRegistry.get(toolName);
            tool.setPath(toolPath.toString());
            tool.setVersion(version);
            tool.setUploadedAt(new Date());

            // 验证工具
            boolean verified = verifyTool(toolName);
            tool.setStatus(verified ? ToolStatus.AVAILABLE : ToolStatus.UPLOADED);
            tool.setLastVerified(new Date());

            result.put("success", true);
            result.put("toolName", toolName);
            result.put("path", toolPath.toString());
            result.put("verified", verified);
            result.put("status", tool.getStatus().name());

            log.info("工具上传成功: {}, 路径: {}, 验证: {}",
                toolName, toolPath, verified);

        } catch (Exception e) {
            log.error("工具上传失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 获取所有工具状态
     */
    public Map<String, Object> getAllToolsStatus() {
        Map<String, Object> result = new HashMap<>();

        // 检查每个工具
        for (Map.Entry<String, ToolInfo> entry : toolRegistry.entrySet()) {
            String toolName = entry.getKey();
            ToolInfo tool = entry.getValue();

            // 自动检查工具可用性
            if (autoCheckEnabled && tool.getStatus() != ToolStatus.NOT_CONFIGURED) {
                boolean available = verifyTool(toolName);
                tool.setStatus(available ? ToolStatus.AVAILABLE : ToolStatus.UNAVAILABLE);
            }
        }

        List<ToolInfo> tools = new ArrayList<>(toolRegistry.values());
        result.put("tools", tools);
        result.put("totalCount", tools.size());
        result.put("availableCount", tools.stream()
            .filter(t -> t.getStatus() == ToolStatus.AVAILABLE)
            .count());

        return result;
    }

    /**
     * 获取指定工具状态
     */
    public Map<String, Object> getToolStatus(String toolName) {
        Map<String, Object> result = new HashMap<>();

        ToolInfo tool = toolRegistry.get(toolName);
        if (tool == null) {
            result.put("success", false);
            result.put("error", "未知工具: " + toolName);
            return result;
        }

        // 验证工具
        boolean verified = verifyTool(toolName);
        tool.setStatus(verified ? ToolStatus.AVAILABLE : ToolStatus.UNAVAILABLE);
        tool.setLastVerified(new Date());

        result.put("success", true);
        result.put("tool", tool);
        result.put("available", verified);

        return result;
    }

    /**
     * 检查工具是否可用
     */
    public boolean isToolAvailable(String toolName) {
        ToolInfo tool = toolRegistry.get(toolName);
        if (tool == null) return false;

        // 验证工具（缓存5分钟）
        if (tool.getLastVerified() != null) {
            long diff = System.currentTimeMillis() - tool.getLastVerified().getTime();
            if (diff < 5 * 60 * 1000 && tool.getStatus() != ToolStatus.NOT_CONFIGURED) {
                return tool.getStatus() == ToolStatus.AVAILABLE;
            }
        }

        boolean verified = verifyTool(toolName);
        tool.setStatus(verified ? ToolStatus.AVAILABLE : ToolStatus.UNAVAILABLE);
        tool.setLastVerified(new Date());

        return verified;
    }

    /**
     * 检查是否需要管理员授权
     */
    public boolean requiresAdmin(String toolName) {
        ToolInfo tool = toolRegistry.get(toolName);
        return tool != null && tool.isRequiresAdmin();
    }

    /**
     * 验证工具是否可用
     */
    private boolean verifyTool(String toolName) {
        ToolInfo tool = toolRegistry.get(toolName);
        if (tool == null || tool.getPath() == null) {
            return false;
        }

        try {
            Path toolPath = Paths.get(tool.getPath());
            if (!Files.exists(toolPath)) {
                return false;
            }

            // 根据工具类型进行验证
            switch (toolName) {
                case TOOL_APKTOOL:
                    return verifyApktool(toolPath);
                case TOOL_JADX:
                    return verifyJadx(toolPath);
                case TOOL_FRIDA:
                    return verifyFrida(toolPath);
                case TOOL_ANDROID_SDK:
                    return verifyAndroidSdk(toolPath);
                case TOOL_TSHARK:
                    return verifyTshark(toolPath);
                default:
                    return Files.isExecutable(toolPath);
            }
        } catch (Exception e) {
            log.warn("工具验证失败: {}", toolName, e);
            return false;
        }
    }

    private boolean verifyApktool(Path path) {
        try {
            // 检查是否为jar文件或脚本
            String name = path.getFileName().toString().toLowerCase();
            return name.contains("apktool") &&
                   (name.endsWith(".jar") || name.endsWith(".bat") || name.endsWith(".sh"));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean verifyJadx(Path path) {
        try {
            // 检查jadx目录结构
            if (Files.isDirectory(path)) {
                Path binDir = path.resolve("bin");
                return Files.exists(binDir);
            }
            // 或为单一脚本
            String name = path.getFileName().toString().toLowerCase();
            return name.contains("jadx");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean verifyFrida(Path path) {
        try {
            String name = path.getFileName().toString().toLowerCase();
            return name.contains("frida-server");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean verifyAndroidSdk(Path path) {
        try {
            // 检查adb是否存在
            if (Files.isDirectory(path)) {
                Path adb = path.resolve("platform-tools/adb");
                return Files.exists(adb) || Files.exists(path.resolve("adb"));
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean verifyTshark(Path path) {
        try {
            String name = path.getFileName().toString().toLowerCase();
            return name.contains("tshark") || name.contains("wireshark");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取工具路径
     */
    public String getToolPath(String toolName) {
        ToolInfo tool = toolRegistry.get(toolName);
        return tool != null ? tool.getPath() : null;
    }

    /**
     * 删除工具（管理员操作）
     */
    public Map<String, Object> deleteTool(String toolName) {
        Map<String, Object> result = new HashMap<>();

        try {
            ToolInfo tool = toolRegistry.get(toolName);
            if (tool == null) {
                result.put("success", false);
                result.put("error", "未知工具: " + toolName);
                return result;
            }

            // 删除工具文件
            if (tool.getPath() != null) {
                Path toolPath = Paths.get(tool.getPath());
                if (Files.exists(toolPath)) {
                    Files.delete(toolPath);
                }
            }

            // 重置工具状态
            tool.setPath(null);
            tool.setVersion(null);
            tool.setStatus(ToolStatus.NOT_CONFIGURED);
            tool.setUploadedAt(null);
            tool.setLastVerified(null);

            result.put("success", true);
            result.put("toolName", toolName);

            log.info("工具已删除: {}", toolName);

        } catch (Exception e) {
            log.error("删除工具失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 获取已启用的安全功能列表
     */
    public List<String> getEnabledFeatures() {
        List<String> features = new ArrayList<>();

        if (isToolAvailable(TOOL_APKTOOL)) {
            features.add("apk_decompile");
            features.add("apk_reverse");
        }

        if (isToolAvailable(TOOL_JADX)) {
            features.add("smali_to_java");
        }

        if (isToolAvailable(TOOL_FRIDA)) {
            features.add("frida_hook");
            features.add("dynamic_analysis");
        }

        if (isToolAvailable(TOOL_ANDROID_SDK)) {
            features.add("android_emulator");
            features.add("sandbox_analysis");
        }

        if (isToolAvailable(TOOL_TSHARK)) {
            features.add("protocol_analysis");
        }

        return features;
    }

    /**
     * 测试工具是否可正常执行（运行 --version 命令）
     */
    public Map<String, Object> testTool(String toolName) {
        Map<String, Object> result = new HashMap<>();

        ToolInfo tool = toolRegistry.get(toolName);
        if (tool == null || tool.getPath() == null) {
            result.put("success", false);
            result.put("error", "工具未配置或路径为空");
            return result;
        }

        Path toolPath = Paths.get(tool.getPath());
        if (!Files.exists(toolPath)) {
            result.put("success", false);
            result.put("error", "工具文件不存在: " + tool.getPath());
            return result;
        }

        try {
            List<String> command = buildTestCommand(toolName, toolPath);
            if (command == null) {
                result.put("success", false);
                result.put("error", "不支持测试该工具: " + toolName);
                return result;
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.put("success", false);
                result.put("error", "工具执行超时（30秒）");
                return result;
            }

            int exitCode = process.exitValue();
            String outputStr = output.toString().trim();

            if (exitCode == 0 && !outputStr.isEmpty()) {
                result.put("success", true);
                result.put("output", outputStr);
                result.put("version", extractVersion(outputStr));

                tool.setStatus(ToolStatus.AVAILABLE);
                tool.setLastVerified(new Date());
            } else {
                result.put("success", false);
                result.put("error", "退出码: " + exitCode + ", 输出: " + outputStr);

                tool.setStatus(ToolStatus.UNAVAILABLE);
                tool.setLastVerified(new Date());
            }

        } catch (Exception e) {
            log.error("测试工具失败: {}", toolName, e);
            result.put("success", false);
            result.put("error", "执行异常: " + e.getMessage());
            tool.setStatus(ToolStatus.UNAVAILABLE);
            tool.setLastVerified(new Date());
        }

        return result;
    }

    private List<String> buildTestCommand(String toolName, Path toolPath) {
        String fileName = toolPath.getFileName().toString().toLowerCase();
        List<String> command = new ArrayList<>();

        switch (toolName) {
            case TOOL_APKTOOL:
                if (fileName.endsWith(".jar")) {
                    command.add("java");
                    command.add("-jar");
                    command.add(toolPath.toString());
                    command.add("--version");
                } else {
                    command.add(toolPath.toString());
                    command.add("--version");
                }
                return command;
            case TOOL_JADX:
                if (fileName.endsWith(".jar")) {
                    command.add("java");
                    command.add("-jar");
                    command.add(toolPath.toString());
                    command.add("--version");
                } else if (Files.isDirectory(toolPath)) {
                    Path binJadx = toolPath.resolve("bin/jadx");
                    command.add(binJadx.toString());
                    command.add("--version");
                } else {
                    command.add(toolPath.toString());
                    command.add("--version");
                }
                return command;
            case TOOL_TSHARK:
                command.add(toolPath.toString());
                command.add("--version");
                return command;
            default:
                return null;
        }
    }

    private String extractVersion(String output) {
        if (output == null || output.isEmpty()) return "unknown";
        String[] lines = output.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches(".*\\d+\\.\\d+.*")) {
                return trimmed;
            }
        }
        return lines[0].trim();
    }

    /**
     * 检查用户是否有权限使用某功能
     */
    public Map<String, Object> checkFeaturePermission(String userRole, String feature) {
        Map<String, Object> result = new HashMap<>();

        // 安全相关功能需要管理员权限
        boolean isAdmin = "ADMIN".equalsIgnoreCase(userRole);
        boolean requiresAdmin = false;

        switch (feature) {
            case "apk_decompile":
            case "apk_reverse":
            case "smali_to_java":
                requiresAdmin = requiresAdmin(TOOL_APKTOOL) || requiresAdmin(TOOL_JADX);
                break;
            case "frida_hook":
            case "dynamic_analysis":
                requiresAdmin = requiresAdmin(TOOL_FRIDA);
                break;
            case "sandbox_analysis":
            case "android_emulator":
                requiresAdmin = requiresAdmin(TOOL_ANDROID_SDK);
                break;
            default:
                requiresAdmin = false;
        }

        boolean allowed = !requiresAdmin || isAdmin;

        result.put("allowed", allowed);
        result.put("requiresAdmin", requiresAdmin);
        result.put("isAdmin", isAdmin);

        if (!allowed) {
            result.put("reason", "此功能需要管理员授权");
        }

        return result;
    }
}