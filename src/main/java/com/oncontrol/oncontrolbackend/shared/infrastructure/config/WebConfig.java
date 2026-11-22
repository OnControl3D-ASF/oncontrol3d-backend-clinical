package com.oncontrol.oncontrolbackend.shared.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Explicit allowed origins. Configurable via the APP_CORS_ALLOWED_ORIGINS env var
     * (property {@code app.cors.allowed-origins}, comma-separated). On AKS it is fed from the
     * edge ConfigMap key PUBLIC_ORIGIN. The broad "https://*.vercel.app" wildcard was removed because,
     * combined with allowCredentials(true), it let ANY Vercel deployment send
     * credentialed requests. Add your real frontend URL to the env var if it changes.
     */
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:5173,https://on-control.vercel.app,https://oncontrol.vercel.app,https://oncontrol-front-sem.vercel.app}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
