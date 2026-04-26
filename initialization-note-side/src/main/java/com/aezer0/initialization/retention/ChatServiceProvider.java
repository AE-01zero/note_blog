package com.aezer0.initialization.retention;

import org.springframework.stereotype.Service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE) // 这个注解可以标记在类或接口上
@Retention(RetentionPolicy.RUNTIME)
@Service
public @interface ChatServiceProvider {
    /**
     * 定义该服务提供商对应的模型名称 (e.g., "openai", "local")
     */
    String value();
}