package com.aezer0.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.aezer0.initialization.config.ChatStreamConfig;
import com.aezer0.initialization.dto.ChatRequestDTO;
import com.aezer0.initialization.service.ai.UserConsultantService;
import com.aezer0.initialization.service.ai.adapter.DynamicChatDispatcher;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatController {

    @Autowired
    private UserConsultantService userConsultantService;

    @Autowired
    @Qualifier("streamingExecutor")
    private ThreadPoolTaskExecutor streamingExecutor;

    @Autowired
    private ChatStreamConfig chatStreamConfig;

    @PostConstruct
    public void init() {
        // 验证配置参数
        chatStreamConfig.validate();
        log.info("流式聊天速率控制配置已加载: {}", chatStreamConfig);
    }

    /**
     * 流式聊天接口 - 带速率控制
     */
    @SaCheckLogin
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequestDTO request) {
        // 获取当前登录用户ID
        String currentUserId = (StpUtil.getLoginId().toString());
        request.setUserId(currentUserId);
        log.info("用户 {} 发起流式聊天请求，会话ID: {}", request.getUserId(), request.getMemoryId());


        try {
            // 构建组合的会话ID，确保用户隔离
            String combinedMemoryId = request.getUserId() + "_" + request.getMemoryId();

            // 获取用户相关的增强消息
            String enhancedMessage = userConsultantService.buildEnhancedMessage(
                request.getUserId(),
                request.getMessage(),
                request.getCategoryFilter()
            );
            
            // 调用流式聊天服务，添加速率控制
            return userConsultantService.streamChat(combinedMemoryId, enhancedMessage)
                .subscribeOn(Schedulers.fromExecutor(streamingExecutor)) // 使用自定义执行器
                .timeout(Duration.ofSeconds(60), Flux.just("抱歉，AI响应超时，请检查API配置是否正确后重试。"))
                .map(chunk -> chunk.replace("\n", "\\n")) // 编码换行符，防止SSE协议丢失
                .transform(this::applyRateControl) // 应用速率控制
                .doOnNext(chunk -> log.debug("发送聊天块: {}", chunk.length() > 100 ? chunk.substring(0, 100) + "..." : chunk))
                .doOnComplete(() -> log.info("用户 {} 的流式聊天完成", request.getUserId()))
                .doOnError(error -> {
                    if (isRemoteModelError(error)) {
                        log.warn("用户 {} 的流式聊天出错（模型服务不可达）: {}", request.getUserId(), error.getMessage());
                    } else {
                        log.error("用户 {} 的流式聊天出错: {}", request.getUserId(), error.getMessage(), error);
                    }
                })
                .onErrorResume(throwable -> {
                    if (isRemoteModelError(throwable)) {
                        log.warn("流式聊天处理失败（模型服务不可达）: {}", throwable.getMessage());
                        return Flux.just("抱歉，当前模型服务连接超时或不可达，请稍后再试。");
                    }
                    log.error("流式聊天处理失败: {}", throwable.getMessage(), throwable);
                    return Flux.just("抱歉，聊天服务暂时不可用，请稍后再试。");
                })
                .switchIfEmpty(Flux.just("抱歉，AI模型未返回任何响应，请检查API配置（Base URL / API Key / 模型名称）是否正确。"));
                
        } catch (Exception e) {
            if (isRemoteModelError(e)) {
                log.warn("流式聊天处理失败（模型服务不可达）: {}", e.getMessage());
                return Flux.just("抱歉，当前模型服务连接超时或不可达，请稍后再试。");
            }
            log.error("流式聊天处理失败: {}", e.getMessage(), e);
            return Flux.just("聊天服务暂时不可用，请稍后再试。");
        }
    }

    private boolean isRemoteModelError(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            String name = cursor.getClass().getName().toLowerCase(Locale.ROOT);
            String message = cursor.getMessage() == null ? "" : cursor.getMessage().toLowerCase(Locale.ROOT);
            if (name.contains("resourceaccessexception")
                    || name.contains("connectexception")
                    || name.contains("unknownhost")
                    || name.contains("readtimeoutexception")
                    || message.contains("timed out")
                    || message.contains("timeout")
                    || message.contains("unknown host")
                    || message.contains("failed to resolve")
                    || message.contains("connection refused")
                    || message.contains("servfail")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /**
     * 应用速率控制的转换器
     * 使用多种策略来平滑响应速度：
     * 1. 基础延迟 - 确保最小间隔
     * 2. 自适应延迟 - 根据内容长度调整
     * 3. 平滑处理 - 避免突发性传输
     */
    private Flux<String> applyRateControl(Flux<String> source) {
        // 如果全局禁用速率控制，直接返回原始流
        if (!chatStreamConfig.isEnableRateControl()) {
            return source;
        }
        
        AtomicInteger chunkCounter = new AtomicInteger(0);
        
        Flux<String> processedFlux = source
            // 1. 基础延迟控制 - 确保每个块之间有最小间隔
            .delayElements(chatStreamConfig.getBaseDelay(), Schedulers.parallel());
        
        // 2. 自适应延迟 - 根据内容长度和频率动态调整
        if (chatStreamConfig.isEnableAdaptiveDelay()) {
            processedFlux = processedFlux.concatMap(chunk -> {
                int chunkIndex = chunkCounter.incrementAndGet();
                Duration adaptiveDelay = calculateAdaptiveDelay(chunk, chunkIndex);
                
                if (chatStreamConfig.isEnableLogging()) {
                    log.debug("块 {} 长度: {}, 延迟: {}ms", chunkIndex, chunk.length(), adaptiveDelay.toMillis());
                }
                
                return Flux.just(chunk)
                    .delayElements(adaptiveDelay, Schedulers.parallel());
            });
        }
        
        // 3. 平滑处理 - 使用滑动窗口平滑传输
        if (chatStreamConfig.isEnableSmoothTransmission()) {
            processedFlux = processedFlux
                .window(chatStreamConfig.getRateLimitWindow())
                .concatMap(window -> 
                    window.concatMap(chunk -> 
                        Flux.just(chunk)
                            .delayElements(chatStreamConfig.getWindowDelay(), Schedulers.parallel())
                    )
                );
        }
        
        // 4. 缓冲控制 - 避免过快传输
        return processedFlux.onBackpressureBuffer(chatStreamConfig.getBackpressureBufferSize());
    }

    /**
     * 计算自适应延迟
     * 根据内容长度、块索引等因素动态调整延迟时间
     */
    private Duration calculateAdaptiveDelay(String chunk, int chunkIndex) {
        // 基础延迟
        long baseDelayMs = chatStreamConfig.getBaseDelay().toMillis();
        
        // 根据内容长度调整延迟
        int contentLength = chunk.length();
        long contentDelay = 0;
        
        if (contentLength > chatStreamConfig.getChunkSizeThreshold()) {
            // 内容较长时，适当增加延迟
            contentDelay = Math.min(
                (long)(contentLength * chatStreamConfig.getContentDelayFactor()), 
                chatStreamConfig.getMaxDelay().toMillis() - baseDelayMs
            );
        }
        
        // 根据块索引调整延迟（前几个块稍微快一点，后面稳定）
        long indexDelay = 0;
        if (chunkIndex <= 5) {
            indexDelay = Math.max(0, baseDelayMs - (chunkIndex * chatStreamConfig.getIndexDelayReduction()));
        }
        
        // 计算最终延迟
        long finalDelay = Math.max(baseDelayMs, baseDelayMs + contentDelay - indexDelay);
        finalDelay = Math.min(finalDelay, chatStreamConfig.getMaxDelay().toMillis());
        
        return Duration.ofMillis(finalDelay);
    }
    @Resource
    OpenAiChatModel localChatModel;

    @Resource
    DynamicChatDispatcher dispatcher;
    /**
     * 流式聊天接口 - 快速版本（无速率控制）
     * 提供给需要快速响应的场景使用
     */
    @SaIgnore
    @PostMapping(value = "/stream/local", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStreamFast(@RequestParam("modelName") String modelName, @RequestParam("memoryId") String memoryId,
                                @RequestParam("message") String message) {
        Flux<String> chatStream = dispatcher.streamChat(modelName,memoryId, message);
        return chatStream
            .subscribeOn(Schedulers.fromExecutor(streamingExecutor))
            .map(chunk -> chunk.replace("\n", "\\n")) // 编码换行符，防止SSE协议丢失
            .doOnNext(chunk -> log.info("发送聊天块: {}", chunk.length() > 100 ? chunk.substring(0, 100) + "..." : chunk))
            .doOnComplete(() -> log.info("流式聊天完成"))
            .doOnError(error -> log.error("流式聊天出错: {}", error.getMessage()));

       // System.out.println("localConsultantService.chat1(memoryId, message) = " + localConsultantService.chat1(memoryId, message));

    }


}
