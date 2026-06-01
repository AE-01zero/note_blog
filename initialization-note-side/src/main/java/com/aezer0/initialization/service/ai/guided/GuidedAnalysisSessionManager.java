package com.aezer0.initialization.service.ai.guided;

import com.aezer0.initialization.config.ai.AiAnalysisConfig;
import com.aezer0.initialization.service.ai.guided.model.AnalysisSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class GuidedAnalysisSessionManager {

    private final Map<String, AnalysisSession> sessions = new ConcurrentHashMap<>();
    private static final String REDIS_PREFIX = "guided:session:";

    @Autowired
    private AiAnalysisConfig config;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void save(AnalysisSession session) {
        session.setLastActiveAt(Instant.now());
        sessions.put(session.getSessionId(), session);
        persistToRedis(session);
    }

    public AnalysisSession get(String sessionId) {
        AnalysisSession session = sessions.get(sessionId);
        if (session == null && redisTemplate != null) {
            session = loadFromRedis(sessionId);
            if (session != null) {
                sessions.put(sessionId, session);
            }
        }
        return session;
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
        if (redisTemplate != null) {
            redisTemplate.delete(REDIS_PREFIX + sessionId);
        }
    }

    public long countByUser(String userId) {
        return sessions.values().stream()
                .filter(s -> userId.equals(s.getUserId()))
                .filter(s -> s.getStatus() != AnalysisSession.SessionStatus.COMPLETED
                        && s.getStatus() != AnalysisSession.SessionStatus.TERMINATED)
                .count();
    }

    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredSessions() {
        int ttlMinutes = config.getGuided().getSessionTtlMinutes();
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(ttlMinutes));
        sessions.entrySet().removeIf(entry -> {
            AnalysisSession session = entry.getValue();
            if (session.getLastActiveAt().isBefore(cutoff)) {
                log.info("清理过期分析会话: {}", entry.getKey());
                if (redisTemplate != null) {
                    redisTemplate.delete(REDIS_PREFIX + entry.getKey());
                }
                return true;
            }
            return false;
        });
    }

    private void persistToRedis(AnalysisSession session) {
        if (redisTemplate == null) return;
        try {
            String json = objectMapper.writeValueAsString(session);
            Duration ttl = Duration.ofMinutes(config.getGuided().getSessionTtlMinutes());
            redisTemplate.opsForValue().set(REDIS_PREFIX + session.getSessionId(), json, ttl);
        } catch (Exception e) {
            log.warn("Redis持久化会话失败: {}", session.getSessionId(), e);
        }
    }

    private AnalysisSession loadFromRedis(String sessionId) {
        if (redisTemplate == null) return null;
        try {
            String json = redisTemplate.opsForValue().get(REDIS_PREFIX + sessionId);
            if (json != null) {
                return objectMapper.readValue(json, AnalysisSession.class);
            }
        } catch (Exception e) {
            log.warn("Redis加载会话失败: {}", sessionId, e);
        }
        return null;
    }
}
