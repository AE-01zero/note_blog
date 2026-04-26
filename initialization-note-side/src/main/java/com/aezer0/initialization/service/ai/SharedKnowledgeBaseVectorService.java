package com.aezer0.initialization.service.ai;

import com.aezer0.initialization.config.ai.PgVectorConnectionPool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 共享知识库向量Service
 */
@Service
@Slf4j
public class SharedKnowledgeBaseVectorService {

    @Autowired
    private PgVectorConnectionPool connectionPool;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private EmbeddingModel embeddingModel() {
        return applicationContext.getBean(EmbeddingModel.class);
    }

    public void addDocumentsToSharedKnowledgeBase(Long knowledgeBaseId, List<Document> documents, Long fileId) {
        try (Connection connection = connectionPool.getConnection()) {
            String sql = "INSERT INTO documents (text, metadata, embedding, file_id, knowledge_base_id, knowledge_base_type) VALUES (?, ?::jsonb, CAST(? AS vector), ?, ?, ?)";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                for (Document doc : documents) {
                    Embedding embedding = embeddingModel().embed(doc.text()).content();

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("knowledgeBaseId", knowledgeBaseId);
                    metadata.put("knowledgeBaseType", 2);
                    metadata.put("fileId", fileId);
                    metadata.put("contentLength", doc.text().length());
                    metadata.put("uploadTime", System.currentTimeMillis());
                    metadata.put("sourceType", "shared");

                    if (doc.metadata() != null) {
                        try {
                            metadata.putAll(doc.metadata().toMap());
                        } catch (Exception e) {
                            metadata.put("originalMetadata", doc.metadata().toString());
                        }
                    }

                    List<Float> vectorList = embedding.vectorAsList();
                    String vectorString = "[" + vectorList.stream()
                            .map(String::valueOf)
                            .reduce((a, b) -> a + "," + b)
                            .orElse("") + "]";

                    stmt.setString(1, doc.text());
                    stmt.setString(2, toJsonString(metadata));
                    stmt.setString(3, vectorString);
                    if (fileId != null) {
                        stmt.setLong(4, fileId);
                    } else {
                        stmt.setNull(4, Types.BIGINT);
                    }
                    stmt.setLong(5, knowledgeBaseId);
                    stmt.setInt(6, 2);
                    stmt.addBatch();
                }

                int[] results = stmt.executeBatch();
                log.info("成功为共享知识库 {} 添加 {} 个文档，文件ID: {}", knowledgeBaseId, results.length, fileId);
            }
        } catch (SQLException e) {
            log.error("为共享知识库 {} 添加文档失败: {}", knowledgeBaseId, e.getMessage(), e);
            throw new RuntimeException("添加文档到共享知识库失败", e);
        }
    }

    public List<EmbeddingMatch<TextSegment>> searchInSharedKnowledgeBase(Long knowledgeBaseId, String query, int maxResults, double minScore) {
        return searchInSharedKnowledgeBase(knowledgeBaseId, query, maxResults, minScore, null);
    }

    public List<EmbeddingMatch<TextSegment>> searchInSharedKnowledgeBase(Long knowledgeBaseId, String query, int maxResults, double minScore, String categoryFilter) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String normalizedQuery = query.trim();
        List<EmbeddingMatch<TextSegment>> keywordMatches = searchByKeyword(knowledgeBaseId, normalizedQuery, maxResults, categoryFilter);
        try {
            Embedding queryEmbedding = embeddingModel().embed(normalizedQuery).content();
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();
            List<EmbeddingMatch<TextSegment>> vectorMatches = searchInSharedKnowledgeBase(knowledgeBaseId, request, categoryFilter);
            return mergeAndRerank(vectorMatches, keywordMatches, normalizedQuery, maxResults);
        } catch (Exception e) {
            log.warn("共享知识库 {} 生成查询向量失败，降级为关键词检索: {}", knowledgeBaseId, e.getMessage());
            return keywordMatches.stream().limit(maxResults).collect(Collectors.toList());
        }
    }

    public List<EmbeddingMatch<TextSegment>> searchInSharedKnowledgeBase(Long knowledgeBaseId, EmbeddingSearchRequest request) {
        return searchInSharedKnowledgeBase(knowledgeBaseId, request, null);
    }

    public List<EmbeddingMatch<TextSegment>> searchInSharedKnowledgeBase(Long knowledgeBaseId, EmbeddingSearchRequest request, String categoryFilter) {
        List<EmbeddingMatch<TextSegment>> results = new ArrayList<>();
        boolean hasCategory = categoryFilter != null && !categoryFilter.trim().isEmpty();

        String sql = hasCategory
                ? """
                SELECT d.embedding_id, d.text, d.metadata, d.embedding <=> CAST(? AS vector) as distance
                FROM documents d LEFT JOIN t_file_info f ON d.file_id = f.id
                WHERE d.knowledge_base_id = ? AND d.knowledge_base_type = 2 AND f.category = ?
                ORDER BY distance LIMIT ?
                """
                : """
                SELECT embedding_id, text, metadata, embedding <=> CAST(? AS vector) as distance
                FROM documents
                WHERE knowledge_base_id = ? AND knowledge_base_type = 2
                ORDER BY distance LIMIT ?
                """;

        try (Connection connection = connectionPool.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            List<Float> vectorList = request.queryEmbedding().vectorAsList();
            String vectorString = "[" + vectorList.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("") + "]";

            stmt.setString(1, vectorString);
            stmt.setLong(2, knowledgeBaseId);
            if (hasCategory) {
                stmt.setString(3, categoryFilter.trim());
                stmt.setInt(4, request.maxResults());
            } else {
                stmt.setInt(3, request.maxResults());
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    double similarity = 1.0 - rs.getDouble("distance");
                    if (similarity < request.minScore()) {
                        continue;
                    }
                    TextSegment textSegment = TextSegment.from(rs.getString("text"), Metadata.from(parseJsonString(rs.getString("metadata"))));
                    results.add(new EmbeddingMatch<>(similarity, rs.getString("embedding_id"), null, textSegment));
                }
            }
        } catch (SQLException e) {
            log.error("在共享知识库 {} 中搜索失败: {}", knowledgeBaseId, e.getMessage(), e);
            throw new RuntimeException("共享知识库搜索失败", e);
        }

        log.debug("在共享知识库 {} 中搜索到 {} 个相关文档，分类过滤: {}", knowledgeBaseId, results.size(), categoryFilter);
        return results;
    }

    private List<EmbeddingMatch<TextSegment>> searchByKeyword(Long knowledgeBaseId, String query, int maxResults, String categoryFilter) {
        List<EmbeddingMatch<TextSegment>> results = new ArrayList<>();
        boolean hasCategory = categoryFilter != null && !categoryFilter.trim().isEmpty();
        String sql = hasCategory
                ? "SELECT d.embedding_id, d.text, d.metadata FROM documents d LEFT JOIN t_file_info f ON d.file_id = f.id WHERE d.knowledge_base_id = ? AND d.knowledge_base_type = 2 AND f.category = ? AND d.text ILIKE ? LIMIT ?"
                : "SELECT embedding_id, text, metadata FROM documents WHERE knowledge_base_id = ? AND knowledge_base_type = 2 AND text ILIKE ? LIMIT ?";

        try (Connection connection = connectionPool.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            String pattern = "%" + query.trim() + "%";
            stmt.setLong(1, knowledgeBaseId);
            if (hasCategory) {
                stmt.setString(2, categoryFilter.trim());
                stmt.setString(3, pattern);
                stmt.setInt(4, maxResults);
            } else {
                stmt.setString(2, pattern);
                stmt.setInt(3, maxResults);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TextSegment segment = TextSegment.from(rs.getString("text"), Metadata.from(parseJsonString(rs.getString("metadata"))));
                    results.add(new EmbeddingMatch<>(0.55, rs.getString("embedding_id"), null, segment));
                }
            }
        } catch (SQLException e) {
            log.warn("共享知识库关键词检索失败: {}", e.getMessage());
        }
        return results;
    }

    private List<EmbeddingMatch<TextSegment>> mergeAndRerank(List<EmbeddingMatch<TextSegment>> vectorMatches, List<EmbeddingMatch<TextSegment>> keywordMatches, String query, int maxResults) {
        Map<String, EmbeddingMatch<TextSegment>> merged = new LinkedHashMap<>();
        for (EmbeddingMatch<TextSegment> match : vectorMatches) {
            merged.put(match.embeddingId(), match);
        }
        for (EmbeddingMatch<TextSegment> match : keywordMatches) {
            merged.merge(match.embeddingId(), match, (existing, incoming) -> existing.score() >= incoming.score() ? existing : incoming);
        }
        return merged.values().stream()
                .sorted((a, b) -> Double.compare(scoreForRerank(b, query), scoreForRerank(a, query)))
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    private double scoreForRerank(EmbeddingMatch<TextSegment> match, String query) {
        double score = match.score();
        Map<String, Object> metadata = match.embedded() == null ? Map.of() : extractMetadata(match.embedded());
        String normalizedQuery = safeLower(query).trim();
        String text = match.embedded() == null ? "" : safeLower(match.embedded().text());
        String sourceName = safeLower(firstNonBlank(metadata.get("sourceName"), metadata.get("title"), metadata.get("fileName"), metadata.get("originalFilename")));
        String category = safeLower(firstNonBlank(metadata.get("category"), metadata.get("categoryName")));

        if (!sourceName.isEmpty()) {
            score += 0.08;
        }
        if (!category.isEmpty()) {
            score += 0.05;
        }
        if (!text.isEmpty() && text.length() <= 280) {
            score += 0.03;
        }
        if (!sourceName.isEmpty() && text.contains(sourceName)) {
            score += 0.04;
        }
        if (!normalizedQuery.isEmpty()) {
            if (!sourceName.isEmpty() && sourceName.contains(normalizedQuery)) {
                score += 0.24;
            }
            if (!category.isEmpty() && category.contains(normalizedQuery)) {
                score += 0.16;
            }
            if (!text.isEmpty() && text.contains(normalizedQuery)) {
                score += 0.09;
            }
            for (String term : splitQueryTerms(normalizedQuery)) {
                if (term.length() < 2) {
                    continue;
                }
                if (!sourceName.isEmpty() && sourceName.contains(term)) {
                    score += 0.08;
                }
                if (!category.isEmpty() && category.contains(term)) {
                    score += 0.05;
                }
                if (!text.isEmpty() && text.contains(term)) {
                    score += 0.03;
                }
            }
        }
        return score;
    }

    private Map<String, Object> extractMetadata(TextSegment segment) {
        try {
            return segment.metadata() == null ? Map.of() : segment.metadata().toMap();
        } catch (Exception e) {
            return Map.of();
        }
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

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private List<String> splitQueryTerms(String query) {
        String normalized = safeLower(query).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(normalized.split("\\s+"))
                .filter(term -> !term.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    public int deleteSharedKnowledgeBaseFileVectors(Long knowledgeBaseId, Long fileId) {
        try (Connection connection = connectionPool.getConnection()) {
            String sql = "DELETE FROM documents WHERE knowledge_base_id = ? AND knowledge_base_type = 2 AND file_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setLong(1, knowledgeBaseId);
                stmt.setLong(2, fileId);
                int deletedCount = stmt.executeUpdate();
                log.info("成功删除共享知识库 {} 文件 {} 对应的 {} 个向量记录", knowledgeBaseId, fileId, deletedCount);
                return deletedCount;
            }
        } catch (SQLException e) {
            log.error("删除共享知识库 {} 文件 {} 向量数据失败: {}", knowledgeBaseId, fileId, e.getMessage(), e);
            throw new RuntimeException("删除共享知识库向量数据失败", e);
        }
    }

    public int deleteSharedKnowledgeBaseVectors(Long knowledgeBaseId) {
        try (Connection connection = connectionPool.getConnection()) {
            String sql = "DELETE FROM documents WHERE knowledge_base_id = ? AND knowledge_base_type = 2";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setLong(1, knowledgeBaseId);
                int deletedCount = stmt.executeUpdate();
                log.info("成功删除共享知识库 {} 的 {} 个向量记录", knowledgeBaseId, deletedCount);
                return deletedCount;
            }
        } catch (SQLException e) {
            log.error("删除共享知识库 {} 向量数据失败: {}", knowledgeBaseId, e.getMessage(), e);
            throw new RuntimeException("删除共享知识库向量数据失败", e);
        }
    }

    public Map<String, Object> getSharedKnowledgeBaseVectorStats(Long knowledgeBaseId) {
        Map<String, Object> stats = new HashMap<>();

        try (Connection connection = connectionPool.getConnection()) {
            String sql = """
                    SELECT
                        COUNT(*) as total_documents,
                        COUNT(DISTINCT file_id) as file_count,
                        AVG(LENGTH(text)) as avg_text_length
                    FROM documents
                    WHERE knowledge_base_id = ? AND knowledge_base_type = 2
                    """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setLong(1, knowledgeBaseId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    stats.put("totalDocuments", rs.getInt("total_documents"));
                    stats.put("fileCount", rs.getInt("file_count"));
                    stats.put("avgTextLength", rs.getDouble("avg_text_length"));
                }
            }
        } catch (SQLException e) {
            log.error("获取共享知识库 {} 向量统计失败: {}", knowledgeBaseId, e.getMessage(), e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    public int deleteDocumentsByFileIdAndKnowledgeBaseId(Long fileId, Long knowledgeBaseId) {
        return deleteSharedKnowledgeBaseFileVectors(knowledgeBaseId, fileId);
    }

    public Map<String, Object> getSharedKnowledgeBaseStats(Long knowledgeBaseId) {
        Map<String, Object> stats = getSharedKnowledgeBaseVectorStats(knowledgeBaseId);
        stats.put("success", !stats.containsKey("error"));
        stats.put("knowledgeBaseId", knowledgeBaseId);
        stats.put("knowledgeBaseType", 2);
        return stats;
    }

    private String toJsonString(Map<String, Object> map) {
        try {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"").append(entry.getKey()).append("\":");
                Object value = entry.getValue();
                if (value instanceof String) {
                    json.append("\"").append(value).append("\"");
                } else {
                    json.append(value.toString());
                }
                first = false;
            }
            json.append("}");
            return json.toString();
        } catch (Exception e) {
            log.warn("转换JSON失败，使用简单格式: {}", e.getMessage());
            throw new RuntimeException();
        }
    }

    private Map<String, Object> parseJsonString(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析共享知识库 metadata JSON失败: {}", e.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("raw", json);
            return fallback;
        }
    }
}
