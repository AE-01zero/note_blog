package com.aezer0.initialization.service.ai.adapter;


import com.aezer0.initialization.retention.ChatServiceProvider;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DynamicChatDispatcher {

    @Autowired
    private ApplicationContext context;

    private final Map<String, Object> serviceMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        // 从Spring容器中获取所有被@ChatServiceProvider注解的beans
        Map<String, Object> beans = context.getBeansWithAnnotation(ChatServiceProvider.class);

        beans.forEach((beanName, beanInstance) -> {
            // 获取Bean的接口上的注解，因为Langchain4j创建的是代理类
            ChatServiceProvider annotation = beanInstance.getClass().getInterfaces()[0].getAnnotation(ChatServiceProvider.class);
            if (annotation != null) {
                String modelName = annotation.value();
                serviceMap.put(modelName, beanInstance);
                System.out.println("发现并注册聊天服务: " + modelName + " -> " + beanName);
            }
        });
    }

    public Flux<String> streamChat(String modelName, String memoryId, String message) {
        return invokeMethod(modelName, "streamChat", new Class<?>[]{String.class, String.class}, memoryId, message);
    }

    public <T> T simpleChat(String modelName, String memoryId, String message) {
        return invokeMethod(modelName, "simpleChat", new Class<?>[]{String.class, String.class}, memoryId, message);
    }

    private <T> T invokeMethod(String modelName, String methodName, Class<?>[] argTypes, Object... args) {
        Object service = Optional.ofNullable(serviceMap.get(modelName))
                .orElseThrow(() -> new IllegalArgumentException("未知的模型: " + modelName));

        try {
            Method method = service.getClass().getMethod(methodName, argTypes);
            return (T) method.invoke(service, args);
        } catch (Exception e) {
            throw new RuntimeException("无法调用方法 '" + methodName + "' on model '" + modelName + "'", e);
        }
    }
}