package com.capstone.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${story.image.save-path:./story-images}")
    private String imageSavePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 스토리 이미지 정적 리소스 핸들러 추가
        registry.addResourceHandler("/story-images/**")
                .addResourceLocations("file:" + imageSavePath + "/");
    }
}