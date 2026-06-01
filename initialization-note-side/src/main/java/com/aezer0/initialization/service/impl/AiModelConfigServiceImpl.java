package com.aezer0.initialization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aezer0.initialization.config.exception.BizException;
import com.aezer0.initialization.domain.AiModelConfig;
import com.aezer0.initialization.dto.AiModelConfigUpdateDTO;
import com.aezer0.initialization.mapper.AiModelConfigMapper;
import com.aezer0.initialization.service.AiModelConfigService;
import com.aezer0.initialization.service.ai.protocol.AnthropicProtocolAdapter;
import com.aezer0.initialization.service.ai.protocol.ChatProtocolAdapter;
import com.aezer0.initialization.service.ai.protocol.EmbeddingProtocolAdapter;
import com.aezer0.initialization.service.ai.protocol.GeminiEmbeddingProtocolAdapter;
import com.aezer0.initialization.service.ai.protocol.GeminiProtocolAdapter;
import com.aezer0.initialization.service.ai.protocol.OpenAiEmbeddingProtocolAdapter;
import com.aezer0.initialization.service.ai.protocol.OpenAiProtocolAdapter;
import com.aezer0.initialization.vo.AiModelReloadResultVO;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AiModelConfigServiceImpl extends ServiceImpl<AiModelConfigMapper, AiModelConfig>
        implements AiModelConfigService {

    private static final String TYPE_CHAT = "chat";
    private static final String TYPE_STREAMING = "streaming";
    private static final String TYPE_EMBEDDING = "embedding";
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @Autowired
    private ApplicationContext applicationContext;

    private final Map<String, String> protocolByType = new ConcurrentHashMap<>();
    private final List<ChatProtocolAdapter> chatProtocolAdapters = List.of(
            new OpenAiProtocolAdapter(),
            new AnthropicProtocolAdapter(),
            new GeminiProtocolAdapter()
    );
    private final List<EmbeddingProtocolAdapter> embeddingProtocolAdapters = List.of(
            new OpenAiEmbeddingProtocolAdapter(),
            new GeminiEmbeddingProtocolAdapter()
    );

    @Override
    public List<AiModelConfig> listAll() {
        return this.list();
    }

    @Override
    public AiModelConfig getByType(String type) {
        return this.getOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getModelType, type));
    }

    @Override
    @Transactional
    public AiModelReloadResultVO saveConfig(String type, AiModelConfigUpdateDTO dto) {
        AiModelConfig oldConfig = getByType(type);
        if (oldConfig == null) {
            throw new BizException("Model config not found: " + type);
        }

        String normalizedBaseUrl = normalizeBaseUrl(dto.getBaseUrl());
        String effectiveApiKey = resolveEffectiveApiKey(dto, oldConfig);
        Integer effectiveMaxSegmentsPerBatch = dto.getMaxSegmentsPerBatch() != null
                ? dto.getMaxSegmentsPerBatch()
                : oldConfig.getMaxSegmentsPerBatch();

        AiModelReloadResultVO validationResult = checkModelAvailability(
                type,
                normalizedBaseUrl,
                effectiveApiKey,
                dto.getModelName(),
                effectiveMaxSegmentsPerBatch
        );

        if (validationResult.getStatus() != STATUS_OK) {
            return validationResult;
        }
        if (StringUtils.hasText(validationResult.getProtocol())) {
            protocolByType.put(type, validationResult.getProtocol());
        }

        try {
            oldConfig.setBaseUrl(normalizedBaseUrl);
            oldConfig.setModelName(dto.getModelName());
            if (isUsableApiKeyCandidate(dto.getApiKey())) {
                oldConfig.setApiKey(dto.getApiKey().trim());
            }
            if (dto.getLogRequests() != null) {
                oldConfig.setLogRequests(dto.getLogRequests());
            }
            if (dto.getLogResponses() != null) {
                oldConfig.setLogResponses(dto.getLogResponses());
            }
            if (dto.getMaxSegmentsPerBatch() != null) {
                oldConfig.setMaxSegmentsPerBatch(dto.getMaxSegmentsPerBatch());
            }
            oldConfig.setUpdateTime(LocalDateTime.now());
            this.updateById(oldConfig);
            reloadModel(type);

            validationResult.setStatus(STATUS_OK);
            validationResult.setMessage("Hot reload passed");
            return validationResult;
        } catch (Exception e) {
            markRollbackOnly();
            validationResult.setStatus(STATUS_FAIL);
            validationResult.setMessage("Hot reload failed: " + toUserFriendlyMessage(e));
            return validationResult;
        }
    }

    @Override
    public void reloadModel(String type) {
        AiModelConfig config = getByType(type);
        if (config == null) {
            log.warn("Skip reload, model config not found: {}", type);
            return;
        }

        String normalizedBaseUrl = normalizeBaseUrl(config.getBaseUrl());
        Integer maxSegmentsPerBatch = config.getMaxSegmentsPerBatch() != null
                ? config.getMaxSegmentsPerBatch()
                : 10;
        String protocol = resolveRuntimeProtocol(
                type,
                normalizedBaseUrl,
                config.getApiKey(),
                config.getModelName(),
                maxSegmentsPerBatch
        );

        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory)
                ((ConfigurableApplicationContext) applicationContext).getBeanFactory();

        try {
            switch (type) {
                case TYPE_CHAT -> {
                    ChatProtocolAdapter adapter = getChatProtocolAdapter(protocol);
                    ChatModel model = adapter.buildChatModel(
                            normalizedBaseUrl,
                            config.getApiKey(),
                            config.getModelName(),
                            config.getLogRequests(),
                            config.getLogResponses()
                    );
                    replaceSingleton(beanFactory, "openAiChatModel", model);
                    log.info("chat model reloaded, model={}, protocol={}", config.getModelName(), adapter.protocol());
                }
                case TYPE_STREAMING -> {
                    ChatProtocolAdapter adapter = getChatProtocolAdapter(protocol);
                    StreamingChatModel model = adapter.buildStreamingChatModel(
                            normalizedBaseUrl,
                            config.getApiKey(),
                            config.getModelName(),
                            config.getLogRequests(),
                            config.getLogResponses()
                    );
                    replaceSingleton(beanFactory, "openAiStreamingChatModel", model);
                    log.info("streaming model reloaded, model={}, protocol={}", config.getModelName(), adapter.protocol());
                }
                case TYPE_EMBEDDING -> {
                    EmbeddingProtocolAdapter adapter = getEmbeddingProtocolAdapter(protocol);
                    EmbeddingModel model = adapter.buildEmbeddingModel(
                            normalizedBaseUrl,
                            config.getApiKey(),
                            config.getModelName(),
                            config.getLogRequests(),
                            config.getLogResponses(),
                            maxSegmentsPerBatch
                    );
                    replaceSingleton(beanFactory, "openAiEmbeddingModel", model);
                    log.info("embedding model reloaded, model={}, protocol={}", config.getModelName(), adapter.protocol());
                }
                default -> throw new BizException("Unsupported model type: " + type);
            }
        } catch (Exception e) {
            throw new BizException("Failed to reload model [" + type + "]: " + toUserFriendlyMessage(e));
        }
    }

    private AiModelReloadResultVO checkModelAvailability(String type,
                                                         String baseUrl,
                                                         String apiKey,
                                                         String modelName,
                                                         Integer maxSegmentsPerBatch) {
        AiModelReloadResultVO result = new AiModelReloadResultVO();
        result.setModelType(type);
        result.setBaseUrl(baseUrl);
        result.setModelName(modelName);

        long start = System.currentTimeMillis();
        try {
            if (!StringUtils.hasText(apiKey)) {
                throw new BizException("API key is empty");
            }
            if (!StringUtils.hasText(modelName)) {
                throw new BizException("Model name is empty");
            }

            ProtocolProbeOutcome outcome;
            switch (type) {
                case TYPE_CHAT, TYPE_STREAMING -> outcome = detectChatProtocol(baseUrl, apiKey, modelName);
                case TYPE_EMBEDDING -> outcome = detectEmbeddingProtocol(baseUrl, apiKey, modelName, maxSegmentsPerBatch);
                default -> throw new BizException("Unsupported model type: " + type);
            }

            if (!outcome.success) {
                throw new BizException(outcome.combinedErrorMessage);
            }

            result.setProtocol(outcome.protocol);
            result.setStatus(STATUS_OK);
            result.setMessage("Probe passed, protocol=" + outcome.protocol);
            return result;
        } catch (Exception e) {
            result.setStatus(STATUS_FAIL);
            result.setMessage("Hot reload failed: " + toUserFriendlyMessage(e));
            return result;
        } finally {
            result.setCheckCostMs(System.currentTimeMillis() - start);
        }
    }

    private ProtocolProbeOutcome detectChatProtocol(String baseUrl, String apiKey, String modelName) {
        List<String> errors = new ArrayList<>();
        for (ChatProtocolAdapter adapter : chatProtocolAdapters) {
            try {
                adapter.probe(baseUrl, apiKey, modelName);
                return ProtocolProbeOutcome.success(adapter.protocol());
            } catch (Exception e) {
                errors.add(adapter.protocol() + " failed: " + toUserFriendlyMessage(e));
            }
        }
        return ProtocolProbeOutcome.failure(String.join("; ", errors));
    }

    private ProtocolProbeOutcome detectEmbeddingProtocol(String baseUrl,
                                                         String apiKey,
                                                         String modelName,
                                                         Integer maxSegmentsPerBatch) {
        List<String> errors = new ArrayList<>();
        for (EmbeddingProtocolAdapter adapter : embeddingProtocolAdapters) {
            try {
                adapter.probe(baseUrl, apiKey, modelName, maxSegmentsPerBatch);
                return ProtocolProbeOutcome.success(adapter.protocol());
            } catch (Exception e) {
                errors.add(adapter.protocol() + " failed: " + toUserFriendlyMessage(e));
            }
        }
        return ProtocolProbeOutcome.failure(String.join("; ", errors));
    }

    private String resolveRuntimeProtocol(String type,
                                          String baseUrl,
                                          String apiKey,
                                          String modelName,
                                          Integer maxSegmentsPerBatch) {
        String cachedProtocol = protocolByType.get(type);
        if (StringUtils.hasText(cachedProtocol)) {
            return cachedProtocol;
        }

        ProtocolProbeOutcome outcome = TYPE_EMBEDDING.equals(type)
                ? detectEmbeddingProtocol(baseUrl, apiKey, modelName, maxSegmentsPerBatch)
                : detectChatProtocol(baseUrl, apiKey, modelName);
        if (!outcome.success) {
            throw new BizException("Protocol detection failed: " + outcome.combinedErrorMessage);
        }

        protocolByType.put(type, outcome.protocol);
        return outcome.protocol;
    }

    private ChatProtocolAdapter getChatProtocolAdapter(String protocol) {
        for (ChatProtocolAdapter adapter : chatProtocolAdapters) {
            if (adapter.protocol().equals(protocol)) {
                return adapter;
            }
        }
        throw new BizException("Unsupported chat protocol: " + protocol);
    }

    private EmbeddingProtocolAdapter getEmbeddingProtocolAdapter(String protocol) {
        for (EmbeddingProtocolAdapter adapter : embeddingProtocolAdapters) {
            if (adapter.protocol().equals(protocol)) {
                return adapter;
            }
        }
        throw new BizException("Unsupported embedding protocol: " + protocol);
    }

    private String resolveEffectiveApiKey(AiModelConfigUpdateDTO dto, AiModelConfig oldConfig) {
        if (isUsableApiKeyCandidate(dto.getApiKey())) {
            return dto.getApiKey().trim();
        }
        return StringUtils.hasText(oldConfig.getApiKey()) ? oldConfig.getApiKey().trim() : "";
    }

    private boolean isUsableApiKeyCandidate(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return false;
        }

        String trimmed = apiKey.trim();
        if (!StringUtils.hasText(trimmed)) {
            return false;
        }

        if (trimmed.contains("...")) {
            return false;
        }
        if (trimmed.contains("*")) {
            return false;
        }
        if (trimmed.matches("^[\u2022]+$")) {
            return false;
        }
        if ("null".equalsIgnoreCase(trimmed) || "undefined".equalsIgnoreCase(trimmed)) {
            return false;
        }
        return true;
    }

    private void replaceSingleton(DefaultListableBeanFactory factory, String beanName, Object newBean) {
        if (factory.containsSingleton(beanName)) {
            factory.destroySingleton(beanName);
        }
        factory.registerSingleton(beanName, newBean);
    }

    private void markRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (Exception ignore) {
            log.warn("Cannot mark transaction rollback-only: {}", ignore.getMessage());
        }
    }

    private String toUserFriendlyMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String message = StringUtils.hasText(root.getMessage()) ? root.getMessage() : throwable.getMessage();
        if (!StringUtils.hasText(message)) {
            message = throwable.getClass().getSimpleName();
        }

        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("api_key_required")
                || lower.contains("unauthorized")
                || lower.contains("invalid api key")
                || lower.contains("authentication")
                || lower.contains("api key is required")
                || lower.contains("api key not valid")
                || lower.contains("x-goog-api-key")) {
            return "API key is invalid or missing";
        }
        if (lower.contains("unknownhost") || lower.contains("name or service not known")) {
            return "Cannot resolve host, please check baseUrl";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "Request timed out";
        }
        if (lower.contains("connection refused") || lower.contains("connectexception")) {
            return "Connection refused by target endpoint";
        }
        if (lower.contains("404")) {
            return "Endpoint not found (404), please check baseUrl and route";
        }
        if (lower.contains("403")) {
            return "Request forbidden (403), please check API key permissions";
        }
        if (lower.contains("500")) {
            return "Provider internal server error (500)";
        }

        String compact = message.replaceAll("[\\r\\n]+", " ").trim();
        if (compact.length() > 280) {
            compact = compact.substring(0, 280) + "...";
        }
        return compact;
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        if (!StringUtils.hasText(rawBaseUrl)) {
            throw new BizException("API baseUrl cannot be empty");
        }

        String normalized = rawBaseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        try {
            URI uri = new URI(normalized);
            String path = uri.getPath();
            if (!StringUtils.hasText(path) || "/".equals(path)) {
                String autoFixed = normalized + "/v1";
                log.warn("baseUrl had no path, auto-fixed to {}", autoFixed);
                return autoFixed;
            }
            return normalized;
        } catch (URISyntaxException e) {
            throw new BizException("Invalid baseUrl: " + rawBaseUrl);
        }
    }

    @PostConstruct
    public void initModelsFromDb() {
        try {
            long count = this.count();
            if (count == 0) {
                log.info("No ai model config in database, skip hot reload at startup");
                return;
            }
            reloadModel(TYPE_CHAT);
            reloadModel(TYPE_STREAMING);
            reloadModel(TYPE_EMBEDDING);
            log.info("Hot reloaded all AI model configs from database");
        } catch (Exception e) {
            log.error("Failed to initialize AI model config from database: {}", e.getMessage(), e);
        }
    }

    private static final class ProtocolProbeOutcome {
        private final boolean success;
        private final String protocol;
        private final String combinedErrorMessage;

        private ProtocolProbeOutcome(boolean success, String protocol, String combinedErrorMessage) {
            this.success = success;
            this.protocol = protocol;
            this.combinedErrorMessage = combinedErrorMessage;
        }

        private static ProtocolProbeOutcome success(String protocol) {
            return new ProtocolProbeOutcome(true, protocol, null);
        }

        private static ProtocolProbeOutcome failure(String combinedErrorMessage) {
            return new ProtocolProbeOutcome(false, null, combinedErrorMessage);
        }
    }
}

