package com.aezer0.initialization.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 笔记AI优化异步任务服务
 */
@Service
@Slf4j
public class NoteOptimizeTaskService {

    private static final int CHUNK_SIZE = 2400;
    private static final int MAX_CHUNK_COUNT = 32;
    private static final int SOFT_CHUNK_COUNT = 24;

    public static class TaskResult {
        public String status;
        public String result;
        public String error;
        public Integer progress;
        public Integer totalChunks;
        public Integer completedChunks;
        public LocalDateTime updateTime;
        public String stage;
        public String stageLabel;

        public TaskResult(String status) {
            this.status = status;
            this.progress = 0;
            this.totalChunks = 0;
            this.completedChunks = 0;
            this.updateTime = LocalDateTime.now();
            this.stage = "pending";
            this.stageLabel = "等待开始";
        }
    }

    private final Map<String, TaskResult> taskMap = new ConcurrentHashMap<>();

    @Autowired
    @Qualifier("openAiChatModel")
    private OpenAiChatModel openAiChatModel;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    public String submitTask(Long noteId, Long userId, String content) {
        String taskId = UUID.randomUUID().toString();
        taskMap.put(taskId, new TaskResult("pending"));
        taskExecutor.execute(() -> doOptimizeTask(taskId, noteId, userId, content));
        return taskId;
    }

    public TaskResult getTask(String taskId) {
        return taskMap.get(taskId);
    }

    public void removeTask(String taskId) {
        taskMap.remove(taskId);
    }

    public void doOptimizeTask(String taskId, Long noteId, Long userId, String content) {
        TaskResult task = taskMap.get(taskId);
        if (task == null) {
            return;
        }
        task.status = "running";
        updateStage(task, "outline", "正在整理全文结构", 5);
        try {
            String optimized = optimizeContent(content, noteId, userId, task);
            task.result = optimized;
            task.status = "done";
            updateStage(task, "done", "优化完成", 100);
            log.info("笔记 {} 异步优化完成，任务ID: {}", noteId, taskId);
        } catch (Exception e) {
            task.status = "error";
            task.error = e.getMessage();
            task.updateTime = LocalDateTime.now();
            task.stage = "error";
            task.stageLabel = "优化失败";
            log.error("笔记 {} 异步优化失败，任务ID: {}, 错误: {}", noteId, taskId, e.getMessage());
        }
    }

