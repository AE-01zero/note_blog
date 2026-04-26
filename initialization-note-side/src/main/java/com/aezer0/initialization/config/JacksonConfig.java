package com.aezer0.initialization.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 允许从JSON字符串转换为Java对象时字段不匹配
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 允许将JSON空字符串转换为null对象
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

        // 允许将JSON数字转换为Java的Long类型
        mapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);

        // ↓↓↓↓↓↓ 这是需要添加的核心代码 ↓↓↓↓↓↓
        // 注册 Java 8 时间模块，用于序列化和反序列化 LocalDateTime、LocalDate 等
        mapper.registerModule(new JavaTimeModule());

        // (可选) 禁用将日期写为时间戳的行为，这样会输出 "2023-01-01T12:00:00" 格式的字符串
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // ↑↑↑↑↑↑ 这是需要添加的核心代码 ↑↑↑↑↑↑

        return mapper;
    }
}