package com.aezer0.initialization.config.ai;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * PgVector数据库连接池配置
 * 统一管理向量数据库连接，避免重复创建连接，提高性能
 */
@Configuration
@Slf4j
public class PgVectorConnectionPool {

    @Value("${spring.datasource.url}")
    private String pgVectorUrl;

    @Value("${spring.datasource.username}")
    private String pgVectorUsername;

    @Value("${spring.datasource.password}")
    private String pgVectorPassword;

    private HikariDataSource dataSource;

    /**
     * 创建PgVector专用的数据源连接池
     */
    @Bean(name = "pgVectorDataSource")
    @Primary
    public DataSource createPgVectorDataSource() {
        HikariConfig config = new HikariConfig();
        
        // 基本连接配置
        config.setJdbcUrl(pgVectorUrl);
        config.setUsername(pgVectorUsername);
        config.setPassword(pgVectorPassword);
        config.setDriverClassName("org.postgresql.Driver");
        
        // 连接池配置
        config.setMaximumPoolSize(5);              // 最大连接数
        config.setMinimumIdle(2);                   // 最小空闲连接数
        config.setConnectionTimeout(30000);          // 连接超时时间（30秒）
        config.setIdleTimeout(600000);              // 空闲连接超时时间（10分钟）
        config.setMaxLifetime(1800000);             // 连接最大生命周期（30分钟）
        config.setLeakDetectionThreshold(60000);    // 连接泄漏检测阈值（60秒）
        
        // 连接池名称
        config.setPoolName("PgVectorConnectionPool");
        
        // PostgreSQL特定配置
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        
        // 连接验证配置
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);
        
        this.dataSource = new HikariDataSource(config);
        
        log.info("PgVector连接池初始化完成 - URL: {}, 最大连接数: {}, 最小空闲连接数: {}", 
                pgVectorUrl, config.getMaximumPoolSize(), config.getMinimumIdle());
        
        return this.dataSource;
    }

    /**
     * 获取PgVector数据库连接
     * 统一的连接获取方法，替代原来的 DriverManager.getConnection
     * 
     * @return 数据库连接
     * @throws SQLException 获取连接失败时抛出
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("PgVector连接池未初始化");
        }
        
        Connection connection = dataSource.getConnection();
        log.debug("从连接池获取连接 - 活跃连接数: {}, 空闲连接数: {}", 
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections());
        
        return connection;
    }

    /**
     * 获取连接池状态信息
     * 
     * @return 连接池状态信息
     */
    public String getPoolStatus() {
        if (dataSource == null) {
            return "连接池未初始化";
        }
        
        return String.format("连接池状态 - 活跃连接: %d, 空闲连接: %d, 等待连接: %d, 总连接数: %d",
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection(),
                dataSource.getHikariPoolMXBean().getTotalConnections());
    }

    /**
     * 测试连接池是否正常工作
     * 
     * @return 是否正常
     */
    public boolean testConnection() {
        try (Connection connection = getConnection()) {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            log.error("连接池测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 初始化后的处理
     */
    @PostConstruct
    public void init() {
        log.info("PgVector连接池管理器初始化完成");
    }

    /**
     * 销毁时关闭连接池
     */
    @PreDestroy
    public void destroy() {
        if (dataSource != null && !dataSource.isClosed()) {
            log.info("正在关闭PgVector连接池...");
            dataSource.close();
            log.info("PgVector连接池已关闭");
        }
    }
} 