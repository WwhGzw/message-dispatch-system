package com.msg.access.config;

import com.msg.access.auth.AuthInterceptor;
import com.msg.access.ratelimit.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 注册鉴权拦截器和限流拦截器到 /msg/** 路径
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor, RateLimitInterceptor rateLimitInterceptor) {
        this.authInterceptor = authInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Auth first, then rate limiting
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/msg/**");
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/msg/**");
    }
}
