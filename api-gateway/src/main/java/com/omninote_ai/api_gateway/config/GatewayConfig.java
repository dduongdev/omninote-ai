package com.omninote_ai.api_gateway.config;

import java.net.URI;

import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

@Configuration
public class GatewayConfig {

    // server service (auth, users)
    @Bean
    public RouterFunction<ServerResponse> serverRoutes() {
        return GatewayRouterFunctions.route("server-route")
                .route(path("/api/auth/**").or(path("/api/users/**")),
                        HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(URI.create("http://server:8080")))
                .build();
    }

    // ai-service
    @Bean
    public RouterFunction<ServerResponse> aiServiceRoutes() {
        return GatewayRouterFunctions.route("ai-service-route")
                .route(path("/api/ai/**"),
                        HandlerFunctions.http())
                .before(BeforeFilterFunctions.uri(URI.create("http://ai-service:8080")))
                .build();
    }
}
