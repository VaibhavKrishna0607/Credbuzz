package com.credbuzz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * ============================================
 * ML Service Configuration
 * ============================================
 * 
 * Configures WebClient for communicating with the ML prediction service.
 */
@Configuration
public class MLConfig {

    @Value("${ml.service.url:http://localhost:8000}")
    private String mlServiceUrl;

    @Value("${ml.service.timeout:5000}")
    private int timeoutMs;

    @Value("${ml.service.enabled:false}")
    private boolean mlEnabled;

    @Bean
    public WebClient mlWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeoutMs));

        return WebClient.builder()
                .baseUrl(mlServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public boolean isMlEnabled() {
        return mlEnabled;
    }

    public String getMlServiceUrl() {
        return mlServiceUrl;
    }
}
