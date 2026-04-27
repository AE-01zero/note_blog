package com.aezer0.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.aezer0.initialization.config.ChatStreamConfig;
import com.aezer0.initialization.dto.ChatRequestDTO;
import com.aezer0.initialization.service.ai.UserConsultantService;
import com.aezer0.initialization.service.ai.adapter.DynamicChatDispatcher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

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

    @Resource
    private DynamicChatDispatcher dispatcher;

    @PostConstruct
    public void init() {
        chatStreamConfig.validate();
        log.info("流式聊天速率控制配置已加载: {}", chatStreamConfig);
    }

    @SaCheckLogin
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequestDTO request) {
        String currentUserId = StpUtil.getLoginId().toString();
        request.setUserId(currentUserId);
        log.info("用户 {} 发起流式聊天请求，会话ID: {}", request.getUserId(), request.getMemoryId());

        try {
            String combinedMemoryId = request.getUserId() + "_" + request.getMemoryId();
            return userConsultantService.chatWithUserKnowledgeBaseStream(
                            request.getUserId(),
                            combinedMemoryId,
                            request.getMessage(),
                            request.getCategoryFilter(),
                            request.getAnswerMode()
                    )
                    .subscribeOn(Schedulers.fromExecutor(streamingExecutor))
                    .timeout(Duration.ofSeconds(60), Flux.just("抱歉，AI 响应超时，请检查模型配置后重试。"))
                    .map(chunk -> chunk.replace("\n", "\\n"))
                    .transform(this::applyRateControl)
                    .doOnNext(chunk -> log.debug("发送聊天块: {}", chunk.length() > 100 ? chunk.substring(0, 100) + "..." : chunk))
                    .doOnComplete(() -> log.info("用户 {} 的流式聊天已完成", request.getUserId()))
                    .doOnError(error -> {
                        if (isRemoteModelError(error)) {
                            log.warn("用户 {} 的流式聊天失败（模型服务不可达）: {}", request.getUserId(), error.getMessage());
                        } else {
                            log.error("用户 {} 的流式聊天失败: {}", request.getUserId(), error.getMessage(), error);
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
                    .switchIfEmpty(Flux.just("抱歉，AI 模型未返回任何响应，请检查模型配置是否正确。"));
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

    private Flux<String> applyRateControl(Flux<String> source) {
        if (!chatStreamConfig.isEnableRateControl()) {
            return source;
        }

        AtomicInteger chunkCounter = new AtomicInteger(0);
        Flux<String> processedFlux = source.delayElements(chatStreamConfig.getBaseDelay(), Schedulers.parallel());

        if (chatStreamConfig.isEnableAdaptiveDelay()) {
            processedFlux = processedFlux.concatMap(chunk -> {
                int chunkIndex = chunkCounter.incrementAndGet();
                Duration adaptiveDelay = calculateAdaptiveDelay(chunk, chunkIndex);
                if (chatStreamConfig.isEnableLogging()) {
                    log.debug("块 {} 长度: {}, 延迟: {}ms", chunkIndex, chunk.length(), adaptiveDelay.toMillis());
                }
                return Flux.just(chunk).delayElements(adaptiveDelay, Schedulers.parallel());
            });
        }

        if (chatStreamConfig.isEnableSmoothTransmission()) {
            processedFlux = processedFlux
                    .window(chatStreamConfig.getRateLimitWindow())
                    .concatMap(window -> window.concatMap(chunk ->
                            Flux.just(chunk).delayElements(chatStreamConfig.getWindowDelay(), Schedulers.parallel())
                    ));
        }

        return processedFlux.onBackpressureBuffer(chatStreamConfig.getBackpressureBufferSize());
    }

    private Duration calculateAdaptiveDelay(String chunk, int chunkIndex) {
        long baseDelayMs = chatStreamConfig.getBaseDelay().toMillis();
        int contentLength = chunk.length();
        long contentDelay = 0;

        if (contentLength > chatStreamConfig.getChunkSizeThreshold()) {
            contentDelay = Math.min(
                    (long) (contentLength * chatStreamConfig.getContentDelayFactor()),
                    chatStreamConfig.getMaxDelay().toMillis() - baseDelayMs
            );
        }

        long indexDelay = 0;
        if (chunkIndex <= 5) {
            indexDelay = Math.max(0, baseDelayMs - (chunkIndex * chatStreamConfig.getIndexDelayReduction()));
        }

        long finalDelay = Math.max(baseDelayMs, baseDelayMs + contentDelay - indexDelay);
        finalDelay = Math.min(finalDelay, chatStreamConfig.getMaxDelay().toMillis());
        return Duration.ofMillis(finalDelay);
    }

    @SaIgnore
    @PostMapping(value = "/stream/local", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStreamFast(@RequestParam("modelName") String modelName,
                                       @RequestParam("memoryId") String memoryId,
                                       @RequestParam("message") String message) {
        return dispatcher.streamChat(modelName, memoryId, message)
                .subscribeOn(Schedulers.fromExecutor(streamingExecutor))
                .map(chunk -> chunk.replace("\n", "\\n"))
                .doOnNext(chunk -> log.info("发送聊天块: {}", chunk.length() > 100 ? chunk.substring(0, 100) + "..." : chunk))
                .doOnComplete(() -> log.info("流式聊天完成"))
                .doOnError(error -> log.error("流式聊天失败: {}", error.getMessage(), error));
    }
}
