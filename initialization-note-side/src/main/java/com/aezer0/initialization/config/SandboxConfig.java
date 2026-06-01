package com.aezer0.initialization.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 沙箱配置
 */
@Configuration
@ConfigurationProperties(prefix = "sandbox")
@Data
public class SandboxConfig {

    /**
     * 是否启用沙箱
     */
    private boolean enabled = true;

    /**
     * Android SDK路径
     */
    private String androidSdkPath = "/opt/android-sdk";

    /**
     * AVD模拟器名称
     */
    private String avdName = "sandbox_emulator";

    /**
     * 模拟器系统镜像
     */
    private String systemImage = "system-images;android-33;google_apis;x86_64";

    /**
     * 模拟器端口 (adb连接端口)
     */
    private int emulatorPort = 5554;

    /**
     * Frida Server端口
     */
    private int fridaServerPort = 27042;

    /**
     * Frida Server路径
     */
    private String fridaServerPath = "/opt/frida-server";

    /**
     * 分析超时时间（秒）
     */
    private int analysisTimeout = 300;

    /**
     * 分析完成前等待时间（秒）
     */
    private int waitTime = 60;

    /**
     * ADB路径
     */
    public String getAdbPath() {
        return androidSdkPath + "/platform-tools/adb";
    }

    /**
     * Emulator路径
     */
    public String getEmulatorPath() {
        return androidSdkPath + "/emulator/emulator";
    }

    /**
     * Frida Server完整路径
     */
    public String getFridaServerFullPath() {
        return fridaServerPath;
    }
}
