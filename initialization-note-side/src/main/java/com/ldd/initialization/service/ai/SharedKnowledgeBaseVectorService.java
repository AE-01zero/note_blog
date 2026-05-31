package com.ldd.initialization.service.ai;

import com.ldd.initialization.config.ai.PgVectorConnectionPool;
import dev.langchain4j.data.document.Document;
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
import java.util.List;
import java.util.Map;

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

    private EmbeddingModel embeddingModel() {
        return applicationContext.getBean(EmbeddingModel.class);
    }

    /**
     * 为共享知识库添加文档
     */
    public void addDocumentsToSharedKnowledgeBase(Long knowledgeBaseId, List<Document> documents, Long fileId) {
        try (Connection connection = connectionPool.getConnection()) {
            String sql = "INSERT INTO documents (text, metadata, embedding, file_id, knowledge_base_id, knowledge_base_type) VALUES (?, ?::jsonb, CAST(? AS vector), ?, ?, ?)";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                for (Document doc : documents) {
                    // 生成embedding
                    Embedding embedding = embeddingModel().embed(doc.text()).content();

                    // 准备元数据
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("knowledgeBaseId", knowledgeBaseId);
                    metadata.put("knowledgeBaseType", 2); // 2表示共享知识库
                    metadata.put("fileId", fileId);
                    metadata.put("contentLength", doc.text().length());
                    metadata.put("uploadTime", System.currentTimeMillis());

                    // 如果文档有原始元数据，合并它们
                    if (doc.metadata() != null) {
                        try {
                            metadata.putAll(doc.metadata().toMap());
                        } catch (Exception e) {
                            metadata.put("originalMetadata", doc.metadata().toString());
                        }
                    }

                    // 将embedding转换为PostgreSQL向量格式的字符串
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
                        stmt.setNull(4, java.sql.Types.BIGINT);
                    }
                    stmt.setLong(5, knowledgeBaseId);
                    stmt.setInt(6, 2); // 共享知识库类型

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

    /**
     * 从共享知识库搜索相关文档
     */
    public List<EmbeddingMatch<TextSegment>> searchInSharedKnowledgeBase(Long knowledgeBaseId, EmbeddingSearchRequest request) {
        return searchInSharedKnowledgeBase(knowledgeBaseId, request, null);
    }

    /**
     * 从共享知识库搜索相关文档（支持分类过滤）
     */
    public List<EmbeddingMatch<TextSegment>> searchInSharedKnowledgeBase(Long knowledgeBaseId, EmbeddingSearchRequest request, String categoryFilter) {
        List<EmbeddingMatch<TextSegment>> results = new ArrayList<>();
        boolean hasCategory = categoryFilter != null && !categoryFilter.trim().isEmpty();

        String sql = hasCategory
            ? """
              SELECT d.embedding_id, d.text, d.metadata, f.category, d.embedding <=> CAST(? AS vector) as distance
              FROM documents d LEFT JOIN t_file_info f ON d.file_id = f.id
              WHERE d.knowledge_base_id = ? AND d.knowledge_base_type = 2 AND f.category = ?
              ORDER BY distance LIMIT ?
              """
            : """
              SELECT d.embedding_id, d.text, d.metadata, f.category, d.embedding <=> CAST(? AS vector) as distance
              FROM documents d LEFT JOIN t_file_info f ON d.file_id = f.id
              WHERE d.knowledge_base_id = ? AND d.knowledge_base_type = 2
              ORDER BY distance LIMIT ?
              """;

        try (Connection connection = connectionPool.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            List<Float> vectorList = request.queryEmbedding().vectorAsList();
            String vectorString = "[" + vectorList.stream()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("") + "]";

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
                    String text = rs.getString("text");
                    String metadataJson = rs.getString("metadata");
                    Map<String, Object> metadataMap = parseJsonString(metadataJson);
                    String category = rs.getString("category");
                    if (category != null) {
                        metadataMap.put("category", category);
                    }
                    dev.langchain4j.data.document.Metadata metadata = dev.langchain4j.data.document.Metadata.from(metadataMap);
                    TextSegment textSegment = TextSegment.from(text, metadata);
                    results.add(new EmbeddingMatch<>(similarity, rs.getString("embedding_id"), null, textSegment));
                }
            }

            log.debug("在共享知识库 {} 中搜索到 {} 个相关文档，分类过滤: {}", knowledgeBaseId, results.size(), categoryFilter);
        } catch (SQLException e) {
            log.error("在共享知识库 {} 中搜索失败: {}", knowledgeBaseId, e.getMessage(), e);
            throw new RuntimeException("共享知识库搜索失败", e);
        }

        return results;
    }

    /**
     * 删除共享知识库的文件向量数据
     */
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

    /**
     * 删除整个共享知识库的向量数据
     */
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

    /**
     * 获取共享知识库向量统计信息
     */
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

    /**
     * 根据文件ID和知识库ID删除向量数据（API一致性方法）
     *
     * @param fileId          文件ID
     * @param knowledgeBaseId 共享知识库ID
     * @return 删除的记录数
     */
    public int deleteDocumentsByFileIdAndKnowledgeBaseId(Long fileId, Long knowledgeBaseId) {
        return deleteSharedKnowledgeBaseFileVectors(knowledgeBaseId, fileId);
    }

    /**
     * 获取共享知识库统计信息（API一致性方法）
     *
     * @param knowledgeBaseId 共享知识库ID
     * @return 统计信息
     */
    public Map<String, Object> getSharedKnowledgeBaseStats(Long knowledgeBaseId) {
        Map<String, Object> stats = getSharedKnowledgeBaseVectorStats(knowledgeBaseId);
        stats.put("success", !stats.containsKey("error"));
        stats.put("knowledgeBaseId", knowledgeBaseId);
        stats.put("knowledgeBaseType", 2);
        return stats;
    }

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private Map<String, Object> parseJsonString(String json) {
        Map<String, Object> map = new HashMap<>();
        if (json != null && !json.trim().isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
                map.putAll(parsed);
            } catch (Exception e) {
                log.warn("解析metadata JSON失败: {}", e.getMessage());
                map.put("raw", json);
            }
        }
        return map;
    }

    /**
     * 将Map对象转换为JSON字符串
     */
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
} 
