package com.ldd.initialization.config.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai-analysis")
@Data
public class AiAnalysisConfig {

    private ModuleAiConfig apk = new ModuleAiConfig();
    private ModuleAiConfig so = new ModuleAiConfig();
    private ModuleAiConfig protocol = new ModuleAiConfig();
    private ModuleAiConfig sandbox = new ModuleAiConfig();
    private PromptSecurityConfig promptSecurity = new PromptSecurityConfig();
    private GuidedConfig guided = new GuidedConfig();

    @Data
    public static class ModuleAiConfig {
        private boolean aiEnhanceEnabled = false;
    }

    @Data
    public static class PromptSecurityConfig {
        /** AI增强Prompt注入检测开关 */
        private boolean aiEnhanceEnabled = false;
        /** AI分析超时时间（秒） */
        private int aiAnalysisTimeout = 30;
        /** AI分析最低置信度阈值，低于此值不触发AI分析 */
        private double aiAnalysisMinConfidence = 0.3;
    }

    @Data
    public static class GuidedConfig {
        private boolean enabled = true;
        private int sessionTtlMinutes = 120;
        private int maxSessionsPerUser = 3;
        private int heartbeatIntervalSeconds = 15;
        private int stepTimeoutSeconds = 120;
    }
}