    private String optimizeContent(String content, Long noteId, Long userId, TaskResult task) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("笔记内容为空，无法优化");
        }

        String outline = buildOutline(content, noteId, userId, task);
        List<String> chunks = splitIntoMarkdownBlocks(content, CHUNK_SIZE, MAX_CHUNK_COUNT);
        task.totalChunks = chunks.size();
        task.completedChunks = 0;
        task.progress = 10;
        task.updateTime = LocalDateTime.now();

        List<String> optimizedChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            updateStage(task, "chunk", "正在优化第 " + (i + 1) + "/" + chunks.size() + " 段", calculateChunkProgress(i, chunks.size()));
            String optimized = optimizeChunk(chunks.get(i), outline, noteId, userId, i);
            if (StringUtils.hasText(optimized)) {
                optimizedChunks.add(optimized.trim());
            }
            task.completedChunks = i + 1;
            task.updateTime = LocalDateTime.now();
        }

        String merged = String.join("\n\n", optimizedChunks).trim();
        if (!StringUtils.hasText(merged)) {
            throw new IllegalStateException("优化结果为空");
        }

        updateStage(task, "merge", "正在统一全文格式", 95);
        return finalizeOptimizedMarkdown(content, outline, merged, noteId, userId);
    }

    private String buildOutline(String content, Long noteId, Long userId, TaskResult task) {
        updateStage(task, "outline", "正在提取章节大纲", 5);
        String prompt = "请基于以下 Markdown 笔记提取一个简洁的大纲和格式整理原则。" +
                "要求：\n1. 只输出章节结构、主题顺序、标题层级建议、列表/代码块整理原则\n2. 不改写原文内容\n3. 控制在 12 条以内\n4. 直接输出可供后续整理使用的要点\n\n原文内容：\n" + abbreviateForOutline(content);
        return callModel(prompt, "你是一个 Markdown 笔记整理助手。请先提炼结构，不要扩写内容。");
    }

    private String optimizeChunk(String chunk, String outline, Long noteId, Long userId, int index) {
        String userPrompt = "请根据给定的大纲与整理原则，优化下面这段 Markdown 笔记的格式和结构，使其更清晰、规范、易读。" +
                "要求：\n1. 保持事实和原意不变，只做格式与结构优化\n2. 与大纲保持一致\n3. 保留代码块、列表、引用等 Markdown 语义\n4. 不要添加解释\n\n全文大纲与整理原则：\n" + outline +
                "\n\n当前片段：\n" + chunk;
        return callModel(userPrompt, "你是一个 Markdown 笔记格式优化助手。只优化格式，不改变信息含义。");
    }

    private String finalizeOptimizedMarkdown(String originalContent, String outline, String mergedContent, Long noteId, Long userId) {
        String userPrompt = "请对以下已经分段优化后的 Markdown 全文做最后一次统一整理。" +
                "要求：\n1. 保持内容不变，只统一标题层级、段落边界、列表样式、代码块包裹和前后衔接\n2. 删除重复标题或重复空行\n3. 不添加解释，直接返回最终 Markdown\n\n参考大纲：\n" + outline +
                "\n\n优化后全文：\n" + mergedContent;
        return callModel(userPrompt, "你是一个 Markdown 收口整理助手。请统一全文风格，但不要增删核心内容。");
    }

    private String callModel(String userPrompt, String systemPrompt) {
        return openAiChatModel.chat(
                List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userPrompt)
                )
        ).aiMessage().text();
    }

    public static List<String> splitIntoChunks(String content, int chunkSize, int maxChunkCount) {
        return splitIntoMarkdownBlocks(content, chunkSize, maxChunkCount);
    }

    public static List<String> splitIntoMarkdownBlocks(String content, int chunkSize, int maxChunkCount) {
        List<String> blocks = extractMarkdownBlocks(content);
        List<String> chunks = mergeBlocks(blocks, chunkSize);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("笔记内容为空，无法优化");
        }
        if (chunks.size() > maxChunkCount) {
            throw new IllegalArgumentException("笔记内容过长，结构块数量过多，请先按章节拆分后再试");
        }
        return chunks;
    }

    private static List<String> extractMarkdownBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        String normalized = content.replace("\r\n", "\n").trim();
        if (!StringUtils.hasText(normalized)) {
            return blocks;
        }

        StringBuilder current = new StringBuilder();
        String[] lines = normalized.split("\n");
        boolean inCodeBlock = false;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine;
            String trimmed = line.trim();
            boolean isFence = trimmed.startsWith("```") || trimmed.startsWith("~~~");
            boolean startsNewBlock = !inCodeBlock && current.length() > 0 && isStructuralBoundary(trimmed);

            if (startsNewBlock) {
                flushChunk(blocks, current);
            }

            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);

            if (isFence) {
                inCodeBlock = !inCodeBlock;
            }

            if (!inCodeBlock && trimmed.isEmpty()) {
                flushChunk(blocks, current);
            }
        }
        flushChunk(blocks, current);
        return blocks;
    }

    private static boolean isStructuralBoundary(String trimmed) {
        if (!StringUtils.hasText(trimmed)) {
            return false;
        }
        return trimmed.matches("^#{1,6}\\s+.*")
                || trimmed.matches("^[-*+]\\s+.*")
                || trimmed.matches("^>\\s+.*")
                || trimmed.matches("^\\d+\\.\\s+.*")
                || trimmed.matches("^\\|.*\\|$")
                || trimmed.matches("^---+$")
                || trimmed.matches("^===+$");
    }

    private static List<String> mergeBlocks(List<String> blocks, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String block : blocks) {
            String normalized = block == null ? "" : block.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (normalized.length() > chunkSize) {
                flushChunk(chunks, current);
                splitOversizedBlock(chunks, normalized, chunkSize);
                continue;
            }
            if (current.length() > 0 && current.length() + 2 + normalized.length() > chunkSize) {
                flushChunk(chunks, current);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(normalized);
        }
        flushChunk(chunks, current);

        if (chunks.size() > SOFT_CHUNK_COUNT) {
            return compactChunks(chunks, chunkSize);
        }
        return chunks;
    }

    private static void splitOversizedBlock(List<String> chunks, String block, int chunkSize) {
        String[] paragraphs = block.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String normalized = paragraph.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (normalized.length() > chunkSize) {
                flushChunk(chunks, current);
                splitByLine(chunks, normalized, chunkSize);
                continue;
            }
            if (current.length() > 0 && current.length() + 2 + normalized.length() > chunkSize) {
                flushChunk(chunks, current);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(normalized);
        }
        flushChunk(chunks, current);
    }

    private static void splitByLine(List<String> chunks, String block, int chunkSize) {
        String[] lines = block.split("\n");
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            String normalized = line.trim();
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (normalized.length() > chunkSize) {
                flushChunk(chunks, current);
                int start = 0;
                while (start < normalized.length()) {
                    int end = Math.min(start + chunkSize, normalized.length());
                    chunks.add(normalized.substring(start, end).trim());
                    start = end;
                }
                continue;
            }
            if (current.length() > 0 && current.length() + 1 + normalized.length() > chunkSize) {
                flushChunk(chunks, current);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(normalized);
        }
        flushChunk(chunks, current);
    }

    private static List<String> compactChunks(List<String> chunks, int chunkSize) {
        List<String> compacted = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String chunk : chunks) {
            if (!StringUtils.hasText(chunk)) {
                continue;
            }
            if (current.length() > 0 && current.length() + 2 + chunk.length() > chunkSize * 1.25) {
                flushChunk(compacted, current);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(chunk);
        }
        flushChunk(compacted, current);
        return compacted;
    }

    private static void flushChunk(List<String> chunks, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        chunks.add(current.toString().trim());
        current.setLength(0);
    }

    private static String abbreviateForOutline(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.length() <= 12000) {
            return normalized;
        }
        return normalized.substring(0, 12000) + "\n\n[以下内容因篇幅过长已截断，请仅基于已提供部分提炼结构。]";
    }

    private static int calculateChunkProgress(int index, int totalChunks) {
        if (totalChunks <= 0) {
            return 10;
        }
        return Math.min(90, 15 + (int) (((index + 1) * 75.0) / totalChunks));
    }

    private static void updateStage(TaskResult task, String stage, String stageLabel, int progress) {
        task.stage = stage;
        task.stageLabel = stageLabel;
        task.progress = progress;
        task.updateTime = LocalDateTime.now();
    }
}
