package com.aezer0.initialization.service.apktool;

import com.aezer0.initialization.service.tool.ToolManagementService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * ApkTool服务
 * 提供APK逆向编译、反编译功能
 */
@Service
@Slf4j
public class ApkToolService {

    @Autowired
    private ToolManagementService toolService;

    @Value("${apktool.output-dir:/tmp/apktool-output}")
    private String outputDir;

    @Value("${apktool.framework-tag:}")
    private String frameworkTag;

    private static final String APKTOOL_YML = "apktool.yml";
    private static final String ANDROID_MANIFEST = "AndroidManifest.xml";
    private static final String SMALI_DIR = "smali";
    private static final String RES_DIR = "res";
    private static final String ASSETS_DIR = "assets";
    private static final String LIB_DIR = "lib";
    private static final String SOURCES_DIR = "sources";

    /**
     * 反编译APK
     */
    public Map<String, Object> decompile(byte[] apkBytes, String packageName) {
        Map<String, Object> result = new HashMap<>();
        Path workDir = null;

        try {
            log.info("开始反编译APK: {}, 文件大小: {} MB", packageName, String.format("%.2f", apkBytes.length / (1024.0 * 1024.0)));

            // 检查工具是否可用
            if (!toolService.isToolAvailable(ToolManagementService.TOOL_APKTOOL)) {
                result.put("success", false);
                result.put("error", "ApkTool未配置或不可用，请在管理后台上传工具");
                return result;
            }

            // 创建临时目录，先写入文件再反编译（避免大文件占用内存）
            workDir = Files.createTempDirectory("apktool_");
            Path apkFile = workDir.resolve("input.apk");

            // 流式写入磁盘，避免大文件全部加载在堆内存
            log.info("正在将 APK 写入临时目录: {}", apkFile);
            Files.write(apkFile, apkBytes);
            log.info("APK 写入完成，开始反编译...");

            // 释放字节数组引用，帮助 GC
            apkBytes = null;

            Path decompiledDir = workDir.resolve("input");

            // 执行apktool d，根据文件大小动态计算超时
            long fileSizeMB = Files.size(apkFile) / (1024 * 1024);
            result.putAll(executeApktool("d", apkFile.toString(), decompiledDir.toString(), fileSizeMB));

            // 分析反编译结果
            if (Files.exists(decompiledDir)) {
                Map<String, Object> analysis = analyzeDecompiledOutput(decompiledDir);
                result.put("analysis", analysis);
                result.put("decompiledPath", decompiledDir.toString());
            }

            result.put("success", true);
            result.put("packageName", packageName);
            result.put("workDir", workDir.toString());

        } catch (Exception e) {
            log.error("反编译失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 编译回APK
     */
    public Map<String, Object> recompile(Path sourceDir, String outputApk) {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("开始编译APK: {}", sourceDir);

            // 执行apktool b
            Map<String, Object> execResult = executeApktool("b", sourceDir.toString(), null);

            // 查找输出的APK
            Path distDir = sourceDir.resolve("dist");
            if (Files.exists(distDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(distDir, "*.apk")) {
                    for (Path apk : stream) {
                        result.put("outputApk", apk.toString());
                        result.put("size", Files.size(apk));
                        break;
                    }
                }
            }

            result.put("success", true);
            result.putAll(execResult);

        } catch (Exception e) {
            log.error("编译失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 分析反编译输出
     */
    public Map<String, Object> analyzeDecompiledOutput(Path decompiledDir) {
        Map<String, Object> analysis = new HashMap<>();

        try {
            // 分析目录结构
            Map<String, Object> structure = analyzeDirectoryStructure(decompiledDir);
            analysis.put("structure", structure);

            // 分析AndroidManifest
            Map<String, Object> manifest = analyzeManifest(decompiledDir);
            analysis.put("manifest", manifest);

            // 分析Smali代码
            Map<String, Object> smali = analyzeSmaliCode(decompiledDir);
            analysis.put("smali", smali);

            // 分析资源文件
            Map<String, Object> resources = analyzeResources(decompiledDir);
            analysis.put("resources", resources);

            // 分析原生库
            Map<String, Object> nativeLibs = analyzeNativeLibraries(decompiledDir);
            analysis.put("nativeLibraries", nativeLibs);

            // 提取可读的Java源码（如果存在）
            Map<String, Object> sources = analyzeSources(decompiledDir);
            analysis.put("sources", sources);

            // ARSC资源解析
            Map<String, Object> arscAnalysis = analyzeArscResources(decompiledDir);
            analysis.put("arsc", arscAnalysis);

            // APK签名方案分析
            Map<String, Object> signatureAnalysis = analyzeSignatureScheme(decompiledDir);
            analysis.put("signature", signatureAnalysis);

            // 生成源码还原评估
            Map<String, Object> reconstruction = assessSourceReconstruction(structure, smali, sources);
            analysis.put("reconstruction", reconstruction);

        } catch (Exception e) {
            log.error("分析失败", e);
            analysis.put("error", e.getMessage());
        }

        return analysis;
    }

    /**
     * 列出所有源码文件
     */
    public List<Map<String, Object>> listSourceFiles(Path decompiledDir) {
        List<Map<String, Object>> files = new ArrayList<>();

        try {
            // 列出smali文件
            Path smaliDir = decompiledDir.resolve(SMALI_DIR);
            if (Files.exists(smaliDir)) {
                listFilesRecursive(smaliDir, "smali", files);
            }

            // 列出sources文件（如果有）
            Path sourcesDir = decompiledDir.resolve(SOURCES_DIR);
            if (Files.exists(sourcesDir)) {
                listFilesRecursive(sourcesDir, "java", files);
            }

            // 列出资源文件
            Path resDir = decompiledDir.resolve(RES_DIR);
            if (Files.exists(resDir)) {
                listFilesRecursive(resDir, "resource", files);
            }

            // 列出原生库
            Path libDir = decompiledDir.resolve(LIB_DIR);
            if (Files.exists(libDir)) {
                listFilesRecursive(libDir, "native", files);
            }

        } catch (Exception e) {
            log.error("列出文件失败", e);
        }

        return files;
    }

    /**
     * 读取文件内容
     */
    public Map<String, Object> readFile(Path decompiledDir, String relativePath) {
        Map<String, Object> result = new HashMap<>();

        try {
            Path filePath = decompiledDir.resolve(relativePath);
            if (!Files.exists(filePath)) {
                result.put("success", false);
                result.put("error", "文件不存在: " + relativePath);
                return result;
            }

            String content;
            String fileType = getFileType(relativePath);

            if (fileType.equals("image") || fileType.equals("binary")) {
                content = "[二进制文件]";
                result.put("isBinary", true);
            } else {
                content = Files.readString(filePath);
            }

            result.put("success", true);
            result.put("path", relativePath);
            result.put("content", content);
            result.put("size", Files.size(filePath));
            result.put("type", fileType);
            result.put("lastModified", Files.getLastModifiedTime(filePath).toMillis());

            if (fileType.equals("smali")) {
                result.put("pseudocode", decompileSmaliToPseudocode(content));

                List<String> jniMethods = new ArrayList<>();
                for (String line : content.split("\n")) {
                    if (line.trim().startsWith(".method") && line.contains("native")) {
                        String[] parts = line.trim().split("\\s+");
                        String mName = parts[parts.length - 1];
                        if (mName.contains("(")) {
                            mName = mName.substring(0, mName.indexOf('('));
                        }
                        jniMethods.add(mName);
                    }
                }
                result.put("jniMethods", jniMethods);
            }

        } catch (Exception e) {
            log.error("读取文件失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 获取文件树结构
     */
    public Map<String, Object> getFileTree(Path decompiledDir) {
        Map<String, Object> tree = new HashMap<>();

        try {
            tree.put("name", decompiledDir.getFileName().toString());
            tree.put("path", "");
            tree.put("type", "directory");
            tree.put("children", buildFileTree(decompiledDir, ""));
        } catch (Exception e) {
            log.error("获取文件树失败", e);
        }

        return tree;
    }

    /**
     * 提取SO文件列表（用于单独分析）
     */
    public List<Map<String, Object>> extractSoFiles(Path decompiledDir) {
        List<Map<String, Object>> soFiles = new ArrayList<>();
        Path libDir = decompiledDir.resolve(LIB_DIR);

        if (Files.exists(libDir)) {
            try (DirectoryStream<Path> archStream = Files.newDirectoryStream(libDir)) {
                for (Path archDir : archStream) {
                    if (Files.isDirectory(archDir)) {
                        try (DirectoryStream<Path> soStream = Files.newDirectoryStream(archDir, "*.so")) {
                            for (Path soFile : soStream) {
                                Map<String, Object> soInfo = new HashMap<>();
                                soInfo.put("name", soFile.getFileName().toString());
                                soInfo.put("arch", archDir.getFileName().toString());
                                soInfo.put("path", libDir.relativize(soFile).toString());
                                soInfo.put("fullPath", soFile.toString());
                                soInfo.put("size", Files.size(soFile));
                                soFiles.add(soInfo);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("提取SO文件失败", e);
            }
        }

        return soFiles;
    }

    /**
     * 清理工作目录
     */
    public void cleanupWorkDir(Path workDir) {
        try {
            if (Files.exists(workDir)) {
                // 删除临时文件
                Files.walk(workDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception e) {
                            log.warn("删除文件失败: {}", p);
                        }
                    });
                log.info("清理工作目录: {}", workDir);
            }
        } catch (Exception e) {
            log.error("清理失败", e);
        }
    }

    // ========== 私有方法 ==========

    private Map<String, Object> executeApktool(String command, String input, String output) throws Exception {
        return executeApktool(command, input, output, 0);
    }

    private Map<String, Object> executeApktool(String command, String input, String output, long fileSizeMB) throws Exception {
        // 从工具管理服务获取apktool路径
        String apktoolPath = toolService.getToolPath(ToolManagementService.TOOL_APKTOOL);
        if (apktoolPath == null) {
            throw new Exception("ApkTool未配置");
        }

        List<String> args = new ArrayList<>();
        if (apktoolPath != null && apktoolPath.endsWith(".jar")) {
            args.add("java");
            args.add("-jar");
            // 大文件增加 JVM 堆内存
            if (fileSizeMB > 100) {
                args.add("-Xmx2048m");
            } else if (fileSizeMB > 50) {
                args.add("-Xmx1024m");
            }
        }
        args.add(apktoolPath);

        if (!frameworkTag.isEmpty()) {
            args.add("-f"); // force apply framework tag
        }

        args.add(command);

        if ("d".equals(command)) {
            args.add("-o"); // output directory
            args.add(output);
            args.add("-f"); // force delete output directory
        }

        args.add(input);

        log.info("执行 apktool 命令: {}", String.join(" ", args));

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder outputBuilder = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputBuilder.append(line).append("\n");
                log.debug("apktool: {}", line);
            }
        }

        // 动态超时：基础5分钟，每10MB增加30秒，最大60分钟
        long baseTimeout = 5;
        long additionalTimeout = Math.max(0, fileSizeMB - 10) * 30 / 60; // 每10MB增加0.5分钟
        long timeoutMinutes = Math.min(60, baseTimeout + additionalTimeout);
        log.info("ApkTool 超时设置为 {} 分钟 (文件大小 {} MB)", timeoutMinutes, fileSizeMB);

        boolean finished = process.waitFor(timeoutMinutes, java.util.concurrent.TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("ApkTool执行超时 (" + timeoutMinutes + "分钟)，文件大小: " + fileSizeMB + "MB");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("exitCode", process.exitValue());
        result.put("output", outputBuilder.toString());

        if (process.exitValue() != 0) {
            throw new Exception("Apktool执行失败: " + outputBuilder);
        }

        return result;
    }

    private Map<String, Object> analyzeDirectoryStructure(Path dir) throws IOException {
        Map<String, Object> structure = new HashMap<>();
        Map<String, Integer> dirCounts = new HashMap<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    dirCounts.put(name, countFiles(entry));
                }
            }
        }

        structure.put("directories", dirCounts);
        structure.put("hasSmali", dirCounts.containsKey(SMALI_DIR));
        structure.put("hasRes", dirCounts.containsKey(RES_DIR));
        structure.put("hasAssets", dirCounts.containsKey(ASSETS_DIR));
        structure.put("hasLib", dirCounts.containsKey(LIB_DIR));
        structure.put("hasSources", dirCounts.containsKey(SOURCES_DIR));

        return structure;
    }

    private Map<String, Object> analyzeManifest(Path dir) {
        Map<String, Object> manifest = new HashMap<>();

        Path manifestFile = dir.resolve(ANDROID_MANIFEST);
        if (Files.exists(manifestFile)) {
            try {
                String content = Files.readString(manifestFile);
                manifest.put("size", Files.size(manifestFile));

                // 基础组件提取
                List<String> activities = extractActivities(content);
                List<String> services = extractServices(content);
                List<String> receivers = extractReceivers(content);
                List<String> permissions = extractPermissions(content);

                manifest.put("activities", activities);
                manifest.put("services", services);
                manifest.put("receivers", receivers);
                manifest.put("permissions", permissions);
                manifest.put("activityCount", activities.size());
                manifest.put("serviceCount", services.size());
                manifest.put("receiverCount", receivers.size());
                manifest.put("permissionCount", permissions.size());

                // 深度安全属性提取
                manifest.put("debuggable", content.contains("android:debuggable=\"true\""));
                manifest.put("allowBackup", !content.contains("android:allowBackup=\"false\""));
                manifest.put("usesCleartextTraffic", content.contains("android:usesCleartextTraffic=\"true\""));
                manifest.put("testOnly", content.contains("android:testOnly=\"true\""));
                manifest.put("extractNativeLibs", !content.contains("android:extractNativeLibs=\"false\""));
                manifest.put("hasSharedUserId", content.contains("android:sharedUserId="));

                // 检查 application 安全属性
                Matcher appMatcher = Pattern.compile("<application[^>]+>").matcher(content);
                if (appMatcher.find()) {
                    String appTag = appMatcher.group();
                    manifest.put("appDebuggable", appTag.contains("android:debuggable=\"true\""));
                    manifest.put("appAllowBackup", !appTag.contains("android:allowBackup=\"false\""));
                    manifest.put("appNetworkSecurityConfig", appTag.contains("android:networkSecurityConfig="));
                    manifest.put("appUsesCleartextTraffic", appTag.contains("android:usesCleartextTraffic=\"true\""));
                }

                // 提取导出组件详情 (含Intent Filter和权限保护)
                List<Map<String, Object>> exportedComponents = extractExportedComponents(content);
                manifest.put("exportedComponents", exportedComponents);
                manifest.put("exportedComponentCount", exportedComponents.size());

            } catch (Exception e) {
                log.error("解析Manifest失败", e);
            }
        }

        return manifest;
    }

    /**
     * 提取导出组件详细信息 (含Intent Filter、权限保护)
     */
    private List<Map<String, Object>> extractExportedComponents(String manifest) {
        List<Map<String, Object>> components = new ArrayList<>();
        String[] types = {"activity", "service", "receiver", "provider"};

        for (String type : types) {
            Pattern p = Pattern.compile(
                    "<" + type + "\\s+([^>]+)>([\\s\\S]*?)</" + type + ">",
                    Pattern.DOTALL);
            Matcher m = p.matcher(manifest);
            while (m.find()) {
                String attrs = m.group(1);
                String body = m.group(2);

                boolean exported = attrs.contains("android:exported=\"true\"");
                boolean hasIntentFilter = body.contains("<intent-filter");
                boolean isExported = exported || hasIntentFilter;

                if (isExported) {
                    Map<String, Object> comp = new HashMap<>();
                    // 提取组件名称
                    Matcher nameMatcher = Pattern.compile("android:name=\"([^\"]+)\"").matcher(attrs);
                    comp.put("name", nameMatcher.find() ? nameMatcher.group(1) : "unknown");
                    comp.put("type", type);
                    comp.put("explicitlyExported", exported);
                    comp.put("hasIntentFilter", hasIntentFilter);

                    // 提取权限保护
                    Matcher permMatcher = Pattern.compile("android:permission=\"([^\"]+)\"").matcher(attrs);
                    comp.put("permissionProtected", permMatcher.find());
                    if (permMatcher.find()) {
                        comp.put("requiredPermission", permMatcher.group(0));
                    } else {
                        // re-match after resetting
                        Matcher permMatcher2 = Pattern.compile("android:permission=\"([^\"]+)\"").matcher(attrs);
                        comp.put("requiredPermission", permMatcher2.find() ? permMatcher2.group(1) : "");

                        // Actually let me just search manually
                        int permIdx = attrs.indexOf("android:permission=\"");
                        if (permIdx >= 0) {
                            int start = permIdx + "android:permission=\"".length();
                            int end = attrs.indexOf("\"", start);
                            comp.put("requiredPermission", attrs.substring(start, end));
                        }
                    }

                    // 提取进程名
                    Matcher procMatcher = Pattern.compile("android:process=\"([^\"]+)\"").matcher(attrs);
                    if (procMatcher.find()) {
                        comp.put("process", procMatcher.group(1));
                    }

                    // Intent Filter Action提取
                    List<String> actions = new ArrayList<>();
                    Matcher actionMatcher = Pattern.compile("<action\\s+android:name=\"([^\"]+)\"").matcher(body);
                    while (actionMatcher.find()) {
                        actions.add(actionMatcher.group(1));
                    }
                    if (!actions.isEmpty()) {
                        comp.put("intentFilterActions", actions);
                    }

                    // 风险评估
                    if (comp.get("requiredPermission") == null || "".equals(comp.get("requiredPermission"))) {
                        comp.put("risk", type.equals("provider") ? "CRITICAL" : "HIGH");
                        comp.put("riskDescription", "导出组件未受权限保护，可被任意第三方应用调用");
                    } else {
                        comp.put("risk", "MEDIUM");
                        comp.put("riskDescription", "导出组件有权限保护");
                    }

                    components.add(comp);
                }
            }
        }
        return components;
    }

    private Map<String, Object> analyzeSmaliCode(Path dir) throws IOException {
        Map<String, Object> smali = new HashMap<>();
        Path smaliDir = dir.resolve(SMALI_DIR);

        if (Files.exists(smaliDir)) {
            List<String> classes = new ArrayList<>();
            Map<String, Integer> packageStats = new HashMap<>();
            int totalMethods = 0;
            int totalInstructions = 0;
            int totalFields = 0;
            int maxMethodComplexity = 0;
            String mostComplexMethod = "";
            Map<String, Integer> complexityDistribution = new LinkedHashMap<>();

            List<Path> smaliFiles = new ArrayList<>();
            try (var walk = Files.walk(smaliDir)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".smali"))
                    .forEach(smaliFiles::add);
            }

            for (Path smaliFile : smaliFiles) {
                String relativePath = smaliDir.relativize(smaliFile).toString().replace("\\", "/");
                classes.add(relativePath);

                // 统计包
                Path parent = smaliFile.getParent();
                String packageName = parent != null ? smaliDir.relativize(parent).toString().replace("\\", "/") : "root";
                packageStats.merge(packageName, 1, Integer::sum);

                // 分析文件内容
                try {
                    String content = Files.readString(smaliFile);
                    String[] lines = content.split("\n");
                    int methodCount = 0;
                    int currentMethodInstructions = 0;
                    String currentMethodName = "";
                    int fieldCount = 0;

                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith(".method")) {
                            methodCount++;
                            if (currentMethodInstructions > 0) {
                                if (currentMethodInstructions > maxMethodComplexity) {
                                    maxMethodComplexity = currentMethodInstructions;
                                    mostComplexMethod = currentMethodName;
                                }
                                updateComplexityDistribution(complexityDistribution, currentMethodInstructions);
                            }
                            currentMethodInstructions = 0;
                            // 提取方法名
                            String[] parts = trimmed.split("\\s+");
                            if (parts.length > 0) {
                                currentMethodName = parts[parts.length - 1];
                                if (currentMethodName.contains("(")) {
                                    currentMethodName = currentMethodName.substring(0, currentMethodName.indexOf('('));
                                }
                            }
                        } else if (trimmed.startsWith(".field")) {
                            fieldCount++;
                        } else if (trimmed.startsWith(".end method")) {
                            updateComplexityDistribution(complexityDistribution, currentMethodInstructions);
                            if (currentMethodInstructions > maxMethodComplexity) {
                                maxMethodComplexity = currentMethodInstructions;
                                mostComplexMethod = currentMethodName;
                            }
                            currentMethodInstructions = 0;
                        } else if (!trimmed.isEmpty() && !trimmed.startsWith(".") && !trimmed.startsWith("#")) {
                            currentMethodInstructions++;
                        }
                    }
                    totalMethods += methodCount;
                    totalInstructions += currentMethodInstructions;
                    totalFields += fieldCount;
                } catch (Exception ignored) {}
            }

            smali.put("classCount", classes.size());
            smali.put("methodCount", totalMethods);
            smali.put("instructionCount", totalInstructions);
            smali.put("fieldCount", totalFields);
            smali.put("avgInstructionsPerMethod", totalMethods > 0 ? totalInstructions / totalMethods : 0);
            smali.put("maxMethodComplexity", maxMethodComplexity);
            smali.put("mostComplexMethod", mostComplexMethod);
            smali.put("complexityDistribution", complexityDistribution);
            smali.put("packageCount", packageStats.size());
            smali.put("topPackages", getTopEntries(packageStats, 10));
            smali.put("classes", classes);
        }

        return smali;
    }

    private void updateComplexityDistribution(Map<String, Integer> dist, int complexity) {
        String bucket;
        if (complexity <= 5) bucket = "1-5";
        else if (complexity <= 20) bucket = "6-20";
        else if (complexity <= 50) bucket = "21-50";
        else if (complexity <= 100) bucket = "51-100";
        else bucket = "101+";
        dist.merge(bucket, 1, Integer::sum);
    }

    private Map<String, Object> analyzeResources(Path dir) throws IOException {
        Map<String, Object> resources = new HashMap<>();
        Path resDir = dir.resolve(RES_DIR);

        if (Files.exists(resDir)) {
            Map<String, Integer> resourceTypes = new HashMap<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(resDir)) {
                for (Path entry : stream) {
                    String name = entry.getFileName().toString();
                    if (Files.isDirectory(entry)) {
                        int count = countFiles(entry);
                        resourceTypes.put(name, count);
                    }
                }
            }

            resources.put("types", resourceTypes);
            resources.put("totalTypes", resourceTypes.size());
            resources.put("hasLayout", resourceTypes.containsKey("layout"));
            resources.put("hasValues", resourceTypes.containsKey("values"));
            resources.put("hasDrawable", resourceTypes.containsKey("drawable"));
        }

        return resources;
    }

    private Map<String, Object> analyzeNativeLibraries(Path dir) throws IOException {
        Map<String, Object> libs = new HashMap<>();
        Path libDir = dir.resolve(LIB_DIR);

        List<Map<String, Object>> libraries = new ArrayList<>();

        if (Files.exists(libDir)) {
            try (DirectoryStream<Path> archStream = Files.newDirectoryStream(libDir)) {
                for (Path archDir : archStream) {
                    if (Files.isDirectory(archDir)) {
                        try (DirectoryStream<Path> soStream = Files.newDirectoryStream(archDir, "*.so")) {
                            for (Path soFile : soStream) {
                                Map<String, Object> lib = new HashMap<>();
                                lib.put("name", soFile.getFileName().toString());
                                lib.put("arch", archDir.getFileName().toString());
                                lib.put("path", libDir.relativize(soFile).toString().replace("\\", "/"));
                                lib.put("size", Files.size(soFile));
                                libraries.add(lib);
                            }
                        }
                    }
                }
            }
        }

        libs.put("libraries", libraries);
        libs.put("count", libraries.size());
        libs.put("archs", libraries.stream()
            .map(m -> (String) m.get("arch"))
            .distinct()
            .toList());

        return libs;
    }

    private Map<String, Object> analyzeSources(Path dir) throws IOException {
        Map<String, Object> sources = new HashMap<>();
        Path sourcesDir = dir.resolve(SOURCES_DIR);

        if (Files.exists(sourcesDir)) {
            List<String> javaFiles = new ArrayList<>();
            List<Path> javaPaths = new ArrayList<>();

            try (var walk = Files.walk(sourcesDir)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(javaPaths::add);
            }

            for (Path javaFile : javaPaths) {
                javaFiles.add(sourcesDir.relativize(javaFile).toString().replace("\\", "/"));
            }

            sources.put("javaFiles", javaFiles);
            sources.put("count", javaFiles.size());
            sources.put("hasJavaSource", true);
        } else {
            sources.put("hasJavaSource", false);
            sources.put("count", 0);
        }

        return sources;
    }

    private Map<String, Object> assessSourceReconstruction(Map<String, Object> structure,
                                                           Map<String, Object> smali,
                                                           Map<String, Object> sources) {
        Map<String, Object> assessment = new HashMap<>();

        int smaliClassCount = (int) smali.getOrDefault("classCount", 0);
        boolean hasSources = (boolean) sources.getOrDefault("hasJavaSource", false);
        int javaSourceCount = (int) sources.getOrDefault("count", 0);

        // 安全获取 nativeLibCount，避免泛型类型推断问题
        Map<?, ?> nativeLibrariesMap = (Map<?, ?>) structure.getOrDefault("nativeLibraries", Collections.emptyMap());
        int nativeLibCount = 0;
        Object nativeLibCountObj = nativeLibrariesMap.get("count");
        if (nativeLibCountObj instanceof Integer) {
            nativeLibCount = (Integer) nativeLibCountObj;
        }

        // 评估还原可能性
        String reconstructionLevel;
        String description;
        List<String> limitations = new ArrayList<>();

        if (hasSources) {
            double coverage = smaliClassCount > 0 ? (double) javaSourceCount / smaliClassCount : 0;
            if (coverage > 0.9) {
                reconstructionLevel = "EXCELLENT";
                description = "Java源码覆盖率 >90%，可完整还原业务逻辑";
            } else if (coverage > 0.5) {
                reconstructionLevel = "GOOD";
                description = "Java源码覆盖率 " + String.format("%.0f%%", coverage * 100) + "，主要逻辑可还原";
                limitations.add("部分类反编译失败，需结合Smali分析");
            } else {
                reconstructionLevel = "PARTIAL";
                description = "Java源码覆盖率较低 (" + String.format("%.0f%%", coverage * 100) + ")，需大量Smali辅助分析";
                limitations.add("大量类无法还原为Java源码");
            }
        } else if (smaliClassCount > 0) {
            reconstructionLevel = "GOOD";
            description = "存在Smali字节码，可通过反编译还原大部分代码逻辑";
            limitations.add("变量名、方法名丢失");
            limitations.add("注释丢失");
            limitations.add("代码结构可能与原始代码不同");
        } else {
            reconstructionLevel = "LIMITED";
            description = "无Smali代码，无法进行源码还原";
        }

        if (nativeLibCount > 0) {
            limitations.add("存在 " + nativeLibCount + " 个原生库(Native Code)，核心逻辑可能在SO中");
        }

        // 方法体覆盖评估
        int totalMethods = (int) smali.getOrDefault("methodCount", 0);
        int totalInstructions = (int) smali.getOrDefault("instructionCount", 0);
        assessment.put("totalMethods", totalMethods);
        assessment.put("totalInstructions", totalInstructions);

        assessment.put("level", reconstructionLevel);
        assessment.put("description", description);
        assessment.put("limitations", limitations);
        assessment.put("smaliClassCount", smaliClassCount);
        assessment.put("javaSourceCount", javaSourceCount);
        assessment.put("nativeLibCount", nativeLibCount);
        assessment.put("canReconstruct", smaliClassCount > 0 || hasSources);

        return assessment;
    }

    /**
     * ARSC (resources.arsc) 资源表基础分析
     */
    private Map<String, Object> analyzeArscResources(Path decompiledDir) {
        Map<String, Object> arsc = new HashMap<>();
        Path arscFile = decompiledDir.resolve("resources.arsc");

        if (!Files.exists(arscFile)) {
            arsc.put("exists", false);
            arsc.put("note", "resources.arsc不在反编译根目录中（可能被apktool解码为res/values/目录）");
        } else {
            arsc.put("exists", true);
            arsc.put("size", 0L);
            try {
                long size = Files.size(arscFile);
                arsc.put("size", size);

                // 读取ARSC头部 (8 bytes)
                byte[] header = new byte[8];
                try (var is = Files.newInputStream(arscFile)) {
                    is.read(header);
                }
                // ARSC header: type(2) + headerSize(2) + chunkSize(4)
                int type = ((header[1] & 0xFF) << 8) | (header[0] & 0xFF);
                int headerSize = ((header[3] & 0xFF) << 8) | (header[2] & 0xFF);
                long chunkSize = ((long)(header[7] & 0xFF) << 24) | ((header[6] & 0xFF) << 16) |
                        ((header[5] & 0xFF) << 8) | (header[4] & 0xFF);

                arsc.put("type", "0x" + String.format("%04X", type));
                arsc.put("headerSize", headerSize);
                arsc.put("chunkSize", chunkSize);
                arsc.put("valid", type == 0x0002 && headerSize >= 8); // RES_TABLE_TYPE
            } catch (Exception e) {
                arsc.put("error", "ARSC文件读取失败: " + e.getMessage());
            }
        }
        return arsc;
    }

    /**
     * APK签名方案分析 v1/v2/v3
     */
    private Map<String, Object> analyzeSignatureScheme(Path decompiledDir) {
        Map<String, Object> sig = new HashMap<>();
        Path originalDir = decompiledDir.resolve("original");

        // 检查META-INF中的签名文件
        Path metaInf = decompiledDir.resolve("original").resolve("META-INF");
        if (!Files.exists(metaInf)) {
            metaInf = decompiledDir.resolve("META-INF");
        }

        sig.put("hasV1Signing", false);
        sig.put("hasV2Signing", false);
        sig.put("hasV3Signing", false);
        sig.put("certRSA", false);
        sig.put("certDSA", false);
        sig.put("certEC", false);
        sig.put("signFiles", new ArrayList<>());

        try {
            // 在反编译根目录下寻找 signing info
            Path apktoolYml = decompiledDir.resolve("apktool.yml");
            if (Files.exists(apktoolYml)) {
                String ymlContent = Files.readString(apktoolYml);
                sig.put("hasV1Signing", ymlContent.contains("doNotCompress") || ymlContent.contains("unknownFiles"));
            }

            // 检查 META-INF 目录
            Path[] searchDirs = {
                    decompiledDir.resolve("original").resolve("META-INF"),
                    decompiledDir.resolve("META-INF")
            };

            for (Path searchDir : searchDirs) {
                if (!Files.exists(searchDir)) continue;

                try (DirectoryStream<Path> stream = Files.newDirectoryStream(searchDir)) {
                    List<Map<String, Object>> signFiles = new ArrayList<>();
                    for (Path entry : stream) {
                        String name = entry.getFileName().toString().toUpperCase();
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("name", entry.getFileName().toString());
                        fileInfo.put("size", Files.size(entry));

                        if (name.endsWith(".RSA")) {
                            sig.put("certRSA", true);
                            sig.put("hasV1Signing", true);
                            fileInfo.put("type", "RSA签名证书");
                        } else if (name.endsWith(".DSA")) {
                            sig.put("certDSA", true);
                            sig.put("hasV1Signing", true);
                            fileInfo.put("type", "DSA签名证书");
                        } else if (name.endsWith(".EC")) {
                            sig.put("certEC", true);
                            sig.put("hasV1Signing", true);
                            fileInfo.put("type", "EC签名证书");
                        } else if (name.equals("MANIFEST.MF")) {
                            fileInfo.put("type", "v1签名清单");
                        } else if (name.endsWith(".SF")) {
                            fileInfo.put("type", "v1签名文件");
                        } else if (name.equals("CERT.SF") || name.startsWith("CERT-")) {
                            fileInfo.put("type", "v1证书签名");
                        }

                        signFiles.add(fileInfo);
                    }
                    sig.put("signFiles", signFiles);

                    // V2/V3签名检测（APK Signature Block）
                    if (signFiles.stream().anyMatch(f -> {
                        String n = ((String) f.get("name")).toUpperCase();
                        return n.contains("CERT") && (n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC"));
                    })) {
                        sig.put("hasV1Signing", true);
                    }
                }
            }

            // 凭apktool.yml判断v2/v3
            if (Files.exists(apktoolYml)) {
                String ymlContent = Files.readString(apktoolYml);
                if (ymlContent.contains("unknownFiles") || ymlContent.contains("doNotCompress")) {
                    sig.put("hasV2Signing", true);
                    sig.put("hasV3Signing", ymlContent.contains("v3") || sig.containsKey("hasV3Signing"));
                }
            }

            String schemeDescription;
            if (Boolean.TRUE.equals(sig.get("hasV3Signing"))) {
                schemeDescription = "v1 + v2 + v3 (APK Signature Scheme v3 — Android 9+)";
            } else if (Boolean.TRUE.equals(sig.get("hasV2Signing"))) {
                schemeDescription = "v1 + v2 (APK Signature Scheme v2 — Android 7+)";
            } else if (Boolean.TRUE.equals(sig.get("hasV1Signing"))) {
                schemeDescription = "v1 only (JAR签名 — 兼容所有版本)";
            } else {
                schemeDescription = "未检测到标准签名方案";
            }
            sig.put("schemeDescription", schemeDescription);

        } catch (Exception e) {
            log.warn("签名分析失败: {}", e.getMessage());
            sig.put("error", e.getMessage());
        }

        return sig;
    }

    private List<String> extractActivities(String manifest) {
        List<String> activities = new ArrayList<>();
        String pattern = "android:name=\"([^\"]+)\"";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(manifest);
        while (m.find()) {
            activities.add(m.group(1));
        }
        return activities;
    }

    private List<String> extractServices(String manifest) {
        List<String> services = new ArrayList<>();
        String pattern = "<service[^>]*android:name=\"([^\"]+)\"";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(manifest);
        while (m.find()) {
            services.add(m.group(1));
        }
        return services;
    }

    private List<String> extractReceivers(String manifest) {
        List<String> receivers = new ArrayList<>();
        String pattern = "<receiver[^>]*android:name=\"([^\"]+)\"";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(manifest);
        while (m.find()) {
            receivers.add(m.group(1));
        }
        return receivers;
    }

    private List<String> extractPermissions(String manifest) {
        List<String> permissions = new ArrayList<>();
        String pattern = "android.permission\\.([A-Z_]+)";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(manifest);
        while (m.find()) {
            permissions.add(m.group(1));
        }
        return permissions;
    }

    private void listFilesRecursive(Path dir, String fileType, List<Map<String, Object>> files) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                Map<String, Object> fileInfo = new HashMap<>();
                String relativePath = entry.toString();

                fileInfo.put("name", entry.getFileName().toString());
                fileInfo.put("path", relativePath);
                fileInfo.put("type", fileType);

                if (Files.isDirectory(entry)) {
                    fileInfo.put("isDirectory", true);
                    listFilesRecursive(entry, fileType, files);
                } else {
                    fileInfo.put("isDirectory", false);
                    fileInfo.put("size", Files.size(entry));
                }

                files.add(fileInfo);
            }
        } catch (Exception e) {
            log.error("递归列出文件失败", e);
        }
    }

    private List<Map<String, Object>> buildFileTree(Path dir, String parentPath) {
        List<Map<String, Object>> children = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                Map<String, Object> node = new HashMap<>();
                String name = entry.getFileName().toString();
                String path = parentPath.isEmpty() ? name : parentPath + "/" + name;

                node.put("name", name);
                node.put("path", path);
                node.put("type", Files.isDirectory(entry) ? "directory" : getFileCategory(name));

                if (Files.isDirectory(entry)) {
                    node.put("children", buildFileTree(entry, path));
                } else {
                    node.put("size", Files.size(entry));
                }

                children.add(node);
            }
        } catch (Exception e) {
            log.error("构建文件树失败", e);
        }

        return children;
    }

    private String getFileType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".smali")) return "smali";
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".webp")) return "image";
        if (lower.endsWith(".so")) return "native";
        if (lower.endsWith(".dex")) return "dex";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "config";
        return "text";
    }

    private String getFileCategory(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".smali")) return "smali";
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".webp")) return "image";
        if (lower.endsWith(".so")) return "native";
        if (lower.endsWith(".dex")) return "dex";
        return "file";
    }

    private int countFiles(Path dir) {
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                count++;
            }
        } catch (Exception e) {
            // ignore
        }
        return count;
    }

    private <K> List<Map.Entry<K, Integer>> getTopEntries(Map<K, Integer> map, int n) {
        return map.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(n)
            .toList();
    }

    public String decompileSmaliToPseudocode(String smali) {
        if (smali == null || smali.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = smali.split("\n");
        String currentClass = "UnknownClass";

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith(".class")) {
                String[] parts = trimmed.split("\\s+");
                String access = "";
                String className = "MyClass";
                for (String part : parts) {
                    if (part.equals("public") || part.equals("private") || part.equals("protected") || part.equals("interface") || part.equals("abstract")) {
                        access += part + " ";
                    } else if (part.startsWith("L") && part.endsWith(";")) {
                        className = part.substring(part.lastIndexOf('/') + 1, part.length() - 1);
                    }
                }
                currentClass = className;
                sb.append(access).append("class ").append(className).append(" {\n");
            } else if (trimmed.startsWith(".super")) {
                String superClass = trimmed.substring(trimmed.lastIndexOf('/') + 1, trimmed.length() - 1);
                sb.append("    // Extends: ").append(superClass).append("\n\n");
            } else if (trimmed.startsWith(".field")) {
                String[] parts = trimmed.split("\\s+");
                String access = "";
                String fieldName = "field";
                String fieldType = "Object";
                for (String part : parts) {
                    if (part.equals("public") || part.equals("private") || part.equals("protected") || part.equals("static") || part.equals("final")) {
                        access += part + " ";
                    } else if (part.contains(":")) {
                        String[] pair = part.split(":");
                        fieldName = pair[0];
                        fieldType = parseSmaliType(pair[1]);
                    }
                }
                sb.append("    ").append(access).append(fieldType).append(" ").append(fieldName).append(";\n");
            } else if (trimmed.startsWith(".method")) {
                String[] parts = trimmed.split("\\s+");
                String access = "";
                String methodName = "method";
                String signature = "";
                for (String part : parts) {
                    if (part.equals("public") || part.equals("private") || part.equals("protected") || part.equals("static") || part.equals("final") || part.equals("native")) {
                        access += part + " ";
                    } else if (part.contains("(")) {
                        int idx = part.indexOf('(');
                        methodName = part.substring(0, idx);
                        signature = part.substring(idx);
                    }
                }
                String retType = "void";
                String params = "";
                if (!signature.isEmpty()) {
                    int closeIdx = signature.indexOf(')');
                    if (closeIdx != -1) {
                        String rawParams = signature.substring(1, closeIdx);
                        String rawRet = signature.substring(closeIdx + 1);
                        retType = parseSmaliType(rawRet);
                        params = parseSmaliParams(rawParams);
                    }
                }
                sb.append("\n    ").append(access).append(retType).append(" ").append(methodName).append("(").append(params).append(")");
                if (access.contains("native")) {
                    sb.append(";\n");
                } else {
                    sb.append(" {\n");
                }
            } else if (trimmed.startsWith(".end method")) {
                sb.append("    }\n");
            } else if (trimmed.startsWith("invoke-")) {
                try {
                    int arrowIdx = trimmed.indexOf("->");
                    if (arrowIdx != -1) {
                        int parenIdx = trimmed.indexOf('(', arrowIdx);
                        String methodCall = trimmed.substring(arrowIdx + 2, parenIdx != -1 ? parenIdx : trimmed.length());
                        String classPart = trimmed.substring(trimmed.indexOf('L') + 1, arrowIdx - 1);
                        String simpleClass = classPart.substring(classPart.lastIndexOf('/') + 1);
                        sb.append("        ").append(simpleClass).append(".").append(methodCall).append("(...);\n");
                    }
                } catch (Exception e) {
                    sb.append("        // ").append(trimmed).append(";\n");
                }
            } else if (trimmed.startsWith("const-string")) {
                int quoteIdx = trimmed.indexOf('"');
                if (quoteIdx != -1) {
                    String strVal = trimmed.substring(quoteIdx);
                    sb.append("        String val = ").append(strVal).append(";\n");
                }
            } else if (trimmed.startsWith("return-")) {
                sb.append("        return;\n");
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    private String parseSmaliType(String raw) {
        if (raw == null || raw.isEmpty()) return "void";
        if (raw.startsWith("[")) return parseSmaliType(raw.substring(1)) + "[]";
        if (raw.startsWith("L") && raw.endsWith(";")) {
            String className = raw.substring(raw.lastIndexOf('/') + 1, raw.length() - 1);
            return className;
        }
        return switch (raw) {
            case "V" -> "void";
            case "Z" -> "boolean";
            case "B" -> "byte";
            case "S" -> "short";
            case "C" -> "char";
            case "I" -> "int";
            case "J" -> "long";
            case "F" -> "float";
            case "D" -> "double";
            default -> "Object";
        };
    }

    private String parseSmaliParams(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        List<String> list = new ArrayList<>();
        int i = 0;
        int pCount = 1;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '[') {
                i++;
                continue;
            }
            if (c == 'L') {
                int end = raw.indexOf(';', i);
                if (end != -1) {
                    list.add(parseSmaliType(raw.substring(i, end + 1)) + " p" + pCount++);
                    i = end + 1;
                } else {
                    break;
                }
            } else {
                list.add(parseSmaliType(String.valueOf(c)) + " p" + pCount++);
                i++;
            }
        }
        return String.join(", ", list);
    }

    public Map<String, Object> calculateXrefs(Path decompiledDir, String relativePath) {
        Map<String, Object> xrefs = new HashMap<>();
        List<Map<String, Object>> callers = new ArrayList<>();
        List<Map<String, Object>> callees = new ArrayList<>();

        try {
            Path targetFile = decompiledDir.resolve(relativePath);
            if (!Files.exists(targetFile)) {
                xrefs.put("success", false);
                xrefs.put("error", "文件不存在");
                return xrefs;
            }

            String smaliPath = relativePath.replace("\\", "/");
            if (smaliPath.startsWith("smali/")) {
                smaliPath = smaliPath.substring(6);
            }
            if (smaliPath.endsWith(".smali")) {
                smaliPath = smaliPath.substring(0, smaliPath.length() - 6);
            }
            String classSignature = "L" + smaliPath + ";";

            String targetContent = Files.readString(targetFile);
            String[] lines = targetContent.split("\n");
            String currentMethod = "clinit";

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith(".method")) {
                    String[] parts = trimmed.split("\\s+");
                    currentMethod = parts[parts.length - 1];
                    if (currentMethod.contains("(")) {
                        currentMethod = currentMethod.substring(0, currentMethod.indexOf('('));
                    }
                }
                if (trimmed.startsWith("invoke-")) {
                    int arrowIdx = trimmed.indexOf("->");
                    if (arrowIdx != -1) {
                        int parenIdx = trimmed.indexOf('(', arrowIdx);
                        String methodCall = trimmed.substring(arrowIdx + 2, parenIdx != -1 ? parenIdx : trimmed.length());
                        int classStart = trimmed.indexOf('L', trimmed.indexOf('}'));
                        if (classStart != -1 && classStart < arrowIdx) {
                            String targetClass = trimmed.substring(classStart, arrowIdx);
                            Map<String, Object> callee = new HashMap<>();
                            callee.put("calleeClass", targetClass);
                            callee.put("calleeMethod", methodCall);
                            callee.put("callerMethod", currentMethod);
                            callee.put("lineContent", trimmed);
                            callees.add(callee);
                        }
                    }
                }
            }

            Path smaliDir = decompiledDir.resolve("smali");
            if (Files.exists(smaliDir)) {
                try (var walk = Files.walk(smaliDir)) {
                    walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".smali"))
                        .forEach(p -> {
                            try {
                                String content = Files.readString(p);
                                if (content.contains(classSignature)) {
                                    String callerRelPath = decompiledDir.relativize(p).toString().replace("\\", "/");
                                    String[] sLines = content.split("\n");
                                    String cMethod = "clinit";
                                    for (String sLine : sLines) {
                                        String sTrimmed = sLine.trim();
                                        if (sTrimmed.startsWith(".method")) {
                                            String[] parts = sTrimmed.split("\\s+");
                                            cMethod = parts[parts.length - 1];
                                            if (cMethod.contains("(")) {
                                                cMethod = cMethod.substring(0, cMethod.indexOf('('));
                                            }
                                        }
                                        if (sTrimmed.contains(classSignature)) {
                                            Map<String, Object> caller = new HashMap<>();
                                            caller.put("callerClass", callerRelPath);
                                            caller.put("callerMethod", cMethod);
                                            caller.put("lineContent", sTrimmed);
                                            callers.add(caller);
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        });
                }
            }

            xrefs.put("success", true);
            xrefs.put("classSignature", classSignature);
            xrefs.put("callers", callers.stream().distinct().limit(30).toList());
            xrefs.put("callees", callees.stream().distinct().limit(30).toList());

        } catch (Exception e) {
            log.error("计算交叉引用失败", e);
            xrefs.put("success", false);
            xrefs.put("error", e.getMessage());
        }

        return xrefs;
    }

    public List<Map<String, Object>> searchInCodebase(Path decompiledDir, String query, boolean isRegex) {
        return searchInCodebase(decompiledDir, query, isRegex, false, "all", false);
    }

    public List<Map<String, Object>> searchInCodebase(Path decompiledDir, String query, boolean isRegex,
                                                      boolean caseSensitive, String fileType, boolean excludeThirdParty) {
        List<Map<String, Object>> results = Collections.synchronizedList(new ArrayList<>());
        if (query == null || query.trim().isEmpty()) return results;

        try {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            Pattern pattern = isRegex
                ? Pattern.compile(query, flags)
                : Pattern.compile(Pattern.quote(query), flags);

            Path inputDir = decompiledDir;
            if (!Files.exists(inputDir)) return results;

            try (var walk = Files.walk(inputDir)) {
                walk.parallel()
                    .filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        if (results.size() >= 200) {
                            return;
                        }
                        String relativePath = inputDir.relativize(filePath).toString().replace("\\", "/");
                        String lowerPath = relativePath.toLowerCase();

                        // Exclude third-party libraries to filter noise
                        if (excludeThirdParty) {
                            if (lowerPath.contains("smali/android/") ||
                                lowerPath.contains("smali/androidx/") ||
                                lowerPath.contains("smali/kotlin/") ||
                                lowerPath.contains("smali/com/google/") ||
                                lowerPath.contains("smali/com/android/")) {
                                return;
                            }
                        }

                        // Filter by specified file type scope
                        if (fileType != null && !"all".equalsIgnoreCase(fileType)) {
                            if ("smali".equalsIgnoreCase(fileType) && !lowerPath.endsWith(".smali")) return;
                            if ("xml".equalsIgnoreCase(fileType) && !lowerPath.endsWith(".xml")) return;
                            if ("java".equalsIgnoreCase(fileType) && !lowerPath.endsWith(".java")) return;
                        } else {
                            // Binary files exclusion by default
                            if (lowerPath.endsWith(".so") || lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg") ||
                                lowerPath.endsWith(".apk") || lowerPath.endsWith(".dex") || lowerPath.endsWith(".gif")) {
                                return;
                            }
                        }

                        try {
                            List<String> lines = Files.readAllLines(filePath, java.nio.charset.StandardCharsets.UTF_8);
                            searchLines(lines, relativePath, pattern, results);
                        } catch (Exception e) {
                            try {
                                byte[] bytes = Files.readAllBytes(filePath);
                                String content = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                                String[] lines = content.split("\n");
                                searchLines(Arrays.asList(lines), relativePath, pattern, results);
                            } catch (Exception ignored) {}
                        }
                    });
            }
        } catch (Exception e) {
            log.error("代码检索失败", e);
        }

        // Parallel operations lead to randomized order. Sort results by path and line number for robust UI rendering.
        List<Map<String, Object>> sortedResults = new ArrayList<>(results);
        sortedResults.sort((a, b) -> {
            String pathA = (String) a.get("path");
            String pathB = (String) b.get("path");
            int pathComp = pathA.compareTo(pathB);
            if (pathComp != 0) return pathComp;
            return Integer.compare((Integer) a.get("lineNumber"), (Integer) b.get("lineNumber"));
        });

        return sortedResults;
    }

    private void searchLines(List<String> lines, String relativePath, Pattern pattern, List<Map<String, Object>> results) {
        int totalLines = lines.size();
        for (int i = 0; i < totalLines; i++) {
            if (results.size() >= 200) {
                break;
            }
            String line = lines.get(i);
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                Map<String, Object> match = new HashMap<>();
                match.put("path", relativePath);
                match.put("lineNumber", i + 1);
                match.put("lineContent", line.trim());

                // Capture 2 lines of context before
                List<Map<String, Object>> contextBefore = new ArrayList<>();
                for (int j = Math.max(0, i - 2); j < i; j++) {
                    Map<String, Object> cLine = new HashMap<>();
                    cLine.put("lineNumber", j + 1);
                    cLine.put("content", lines.get(j));
                    contextBefore.add(cLine);
                }
                match.put("contextBefore", contextBefore);

                // Capture 2 lines of context after
                List<Map<String, Object>> contextAfter = new ArrayList<>();
                for (int j = i + 1; j < Math.min(totalLines, i + 3); j++) {
                    Map<String, Object> cLine = new HashMap<>();
                    cLine.put("lineNumber", j + 1);
                    cLine.put("content", lines.get(j));
                    contextAfter.add(cLine);
                }
                match.put("contextAfter", contextAfter);

                results.add(match);
            }
        }
    }
}
