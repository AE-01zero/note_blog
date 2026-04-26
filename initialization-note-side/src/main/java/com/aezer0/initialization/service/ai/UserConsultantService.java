package com.aezer0.initialization.service.ai;

import com.aezer0.initialization.dto.SharedKnowledgeBaseChatDTO;
import com.aezer0.initialization.service.ai.adapter.ConsultantService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserConsultantService {

    private static final int MAX_CONTEXT_SEGMENTS = 8;
    private static final int MAX_SHARED_CONTEXT_SEGMENTS = 5;
    private static final int MAX_SEGMENT_CHARS = 1200;
    private static final int MAX_TOTAL_CONTEXT_CHARS = 6000;
    private static final int MAX_SUMMARY_SNIPPET_CHARS = 220;

    @Autowired
    private ApplicationContext applicationContext;

    private final Object consultantServiceLock = new Object();
    private volatile ConsultantService consultantService;
    private volatile ChatModel cachedChatModel;
    private volatile StreamingChatModel cachedStreamingChatModel;
    private volatile ChatMemoryProvider cachedChatMemoryProvider;

    @Autowired
    private SharedKnowledgeBaseVectorService sharedVectorService;

    @Autowired
    private UserVectorService userVectorService;

    @Autowired
    private KnowledgeRetrievalRouterService routerService;

    private String systemPrompt;

    private EmbeddingModel embeddingModel() {
        return applicationContext.getBean(EmbeddingModel.class);
    }

    public void setCurrentUserId(String userId) {}

    private ConsultantService resolveConsultantService() {
        ChatModel currentChatModel = applicationContext.getBean("openAiChatModel", ChatModel.class);
        StreamingChatModel currentStreamingChatModel = applicationContext.getBean("openAiStreamingChatModel", StreamingChatModel.class);
        ChatMemoryProvider currentChatMemoryProvider = applicationContext.getBean("chatMemoryProvider", ChatMemoryProvider.class);

        ConsultantService cached = this.consultantService;
        if (cached != null
                && cachedChatModel == currentChatModel
                && cachedStreamingChatModel == currentStreamingChatModel
                && cachedChatMemoryProvider == currentChatMemoryProvider) {
            return cached;
        }

        synchronized (consultantServiceLock) {
            if (this.consultantService != null
                    && cachedChatModel == currentChatModel
                    && cachedStreamingChatModel == currentStreamingChatModel
                    && cachedChatMemoryProvider == currentChatMemoryProvider) {
                return this.consultantService;
            }

            ConsultantService rebuilt = AiServices.builder(ConsultantService.class)
                    .chatModel(currentChatModel)
                    .streamingChatModel(currentStreamingChatModel)
                    .chatMemoryProvider(currentChatMemoryProvider)
                    .build();

            this.consultantService = rebuilt;
            this.cachedChatModel = currentChatModel;
            this.cachedStreamingChatModel = currentStreamingChatModel;
            this.cachedChatMemoryProvider = currentChatMemoryProvider;
            return rebuilt;
        }
    }

    public Flux<String> streamChat(String memoryId, String message) {
        return resolveConsultantService().streamChat(memoryId, message);
    }

    public void initSystemPrompt() {
        if (systemPrompt == null) {
            try {
                ClassPathResource resource = new ClassPathResource("system.txt");
                systemPrompt = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("读取系统提示词失败: {}", e.getMessage());
                systemPrompt = "你是一个智能助手，可以帮助用户回答问题和处理任务,参考资料仅供参考，回答的内容不要和参考的一模一样，要有自己的回答的特色！";
            }
        }
    }

    public List<Content> retrieveForUser(String userId, String query) {
        return retrieveForUser(userId, query, null);
    }

    public List<Content> retrieveForUser(String userId, String query, String categoryFilter) {
        log.info("为用户 {} 从个人知识库检索内容，查询: {}, 分类: {}", userId, query, categoryFilter);
        RetrievalContext retrievalContext = retrieveUserContext(userId, query, categoryFilter);
        return retrievalContext.getMatches().stream().map(match -> Content.from(match.embedded())).collect(Collectors.toList());
    }

    public String buildEnhancedMessage(String userId, String message, String categoryFilter) {
        try {
            initSystemPrompt();
            RetrievalContext retrievalContext = retrieveUserContext(userId, message, categoryFilter);
            String enhancedMessage = buildUserMessage(message, retrievalContext.getContext(), retrievalContext.getPlan());
            return systemPrompt + "\n\n" + enhancedMessage;
        } catch (Exception e) {
            log.error("构建增强消息失败: {}", e.getMessage(), e);
            return systemPrompt + "\n\n用户问题：" + message;
        }
    }

    public Flux<String> chatWithSharedKnowledgeBaseStream(SharedKnowledgeBaseChatDTO chatDTO, Long userId) {
        try {
            String enhancedMessage = buildSharedKnowledgeBaseMessage(chatDTO.getKnowledgeBaseId(), chatDTO.getMessage(), chatDTO.getCategoryFilter());
            String memoryId = chatDTO.getMemoryId() != null ? chatDTO.getMemoryId() : "shared_kb_" + chatDTO.getKnowledgeBaseId() + "_" + userId;
            return resolveConsultantService().streamChat(memoryId, enhancedMessage);
        } catch (Exception e) {
            log.error("共享知识库流式对话处理失败: {}", e.getMessage(), e);
            return Flux.error(new RuntimeException("共享知识库对话服务暂时不可用，请稍后再试"));
        }
    }

    private RetrievalContext retrieveUserContext(String userId, String message, String categoryFilter) {
        RetrievalPlan plan = routerService.plan(message, categoryFilter, false);
        List<EmbeddingMatch<TextSegment>> matches;
        try {
            matches = userVectorService.searchForUser(userId, message, plan.getMaxResults(), plan.getMinScore(), categoryFilter);
        } catch (Exception e) {
            log.warn("为用户 {} 从个人知识库检索内容失败（降级为无RAG模式）: {}", userId, e.getMessage());
            matches = List.of();
        }
        String context = buildCompactContext(matches, plan, "以下是知识库中的相关参考资料（仅供参考）：");
        return RetrievalContext.builder().plan(plan).matches(matches).context(context).build();
    }

    private String buildSharedKnowledgeBaseMessage(Long knowledgeBaseId, String message, String categoryFilter) {
        try {
            initSystemPrompt();
            RetrievalContext retrievalContext = retrieveSharedContext(knowledgeBaseId, message, categoryFilter);
            return systemPrompt + "\n\n" + buildUserMessage(message, retrievalContext.getContext(), retrievalContext.getPlan());
        } catch (Exception e) {
            log.error("构建共享知识库增强消息失败: {}", e.getMessage(), e);
            return systemPrompt + "\n\n用户问题：" + message;
        }
    }

    private RetrievalContext retrieveSharedContext(Long knowledgeBaseId, String message, String categoryFilter) {
        RetrievalPlan plan = routerService.plan(message, categoryFilter, true);
        List<EmbeddingMatch<TextSegment>> matches;
        try {
            matches = sharedVectorService.searchInSharedKnowledgeBase(knowledgeBaseId, message, plan.getMaxResults(), plan.getMinScore(), categoryFilter);
        } catch (Exception e) {
            log.warn("共享知识库 {} 检索失败（降级为无RAG模式）: {}", knowledgeBaseId, e.getMessage());
            matches = List.of();
        }
        String context = buildCompactContext(matches, plan, "共享知识库参考资料：");
        return RetrievalContext.builder().plan(plan).matches(matches).context(context).build();
    }

    private String buildCompactContext(List<EmbeddingMatch<TextSegment>> matches, RetrievalPlan plan, String header) {
        if (matches == null || matches.isEmpty()) {
            return "";
        }

        List<EmbeddingMatch<TextSegment>> orderedMatches = matches.stream()
                .sorted(Comparator.comparingDouble(EmbeddingMatch<TextSegment>::score).reversed())
                .collect(Collectors.toList());

        StringBuilder context = new StringBuilder();
        Set<String> sources = new LinkedHashSet<>();

        if (plan.isPreferFileSummary()) {
            appendFileSummaries(context, sources, orderedMatches, plan);
        }
        appendEvidenceSections(context, sources, orderedMatches, plan);

        if (context.isEmpty()) {
            return "";
        }
        return header + "\n" + context + "\n\n可参考来源：\n" + String.join("\n", sources) + "\n\n";
    }

    private void appendFileSummaries(StringBuilder context, Set<String> sources, List<EmbeddingMatch<TextSegment>> matches, RetrievalPlan plan) {
        Map<String, List<EmbeddingMatch<TextSegment>>> grouped = matches.stream()
                .collect(Collectors.groupingBy(this::sourceGroupKey, LinkedHashMap::new, Collectors.toList()));
        int summaryCount = 0;
        for (List<EmbeddingMatch<TextSegment>> group : grouped.values()) {
            if (summaryCount >= 3 || context.length() >= plan.getMaxContextChars()) {
                break;
            }
            EmbeddingMatch<TextSegment> first = group.get(0);
            Map<String, Object> metadata = extractMetadata(first.embedded());
            String sourceLine = buildSourceLine(metadata, summaryCount + 1);
            sources.add(sourceLine);
            String summary = buildFileSummary(group);
            appendSection(context, sourceLine + "\n摘要：" + summary, plan.getMaxContextChars(), true);
            summaryCount++;
        }
    }

    private void appendEvidenceSections(StringBuilder context, Set<String> sources, List<EmbeddingMatch<TextSegment>> matches, RetrievalPlan plan) {
        int segmentCount = 0;
        for (EmbeddingMatch<TextSegment> match : matches) {
            if (segmentCount >= plan.getMaxContextSegments() || context.length() >= plan.getMaxContextChars()) {
                break;
            }
            TextSegment segment = match.embedded();
            String text = trimContextText(segment.text());
            if (text.isEmpty()) {
                continue;
            }
            Map<String, Object> metadata = extractMetadata(segment);
            String sourceLine = buildSourceLine(metadata, segmentCount + 1);
            sources.add(sourceLine);
            String section = sourceLine + "\n" + compressSegment(text, plan);
            if (appendSection(context, section, plan.getMaxContextChars(), !plan.isPreferFileSummary())) {
                segmentCount++;
            }
        }
    }

    private boolean appendSection(StringBuilder context, String section, int maxContextChars, boolean separated) {
        int remain = maxContextChars - context.length();
        if (remain <= 0) {
            return false;
        }
        if (context.length() > 0) {
            String separator = separated ? "\n\n---\n\n" : "\n\n";
            context.append(separator);
            remain = maxContextChars - context.length();
            if (remain <= 0) {
                return false;
            }
        }
        if (section.length() > remain) {
            context.append(section, 0, Math.max(0, remain - 3)).append("...");
        } else {
            context.append(section);
        }
        return true;
    }

    private String buildUserMessage(String originalMessage, String context, RetrievalPlan plan) {
        if (context.isEmpty()) {
            return "问题：" + originalMessage;
        }
        String routeLabel = switch (plan.getRouteType()) {
            case SUMMARY_FIRST -> "摘要优先";
            case KEYWORD -> "关键词优先";
            case VECTOR -> "向量优先";
            default -> "混合检索";
        };
        String routeHint = switch (plan.getRouteType()) {
            case SUMMARY_FIRST -> "回答时优先给出整体梳理，再补充关键证据，并在结尾附上“参考来源”小节。";
            case KEYWORD -> "回答时优先基于精确命中的资料作答，并在结尾附上“参考来源”小节。";
            default -> "回答时请尽量结合参考资料，并在结尾附上“参考来源”小节。";
        };
        String routeMeta = "检索策略：" + routeLabel + "\n策略原因：" + plan.getReason() + "\n\n";
        return routeMeta + context + routeHint + "\n\n用户问题：" + originalMessage;
    }

    private String sourceGroupKey(EmbeddingMatch<TextSegment> match) {
        Map<String, Object> metadata = extractMetadata(match.embedded());
        String sourceName = firstNonBlank(metadata.get("sourceName"), metadata.get("title"), metadata.get("fileName"));
        Object fileId = metadata.get("fileId");
        return (fileId == null ? "nofile" : fileId.toString()) + "|" + (sourceName == null ? "unknown" : sourceName);
    }

    private String buildFileSummary(List<EmbeddingMatch<TextSegment>> matches) {
        StringBuilder summary = new StringBuilder();
        int count = 0;
        for (EmbeddingMatch<TextSegment> match : matches) {
            String text = trimContextText(match.embedded().text());
            if (text.isEmpty()) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append("；");
            }
            summary.append(compressText(text, MAX_SUMMARY_SNIPPET_CHARS));
            count++;
            if (count >= 2 || summary.length() >= MAX_SUMMARY_SNIPPET_CHARS * 2) {
                break;
            }
        }
        return summary.toString();
    }

    private String compressSegment(String text, RetrievalPlan plan) {
        int limit = plan.getRouteType() == RetrievalPlan.RouteType.SUMMARY_FIRST ? 280 : Math.min(MAX_SEGMENT_CHARS, 420);
        return compressText(text, limit);
    }

    private String compressText(String text, int limit) {
        String normalized = trimContextText(text);
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }

    private Map<String, Object> extractMetadata(TextSegment segment) {
        try {
            return segment.metadata() == null ? Map.of() : segment.metadata().toMap();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String buildSourceLine(Map<String, Object> metadata, int index) {
        List<String> parts = new ArrayList<>();
        String sourceName = firstNonBlank(metadata.get("sourceName"), metadata.get("title"), metadata.get("fileName"), "片段" + index);
        parts.add("[来源" + index + "] " + sourceName);

        String category = firstNonBlank(metadata.get("category"), metadata.get("categoryName"));
        if (category != null) {
            parts.add("分类：" + category);
        }

        String sourceType = firstNonBlank(metadata.get("sourceType"));
        if (sourceType != null) {
            parts.add("类型：" + sourceType);
        }

        Object fileId = metadata.get("fileId");
        if (fileId != null) {
            parts.add("文件ID：" + fileId);
        }

        return String.join(" | ", parts);
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                return text;
            }
        }
        return null;
    }

    private String trimContextText(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        if (normalized.length() <= MAX_SEGMENT_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_SEGMENT_CHARS) + "...";
    }
}
