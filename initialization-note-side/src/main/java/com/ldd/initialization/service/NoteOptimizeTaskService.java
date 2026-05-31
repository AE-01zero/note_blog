package com.ldd.initialization.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 笔记AI优化异步任务服务
 * 任务状态存储在内存中，服务重启后任务状态会丢失
 */
@Service
@Slf4j
public class NoteOptimizeTaskService {

    // 任务状态：pending / running / done / error
    public static class TaskResult {
        public String status;   // pending, running, done, error
        public String result;   // 优化后内容
        public String error;    // 错误信息

        public TaskResult(String status) {
            this.status = status;
        }
    }

    private final Map<String, TaskResult> taskMap = new ConcurrentHashMap<>();

    @Autowired
    @Qualifier("openAiChatModel")
    private ChatModel openAiChatModel;

    /**
     * 提交异步优化任务，返回任务ID
     */
    public String submitTask(Long noteId, Long userId, String content) {
        String taskId = UUID.randomUUID().toString();
        taskMap.put(taskId, new TaskResult("pending"));
        doOptimizeAsync(taskId, noteId, userId, content);
        return taskId;
    }

    /**
     * 查询任务结果
     */
    public TaskResult getTask(String taskId) {
        return taskMap.get(taskId);
    }

    /**
     * 删除任务（前端获取结果后清理）
     */
    public void removeTask(String taskId) {
        taskMap.remove(taskId);
    }

    @Async("taskExecutor")
    public void doOptimizeAsync(String taskId, Long noteId, Long userId, String content) {
        TaskResult task = taskMap.get(taskId);
        task.status = "running";
        try {
            String optimized = optimizeContent(content, noteId, userId);
            task.result = optimized;
            task.status = "done";
            log.info("笔记 {} 异步优化完成，任务ID: {}", noteId, taskId);
        } catch (Exception e) {
            task.status = "error";
            task.error = e.getMessage();
            log.error("笔记 {} 异步优化失败，任务ID: {}, 错误: {}", noteId, taskId, e.getMessage());
        }
    }

    private String optimizeContent(String content, Long noteId, Long userId) {
        final int CHUNK_SIZE = 3000;
        if (content.length() <= CHUNK_SIZE) {
            return doOptimize(content, "format_opt_" + noteId + "_" + userId);
        }
        java.util.List<String> chunks = splitIntoChunks(content, CHUNK_SIZE);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            String optimized = doOptimize(chunks.get(i), "format_opt_" + noteId + "_" + userId + "_" + i);
            result.append(optimized);
            if (i < chunks.size() - 1) {
                result.append("\n\n");
            }
        }
        return result.toString();
    }

    private String doOptimize(String content, String memoryId) {
        String systemPrompt = "你是小助手!";
        String userPrompt = "请帮我优化以下Markdown笔记的格式和结构，使其更加清晰、规范、易读。" +
                "要求：\n1. 保持原有内容不变，只优化格式\n2. 合理使用标题层级\n3. 优化列表和代码块格式\n" +
                "4. 确保段落间距合理\n5. 直接返回优化后的Markdown内容，不要添加任何说明。\n\n原文内容：\n" + content;
        return openAiChatModel.chat(
                List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userPrompt)
                )
        ).aiMessage().text();
    }

    private java.util.List<String> splitIntoChunks(String content, int chunkSize) {
        java.util.List<String> chunks = new java.util.ArrayList<>();
        String[] paragraphs = content.split("(?=\\n#{1,6} )");
        StringBuilder current = new StringBuilder();
        for (String para : paragraphs) {
            if (para.length() > chunkSize) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current = new StringBuilder();
                }
                int start = 0;
                while (start < para.length()) {
                    int end = Math.min(start + chunkSize, para.length());
                    if (end < para.length()) {
                        int newline = para.lastIndexOf('\n', end);
                        if (newline > start) end = newline + 1;
                    }
                    chunks.add(para.substring(start, end).trim());
                    start = end;
                }
            } else if (current.length() + para.length() > chunkSize && current.length() > 0) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
                current.append(para);
            } else {
                current.append(para);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }
}
