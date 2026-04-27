package com.aezer0.initialization.service.ai;

import com.aezer0.initialization.dto.SharedKnowledgeBaseChatDTO;
import com.aezer0.initialization.service.KnowledgeBaseFileService;
import com.aezer0.initialization.service.ai.adapter.ConsultantService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingMatch;
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

    private static final int MAX_SEGMENT_CHARS = 1200;
    private static final int MAX_SUMMARY_SNIPPET_CHARS = 220;
    private static final String AI_META_START = "[[AI_META]]";
    private static final String AI_META_END = "[[/AI_META]]";

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

    @Autowired
    private KnowledgeBaseFileService knowledgeBaseFileService;

    private String systemPrompt;

    public void setCurrentUserId(String userId) {
    }

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
                log.error("读取系统提示词失败: {}", e.getMessage(), e);
                systemPrompt = """
                        你是一个智能知识库助手。
                        你必须优先基于知识库参考资料回答问题。
                        不能把未命中的内容说成来自知识库，也不能编造来源、文件名、数据或细节。
                        如果资料不足，要明确说明不足。
                        """;
            }
        }
    }

    public List<Content> retrieveForUser(String userId, String query) {
        return retrieveForUser(userId, query, null);
    }

    public List<Content> retrieveForUser(String userId, String query, String categoryFilter) {
        RetrievalContext retrievalContext = retrieveUserContext(userId, query, categoryFilter, KnowledgeAnswerMode.STRICT_KB);
        return retrievalContext.getMatches().stream()
                .map(match -> Content.from(match.embedded()))
                .collect(Collectors.toList());
    }

    public String buildEnhancedMessage(String userId, String message, String categoryFilter) {
        return buildEnhancedMessage(userId, message, categoryFilter, KnowledgeAnswerMode.STRICT_KB.getCode());
    }

    public String buildEnhancedMessage(String userId, String message, String categoryFilter, String answerModeCode) {
        try {
            initSystemPrompt();
            KnowledgeAnswerMode answerMode = KnowledgeAnswerMode.fromCode(answerModeCode);
            RetrievalContext retrievalContext = retrieveUserContext(userId, message, categoryFilter, answerMode);
            return systemPrompt + "\n\n" + buildUserMessage(message, retrievalContext);
        } catch (Exception e) {
            log.error("构建增强消息失败: {}", e.getMessage(), e);
            return systemPrompt + "\n\n用户问题：" + message;
        }
    }

    public Flux<String> chatWithUserKnowledgeBaseStream(String userId,
                                                        String memoryId,
                                                        String message,
                                                        String categoryFilter,
                                                        String answerModeCode) {
        try {
            initSystemPrompt();
            KnowledgeAnswerMode answerMode = KnowledgeAnswerMode.fromCode(answerModeCode);
            RetrievalContext retrievalContext = retrieveUserContext(userId, message, categoryFilter, answerMode);
            String enhancedMessage = systemPrompt + "\n\n" + buildUserMessage(message, retrievalContext);
            return appendMetaBlock(
                    resolveConsultantService().streamChat(memoryId, enhancedMessage),
                    buildMetaBlock(retrievalContext, categoryFilter)
            );
        } catch (Exception e) {
            log.error("个人知识库流式对话处理失败: {}", e.getMessage(), e);
            return Flux.error(new RuntimeException("个人知识库对话服务暂时不可用，请稍后再试"));
        }
    }

    public Flux<String> chatWithSharedKnowledgeBaseStream(SharedKnowledgeBaseChatDTO chatDTO, Long userId) {
        try {
            initSystemPrompt();
            KnowledgeAnswerMode answerMode = KnowledgeAnswerMode.fromCode(chatDTO.getAnswerMode());
            RetrievalContext retrievalContext = retrieveSharedContext(
                    chatDTO.getKnowledgeBaseId(),
                    userId,
                    chatDTO.getMessage(),
                    chatDTO.getCategoryFilter(),
                    answerMode
            );
            String enhancedMessage = systemPrompt + "\n\n" + buildUserMessage(chatDTO.getMessage(), retrievalContext);
            String memoryId = scopedSharedMemoryId(chatDTO, userId, answerMode);
            return appendMetaBlock(
                    resolveConsultantService().streamChat(memoryId, enhancedMessage),
                    buildMetaBlock(retrievalContext, chatDTO.getCategoryFilter())
            );
        } catch (Exception e) {
            log.error("共享知识库流式对话处理失败: {}", e.getMessage(), e);
            return Flux.error(new RuntimeException("共享知识库对话服务暂时不可用，请稍后再试"));
        }
    }

    private Flux<String> appendMetaBlock(Flux<String> answerStream, String metaBlock) {
        if (metaBlock == null || metaBlock.isBlank()) {
            return answerStream;
        }
        return answerStream.concatWithValues(metaBlock);
    }

    private String scopedSharedMemoryId(SharedKnowledgeBaseChatDTO chatDTO, Long userId, KnowledgeAnswerMode answerMode) {
        String category = chatDTO.getCategoryFilter() == null || chatDTO.getCategoryFilter().trim().isEmpty()
                ? "all"
                : chatDTO.getCategoryFilter().trim();
        return "shared_kb_" + chatDTO.getKnowledgeBaseId()
                + "_user_" + userId
                + "_mode_" + answerMode.getCode()
                + "_category_" + Integer.toHexString(category.hashCode());
    }

    private RetrievalContext retrieveUserContext(String userId,
                                                 String message,
                                                 String categoryFilter,
                                                 KnowledgeAnswerMode answerMode) {
        RetrievalPlan plan = routerService.plan(message, categoryFilter, false, answerMode);
        List<EmbeddingMatch<TextSegment>> matches;
        try {
            matches = userVectorService.searchForUser(
                    userId,
                    message,
                    plan.getMaxResults(),
                    plan.getMinScore(),
                    categoryFilter
            );
        } catch (Exception e) {
            log.warn("为用户 {} 检索个人知识库失败，降级为空上下文: {}", userId, e.getMessage());
            matches = List.of();
        }
        ContextBuildResult contextBuild = buildCompactContext(matches, plan, "以下是知识库中的相关参考资料（仅供参考）：");
        return RetrievalContext.builder()
                .plan(plan)
                .matches(matches)
                .context(contextBuild.getContext())
                .referenceLines(contextBuild.getReferenceLines())
                .answerMode(answerMode)
                .knowledgeBaseScope("个人知识库(userId=" + userId + ")")
                .categoryFilter(categoryFilter)
                .build();
    }

    private RetrievalContext retrieveSharedContext(Long knowledgeBaseId,
                                                   Long userId,
                                                   String message,
                                                   String categoryFilter,
                                                   KnowledgeAnswerMode answerMode) {
        RetrievalPlan plan = routerService.plan(message, categoryFilter, true, answerMode);
        List<EmbeddingMatch<TextSegment>> matches;
        try {
            matches = sharedVectorService.searchInSharedKnowledgeBase(
                    knowledgeBaseId,
                    message,
                    plan.getMaxResults(),
                    plan.getMinScore(),
                    categoryFilter
            );
            if (matches.isEmpty()) {
                int repaired = knowledgeBaseFileService.repairMissingVectors(knowledgeBaseId, userId);
                if (repaired > 0) {
                    matches = sharedVectorService.searchInSharedKnowledgeBase(
                            knowledgeBaseId,
                            message,
                            plan.getMaxResults(),
                            plan.getMinScore(),
                            categoryFilter
                    );
                }
            }
        } catch (Exception e) {
            log.warn("共享知识库 {} 检索失败，降级为空上下文: {}", knowledgeBaseId, e.getMessage());
            matches = List.of();
        }
        ContextBuildResult contextBuild = buildCompactContext(matches, plan, "共享知识库参考资料：");
        return RetrievalContext.builder()
                .plan(plan)
                .matches(matches)
                .context(contextBuild.getContext())
                .referenceLines(contextBuild.getReferenceLines())
                .answerMode(answerMode)
                .knowledgeBaseScope("共享知识库(id=" + knowledgeBaseId + ")")
                .categoryFilter(categoryFilter)
                .build();
    }

    private ContextBuildResult buildCompactContext(List<EmbeddingMatch<TextSegment>> matches,
                                                   RetrievalPlan plan,
                                                   String header) {
        if (matches == null || matches.isEmpty()) {
            return new ContextBuildResult("", List.of());
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
            return new ContextBuildResult("", List.copyOf(sources));
        }

        String finalContext = header + "\n" + context + "\n\n可参考来源：\n"
                + sources.stream().map(source -> "- " + source).collect(Collectors.joining("\n"))
                + "\n";
        return new ContextBuildResult(finalContext, List.copyOf(sources));
    }

    private void appendFileSummaries(StringBuilder context,
                                     Set<String> sources,
                                     List<EmbeddingMatch<TextSegment>> matches,
                                     RetrievalPlan plan) {
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

    private void appendEvidenceSections(StringBuilder context,
                                        Set<String> sources,
                                        List<EmbeddingMatch<TextSegment>> matches,
                                        RetrievalPlan plan) {
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
            int end = Math.max(0, remain - 3);
            context.append(section, 0, end).append("...");
        } else {
            context.append(section);
        }
        return true;
    }

    private String buildUserMessage(String originalMessage, RetrievalContext retrievalContext) {
        KnowledgeAnswerMode answerMode = retrievalContext.getAnswerMode() == null
                ? KnowledgeAnswerMode.STRICT_KB
                : retrievalContext.getAnswerMode();

        StringBuilder prompt = new StringBuilder();
        prompt.append("当前检索范围：").append(retrievalContext.getKnowledgeBaseScope()).append("\n");
        if (retrievalContext.getCategoryFilter() != null && !retrievalContext.getCategoryFilter().trim().isEmpty()) {
            prompt.append("当前分类限定：").append(retrievalContext.getCategoryFilter().trim()).append("\n");
        }
        prompt.append("回答模式：").append(answerMode.getLabel()).append("\n");
        prompt.append("边界策略：").append(answerMode.getBoundaryPolicy()).append("\n");
        prompt.append("执行要求：\n").append(buildExecutionRules(answerMode)).append("\n\n");

        if (retrievalContext.getContext() == null || retrievalContext.getContext().isBlank()) {
            prompt.append("当前没有命中的知识库参考资料。\n");
        } else {
            prompt.append(retrievalContext.getContext()).append("\n");
        }

        prompt.append("\n用户问题：").append(originalMessage);
        return prompt.toString();
    }

    private String buildExecutionRules(KnowledgeAnswerMode answerMode) {
        if (answerMode == KnowledgeAnswerMode.KB_HYBRID_REASONING) {
            return """
                    - 优先依据本次消息中“命中的知识库参考资料”作答；能从知识库确认的内容，先完整提炼为“知识库依据”。
                    - 如果设置了分类限定，只能把该分类命中的资料作为知识库依据，不能引用其他分类或个人知识库内容。
                    - 对知识库未命中或证据不足的部分，不要直接停止回答；继续使用通用知识和逻辑推理给出可执行分析、建议或方案。
                    - 当本次没有命中的知识库参考资料时，必须明确说明知识库没有命中依据，再进入“补充判断”；不能沿用历史对话里的知识库内容。
                    - 若用户要求分析、规划、改写、总结或给建议，必须产出有帮助的结论；除非完全没有问题相关信息，否则不要只回答“无法进行实质性分析”。
                    - 不能把补充判断写成“知识库已说明”；知识库内容与通用知识冲突时，以知识库资料为准，并指出冲突点。
                    - 不要编造不存在的文件名、来源编号、知识库原文、数据或代码。
                    - 不要自行输出“参考来源”小节，系统会自动附加来源清单。
                    """;
        }
        return """
                - 只依据本次消息中“命中的知识库参考资料”作答，不能使用历史对话、个人知识库、常识或猜测补全答案。
                - 如果设置了分类限定，只能依据该分类命中的资料；其他分类即使命中过往对话也不能引用。
                - 如果资料不足，明确说明“知识库中暂无相关资料”或“知识库中暂无足够资料支持该结论”。
                - 不要补全未命中的步骤、代码、数值、日期、训练天数或文件内容。
                - 不要把常识、猜测或经验写成知识库结论。
                - 不要编造文件名、来源编号、数据、代码或不存在的文档内容。
                - 不要自行输出“参考来源”小节，系统会自动附加来源清单。
                """;
    }

    private String buildMetaBlock(RetrievalContext retrievalContext, String categoryFilter) {
        RetrievalPlan plan = retrievalContext.getPlan();
        KnowledgeAnswerMode answerMode = retrievalContext.getAnswerMode() == null
                ? KnowledgeAnswerMode.STRICT_KB
                : retrievalContext.getAnswerMode();

        StringBuilder meta = new StringBuilder();
        meta.append("\n\n").append(AI_META_START).append("\n");
        meta.append("检索策略：").append(routeLabelFor(plan.getRouteType())).append("\n");
        meta.append("策略原因：").append(routeReasonLabelFor(plan.getReason())).append("\n");
        meta.append("回答模式：").append(answerMode.getLabel()).append("\n");
        meta.append("边界策略：").append(answerMode.getBoundaryPolicy()).append("\n");
        if (categoryFilter != null && !categoryFilter.trim().isEmpty()) {
            meta.append("分类限定：").append(categoryFilter.trim()).append("\n");
        }
        meta.append("参考来源：\n");
        List<String> referenceLines = retrievalContext.getReferenceLines();
        if (referenceLines == null || referenceLines.isEmpty()) {
            meta.append("- 未命中知识库文件\n");
        } else {
            referenceLines.forEach(line -> meta.append("- ").append(line).append("\n"));
        }
        meta.append(AI_META_END);
        return meta.toString();
    }

    private String routeLabelFor(RetrievalPlan.RouteType routeType) {
        return switch (routeType) {
            case SUMMARY_FIRST -> "摘要优先";
            case KEYWORD -> "关键词优先";
            case VECTOR -> "向量优先";
            case HYBRID -> "混合检索";
        };
    }

    private String routeReasonLabelFor(String reason) {
        return switch (reason == null ? "" : reason) {
            case "summary_intent" -> "问题偏向总结/概览，先收敛整体结论再补充证据";
            case "exact_or_file_intent" -> "问题更偏向文件名、来源、分类或精确定位";
            case "short_or_filtered_query" -> "问题较短或带分类限定，优先扩大精确命中";
            default -> "默认采用关键词与向量混合检索";
        };
    }

    private String sourceGroupKey(EmbeddingMatch<TextSegment> match) {
        Map<String, Object> metadata = extractMetadata(match.embedded());
        String sourceName = firstNonBlank(
                metadata.get("sourceName"),
                metadata.get("sourceNoteTitle"),
                metadata.get("originalFilename"),
                metadata.get("fileName"),
                metadata.get("title")
        );
        Object fileId = metadata.get("fileId");
        return (fileId == null ? "nofile" : fileId.toString())
                + "|"
                + (sourceName == null ? "unknown" : sourceName);
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
        int limit = plan.getRouteType() == RetrievalPlan.RouteType.SUMMARY_FIRST
                ? 280
                : Math.min(MAX_SEGMENT_CHARS, 420);
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
        String sourceName = firstNonBlank(
                metadata.get("sourceName"),
                metadata.get("sourceNoteTitle"),
                metadata.get("originalFilename"),
                metadata.get("fileName"),
                metadata.get("title")
        );
        if (sourceName == null) {
            sourceName = "未命名文档";
        }
        parts.add("[来源" + index + "] 文件名：" + sourceName);

        String category = firstNonBlank(metadata.get("category"), metadata.get("categoryName"));
        if (category != null) {
            parts.add("分类：" + category);
        }

        String sourceType = normalizeSourceType(firstNonBlank(metadata.get("sourceType")));
        if (sourceType != null) {
            parts.add("来源类型：" + sourceType);
        }

        return String.join(" | ", parts);
    }

    private String normalizeSourceType(String sourceType) {
        if (sourceType == null) {
            return null;
        }
        return switch (sourceType.trim().toLowerCase()) {
            case "shared" -> "共享知识库";
            case "personal" -> "个人知识库";
            default -> sourceType.trim();
        };
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

    private static final class ContextBuildResult {
        private final String context;
        private final List<String> referenceLines;

        private ContextBuildResult(String context, List<String> referenceLines) {
            this.context = context;
            this.referenceLines = referenceLines;
        }

        private String getContext() {
            return context;
        }

        private List<String> getReferenceLines() {
            return referenceLines;
        }
    }
}
