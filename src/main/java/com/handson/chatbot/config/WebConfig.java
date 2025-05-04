package com.handson.chatbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/bot/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }
}
