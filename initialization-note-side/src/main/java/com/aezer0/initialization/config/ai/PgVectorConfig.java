package com.aezer0.initialization.config.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Configuration
@Slf4j
public class PgVectorConfig {
    
    @Value("${spring.datasource.url}")
    private String pgVectorUrl;
    
    @Value("${spring.datasource.username}")
    private String pgVectorUsername;
    
    @Value("${spring.datasource.password}")
    private String pgVectorPassword;
    @Autowired
    private EmbeddingModel embeddingModel;
    /**
     * 创建PostgreSQL向量数据库存储
     */
    @Bean
    @Primary
    public PgVectorEmbeddingStore buildEmbeddingStore() {
        // 从 JDBC URL 中解析主机、端口和数据库名
        // 假设 URL 格式为 jdbc:postgresql://<host>:<port>/<database>
        String cleanUrl = pgVectorUrl.substring("jdbc:postgresql://".length());
        String[] hostAndPortDb = cleanUrl.split(":");
        String host = hostAndPortDb[0];
        String portAndDb = hostAndPortDb[1];
        String[] portDbParts = portAndDb.split("/");
        int port = Integer.parseInt(portDbParts[0]);
        String database = portDbParts[1];

        // 使用注入的配置来构建 PgVectorEmbeddingStore
        PgVectorEmbeddingStore store = PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(pgVectorUsername)
                .password(pgVectorPassword)
                .table("documents")
                .dimension(1024) // text-embedding-v3 的实际维度
                .build();

        return store;
    }
    
    /**
     * 创建向量数据库检索对象
     */
    @Bean("contentRetriever")
    @Primary
    public ContentRetriever contentRetriever(PgVectorEmbeddingStore pgVectorEmbeddingStore) {
        log.info("创建PostgreSQL向量数据库检索器...");
        
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(pgVectorEmbeddingStore)
                .minScore(0.5) // 最小相似度阈值
                .maxResults(5) // 最大返回结果数
                .embeddingModel(embeddingModel)
                .build();
    }

}