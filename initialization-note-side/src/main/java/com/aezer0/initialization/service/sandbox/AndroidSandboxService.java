package com.aezer0.initialization.service.sandbox;

import com.aezer0.initialization.config.SandboxConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.aezer0.initialization.service.sandbox.FriggaScriptExecutor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android沙箱管理服务
 * 负责管理Android模拟器、安装应用、收集行为数据
 */
@Service
@Slf4j
public class AndroidSandboxService {

    @Autowired
    private SandboxConfig config;

    @Autowired
    private FriggaScriptExecutor fridaExecutor;

    // 模拟器状态
    private String emulatorStatus = "stopped";
    private String deviceSerial = null;
    private boolean fridaServerRunning = false;

    /**
     * 启动沙箱环境
     */
    public Map<String, Object> startSandbox() {
        Map<String, Object> result = new HashMap<>();
        List<String> steps = new ArrayList<>();

        try {
            // 1. 检查环境
            steps.add("检查Android SDK环境");
            if (!checkAndroidSdk()) {
                result.put("success", false);
                result.put("error", "Android SDK未正确配置");
                result.put("steps", steps);
                return result;
            }
            steps.add("Android SDK环境检查通过");

            // 2. 启动模拟器
            steps.add("启动Android模拟器");
            if (!startEmulator()) {
                result.put("success", false);
                result.put("error", "模拟器启动失败");
                result.put("steps", steps);
                return result;
            }
            steps.add("模拟器启动成功，设备: " + deviceSerial);

            // 3. 等待设备就绪
            steps.add("等待设备就绪");
            if (!waitForDevice()) {
                result.put("success", false);
                result.put("error", "设备未就绪");
                result.put("steps", steps);
                return result;
            }
            steps.add("设备就绪");

            // 4. 启动Frida Server
            steps.add("启动Frida Server");
            if (!startFridaServer()) {
                result.put("success", false);
                result.put("error", "Frida Server启动失败");
                result.put("steps", steps);
                return result;
            }
            steps.add("Frida Server已启动");

            emulatorStatus = "running";
            result.put("success", true);
            result.put("steps", steps);
            result.put("deviceSerial", deviceSerial);

        } catch (Exception e) {
            log.error("沙箱启动失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("steps", steps);
        }

        return result;
    }

    /**
     * 停止沙箱环境
     */
    public Map<String, Object> stopSandbox() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 停止Frida Server
            if (fridaServerRunning && deviceSerial != null) {
                execCommand("adb", "-s", deviceSerial, "shell", "pkill", "frida-server");
                fridaServerRunning = false;
            }

            // 停止模拟器
            if (deviceSerial != null) {
                execCommand("adb", "-s", deviceSerial, "emu", "kill");
            }

            emulatorStatus = "stopped";
            deviceSerial = null;
            result.put("success", true);

        } catch (Exception e) {
            log.error("沙箱停止失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 安装APK到模拟器
     */
    public Map<String, Object> installApk(byte[] apkBytes, String packageName) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 保存APK到临时文件
            Path tempApk = Files.createTempFile("sample_", ".apk");
            Files.write(tempApk, apkBytes);

            // 安装APK
            String installResult = execCommand(
                config.getAdbPath(),
                "-s", deviceSerial,
                "install", "-r", tempApk.toString()
            );

            // 删除临时文件
            Files.deleteIfExists(tempApk);

            if (installResult.contains("Success")) {
                result.put("success", true);
                result.put("packageName", packageName);
                log.info("APK安装成功: {}", packageName);
            } else {
                result.put("success", false);
                result.put("error", "安装失败: " + installResult);
            }

        } catch (Exception e) {
            log.error("APK安装失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 启动应用并监控行为
     */
    public Map<String, Object> runAndMonitor(String packageName, String mainActivity, int durationSeconds) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> events = new ArrayList<>();

        try {
            log.info("开始监控应用: {}", packageName);

            // 1. 启动Frida监控脚本
            Map<String, Object> fridaResult = fridaExecutor.monitorApp(
                deviceSerial, packageName, durationSeconds
            );

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fridaEvents = (List<Map<String, Object>>) fridaResult.get("events");
            if (fridaEvents != null) {
                events.addAll(fridaEvents);
            }

            // 2. 启动应用
            String launchActivity = mainActivity != null ? mainActivity : packageName + ".MainActivity";
            execCommand(
                config.getAdbPath(),
                "-s", deviceSerial,
                "shell", "am", "start", "-n", launchActivity
            );

            // 3. 等待应用运行
            Thread.sleep(durationSeconds * 1000);

            // 4. 收集网络流量
            Map<String, Object> networkEvents = collectNetworkTraffic();
            events.addAll((List<Map<String, Object>>) networkEvents.get("events"));

            // 5. 收集文件操作
            Map<String, Object> fileEvents = collectFileOperations();
            events.addAll((List<Map<String, Object>>) fileEvents.get("events"));

            result.put("success", true);
            result.put("events", events);
            result.put("eventCount", events.size());
            result.put("packageName", packageName);
            result.put("duration", durationSeconds);

        } catch (Exception e) {
            log.error("应用监控失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 收集网络流量
     */
    private Map<String, Object> collectNetworkTraffic() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> events = new ArrayList<>();

        try {
            // 使用tcpdump或模拟收集网络连接
            String netstatOutput = execCommand(
                config.getAdbPath(),
                "-s", deviceSerial,
                "shell", "netstat"
            );

            // 解析网络连接
            Pattern connPattern = Pattern.compile("(tcp|udp)\\s+(\\d+)\\s+(\\S+):(\\d+)\\s+(\\S+):(\\d+)");
            Matcher matcher = connPattern.matcher(netstatOutput);

            while (matcher.find()) {
                Map<String, Object> event = new HashMap<>();
                event.put("time", System.currentTimeMillis());
                event.put("type", "NETWORK");
                event.put("protocol", matcher.group(1));
                event.put("localPort", matcher.group(4));
                event.put("remote", matcher.group(5));
                event.put("remotePort", matcher.group(6));
                event.put("action", "OUTBOUND_CONNECTION");
                events.add(event);
            }

            // 也获取套接字信息
            String ssOutput = execCommand(
                config.getAdbPath(),
                "-s", deviceSerial,
                "shell", "ss", "-tp"
            );

            result.put("events", events);

        } catch (Exception e) {
            log.error("收集网络流量失败", e);
            result.put("events", events);
        }

        return result;
    }

    /**
     * 收集文件操作
     */
    private Map<String, Object> collectFileOperations() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> events = new ArrayList<>();

        try {
            // 检查应用数据目录
            String[] appDirs = {
                "/data/data",
                "/sdcard/Android/data"
            };

            for (String baseDir : appDirs) {
                String lsOutput = execCommand(
                    config.getAdbPath(),
                    "-s", deviceSerial,
                    "shell", "ls", "-la", baseDir
                );

                // 解析目录列表
                if (lsOutput != null && !lsOutput.isEmpty()) {
                    Map<String, Object> event = new HashMap<>();
                    event.put("time", System.currentTimeMillis());
                    event.put("type", "FILE");
                    event.put("operation", "LIST_DIRECTORY");
                    event.put("path", baseDir);
                    event.put("content", lsOutput);
                    events.add(event);
                }
            }

            result.put("events", events);

        } catch (Exception e) {
            log.error("收集文件操作失败", e);
            result.put("events", events);
        }

        return result;
    }

    // ========== 私有方法 ==========

    private boolean checkAndroidSdk() {
        try {
            String result = execCommand(config.getAdbPath(), "version");
            return result != null && result.contains("Android Debug Bridge");
        } catch (Exception e) {
            log.error("Android SDK检查失败", e);
            return false;
        }
    }

    private boolean startEmulator() throws Exception {
        // 启动模拟器
        ProcessBuilder pb = new ProcessBuilder(
            config.getEmulatorPath(),
            "-avd", config.getAvdName(),
            "-port", String.valueOf(config.getEmulatorPort()),
            "-no-snapshot",
            "-no-audio"
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 等待模拟器启动
        Thread.sleep(10000);

        // 获取设备序列号
        String devices = execCommand(config.getAdbPath(), "devices");
        if (devices.contains("emulator-" + config.getEmulatorPort())) {
            deviceSerial = "emulator-" + config.getEmulatorPort();
            return true;
        }

        return false;
    }

    private boolean waitForDevice() throws Exception {
        int retries = 30;
        while (retries > 0) {
            String result = execCommand(
                config.getAdbPath(),
                "-s", deviceSerial,
                "shell", "getprop", "sys.boot_completed"
            );

            if (result != null && result.trim().equals("1")) {
                return true;
            }

            Thread.sleep(2000);
            retries--;
        }
        return false;
    }

    private boolean startFridaServer() throws Exception {
        // 推送Frida Server到设备
        String pushResult = execCommand(
            config.getAdbPath(),
            "-s", deviceSerial,
            "push", config.getFridaServerPath(), "/data/local/tmp/frida-server"
        );

        // 设置权限
        execCommand(
            config.getAdbPath(),
            "-s", deviceSerial,
            "shell", "chmod", "+x", "/data/local/tmp/frida-server"
        );

        // 启动Frida Server
        execCommand(
            config.getAdbPath(),
            "-s", deviceSerial,
            "shell", "/data/local/tmp/frida-server",
            "-l", "0.0.0.0:" + config.getFridaServerPort()
        );

        // 后台运行，不等待
        new Thread(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{
                    config.getAdbPath(), "-s", deviceSerial,
                    "shell", "/data/local/tmp/frida-server",
                    "-l", "0.0.0.0:" + config.getFridaServerPort()
                });
            } catch (IOException e) {
                log.error("Frida Server启动失败", e);
            }
        }).start();

        Thread.sleep(2000);
        fridaServerRunning = true;

        return true;
    }

    private String execCommand(String... commands) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(Arrays.asList(commands));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        process.waitFor(30, TimeUnit.SECONDS);
        return output.toString();
    }

    /**
     * 获取沙箱状态
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("emulatorStatus", emulatorStatus);
        status.put("deviceSerial", deviceSerial);
        status.put("fridaServerRunning", fridaServerRunning);
        status.put("config", config);
        return status;
    }
}
