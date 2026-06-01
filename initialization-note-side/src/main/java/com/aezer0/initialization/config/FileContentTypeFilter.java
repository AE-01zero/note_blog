package com.aezer0.initialization.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class FileContentTypeFilter {

    @Bean
    public FilterRegistrationBean<FileHeaderFilter> fileHeaderFilter() {
        FilterRegistrationBean<FileHeaderFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new FileHeaderFilter());
        bean.addUrlPatterns("/file/*");
        bean.setOrder(1);
        return bean;
    }

    public static class FileHeaderFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse resp = (HttpServletResponse) response;

            String path = req.getRequestURI().toLowerCase();

            if (path.endsWith(".pdf")) {
                resp.setHeader("Content-Type", "application/pdf");
                resp.setHeader("Content-Disposition", "inline");
            } else if (path.endsWith(".md") || path.endsWith(".markdown")) {
                resp.setHeader("Content-Type", "text/plain; charset=UTF-8");
                resp.setHeader("Content-Disposition", "inline");
            } else if (path.endsWith(".txt")) {
                resp.setHeader("Content-Type", "text/plain; charset=UTF-8");
                resp.setHeader("Content-Disposition", "inline");
            }

            chain.doFilter(request, response);
        }
    }
}
