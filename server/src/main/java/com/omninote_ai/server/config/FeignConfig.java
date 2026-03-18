package com.omninote_ai.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.RequestInterceptor;



@Configuration
public class FeignConfig {
    @Value("$internal.api.key")
    private String internalApiKey;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("INTERNAL-API-KEY", internalApiKey);
        };
    }
}
