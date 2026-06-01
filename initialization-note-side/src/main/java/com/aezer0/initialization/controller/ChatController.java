package com.aezer0.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.aezer0.initialization.dto.ChatRequestDTO;
import com.aezer0.initialization.service.ai.UserConsultantService;
import com.aezer0.initialization.service.ai.adapter.DynamicChatDispatcher;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.time.Duration;
import java.util.Locale;

@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatController {

    @Autowired
    private UserConsultantService userConsultantService;

    @Autowired
    @Qualifier("streamingExecutor")
    private ThreadPoolTaskExecutor streamingExecutor;

    /**
     * 流式聊天接口 — 结构化SSE事件输出
     * 事件类型: phase, knowledge_result, thinking_start, ai_chunk, ai_error, done
     */
    @SaCheckLogin
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> chatStream(@Valid @RequestBody ChatRequestDTO request) {
        String currentUserId = StpUtil.getLoginId().toString();
        request.setUserId(currentUserId);
        log.info("用户 {} 发起流式聊天请求，会话ID: {}, mode={}", request.getUserId(), request.getMemoryId(), request.getMode());

        try {
            String combinedMemoryId = request.getUserId() + "_" + request.getMemoryId();
            String mode = request.getMode() != null ? request.getMode() : "relaxed";

            Flux<ServerSentEvent<String>> stream = userConsultantService.streamChatStructured(
                    combinedMemoryId, request.getUserId(), request.getMessage(),
                    request.getCategoryFilter(), mode)
                .subscribeOn(Schedulers.fromExecutor(streamingExecutor))
                .timeout(Duration.ofSeconds(120), Flux.just(
                    "{\"type\":\"ai_error\",\"message\":\"AI响应超时，请稍后再试\"}",
                    "{\"type\":\"done\",\"status\":\"timeout\"}"
                ))
                .doOnNext(json -> log.debug("发送SSE事件: {}", json.length() > 120 ? json.substring(0, 120) + "..." : json))
                .map(this::toSseEvent)
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
                        return Flux.just(
                            "{\"type\":\"ai_error\",\"message\":\"模型服务连接超时，请稍后再试\"}",
                            "{\"type\":\"done\",\"status\":\"error\"}"
                        ).map(this::toSseEvent);
                    }
                    log.error("流式聊天处理失败: {}", throwable.getMessage(), throwable);
                    return Flux.just(
                        "{\"type\":\"ai_error\",\"message\":\"聊天服务暂时不可用\"}",
                        "{\"type\":\"done\",\"status\":\"error\"}"
                    ).map(this::toSseEvent);
                });
            return withNoBufferHeaders(stream);
        } catch (Exception e) {
            if (isRemoteModelError(e)) {
                log.warn("流式聊天处理失败（模型服务不可达）: {}", e.getMessage());
                return withNoBufferHeaders(Flux.just(
                    "{\"type\":\"ai_error\",\"message\":\"模型服务连接超时，请稍后再试\"}",
                    "{\"type\":\"done\",\"status\":\"error\"}"
                ).map(this::toSseEvent));
            }
            log.error("流式聊天处理失败: {}", e.getMessage(), e);
            return withNoBufferHeaders(Flux.just(
                "{\"type\":\"ai_error\",\"message\":\"聊天服务暂时不可用\"}",
                "{\"type\":\"done\",\"status\":\"error\"}"
            ).map(this::toSseEvent));
        }
    }

    private ServerSentEvent<String> toSseEvent(String json) {
        return ServerSentEvent.builder(json).build();
    }

    private ResponseEntity<Flux<ServerSentEvent<String>>> withNoBufferHeaders(Flux<ServerSentEvent<String>> source) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(source);
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
