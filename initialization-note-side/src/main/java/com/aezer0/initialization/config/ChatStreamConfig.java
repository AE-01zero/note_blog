package com.aezer0.initialization.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 流式聊天速率控制配置
 * 
 * @author aezer0
 * @date 2024-01-01
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "chat.stream")
public class ChatStreamConfig {
    
    /**
     * 基础延迟时间（毫秒）
     * 每个数据块之间的最小间隔时间
     */
    private long baseDelayMs = 50;
    
    /**
     * 最大延迟时间（毫秒）
     * 自适应延迟的上限
     */
    private long maxDelayMs = 200;
    
    /**
     * 内容长度阈值
     * 超过此长度的内容块会增加额外延迟
     */
    private int chunkSizeThreshold = 10;
    
    /**
     * 速率限制窗口大小
     * 用于平滑传输的滑动窗口大小
     */
    private int rateLimitWindow = 20;
    
    /**
     * 滑动窗口内的延迟时间（毫秒）
     * 用于平滑传输的小延迟
     */
    private long windowDelayMs = 10;
    
    /**
     * 背压缓冲区大小
     * 防止客户端处理不及时的缓冲区大小
     */
    private int backpressureBufferSize = 100;
    
    /**
     * 内容延迟系数
     * 用于计算基于内容长度的延迟时间
     */
    private double contentDelayFactor = 2.0;
    
    /**
     * 索引延迟减少系数
     * 前几个块的延迟减少量
     */
    private long indexDelayReduction = 5;
    
    /**
     * 启用速率控制
     * 全局开关，可以完全关闭速率控制
     */
    private boolean enableRateControl = true;
    
    /**
     * 启用自适应延迟
     * 是否根据内容长度动态调整延迟
     */
    private boolean enableAdaptiveDelay = true;
    
    /**
     * 启用平滑传输
     * 是否使用滑动窗口平滑传输
     */
    private boolean enableSmoothTransmission = true;
    
    /**
     * 启用日志记录
     * 是否记录速率控制的详细日志
     */
    private boolean enableLogging = false;
    
    // 便捷方法
    public Duration getBaseDelay() {
        return Duration.ofMillis(baseDelayMs);
    }
    
    public Duration getMaxDelay() {
        return Duration.ofMillis(maxDelayMs);
    }
    
    public Duration getWindowDelay() {
        return Duration.ofMillis(windowDelayMs);
    }
    
    /**
     * 验证配置参数的有效性
     */
    public void validate() {
        if (baseDelayMs < 0) {
            throw new IllegalArgumentException("基础延迟时间不能为负数");
        }
        if (maxDelayMs < baseDelayMs) {
            throw new IllegalArgumentException("最大延迟时间不能小于基础延迟时间");
        }
        if (chunkSizeThreshold < 0) {
            throw new IllegalArgumentException("内容长度阈值不能为负数");
        }
        if (rateLimitWindow <= 0) {
            throw new IllegalArgumentException("速率限制窗口大小必须大于0");
        }
        if (backpressureBufferSize <= 0) {
            throw new IllegalArgumentException("背压缓冲区大小必须大于0");
        }
        if (contentDelayFactor < 0) {
            throw new IllegalArgumentException("内容延迟系数不能为负数");
        }
    }
} 