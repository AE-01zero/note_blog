package com.aezer0.initialization.config.satoken;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 统一配置类
 * <p>
 * 整合了权限接口、拦截器配置，使所有Sa-Token相关配置集中于此。
 */
@Configuration
public class SaTokenConfiguration implements StpInterface, WebMvcConfigurer {

    /**
     * 注册 Sa-Token 拦截器，并定义登录校验规则。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，校验规则为 StpUtil.checkLogin() 登录校验。
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**") // 拦截所有路径
                .excludePathPatterns("/user/doLogin", "/file/local-plus/**", "/api/blog/tags", "/api/common/login", "/api/common/captcha"); // 排除登录、文件访问、公开接口
    }

    /**
     * 获取指定登录用户的权限列表。
     * (本项目暂不使用权限，直接返回空列表)
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    /**
     * 获取指定登录用户的角色列表。
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 检查用户是否已登录
        if (!StpUtil.isLogin()) {
            return Collections.emptyList();
        }
        // 从当前用户的 Session 中获取角色信息
        String role = (String) StpUtil.getSession().get("role");
        return role != null ? Collections.singletonList(role) : Collections.emptyList();
    }
}
