package com.hmdp.config;

import com.hmdp.interceptors.LoginInterceptor;
import com.hmdp.interceptors.RefreshInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    RefreshInterceptor refreshInterceptor;

    @Autowired
    LoginInterceptor loginInterceptor;

    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(refreshInterceptor)
                .addPathPatterns("/**")
                .order(0);
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/user/login")
                .excludePathPatterns("/user/code")
                .excludePathPatterns("/shop/**")
                .excludePathPatterns("/voucher/**")
                .excludePathPatterns("/shop-type/**")
                .excludePathPatterns("/upload/**")
                .excludePathPatterns("/blog/hot/**")
                .order(1);
    }


}
