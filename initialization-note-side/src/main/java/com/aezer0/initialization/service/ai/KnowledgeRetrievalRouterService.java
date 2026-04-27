package com.aezer0.initialization.service.ai;

import org.springframework.stereotype.Service;

@Service
public class KnowledgeRetrievalRouterService {

    public RetrievalPlan plan(String query, String categoryFilter, boolean sharedKnowledgeBase) {
        return plan(query, categoryFilter, sharedKnowledgeBase, KnowledgeAnswerMode.STRICT_KB);
    }

    public RetrievalPlan plan(String query,
                              String categoryFilter,
                              boolean sharedKnowledgeBase,
                              KnowledgeAnswerMode answerMode) {
        String normalizedQuery = query == null ? "" : query.trim();
        boolean hasCategory = categoryFilter != null && !categoryFilter.trim().isEmpty();
        boolean shortQuery = normalizedQuery.length() <= 12;
        boolean explicitFileIntent = containsAny(normalizedQuery,
                "文件", "文档", "文章", "资料", "笔记", ".pdf", ".doc", ".docx", ".md", ".txt");
        boolean summaryIntent = containsAny(normalizedQuery,
                "总结", "概览", "整体", "简介", "是什么", "梳理", "汇总", "有哪些");
        boolean exactIntent = containsAny(normalizedQuery,
                "叫", "名称", "标题", "文件名", "分类", "出处", "来源");

        RetrievalPlan.RouteType routeType;
        boolean preferFileSummary = false;
        String reason;
        int maxResults;
        double minScore;
        int maxContextSegments;
        int maxContextChars;

        if (summaryIntent) {
            routeType = RetrievalPlan.RouteType.SUMMARY_FIRST;
            preferFileSummary = true;
            reason = "summary_intent";
            maxResults = sharedKnowledgeBase ? 10 : 12;
            minScore = 0.35;
            maxContextSegments = 4;
            maxContextChars = 3200;
        } else if (exactIntent || shortQuery || hasCategory || explicitFileIntent) {
            routeType = RetrievalPlan.RouteType.HYBRID;
            preferFileSummary = explicitFileIntent;
            reason = exactIntent || explicitFileIntent ? "exact_or_file_intent" : "short_or_filtered_query";
            maxResults = sharedKnowledgeBase ? 8 : 10;
            minScore = 0.4;
            maxContextSegments = 5;
            maxContextChars = 3600;
        } else {
            routeType = RetrievalPlan.RouteType.HYBRID;
            reason = "default_hybrid";
            maxResults = sharedKnowledgeBase ? 10 : 12;
            minScore = 0.45;
            maxContextSegments = sharedKnowledgeBase ? 5 : 6;
            maxContextChars = 4200;
        }

        if (answerMode != null && answerMode.isAllowGeneralReasoning()) {
            maxResults += sharedKnowledgeBase ? 1 : 2;
            maxContextSegments += 1;
            maxContextChars += 600;
            minScore = Math.max(0.32, minScore - 0.03);
        }

        return RetrievalPlan.builder()
                .routeType(routeType)
                .reason(reason)
                .maxResults(maxResults)
                .minScore(minScore)
                .maxContextSegments(maxContextSegments)
                .maxContextChars(maxContextChars)
                .preferFileSummary(preferFileSummary)
                .build();
    }

    private boolean containsAny(String query, String... tokens) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        for (String token : tokens) {
            if (query.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
