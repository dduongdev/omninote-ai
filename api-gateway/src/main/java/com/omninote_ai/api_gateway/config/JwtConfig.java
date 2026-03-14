package com.omninote_ai.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Component
@Getter
public class JwtConfig {

    @Value("${jwt.secret}")
    private String secret;
}
