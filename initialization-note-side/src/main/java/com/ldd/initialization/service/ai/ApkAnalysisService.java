package com.ldd.initialization.service.ai;

import com.ldd.initialization.config.ai.AiAnalysisConfig;
import lombok.extern.slf4j.Slf4j;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.CertificateMeta;
import net.dongliu.apk.parser.bean.UseFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ApkAnalysisService {

    @Autowired
    private SecurityAnalysisLlmService llmService;

    @Autowired
    private AiAnalysisConfig aiConfig;

    private static final Set<String> SENSITIVE_APIS = Set.of(
            // == 设备标识与隐私 (Device Fingerprinting) ==
            "Landroid/telephony/TelephonyManager;->getDeviceId",
            "Landroid/telephony/TelephonyManager;->getSubscriberId",
            "Landroid/telephony/TelephonyManager;->getLine1Number",
            "Landroid/telephony/TelephonyManager;->getNetworkOperatorName",
            "Landroid/telephony/TelephonyManager;->getSimSerialNumber",
            "Landroid/telephony/TelephonyManager;->getImei",
            "Landroid/telephony/TelephonyManager;->getMeid",
            "Landroid/provider/Settings$Secure;->getString",
            "Landroid/provider/Settings$System;->getString",
            "Landroid/net/wifi/WifiManager;->getConnectionInfo",
            "Landroid/net/wifi/WifiInfo;->getMacAddress",
            "Landroid/net/wifi/WifiInfo;->getBSSID",
            "Landroid/net/wifi/WifiInfo;->getSSID",
            "Landroid/os/Build;->getSerial",
            "Landroid/location/LocationManager;->getLastKnownLocation",
            "Landroid/location/LocationManager;->requestLocationUpdates",
            "Landroid/location/LocationManager;->isProviderEnabled",

            // == 命令执行与进程控制 ==
            "Ljava/lang/Runtime;->exec",
            "Ljava/lang/ProcessBuilder;->start",

            // == 短信与通讯 ==
            "Landroid/telephony/SmsManager;->sendTextMessage",
            "Landroid/telephony/SmsManager;->sendDataMessage",
            "Landroid/telephony/SmsManager;->sendMultipartTextMessage",
            "Landroid/telephony/SmsMessage;->getOriginatingAddress",
            "Landroid/telephony/SmsMessage;->getMessageBody",

            // == 摄像头与音频 ==
            "Landroid/hardware/Camera;->open",
            "Landroid/hardware/camera2/CameraManager;->openCamera",
            "Landroid/media/MediaRecorder;->start",
            "Landroid/media/MediaRecorder;->setAudioSource",
            "Landroid/media/MediaRecorder;->setVideoSource",
            "Landroid/media/AudioRecord;->startRecording",

            // == 动态加载与反射 ==
            "Ldalvik/system/DexClassLoader;-><init>",
            "Ljava/lang/reflect/Method;->invoke",
            "Ljava/lang/Class;->forName",
            "Ljava/lang/ClassLoader;->loadClass",
            "Ldalvik/system/PathClassLoader;-><init>",
            "Ldalvik/system/BaseDexClassLoader;-><init>",
            "Ldalvik/system/InMemoryDexClassLoader;-><init>",
            "Ldalvik/system/DexFile;-><init>",
            "Ldalvik/system/DexFile;->loadDex",

            // == 加密/解密 API ==
            "Ljavax/crypto/Cipher;->doFinal",
            "Ljavax/crypto/Cipher;->init",
            "Ljavax/crypto/KeyGenerator;->init",
            "Ljavax/crypto/spec/SecretKeySpec;-><init>",
            "Ljavax/crypto/spec/IvParameterSpec;-><init>",
            "Ljava/security/MessageDigest;->getInstance",
            "Ljava/security/Signature;->initSign",

            // == SSL/TLS 绕过 ==
            "Ljavax/net/ssl/TrustManager;->checkServerTrusted",
            "Ljavax/net/ssl/TrustManagerFactory;->init",
            "Ljavax/net/ssl/HostnameVerifier;->verify",
            "Ljavax/net/ssl/SSLSocketFactory;->createSocket",
            "Ljavax/net/ssl/HttpsURLConnection;->setHostnameVerifier",
            "Ljavax/net/ssl/HttpsURLConnection;->setSSLSocketFactory",

            // == WebView 风险调用 ==
            "Landroid/webkit/WebView;->addJavascriptInterface",
            "Landroid/webkit/WebView;->setWebViewClient",
            "Landroid/webkit/WebView;->setJavaScriptEnabled",
            "Landroid/webkit/WebSettings;->setJavaScriptEnabled",
            "Landroid/webkit/WebSettings;->setAllowFileAccess",
            "Landroid/webkit/WebSettings;->setAllowUniversalAccessFromFileURLs",
            "Landroid/webkit/WebSettings;->setDomStorageEnabled",
            "Landroid/webkit/WebView;->loadUrl",
            "Landroid/webkit/WebView;->evaluateJavascript",

            // == 内容提供者与数据访问 ==
            "Landroid/content/ContentResolver;->query",
            "Landroid/content/ContentResolver;->insert",
            "Landroid/content/ContentResolver;->delete",
            "Landroid/content/ContentResolver;->update",
            "Landroid/database/sqlite/SQLiteDatabase;->execSQL",
            "Landroid/database/sqlite/SQLiteDatabase;->rawQuery",

            // == 文件操作 ==
            "Ljava/io/FileOutputStream;->write",
            "Ljava/io/FileInputStream;->read",
            "Ljava/io/RandomAccessFile;-><init>",
            "Landroid/os/Environment;->getExternalStorageDirectory",
            "Landroid/os/StatFs;-><init>",

            // == 包管理与组件 ==
            "Landroid/content/pm/PackageManager;->getInstalledPackages",
            "Landroid/content/pm/PackageManager;->getInstalledApplications",
            "Landroid/content/pm/PackageManager;->installPackage",
            "Landroid/content/pm/PackageManager;->getPackageInfo",

            // == 无障碍服务 ==
            "Landroid/accessibilityservice/AccessibilityService;->onAccessibilityEvent",
            "Landroid/accessibilityservice/AccessibilityServiceInfo;->feedbackType",

            // == 通知监听 ==
            "Landroid/service/notification/NotificationListenerService;->onNotificationPosted",

            // == 设备管理 ==
            "Landroid/app/admin/DevicePolicyManager;->lockNow",
            "Landroid/app/admin/DevicePolicyManager;->wipeData",
            "Landroid/app/admin/DevicePolicyManager;->resetPassword",

            // == 反模拟器/反调试 ==
            "Ljava/lang/System;->getProperty",
            "Landroid/os/Debug;->isDebuggerConnected",
            "Landroid/os/Debug;->waitingForDebugger",
            "Ljava/net/NetworkInterface;->getHardwareAddress",
            "Ljava/net/NetworkInterface;->getNetworkInterfaces",

            // == 进程与IPC ==
            "Landroid/os/Binder;->clearCallingIdentity",
            "Landroid/os/Process;->killProcess",
            "Landroid/os/Binder;->transact",

            // == SharedPreferences ==
            "Landroid/content/SharedPreferences$Editor;->putString",
            "Landroid/content/SharedPreferences$Editor;->commit",

            // == Alarm与Job ==
            "Landroid/app/AlarmManager;->set",
            "Landroid/app/AlarmManager;->setRepeating",
            "Landroid/app/job/JobScheduler;->schedule",

            // == DownloadManager ==
            "Landroid/app/DownloadManager;->enqueue",

            // == Notification ==
            "Landroid/app/NotificationManager;->notify"
    );

    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[\\w.-]+(?:\\.[a-zA-Z]{2,})+[/\\w.-]*");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}");

    public Map<String, Object> analyzeApk(byte[] apkBytes, String filename,
                                          String depth, boolean aiEnhance,
                                          boolean sandboxMode, String sandboxConfig) {
        Map<String, Object> result = new HashMap<>();
        List<String> processSteps = new ArrayList<>();

        Path tempApk = null;
        try {
            log.info("开始APK分析: {}, 深度: {}", filename, depth);

            tempApk = Files.createTempFile("apk_analysis_", ".apk");
            Files.write(tempApk, apkBytes);

            processSteps.add("解析APK文件结构");
            Map<String, Object> apkInfo = extractApkInfo(tempApk, apkBytes, filename);
            result.put("apkInfo", apkInfo);

            processSteps.add("提取APK特征");
            Map<String, Object> features = extractFeatures(tempApk);
            result.put("features", features);

            processSteps.add("分析权限请求");
            List<Map<String, Object>> permissions = analyzePermissions(features);
            result.put("permissions", permissions);

            processSteps.add("提取网络域名");
            List<String> domains = extractNetworkDomains(tempApk);
            result.put("networkDomains", domains);

            processSteps.add("检测加壳/加固");
            Map<String, Object> packerInfo = detectPacker(tempApk);
            result.put("packerInfo", packerInfo);

            processSteps.add("执行恶意检测");
            Map<String, Object> detection = performMalwareDetection(features, permissions, domains);
            result.put("detection", detection);

            processSteps.add("扫描敏感凭据");
            List<Map<String, Object>> secrets = scanSecretsAndKeys(tempApk);
            result.put("secrets", secrets);

            processSteps.add("匹配 OWASP 移动安全合规规则");
            List<Map<String, Object>> owaspMatches = scanOwaspRules(tempApk, features, domains);
            result.put("owaspMatches", owaspMatches);

            processSteps.add("检测权限虚高声明");
            List<Map<String, Object>> permissionBloat = checkPermissionBloat(tempApk, features);
            result.put("permissionBloat", permissionBloat);

            processSteps.add("WebView安全检测");
            Map<String, Object> webViewSecurity = detectWebViewSecurity(tempApk);
            result.put("webViewSecurity", webViewSecurity);

            processSteps.add("SSL/TLS验证绕过检测");
            Map<String, Object> sslBypass = detectSSLTrustManagerOverride(tempApk);
            result.put("sslBypass", sslBypass);

            processSteps.add("组件导出风险分析");
            List<Map<String, Object>> exportedComponents = detectComponentExport(tempApk);
            result.put("exportedComponents", exportedComponents);

            processSteps.add("反射与动态加载检测");
            Map<String, Object> reflectionInfo = detectReflectionAndDynamicLoading(tempApk);
            result.put("reflectionInfo", reflectionInfo);

            processSteps.add("反模拟器/反沙箱检测");
            Map<String, Object> antiEmulator = detectAntiEmulator(tempApk);
            result.put("antiEmulator", antiEmulator);

            processSteps.add("第三方SDK指纹识别");
            List<Map<String, Object>> thirdPartySdks = identifyThirdPartySdks(tempApk);
            result.put("thirdPartySdks", thirdPartySdks);

            processSteps.add("DEX混淆评估");
            Map<String, Object> dexObfuscation = assessDexObfuscation(tempApk);
            result.put("dexObfuscation", dexObfuscation);

            if (aiEnhance) {
                processSteps.add("AI辅助分析");
                Map<String, Object> aiAnalysis = performAiAnalysis(detection, features, permissions);
                result.put("aiAnalysis", aiAnalysis);
            }

            processSteps.add("生成处置建议");
            List<Map<String, Object>> recommendations = generateRecommendations(detection);
            result.put("recommendations", recommendations);

            result.put("processSteps", processSteps);
            result.put("success", true);

        } catch (Exception e) {
            log.error("APK分析失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("processSteps", processSteps);
        } finally {
            if (tempApk != null) {
                try { Files.deleteIfExists(tempApk); } catch (IOException ignored) {}
            }
        }

        return result;
    }

    // ========== 步骤方法（供GuidedAnalysis调用） ==========

    public Map<String, Object> stepExtractApkInfo(Path apkPath, byte[] apkBytes, String filename) {
        try {
            return extractApkInfo(apkPath, apkBytes, filename);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    public Map<String, Object> stepExtractFeatures(Path apkPath) {
        try {
            return extractFeatures(apkPath);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    public List<Map<String, Object>> stepAnalyzePermissions(Map<String, Object> features) {
        return analyzePermissions(features);
    }

    public List<String> stepExtractNetworkDomains(Path apkPath) {
        return extractNetworkDomains(apkPath);
    }

    public Map<String, Object> stepDetectPacker(Path apkPath) {
        return detectPacker(apkPath);
    }

    public Map<String, Object> stepMalwareDetection(Map<String, Object> features,
                                                     List<Map<String, Object>> permissions,
                                                     List<String> domains) {
        return performMalwareDetection(features, permissions, domains);
    }

    public List<Map<String, Object>> stepGenerateRecommendations(Map<String, Object> detection) {
        return generateRecommendations(detection);
    }

    public List<Map<String, Object>> stepScanSecrets(Path apkPath) {
        return scanSecretsAndKeys(apkPath);
    }

    public List<Map<String, Object>> stepScanOwaspRules(Path apkPath, Map<String, Object> features, List<String> domains) {
        return scanOwaspRules(apkPath, features, domains);
    }

    public Map<String, Object> stepDetectWebViewSecurity(Path apkPath) {
        return detectWebViewSecurity(apkPath);
    }

    public Map<String, Object> stepDetectSSL(Path apkPath) {
        return detectSSLTrustManagerOverride(apkPath);
    }

    public List<Map<String, Object>> stepDetectComponentExport(Path apkPath) {
        return detectComponentExport(apkPath);
    }

    public Map<String, Object> stepDetectReflection(Path apkPath) {
        return detectReflectionAndDynamicLoading(apkPath);
    }

    public Map<String, Object> stepDetectAntiEmulator(Path apkPath) {
        return detectAntiEmulator(apkPath);
    }

    public List<Map<String, Object>> stepIdentifySdks(Path apkPath) {
        return identifyThirdPartySdks(apkPath);
    }

    public Map<String, Object> stepAssessDexObfuscation(Path apkPath) {
        return assessDexObfuscation(apkPath);
    }

    private Map<String, Object> extractApkInfo(Path apkPath, byte[] apkBytes, String filename) throws Exception {
        Map<String, Object> info = new HashMap<>();

        try (ApkFile apkFile = new ApkFile(apkPath.toFile())) {
            ApkMeta meta = apkFile.getApkMeta();

            info.put("packageName", meta.getPackageName());
            info.put("versionName", meta.getVersionName());
            info.put("versionCode", meta.getVersionCode());
            info.put("fileSize", apkBytes.length);
            info.put("md5", calculateHash(apkBytes, "MD5"));
            info.put("sha256", calculateHash(apkBytes, "SHA-256"));
            info.put("minSdkVersion", meta.getMinSdkVersion());
            info.put("targetSdkVersion", meta.getTargetSdkVersion());
            info.put("label", meta.getLabel());
            info.put("fileName", filename);

            // Manifest XML 安全属性提取
            String manifestXml = apkFile.getManifestXml();
            if (manifestXml != null) {
                info.put("debuggable", manifestXml.contains("android:debuggable=\"true\""));
                info.put("allowBackup", !manifestXml.contains("android:allowBackup=\"false\""));
                info.put("usesCleartextTraffic", manifestXml.contains("android:usesCleartextTraffic=\"true\""));
                info.put("extractNativeLibs", !manifestXml.contains("android:extractNativeLibs=\"false\""));

                // 检查是否有 BackupAgent
                info.put("hasBackupAgent", manifestXml.contains("android:backupAgent="));
                // 检查是否有自定义 Application 类
                Matcher appNameMatcher = Pattern.compile("<application[^>]*android:name=\"([^\"]+)\"").matcher(manifestXml);
                info.put("customApplication", appNameMatcher.find() ? appNameMatcher.group(1) : null);
                // 检查是否有 exported provider
                info.put("hasExportedProvider", manifestXml.contains("<provider") && manifestXml.contains("android:exported=\"true\""));
            }

            List<CertificateMeta> certs = apkFile.getCertificateMetaList();
            if (certs != null && !certs.isEmpty()) {
                CertificateMeta cert = certs.get(0);
                Map<String, Object> certInfo = new HashMap<>();
                certInfo.put("signAlgorithm", cert.getSignAlgorithm());
                certInfo.put("certMd5", cert.getCertMd5());
                info.put("certificate", certInfo);
            }

            List<UseFeature> usesFeatures = meta.getUsesFeatures();
            if (usesFeatures != null) {
                info.put("usesFeatures", usesFeatures.stream()
                        .map(UseFeature::getName).toList());
            }
        }

        return info;
    }

    private Map<String, Object> extractFeatures(Path apkPath) throws Exception {
        Map<String, Object> features = new HashMap<>();

        try (ApkFile apkFile = new ApkFile(apkPath.toFile())) {
            ApkMeta meta = apkFile.getApkMeta();

            // 权限
            List<String> permissions = meta.getUsesPermissions();
            features.put("permissions", permissions != null ? permissions : new ArrayList<>());

            // 组件（从AndroidManifest.xml解析）
            Map<String, Object> components = new HashMap<>();
            String manifestXml = apkFile.getManifestXml();
            if (manifestXml != null) {
                components.put("activities", extractComponentNames(manifestXml, "activity"));
                components.put("services", extractComponentNames(manifestXml, "service"));
                components.put("receivers", extractComponentNames(manifestXml, "receiver"));
                components.put("providers", extractComponentNames(manifestXml, "provider"));
            } else {
                components.put("activities", List.of());
                components.put("services", List.of());
                components.put("receivers", List.of());
                components.put("providers", List.of());
            }
            features.put("components", components);
        }

        // 原生库列表
        List<String> nativeLibs = new ArrayList<>();
        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            zip.stream()
                    .filter(e -> e.getName().startsWith("lib/") && e.getName().endsWith(".so"))
                    .forEach(e -> {
                        String name = e.getName();
                        nativeLibs.add(name.substring(name.lastIndexOf('/') + 1));
                    });
        }
        features.put("nativeLibraries", nativeLibs.stream().distinct().toList());

        // DEX中的敏感API扫描
        List<String> sensitiveApis = scanSensitiveApis(apkPath);
        features.put("sensitiveApis", sensitiveApis);

        return features;
    }

    private List<String> scanSensitiveApis(Path apkPath) {
        List<String> found = new ArrayList<>();
        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            zip.stream()
                    .filter(e -> e.getName().endsWith(".dex"))
                    .forEach(entry -> {
                        try {
                            byte[] dexBytes = zip.getInputStream(entry).readAllBytes();
                            String dexContent = new String(dexBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                            for (String api : SENSITIVE_APIS) {
                                String className = api.split(";->")[0].substring(1).replace('/', '.');
                                String methodName = api.split(";->")[1];
                                if (dexContent.contains(className) || dexContent.contains(methodName)) {
                                    found.add(className + "." + methodName);
                                }
                            }
                        } catch (IOException e) {
                            log.debug("DEX扫描失败: {}", entry.getName());
                        }
                    });
        } catch (IOException e) {
            log.warn("敏感API扫描失败", e);
        }
        return found.stream().distinct().toList();
    }

    private List<String> extractNetworkDomains(Path apkPath) {
        Set<String> domains = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            zip.stream()
                    .filter(e -> e.getName().endsWith(".dex"))
                    .forEach(entry -> {
                        try {
                            byte[] dexBytes = zip.getInputStream(entry).readAllBytes();
                            String content = new String(dexBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                            Matcher urlMatcher = URL_PATTERN.matcher(content);
                            while (urlMatcher.find()) {
                                String url = urlMatcher.group();
                                Matcher domainMatcher = DOMAIN_PATTERN.matcher(url);
                                if (domainMatcher.find()) {
                                    domains.add(domainMatcher.group());
                                }
                            }
                        } catch (IOException e) {
                            log.debug("域名提取失败: {}", entry.getName());
                        }
                    });
        } catch (IOException e) {
            log.warn("网络域名提取失败", e);
        }
        return new ArrayList<>(domains);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> analyzePermissions(Map<String, Object> features) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<String> perms = (List<String>) features.get("permissions");
        if (perms == null) return result;

        for (String perm : perms) {
            Map<String, Object> permInfo = new HashMap<>();
            permInfo.put("name", perm);
            permInfo.put("risk", getPermissionRisk(perm));
            permInfo.put("description", getPermissionDescription(perm));
            permInfo.put("purpose", getPermissionPurpose(perm));
            result.add(permInfo);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> performMalwareDetection(Map<String, Object> features,
                                                        List<Map<String, Object>> permissions,
                                                        List<String> domains) {
        Map<String, Object> detection = new HashMap<>();
        List<Map<String, Object>> ruleMatches = new ArrayList<>();
        int riskScore = 0;

        long dangerousPermCount = permissions.stream()
                .filter(p -> "HIGH".equals(p.get("risk")) || "CRITICAL".equals(p.get("risk")))
                .count();

        if (dangerousPermCount >= 3) {
            riskScore += 25;
            ruleMatches.add(createRuleMatch("dangerous_permissions",
                    "检测到" + dangerousPermCount + "个高风险权限组合", "HIGH"));
        } else if (dangerousPermCount >= 2) {
            riskScore += 15;
            ruleMatches.add(createRuleMatch("dangerous_permissions",
                    "检测到多个高风险权限", "MEDIUM"));
        }

        List<String> sensitiveApis = (List<String>) features.getOrDefault("sensitiveApis", List.of());
        if (sensitiveApis.stream().anyMatch(a -> a.contains("Runtime.exec") || a.contains("ProcessBuilder"))) {
            riskScore += 20;
            ruleMatches.add(createRuleMatch("command_execution",
                    "检测到命令执行API调用", "HIGH"));
        }
        if (sensitiveApis.stream().anyMatch(a -> a.contains("DexClassLoader"))) {
            riskScore += 15;
            ruleMatches.add(createRuleMatch("dynamic_loading",
                    "检测到动态代码加载", "HIGH"));
        }
        if (sensitiveApis.stream().anyMatch(a -> a.contains("SmsManager"))) {
            riskScore += 20;
            ruleMatches.add(createRuleMatch("sms_access",
                    "检测到短信发送API", "CRITICAL"));
        }

        boolean hasBootReceiver = permissions.stream()
                .anyMatch(p -> p.get("name").toString().contains("BOOT"));
        if (hasBootReceiver) {
            riskScore += 10;
            ruleMatches.add(createRuleMatch("boot_autostart", "注册开机自启", "MEDIUM"));
        }

        if (domains.size() > 5) {
            riskScore += 10;
            ruleMatches.add(createRuleMatch("multiple_domains",
                    "连接" + domains.size() + "个外部域名", "MEDIUM"));
        }

        String verdict;
        if (riskScore >= 50) verdict = "CRITICAL_MALWARE";
        else if (riskScore >= 35) verdict = "HIGH_RISK";
        else if (riskScore >= 20) verdict = "MEDIUM_RISK";
        else if (riskScore >= 10) verdict = "LOW_RISK";
        else verdict = "CLEAN";

        detection.put("verdict", verdict);
        detection.put("riskScore", riskScore);
        detection.put("confidence", Math.min(1.0, riskScore / 60.0 + 0.2));
        detection.put("malwareType", determineMalwareType(permissions, features));
        detection.put("ruleMatches", ruleMatches);
        detection.put("summary", generateDetectionSummary(verdict, ruleMatches));
        detection.put("technicalAnalysis", generateTechnicalAnalysis(features, permissions));
        detection.put("behaviors", extractBehaviors(permissions, sensitiveApis));
        detection.put("iocs", extractIoCs(permissions, domains));

        return detection;
    }

    private String extractApplicationName(String manifestXml) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<application[^>]*android:name=\"([^\"]+)\"");
        java.util.regex.Matcher m = p.matcher(manifestXml);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String generateFridaUnpackerScript(String packerName) {
        return """
/* 
  [极致静态安全审计] - 针对 %s 的 Frida 动态内存脱壳脚本
  脱壳原理: 拦截 ART 运行时的 DexFile::OpenCommon 或 OpenMemory，当壳完成解密并载入 DEX 时，将其从内存转储(dump)出。
  使用步骤:
    1. 保存本脚本为 dump.js
    2. 执行命令启动目标应用: frida -U -f [您的应用包名] -l dump.js --no-pause
*/
Java.perform(function () {
    console.log("[★] 正在挂钩 libart.so 准备脱壳...");
    
    // 适配大部分 Android 7.0 - 13.0 版本的 OpenCommon 导出符号
    var symbols = [
        "_ZN3art7DexFile10OpenCommonEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPKNS_10OatDexFileEbbPS9_PNS_12DexFileErrorE",
        "_ZN3art7DexFile8OpenFileEiRKNSt3__112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEEbbPS5_PNS_12DexFileErrorE",
        "_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPKNS_10OatDexFileEbbPS9_"
    ];
    
    var openCommon = null;
    for (var i = 0; i < symbols.length; i++) {
        openCommon = Module.findExportByName("libart.so", symbols[i]);
        if (openCommon) {
            console.log("[✔] 挂钩函数寻址成功: " + symbols[i]);
            break;
        }
    }
    
    if (openCommon) {
        Interceptor.attach(openCommon, {
            onEnter: function (args) {
                var base = args[1];
                var size = args[2].toInt32();
                console.log("[★] 发现内存 DEX 载入: base=" + base + ", size=" + size);
                
                // 读取前4字节，验证 dex 魔数 "dex\\n"
                try {
                    var magic = Memory.readUtf8String(base, 4);
                    if (magic === "dex\\n") {
                        var path = "/data/data/com.example.target/dump_" + base + "_" + size + ".dex";
                        var file = new File(path, "wb");
                        file.write(Memory.readByteArray(base, size));
                        file.close();
                        console.log("[✔] 内存脱壳转储成功，DEX 保存至: " + path);
                    }
                } catch(e) {
                    console.log("[!] 读取内存地址异常: " + e.message);
                }
            }
        });
    } else {
        console.log("[!] 警告: 未在当前 ROM 的 libart.so 中匹配到常见的 OpenCommon/OpenMemory 符号，建议动态扫描导出符号表以作适配");
    }
});
""".replace("%s", packerName);
    }

    private Map<String, Object> detectPacker(Path apkPath) {
        Map<String, Object> packerInfo = new HashMap<>();
        packerInfo.put("isPacked", false);

        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            Set<String> entries = new HashSet<>();
            zip.stream().forEach(e -> entries.add(e.getName()));

            String packerName = null;
            List<String> evidence = new ArrayList<>();

            // 1. 基于特征文件扫描 — 15+ 加固方案
            if (entries.stream().anyMatch(e -> e.contains("libjiagu") || e.contains("jiagu"))) {
                packerName = "360加固保";
                evidence.add("检测到libjiagu.so特征库或jiagu目录");
            } else if (entries.stream().anyMatch(e -> e.contains("libSecShell") || e.contains("libDexHelper") || e.contains("libdexhelper") || e.contains("libDexProtect"))) {
                packerName = "梆梆安全加固";
                evidence.add("检测到libSecShell.so/libDexHelper.so/libDexProtect.so特征库");
            } else if (entries.stream().anyMatch(e -> e.contains("ijiami") || e.contains("libim4") || e.contains("libexecmain"))) {
                packerName = "爱加密加固";
                evidence.add("检测到ijiami/libim4.so/libexecmain.so特征");
            } else if (entries.stream().anyMatch(e -> e.contains("libshell") || e.contains("libtxAppProtect") || e.contains("libtupk"))) {
                packerName = "腾讯乐固/御界";
                evidence.add("检测到libshell.so/libtxAppProtect.so/libtupk.so特征库");
            } else if (entries.stream().anyMatch(e -> e.contains("libnesec") || e.contains("libNetHTProtect"))) {
                packerName = "网易易盾";
                evidence.add("检测到libnesec.so/libNetHTProtect.so易盾特征库");
            } else if (entries.stream().anyMatch(e -> e.contains("libegis") || e.contains("libtup"))) {
                packerName = "通付盾加固";
                evidence.add("检测到libegis.so/libtup.so通付盾特征");
            } else if (entries.stream().anyMatch(e -> e.contains("libbaiduprotect") || e.contains("libBaiduProtect"))) {
                packerName = "百度加固";
                evidence.add("检测到libbaiduprotect.so/libBaiduProtect.so特征库");
            } else if (entries.stream().anyMatch(e -> e.contains("libnqshield"))) {
                packerName = "网秦加固";
                evidence.add("检测到libnqshield.so特征库");
            } else if (entries.stream().anyMatch(e -> e.contains("libnagra"))) {
                packerName = "娜迦加固";
                evidence.add("检测到libnagra.so娜迦特征库");
            } else if (entries.stream().anyMatch(e -> e.contains("libalijt"))) {
                packerName = "阿里聚安全";
                evidence.add("检测到libalijt.so阿里聚安全特征库");
            } else if (entries.stream().anyMatch(e -> e.contains("libAPKProtect"))) {
                packerName = "APKProtect加固";
                evidence.add("检测到libAPKProtect.so特征");
            } else if (entries.stream().anyMatch(e -> e.contains("libprotectClass") || e.contains("libprotectClass_x86"))) {
                packerName = "几维安全加固";
                evidence.add("检测到libprotectClass.so几维安全特征");
            } else if (entries.stream().anyMatch(e -> e.contains("libkwscmm") || e.contains("libkwscr"))) {
                packerName = "顶象安全加固";
                evidence.add("检测到顶象加固libkwscmm/libkwscr特征");
            } else if (entries.stream().anyMatch(e -> e.contains("libhuawei") || e.contains("huaweiProtected"))) {
                packerName = "华为应用加固";
                evidence.add("检测到华为加固特征");
            } else if (entries.stream().anyMatch(e -> e.contains("libmobisec") || e.contains("libDexHelper-x86"))) {
                packerName = "梆梆企业版加固";
                evidence.add("检测到梆梆企业版libmobisec/libDexHelper-x86特征");
            }

            // 2. 基于 AndroidManifest.xml Stub Application 静态匹配
            if (packerName == null) {
                try (ApkFile apkFile = new ApkFile(apkPath.toFile())) {
                    String manifestXml = apkFile.getManifestXml();
                    if (manifestXml != null) {
                        String appName = extractApplicationName(manifestXml);
                        if (appName != null) {
                            if (appName.contains("qihoo") || appName.equals("com.stub.StubApp")) {
                                packerName = "360加固";
                                evidence.add("AndroidManifest中检测到360壳入口类: " + appName);
                            } else if (appName.contains("secshell")) {
                                packerName = "梆梆安全加固";
                                evidence.add("AndroidManifest中检测到梆梆壳入口类: " + appName);
                            } else if (appName.contains("txAppEntry") || appName.contains("StubShell")) {
                                packerName = "腾讯乐固";
                                evidence.add("AndroidManifest中检测到腾讯乐固壳入口类: " + appName);
                            } else if (appName.contains("ijiami") || appName.contains("amap")) {
                                packerName = "爱加密加固";
                                evidence.add("AndroidManifest中检测到爱加密壳入口类: " + appName);
                            } else if (appName.contains("baidu.protect")) {
                                packerName = "百度加固";
                                evidence.add("AndroidManifest中检测到百度加固壳入口类: " + appName);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 3. 基于 DEX 特征异常检查
            if (packerName == null) {
                long dexCount = entries.stream().filter(e -> e.matches("classes\\d*\\.dex")).count();
                Optional<? extends ZipEntry> mainDex = zip.stream()
                        .filter(e -> "classes.dex".equals(e.getName())).findFirst();
                if (dexCount == 1 && mainDex.isPresent() && mainDex.get().getSize() < 50000) {
                    packerName = "未知壳";
                    evidence.add("唯一 classes.dex 长度异常小 (" + mainDex.get().getSize() + " bytes)，判定为壳代理");
                }
            }

            if (packerName != null) {
                packerInfo.put("isPacked", true);
                packerInfo.put("packer", packerName);
                packerInfo.put("evidence", evidence);
                packerInfo.put("impact", "由于该APK已被「" + packerName + "」加固保护，静态DEX结构可能被加密隐藏，普通静态分析只能扫描到外壳占位指令。建议进行动态内存Dump脱壳，以提取真实 classes.dex 后再行深入审计。");

                // 生成极为专业的 Frida 动态内存脱壳脱壳脚本
                String unpackerScript = generateFridaUnpackerScript(packerName);
                packerInfo.put("unpackerScript", unpackerScript);

                String aiGuidance = generatePackerAnalysisGuidance(packerName, entries);
                packerInfo.put("analysisGuidance", aiGuidance + "\n\n### 🛡️ 极客动态脱壳 Frida 模块指引\n\n您可以使用我们针对该加固平台定制的 Frida 动态内存脱壳脚本。在有 Root 权限的真机或模拟器下运行该脚本，当应用加载时会自动将解密后的内存 DEX 镜像 Dump 到本地磁盘：\n\n```javascript\n" + unpackerScript + "\n```");
            }

        } catch (IOException e) {
            log.warn("加壳检测失败", e);
            packerInfo.put("error", e.getMessage());
        }

        return packerInfo;
    }

    private String generatePackerAnalysisGuidance(String packerName, Set<String> entries) {
        StringBuilder context = new StringBuilder();
        context.append("检测到APK使用了 ").append(packerName).append(" 加固保护。\n");
        context.append("APK内相关文件:\n");
        entries.stream()
                .filter(e -> e.endsWith(".so") || e.contains("jiagu") || e.contains("shell") ||
                        e.contains("protect") || e.contains("sec"))
                .limit(10)
                .forEach(e -> context.append("- ").append(e).append("\n"));
        context.append("\n请分析该加固方案的技术原理，并给出逆向分析的学习思路和研究方向。");

        String llmResult = llmService.analyzeApk(context.toString());
        if (llmResult != null) {
            return llmResult;
        }

        // 规则兜底
        return "该应用使用了" + packerName + "保护。\n" +
                "分析思路：\n" +
                "1. 了解该加固方案的DEX加载机制（通常通过自定义ClassLoader）\n" +
                "2. 研究其SO层保护逻辑（init_array/JNI_OnLoad中的解密流程）\n" +
                "3. 学习内存dump技术原理（在DEX被加载到内存后的时机点）\n" +
                "4. 关注Application类的attachBaseContext and onCreate时机\n" +
                "5. 研究ART虚拟机的类加载机制以理解脱壳点选择";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> performAiAnalysis(Map<String, Object> detection,
                                                  Map<String, Object> features,
                                                  List<Map<String, Object>> permissions) {
        Map<String, Object> aiAnalysis = new HashMap<>();

        StringBuilder context = new StringBuilder();
        context.append("APK检测结果: ").append(detection.get("verdict")).append("\n");
        context.append("风险评分: ").append(detection.get("riskScore")).append("\n");
        context.append("权限列表:\n");
        for (Map<String, Object> p : permissions) {
            context.append("- ").append(p.get("name")).append(" [").append(p.get("risk")).append("]\n");
        }
        List<String> apis = (List<String>) features.getOrDefault("sensitiveApis", List.of());
        if (!apis.isEmpty()) {
            context.append("敏感API:\n");
            apis.forEach(a -> context.append("- ").append(a).append("\n"));
        }
        List<String> libs = (List<String>) features.getOrDefault("nativeLibraries", List.of());
        if (!libs.isEmpty()) {
            context.append("原生库: ").append(String.join(", ", libs)).append("\n");
        }

        String llmResult = llmService.analyzeApk(context.toString());
        if (llmResult != null) {
            aiAnalysis.put("analysis", llmResult);
            aiAnalysis.put("model", "llm");
        } else {
            aiAnalysis.put("analysis", generateRuleBasedAnalysis(detection, features));
            aiAnalysis.put("model", "rule-based");
        }
        aiAnalysis.put("timestamp", System.currentTimeMillis());
        aiAnalysis.put("confidence", llmResult != null ? 0.9 : 0.7);
        aiAnalysis.put("moduleType", "APK");

        return aiAnalysis;
    }

    private String generateRuleBasedAnalysis(Map<String, Object> detection, Map<String, Object> features) {
        String verdict = (String) detection.get("verdict");
        int score = (int) detection.get("riskScore");
        StringBuilder sb = new StringBuilder();
        sb.append("基于规则引擎分析结果:\n");
        sb.append("- 威胁等级: ").append(verdict).append(" (评分: ").append(score).append(")\n");
        if (score >= 35) {
            sb.append("- 该应用存在较高安全风险，建议谨慎使用\n");
        } else if (score >= 20) {
            sb.append("- 该应用存在一定风险，建议关注权限使用\n");
        } else {
            sb.append("- 该应用风险较低\n");
        }
        return sb.toString();
    }

    private List<Map<String, Object>> generateRecommendations(Map<String, Object> detection) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        String verdict = detection.get("verdict").toString();

        if ("CRITICAL_MALWARE".equals(verdict) || "HIGH_RISK".equals(verdict)) {
            recommendations.add(createRecommendation("立即卸载", "该应用存在严重安全风险，建议立即卸载", "HIGH"));
            recommendations.add(createRecommendation("全盘扫描", "使用杀毒软件进行全面扫描", "HIGH"));
            recommendations.add(createRecommendation("修改密码", "如果曾输入敏感信息，建议修改相关密码", "MEDIUM"));
        } else if ("MEDIUM_RISK".equals(verdict)) {
            recommendations.add(createRecommendation("谨慎使用", "该应用存在一定风险，建议关注其行为", "MEDIUM"));
            recommendations.add(createRecommendation("权限管理", "建议关闭非必要权限", "MEDIUM"));
        }

        recommendations.add(createRecommendation("来源检查", "检查应用来源，避免安装未知来源应用", "LOW"));
        return recommendations;
    }

    private String getPermissionRisk(String permission) {
        if (permission.contains("SMS") || permission.contains("CONTACTS") ||
                permission.contains("RECORD_AUDIO") || permission.contains("CAMERA") ||
                permission.contains("CALL_LOG")) {
            return "CRITICAL";
        }
        if (permission.contains("LOCATION") || permission.contains("PHONE_STATE") ||
                permission.contains("READ_EXTERNAL") || permission.contains("WRITE_EXTERNAL")) {
            return "HIGH";
        }
        if (permission.contains("INTERNET") || permission.contains("BOOT") ||
                permission.contains("WAKE_LOCK")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String getPermissionDescription(String permission) {
        String shortName = permission.replace("android.permission.", "");
        return switch (shortName) {
            case "INTERNET" -> "允许应用访问网络";
            case "READ_PHONE_STATE" -> "允许读取设备标识和通话状态";
            case "ACCESS_FINE_LOCATION" -> "允许获取精确GPS位置";
            case "ACCESS_COARSE_LOCATION" -> "允许获取粗略位置";
            case "READ_CONTACTS" -> "允许读取通讯录";
            case "READ_SMS" -> "允许读取短信内容";
            case "SEND_SMS" -> "允许发送短信";
            case "CAMERA" -> "允许使用摄像头";
            case "RECORD_AUDIO" -> "允许录音";
            case "RECEIVE_BOOT_COMPLETED" -> "允许开机自启动";
            case "WRITE_EXTERNAL_STORAGE" -> "允许写入外部存储";
            case "READ_EXTERNAL_STORAGE" -> "允许读取外部存储";
            default -> "系统权限: " + shortName;
        };
    }

    private String getPermissionPurpose(String permission) {
        if (permission.contains("INTERNET")) return "网络通信";
        if (permission.contains("SMS")) return "短信收发";
        if (permission.contains("LOCATION")) return "位置定位";
        if (permission.contains("PHONE_STATE")) return "设备识别";
        if (permission.contains("BOOT")) return "开机自启";
        if (permission.contains("CAMERA")) return "拍照录像";
        if (permission.contains("RECORD_AUDIO")) return "录音";
        if (permission.contains("CONTACTS")) return "通讯录访问";
        if (permission.contains("STORAGE") || permission.contains("EXTERNAL")) return "文件存储";
        return "其他功能";
    }

    private String determineMalwareType(List<Map<String, Object>> permissions, Map<String, Object> features) {
        boolean hasSms = permissions.stream().anyMatch(p -> p.get("name").toString().contains("SMS"));
        boolean hasContacts = permissions.stream().anyMatch(p -> p.get("name").toString().contains("CONTACTS"));
        boolean hasBoot = permissions.stream().anyMatch(p -> p.get("name").toString().contains("BOOT"));
        boolean hasCamera = permissions.stream().anyMatch(p -> p.get("name").toString().contains("CAMERA"));
        boolean hasRecordAudio = permissions.stream().anyMatch(p -> p.get("name").toString().contains("RECORD_AUDIO"));
        boolean hasAccessibility = permissions.stream().anyMatch(p -> p.get("name").toString().contains("BIND_ACCESSIBILITY"));
        boolean hasNotification = permissions.stream().anyMatch(p -> p.get("name").toString().contains("BIND_NOTIFICATION"));
        boolean hasDeviceAdmin = permissions.stream().anyMatch(p -> p.get("name").toString().contains("BIND_DEVICE_ADMIN"));
        @SuppressWarnings("unchecked")
        List<String> apis = (List<String>) features.getOrDefault("sensitiveApis", List.of());
        boolean hasExec = apis.stream().anyMatch(a -> a.contains("Runtime.exec") || a.contains("ProcessBuilder"));
        boolean hasDexLoader = apis.stream().anyMatch(a -> a.contains("DexClassLoader") || a.contains("PathClassLoader"));
        boolean hasReflect = apis.stream().anyMatch(a -> a.contains("reflect.Method") || a.contains("Class.forName"));
        boolean hasWebView = apis.stream().anyMatch(a -> a.contains("addJavascriptInterface") || a.contains("loadUrl"));
        boolean hasCrypto = apis.stream().anyMatch(a -> a.contains("Cipher") || a.contains("MessageDigest"));

        // 组合判定恶意软件家族
        if (hasSms && hasContacts && hasExec) return "RAT 远控木马 (短信+通讯录+命令执行)";
        if (hasAccessibility && hasNotification) return "银行钓鱼木马 (无障碍+通知监听)";
        if (hasSms && hasBoot && hasDeviceAdmin) return "SMS 扣费木马 (短信+自启+设备管理)";
        if (hasRecordAudio && hasCamera && hasBoot) return "间谍软件 (录音+摄像头+自启)";
        if (hasDexLoader && hasReflect && hasCrypto) return "动态解密加载器 (DEX动态加载+反射+加密)";
        if (hasAccessibility) return "无障碍滥用木马 (可能用于UI劫持)";
        if (hasExec && hasDexLoader) return "多阶段远控下载器 (命令执行+动态加载)";
        if (hasSms && hasContacts) return "隐私窃取木马 (短信+通讯录)";
        if (hasExec && hasBoot) return "持久化后门 (命令执行+开机自启)";
        if (hasDexLoader && hasCrypto) return "加密Payload投递器 (DEX+加密)";
        if (hasWebView && hasReflect) return "WebView注入劫持 (WebView+反射)";
        if (hasBoot && hasSms) return "SMS 僵尸网络节点 (短信+自启)";
        if (hasRecordAudio) return "通话/环境录音窃听器";
        if (hasCamera) return "隐蔽拍照/摄像木马";
        if (permissions.size() > 15) return "过度索权数据采集器 (>15权限)";
        return "可疑应用";
    }

    private String generateDetectionSummary(String verdict, List<Map<String, Object>> ruleMatches) {
        return String.format("检测判定: %s, 命中规则: %d条", verdict, ruleMatches.size());
    }

    @SuppressWarnings("unchecked")
    private String generateTechnicalAnalysis(Map<String, Object> features, List<Map<String, Object>> permissions) {
        StringBuilder sb = new StringBuilder("技术分析报告:\n\n");
        sb.append("1. 权限请求分析:\n");
        for (Map<String, Object> perm : permissions) {
            sb.append("   - ").append(perm.get("name")).append(": ").append(perm.get("risk")).append("\n");
        }
        List<String> apis = (List<String>) features.getOrDefault("sensitiveApis", List.of());
        sb.append("\n2. 敏感API调用:\n");
        for (String api : apis) {
            sb.append("   - ").append(api).append("\n");
        }
        return sb.toString();
    }

    private List<String> extractBehaviors(List<Map<String, Object>> permissions, List<String> sensitiveApis) {
        List<String> behaviors = new ArrayList<>();
        for (Map<String, Object> perm : permissions) {
            String name = perm.get("name").toString();
            if ("CRITICAL".equals(perm.get("risk"))) {
                if (name.contains("SMS")) behaviors.add("可能发送短信（扣费风险）");
                if (name.contains("CONTACTS")) behaviors.add("可能窃取通讯录");
                if (name.contains("RECORD_AUDIO")) behaviors.add("可能进行通话录音");
            }
        }
        if (sensitiveApis.stream().anyMatch(a -> a.contains("exec"))) {
            behaviors.add("可能执行系统命令");
        }
        if (sensitiveApis.stream().anyMatch(a -> a.contains("DexClassLoader"))) {
            behaviors.add("可能动态加载恶意代码");
        }
        return behaviors;
    }

    private Map<String, Object> extractIoCs(List<Map<String, Object>> permissions, List<String> domains) {
        Map<String, Object> iocs = new HashMap<>();
        iocs.put("domains", domains);
        iocs.put("permissions", permissions.stream()
                .filter(p -> "HIGH".equals(p.get("risk")) || "CRITICAL".equals(p.get("risk")))
                .map(p -> p.get("name").toString())
                .toList());
        return iocs;
    }

    private Map<String, Object> createRuleMatch(String name, String description, String severity) {
        return Map.of("name", name, "description", description, "severity", severity);
    }

    private Map<String, Object> createRecommendation(String title, String description, String priority) {
        return Map.of("title", title, "description", description, "priority", priority);
    }

    private String calculateHash(byte[] data, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "error";
        }
    }

    private List<String> extractComponentNames(String manifestXml, String componentType) {
        List<String> names = new ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "<" + componentType + "[^>]*android:name=\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(manifestXml);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static final List<SecretPattern> SECRET_PATTERNS = List.of(
            new SecretPattern("AWS Access Key ID", "AKIA[0-9A-Z]{16}", 3.5),
            new SecretPattern("AWS Secret Key", "(?i)aws[_\\-]?secret[_\\-]?(?:access)?[_\\-]?key[\"']?\\s*[:=]\\s*['\"]?([A-Za-z0-9+/=]{40})", 6.0),
            new SecretPattern("Google API Key", "AIza[0-9A-Za-z\\-_]{35}", 3.8),
            new SecretPattern("Google OAuth Client ID", "[0-9]+-[a-z0-9_]+\\.apps\\.googleusercontent\\.com", 4.5),
            new SecretPattern("GitHub Token", "(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{36,255}", 5.5),
            new SecretPattern("JWT Token", "eyJ[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+", 5.0),
            new SecretPattern("Private Key Header", "-----BEGIN [A-Z ]*PRIVATE KEY-----", 4.0),
            new SecretPattern("RSA Private Key", "-----BEGIN RSA PRIVATE KEY-----", 5.0),
            new SecretPattern("SSH Private Key", "-----BEGIN OPENSSH PRIVATE KEY-----", 5.0),
            new SecretPattern("OAuth 2.0 Bearer Token", "(?i)(?:oauth|bearer|access)[_\\-]?token[\"']?\\s*[:=]\\s*['\"]?([A-Za-z0-9\\-._~+/]{20,256})", 5.0),
            new SecretPattern("阿里云 AccessKey", "LTAI[A-Za-z0-9]{12,20}", 4.0),
            new SecretPattern("腾讯云 SecretId", "AKID[A-Za-z0-9]{13,32}", 4.0),
            new SecretPattern("微信 AppSecret", "(?i)(?:wechat|wx|weixin)[_\\-]?(?:app)?[_\\-]?secret[\"']?\\s*[:=]\\s*['\"]?([a-fA-F0-9]{32})", 6.0),
            new SecretPattern("数据库连接串 (MySQL)", "(?i)jdbc:mysql://[^/]+/[^?]+\\?(?:.*&)?(?:user|password)=([^&\\s]+)", 5.5),
            new SecretPattern("Firebase URL", "[a-z0-9-]+\\.firebaseio\\.com", 3.0),
            new SecretPattern("Slack Webhook", "hooks\\.slack\\.com/services/T[a-zA-Z0-9_]+/B[a-zA-Z0-9_]+/[a-zA-Z0-9_]+", 5.5),
            new SecretPattern("Generic API Key", "(?i)(?:api[_\\-]?key|apikey|secret[_\\-]?key|app[_\\-]?key)[\"']?\\s*[:=]\\s*['\"]?([A-Za-z0-9+/=_-]{16,64})", 5.0),
            new SecretPattern("Twilio Account SID", "AC[a-f0-9]{32}", 4.5),
            new SecretPattern("Stripe Secret Key", "(?:sk_live|sk_test)_[A-Za-z0-9]{24,}", 5.5)
    );

    private List<Map<String, Object>> scanSecretsAndKeys(Path apkPath) {
        List<Map<String, Object>> secrets = new ArrayList<>();
        Pattern genericHighEntropyPattern = Pattern.compile("[A-Za-z0-9+/=_-]{24,72}");

        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            zip.stream()
               .filter(e -> e.getName().endsWith(".dex"))
               .forEach(entry -> {
                   try {
                       byte[] dexBytes = zip.getInputStream(entry).readAllBytes();
                       String content = new String(dexBytes, java.nio.charset.StandardCharsets.ISO_8859_1);

                       // 匹配已知密钥模式
                       for (SecretPattern sp : SECRET_PATTERNS) {
                           Matcher m = sp.compiledPattern().matcher(content);
                           while (m.find()) {
                               String matched = m.groupCount() > 0 ? m.group(1) : m.group();
                               if (matched.length() > 256) matched = matched.substring(0, 256);
                               secrets.add(createSecretMap(sp.name, maskString(matched), sp.minEntropy));
                           }
                       }

                       // 通用高熵值检测
                       Matcher genMatcher = genericHighEntropyPattern.matcher(content);
                       int count = 0;
                       while (genMatcher.find() && count < 30) {
                           String candidate = genMatcher.group();
                           if (candidate.startsWith("Landroid") || candidate.startsWith("Ljava") ||
                               candidate.startsWith("Lkotlin") || candidate.contains("Exception") ||
                               candidate.contains("StringBuilder") || candidate.matches("^[A-Za-z]+/[A-Za-z]+$")) {
                               continue;
                           }
                           double entropy = calculateShannonEntropy(candidate);
                           if (entropy > 4.5) {
                               secrets.add(createSecretMap("High-Entropy Secret Token", maskString(candidate), entropy));
                               count++;
                           }
                       }
                   } catch (IOException e) {
                       log.debug("Dex secrets scan failed", e);
                   }
               });
        } catch (IOException e) {
            log.warn("Secrets scanning failed", e);
        }

        return secrets.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(m -> m.get("value").toString()))),
                        ArrayList::new
                ));
    }

    private static record SecretPattern(String name, String regex, double minEntropy) {
        Pattern compiledPattern() { return Pattern.compile(regex); }
    }

    private double calculateShannonEntropy(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        Map<Character, Integer> freq = new HashMap<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            freq.put(c, freq.getOrDefault(c, 0) + 1);
        }
        double entropy = 0.0;
        double len = s.length();
        for (int count : freq.values()) {
            double p = count / len;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    private String maskString(String input) {
        if (input == null || input.length() < 8) return "****";
        return input.substring(0, 4) + "****" + input.substring(input.length() - 4);
    }

    private Map<String, Object> createSecretMap(String type, String value, double entropy) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("value", value);
        map.put("entropy", Math.round(entropy * 100.0) / 100.0);
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> scanOwaspRules(Path apkPath, Map<String, Object> features, List<String> domains) {
        List<Map<String, Object>> matches = new ArrayList<>();

        boolean cleartextAllowed = false;
        boolean debuggable = false;
        boolean allowBackup = true;
        String manifestXml = "";
        try (ApkFile apkFile = new ApkFile(apkPath.toFile())) {
            manifestXml = apkFile.getManifestXml();
            if (manifestXml != null) {
                if (manifestXml.contains("android:usesCleartextTraffic=\"true\"")) cleartextAllowed = true;
                if (manifestXml.contains("android:debuggable=\"true\"")) debuggable = true;
                if (manifestXml.contains("android:allowBackup=\"false\"")) allowBackup = false;
            }
        } catch (Exception ignored) {}

        boolean cleartextDomain = domains.stream().anyMatch(d -> d.startsWith("http://"));
        if (cleartextAllowed || cleartextDomain) {
            matches.add(createOwaspMatch("M3: Insecure Communication", "检测到允许明文网络传输或非HTTPS域名，可能面临中间人窃听攻击。", "HIGH"));
        }

        boolean weakCrypto = false;
        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            Optional<? extends ZipEntry> dexEntry = zip.stream().filter(e -> e.getName().endsWith(".dex")).findFirst();
            if (dexEntry.isPresent()) {
                byte[] dexBytes = zip.getInputStream(dexEntry.get()).readAllBytes();
                String content = new String(dexBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (content.contains("DES") || content.contains("AES/ECB") || content.contains("MD5") || content.contains("SHA-1") || content.contains("RC4")) {
                    weakCrypto = true;
                }
            }
        } catch (Exception ignored) {}
        if (weakCrypto) {
            matches.add(createOwaspMatch("M5: Insufficient Cryptography", "检测到弱加密（DES/MD5/SHA-1/RC4/AES-ECB），易遭暴力破解或已知明文攻击。", "HIGH"));
        }

        // M1: Improper Platform Usage — 平台功能误用（exported组件、WebView风险）
        boolean hasExportedActivity = manifestXml.contains("android:exported=\"true\"");
        boolean hasWebViewRisk = false;
        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            Optional<? extends ZipEntry> dexEntry = zip.stream().filter(e -> e.getName().endsWith(".dex")).findFirst();
            if (dexEntry.isPresent()) {
                byte[] dexBytes = zip.getInputStream(dexEntry.get()).readAllBytes();
                String content = new String(dexBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (content.contains("addJavascriptInterface") || content.contains("setAllowFileAccess") || content.contains("setAllowUniversalAccessFromFileURLs")) {
                    hasWebViewRisk = true;
                }
            }
        } catch (Exception ignored) {}
        if (hasExportedActivity || hasWebViewRisk) {
            matches.add(createOwaspMatch("M1: Improper Platform Usage", "检测到导出组件未受权限保护或WebView存在远程代码执行/文件访问风险。", "HIGH"));
        }

        // M2: Insecure Data Storage
        if (manifestXml != null && (manifestXml.contains("android:sharedUserId") || manifestXml.contains("android:installLocation=\"preferExternal\""))) {
            matches.add(createOwaspMatch("M2: Insecure Data Storage", "检测到不安全的存储声明或共享用户ID。", "MEDIUM"));
        }

        // M4: Insecure Authentication
        boolean hasHardcodedAuth = false;
        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            Optional<? extends ZipEntry> dexEntry = zip.stream().filter(e -> e.getName().endsWith(".dex")).findFirst();
            if (dexEntry.isPresent()) {
                byte[] dexBytes = zip.getInputStream(dexEntry.get()).readAllBytes();
                String content = new String(dexBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (content.contains("Authorization") || content.contains("Bearer ") || content.contains("Basic ")) {
                    hasHardcodedAuth = true;
                }
            }
        } catch (Exception ignored) {}
        if (hasHardcodedAuth) {
            matches.add(createOwaspMatch("M4: Insecure Authentication", "在DEX中检测到Authorization/Bearer/Basic硬编码认证令牌，可能导致未授权访问。", "HIGH"));
        }

        // M6: Insecure Authorization — debuggable标志
        if (debuggable) {
            matches.add(createOwaspMatch("M6: Insecure Authorization", "android:debuggable=\"true\"，允许攻击者通过ADB获取应用敏感数据。", "HIGH"));
        }

        // M7: Client Code Quality — 检查是否有硬编码调试信息
        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            Optional<? extends ZipEntry> dexEntry = zip.stream().filter(e -> e.getName().endsWith(".dex")).findFirst();
            if (dexEntry.isPresent()) {
                byte[] dexBytes = zip.getInputStream(dexEntry.get()).readAllBytes();
                String content = new String(dexBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (content.contains("Log.d") || content.contains("Log.e") || content.contains("Log.w") || content.contains("android.util.Log")) {
                    matches.add(createOwaspMatch("M7: Client Code Quality", "检测到Log调试日志输出，可能泄露敏感信息到logcat。", "MEDIUM"));
                }
            }
        } catch (Exception ignored) {}

        // M8: Code Tampering
        boolean hasIntegrityCheck = false;
        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            Optional<? extends ZipEntry> dexEntry = zip.stream().filter(e -> e.getName().endsWith(".dex")).findFirst();
            if (dexEntry.isPresent()) {
                byte[] dexBytes = zip.getInputStream(dexEntry.get()).readAllBytes();
                String content = new String(dexBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                if (content.contains("PackageManager") && content.contains("getInstallerPackageName")) hasIntegrityCheck = true;
            }
        } catch (Exception ignored) {}
        if (!hasIntegrityCheck) {
            matches.add(createOwaspMatch("M8: Code Tampering", "未检测到完整性校验或签名验证逻辑，应用易遭二次打包/重签名攻击。", "MEDIUM"));
        }

        // M9: Reverse Engineering — 检查是否有加壳/混淆/反调试
        List<String> nativeLibs = (List<String>) features.getOrDefault("nativeLibraries", List.of());
        boolean hasNativeProtection = nativeLibs.stream().anyMatch(l -> l.contains("jiagu") || l.contains("protect") || l.contains("shell"));
        if (!hasNativeProtection) {
            matches.add(createOwaspMatch("M9: Reverse Engineering", "未检测到加壳或高级代码混淆保护，核心业务逻辑可能在几分钟内逆向。", "LOW"));
        }

        return matches;
    }

    private Map<String, Object> createOwaspMatch(String category, String description, String severity) {
        Map<String, Object> map = new HashMap<>();
        map.put("category", category);
        map.put("description", description);
        map.put("severity", severity);
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> checkPermissionBloat(Path apkPath, Map<String, Object> features) {
        List<Map<String, Object>> bloatList = new ArrayList<>();
        List<String> permissions = (List<String>) features.get("permissions");
        if (permissions == null || permissions.isEmpty()) return bloatList;

        StringBuilder dexContentSb = new StringBuilder();
        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            zip.stream()
               .filter(e -> e.getName().endsWith(".dex"))
               .forEach(entry -> {
                   try {
                       byte[] bytes = zip.getInputStream(entry).readAllBytes();
                       dexContentSb.append(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1));
                   } catch (IOException ignored) {}
               });
        } catch (IOException ignored) {}
        String dexContent = dexContentSb.toString();

        checkSingleBloat(permissions, dexContent, "android.permission.RECORD_AUDIO",
                Arrays.asList("AudioRecord", "MediaRecorder", "RECORD_AUDIO"),
                "录音权限", "DEX未发现音频采集或录制API调用，存在超范围索权风险", bloatList);

        checkSingleBloat(permissions, dexContent, "android.permission.CAMERA",
                Arrays.asList("Camera", "camera", "CAMERA"),
                "相机权限", "DEX未发现摄像头控制或拍照API调用，存在超范围索权风险", bloatList);

        checkSingleBloat(permissions, dexContent, "android.permission.READ_CONTACTS",
                Arrays.asList("ContactsContract", "contacts"),
                "通讯录权限", "DEX未发现通讯录内容提供者访问，涉嫌越界收集隐私", bloatList);

        checkSingleBloat(permissions, dexContent, "android.permission.SEND_SMS",
                Arrays.asList("SmsManager", "sendTextMessage"),
                "发送短信", "DEX中未检测到任何短信发送或管理API，多余的高危权限声明", bloatList);

        checkSingleBloat(permissions, dexContent, "android.permission.ACCESS_FINE_LOCATION",
                Arrays.asList("LocationManager", "LocationClient", "getLastKnownLocation"),
                "精确位置", "DEX未发现定位查询接口调用，权限声明虚高", bloatList);

        return bloatList;
    }

    private void checkSingleBloat(List<String> permissions, String dexContent, String permissionName,
                                  List<String> apis, String label, String desc, List<Map<String, Object>> list) {
        if (permissions.contains(permissionName)) {
            boolean called = false;
            for (String api : apis) {
                if (dexContent.contains(api)) {
                    called = true;
                    break;
                }
            }
            if (!called) {
                Map<String, Object> map = new HashMap<>();
                map.put("permission", permissionName);
                map.put("label", label);
                map.put("description", desc);
                map.put("status", "UNUSED_BLOAT");
                list.add(map);
            }
        }
    }

    // ========== 新增深度检测方法 ==========

    /**
     * WebView安全风险检测
     */
    public Map<String, Object> detectWebViewSecurity(Path apkPath) {
        Map<String, Object> result = new HashMap<>();
        result.put("hasJavascriptInterface", false);
        result.put("hasFileAccess", false);
        result.put("hasUniversalAccessFromFileURLs", false);
        result.put("risks", new ArrayList<>());

        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            Optional<? extends ZipEntry> dexEntry = zip.stream()
                    .filter(e -> e.getName().endsWith(".dex")).findFirst();
            if (dexEntry.isPresent()) {
                byte[] dexBytes = zip.getInputStream(dexEntry.get()).readAllBytes();
                String content = new String(dexBytes, java.nio.charset.StandardCharsets.ISO_8859_1);

                if (content.contains("addJavascriptInterface")) {
                    result.put("hasJavascriptInterface", true);
                    @SuppressWarnings("unchecked")
                    List<String> risks = (List<String>) result.get("risks");
                    risks.add("检测到addJavascriptInterface — 可能暴露Java对象到JS，存在远程代码执行风险 (CVE-2012-6636等)");
                }
                if (content.contains("setAllowFileAccess") || content.contains("allowFileAccess")) {
                    result.put("hasFileAccess", true);
                    @SuppressWarnings("unchecked")
                    List<String> risks = (List<String>) result.get("risks");
                    risks.add("允许WebView文件访问 — 可能读取应用沙箱内的敏感文件");
                }
                if (content.contains("setAllowUniversalAccessFromFileURLs")) {
                    result.put("hasUniversalAccessFromFileURLs", true);
                    @SuppressWarnings("unchecked")
                    List<String> risks = (List<String>) result.get("risks");
                    risks.add("允许file://URL跨域访问 — 极高危，可读取任意本地文件");
                }
                if (content.contains("setDomStorageEnabled")) {
                    @SuppressWarnings("unchecked")
                    List<String> risks = (List<String>) result.get("risks");
                    risks.add("启用DOM Storage — 可能存储敏感数据在WebView本地存储中");
                }
            }
        } catch (IOException e) {
            log.warn("WebView安全检测失败", e);
        }

        return result;
    }

    /**
     * SSL/TLS证书验证绕过检测
     */
    public Map<String, Object> detectSSLTrustManagerOverride(Path apkPath) {
        Map<String, Object> result = new HashMap<>();
        result.put("hasCustomTrustManager", false);
        result.put("hasHostnameVerifierBypass", false);
        result.put("details", new ArrayList<>());

        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            Optional<? extends ZipEntry> dexEntry = zip.stream()
                    .filter(e -> e.getName().endsWith(".dex")).findFirst();
            if (dexEntry.isPresent()) {
                byte[] dexBytes = zip.getInputStream(dexEntry.get()).readAllBytes();
                String content = new String(dexBytes, java.nio.charset.StandardCharsets.ISO_8859_1);

                // 检测自定义TrustManager（接受所有证书）
                if (content.contains("X509TrustManager") && (content.contains("checkServerTrusted") &&
                        (content.contains("return-void") || !content.contains("throw")))) {
                    result.put("hasCustomTrustManager", true);
                    @SuppressWarnings("unchecked")
                    List<String> details = (List<String>) result.get("details");
                    details.add("检测到可能重写checkServerTrusted为空实现，接受所有SSL证书。存在中间人攻击风险。");
                }

                // 检测HostnameVerifier绕过
                if (content.contains("HostnameVerifier") && (content.contains("return true") || content.contains("return-void"))) {
                    result.put("hasHostnameVerifierBypass", true);
                    @SuppressWarnings("unchecked")
                    List<String> details = (List<String>) result.get("details");
                    details.add("检测到HostnameVerifier返回值恒为true，绕过主机名验证。");
                }

                // 检测AllowAllHostnameVerifier
                if (content.contains("ALLOW_ALL_HOSTNAME_VERIFIER") || content.contains("AllowAllHostnameVerifier")) {
                    result.put("hasHostnameVerifierBypass", true);
                    @SuppressWarnings("unchecked")
                    List<String> details = (List<String>) result.get("details");
                    details.add("使用ALLOW_ALL_HOSTNAME_VERIFIER，完全绕过主机名验证。");
                }
            }
        } catch (IOException e) {
            log.warn("SSL检测失败", e);
        }

        return result;
    }

    /**
     * 组件导出风险分析
     */
    public List<Map<String, Object>> detectComponentExport(Path apkPath) {
        List<Map<String, Object>> exportedComponents = new ArrayList<>();

        try (ApkFile apkFile = new ApkFile(apkPath.toFile())) {
            String manifestXml = apkFile.getManifestXml();
            if (manifestXml == null) return exportedComponents;

            String[] componentTypes = {"activity", "service", "receiver", "provider"};
            for (String type : componentTypes) {
                Pattern pattern = Pattern.compile(
                        "<" + type + "[^>]*android:name=\"([^\"]+)\"[^>]*(?:android:exported=\"(true)\")?[^>]*>",
                        Pattern.DOTALL);
                Matcher m = pattern.matcher(manifestXml);
                while (m.find()) {
                    String name = m.group(1);
                    boolean exported = "true".equals(m.group(2));
                    // 同时检查是否有Intent Filter (有则默认导出)
                    boolean hasIntentFilter = false;
                    int endIdx = manifestXml.indexOf("</" + type + ">", m.start());
                    if (endIdx > 0) {
                        String block = manifestXml.substring(m.start(), endIdx);
                        hasIntentFilter = block.contains("<intent-filter");
                    }

                    if (exported || hasIntentFilter) {
                        Map<String, Object> comp = new HashMap<>();
                        comp.put("name", name);
                        comp.put("type", type);
                        comp.put("exported", exported);
                        comp.put("hasIntentFilter", hasIntentFilter);
                        comp.put("risk", (!exported && hasIntentFilter) ? "IMPLICIT_EXPORT" : "EXPLICIT_EXPORT");
                        exportedComponents.add(comp);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("组件导出检测失败", e);
        }

        return exportedComponents;
    }

    /**
     * 反射与动态代码加载检测
     */
    public Map<String, Object> detectReflectionAndDynamicLoading(Path apkPath) {
        Map<String, Object> result = new HashMap<>();
        List<String> reflectionCalls = new ArrayList<>();
        List<String> dynamicLoadCalls = new ArrayList<>();

        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            Optional<? extends ZipEntry> dexEntry = zip.stream()
                    .filter(e -> e.getName().endsWith(".dex")).findFirst();
            if (dexEntry.isPresent()) {
                byte[] dexBytes = zip.getInputStream(dexEntry.get()).readAllBytes();
                String content = new String(dexBytes, java.nio.charset.StandardCharsets.ISO_8859_1);

                if (content.contains("java/lang/reflect/Method")) reflectionCalls.add("Method.invoke反射调用");
                if (content.contains("java/lang/reflect/Field")) reflectionCalls.add("Field反射访问字段");
                if (content.contains("java/lang/Class;->forName")) reflectionCalls.add("Class.forName动态加载类");
                if (content.contains("java/lang/ClassLoader")) dynamicLoadCalls.add("ClassLoader动态类加载");
                if (content.contains("dalvik/system/DexClassLoader")) dynamicLoadCalls.add("DexClassLoader外部DEX加载");
                if (content.contains("dalvik/system/PathClassLoader")) dynamicLoadCalls.add("PathClassLoader路径DEX加载");
                if (content.contains("dalvik/system/DexFile;") && content.contains("->loadDex")) dynamicLoadCalls.add("DexFile.loadDex手动DEX加载");
            }
        } catch (IOException e) {
            log.warn("反射检测失败", e);
        }

        result.put("reflectionCalls", reflectionCalls);
        result.put("dynamicLoadCalls", dynamicLoadCalls);
        result.put("reflectionCount", reflectionCalls.size());
        result.put("dynamicLoadCount", dynamicLoadCalls.size());

        return result;
    }

    /**
     * 反模拟器/反沙箱检测
     */
    public Map<String, Object> detectAntiEmulator(Path apkPath) {
        Map<String, Object> result = new HashMap<>();
        List<String> indicators = new ArrayList<>();

        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            Optional<? extends ZipEntry> dexEntry = zip.stream()
                    .filter(e -> e.getName().endsWith(".dex")).findFirst();
            if (dexEntry.isPresent()) {
                byte[] dexBytes = zip.getInputStream(dexEntry.get()).readAllBytes();
                String content = new String(dexBytes, java.nio.charset.StandardCharsets.ISO_8859_1);

                // 模拟器特征检测
                if (content.contains("ro.product.manufacturer") || content.contains("ro.product.model")) {
                    indicators.add("读取设备厂商/型号属性 (Build.prop fingerprint检测)");
                }
                if (content.contains("isEmulator") || content.contains("simulator") || content.contains("emulator")) {
                    indicators.add("直接调用isEmulator/emulator检测函数");
                }
                if (content.contains("qemu") || content.contains("goldfish") || content.contains("ranchu")) {
                    indicators.add("检测QEMU/Goldfish/Ranchu模拟器特征");
                }
                if (content.contains("genymotion") || content.contains("vbox") || content.contains("virtualBox")) {
                    indicators.add("检测Genymotion/VirtualBox虚拟化环境");
                }
                if (content.contains("/proc/cpuinfo") || content.contains("/proc/self/status")) {
                    indicators.add("读取proc文件系统检测运行环境");
                }
                if (content.contains("TelephonyManager") && content.contains("getNetworkOperator")) {
                    indicators.add("通过SIM卡运营商信息检测模拟器 (模拟器通常无SIM卡)");
                }
                if (content.contains("/system/lib/libc_malloc_debug_qemu.so")) {
                    indicators.add("检测QEMU malloc调试库文件存在性");
                }
                if (content.contains("Debug.isDebuggerConnected") || content.contains("waitingForDebugger")) {
                    indicators.add("检测调试器连接状态");
                }
            }
        } catch (IOException e) {
            log.warn("反模拟器检测失败", e);
        }

        result.put("hasAntiEmulation", !indicators.isEmpty());
        result.put("indicators", indicators);
        result.put("indicatorCount", indicators.size());

        return result;
    }

    /**
     * 第三方SDK指纹识别
     */
    public List<Map<String, Object>> identifyThirdPartySdks(Path apkPath) {
        List<Map<String, Object>> sdks = new ArrayList<>();
        Map<String, String> sdkPatterns = new LinkedHashMap<>();
        sdkPatterns.put("百度地图SDK", "com/baidu/mapapi");
        sdkPatterns.put("高德地图SDK", "com/amap/api");
        sdkPatterns.put("腾讯地图SDK", "com/tencent/mapsdk");
        sdkPatterns.put("微信支付SDK", "com/tencent/mm/sdk");
        sdkPatterns.put("支付宝SDK", "com/alipay/sdk");
        sdkPatterns.put("友盟统计SDK", "com/umeng/analytics");
        sdkPatterns.put("腾讯Bugly", "com/tencent/bugly");
        sdkPatterns.put("极光推送", "cn.jpush/android");
        sdkPatterns.put("小米推送", "com/xiaomi/mipush");
        sdkPatterns.put("华为推送", "com/huawei/hms/push");
        sdkPatterns.put("Google Firebase", "com/google/firebase");
        sdkPatterns.put("Google Ads", "com/google/android/gms/ads");
        sdkPatterns.put("Facebook SDK", "com/facebook/FacebookSdk");
        sdkPatterns.put("OKHttp", "okhttp3/OkHttpClient");
        sdkPatterns.put("Retrofit", "retrofit2/Retrofit");
        sdkPatterns.put("Glide", "com/bumptech/glide");

        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            Optional<? extends ZipEntry> dexEntry = zip.stream()
                    .filter(e -> e.getName().endsWith(".dex")).findFirst();
            if (dexEntry.isPresent()) {
                byte[] dexBytes = zip.getInputStream(dexEntry.get()).readAllBytes();
                String content = new String(dexBytes, java.nio.charset.StandardCharsets.ISO_8859_1);

                for (Map.Entry<String, String> sdk : sdkPatterns.entrySet()) {
                    if (content.contains(sdk.getValue())) {
                        Map<String, Object> sdkInfo = new HashMap<>();
                        sdkInfo.put("name", sdk.getKey());
                        sdkInfo.put("signature", sdk.getValue());
                        sdks.add(sdkInfo);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("SDK识别失败", e);
        }

        return sdks;
    }

    /**
     * DEX混淆评估
     */
    public Map<String, Object> assessDexObfuscation(Path apkPath) {
        Map<String, Object> result = new HashMap<>();

        try (ZipFile zip = new ZipFile(apkPath.toFile())) {
            long dexCount = zip.stream().filter(e -> e.getName().matches("classes\\d*\\.dex")).count();
            result.put("dexCount", dexCount);

            // 检查是否有类名被混淆（单/双字符类名）
            Optional<? extends ZipEntry> dexEntry = zip.stream()
                    .filter(e -> e.getName().endsWith(".dex")).findFirst();
            if (dexEntry.isPresent()) {
                byte[] dexBytes = zip.getInputStream(dexEntry.get()).readAllBytes();
                String content = new String(dexBytes, java.nio.charset.StandardCharsets.ISO_8859_1);

                // 检测ProGuard/R8/DexGuard混淆特征
                boolean hasSingleCharPaths = Pattern.compile("L[a-z]/").matcher(content).find();
                boolean hasProguardMapping = content.contains("proguard") || content.contains("ProGuard");
                boolean hasDexguard = content.contains("dexguard") || content.contains("DexGuard");

                result.put("hasSingleCharClassNames", hasSingleCharPaths);
                result.put("proguardObfuscated", hasProguardMapping || hasSingleCharPaths);
                result.put("dexguardObfuscated", hasDexguard);

                // 混淆程度评估
                if (hasDexguard) {
                    result.put("level", "HIGH");
                    result.put("description", "检测到DexGuard商业混淆器特征");
                } else if (hasSingleCharPaths && dexCount > 2) {
                    result.put("level", "MEDIUM");
                    result.put("description", "检测到ProGuard/R8混淆，多DEX分包");
                } else if (hasSingleCharPaths) {
                    result.put("level", "LOW");
                    result.put("description", "检测到基本ProGuard/R8混淆");
                } else {
                    result.put("level", "NONE");
                    result.put("description", "未检测到代码混淆");
                }
            }
        } catch (IOException e) {
            log.warn("DEX混淆评估失败", e);
        }

        return result;
    }
}
