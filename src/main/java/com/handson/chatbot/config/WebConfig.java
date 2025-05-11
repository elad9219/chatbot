package com.handson.chatbot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/bot/**")    // כל קריאה ל־/bot/…
                .allowedOrigins(
                        "http://localhost:3000",
                        "https://bot.runmydocker-app.com"
                )
                .allowedMethods("GET","POST","OPTIONS")
                .allowedHeaders("*");

        // לתפוס גם את ה-OPTIONS ל־/bot בלי הסלאש:
        registry.addMapping("/bot")
                .allowedOrigins(
                        "http://localhost:3000",
                        "https://bot.runmydocker-app.com"
                )
                .allowedMethods("OPTIONS")
                .allowedHeaders("*");
    }
}
