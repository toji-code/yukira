package com.yukira.backend.client;

import com.yukira.backend.client.dto.CalculationDtos.CalculationRequestDto;
import com.yukira.backend.client.dto.CalculationDtos.CalculationResponseDto;
import com.yukira.backend.client.dto.CalculationDtos.HealthResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class QuantEngineClient {

    private final RestClient restClient;

    public QuantEngineClient(@Value("${yukira.quant-engine.url:http://localhost:8001}") String quantEngineUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());

        this.restClient = RestClient.builder()
            .baseUrl(quantEngineUrl)
            .requestFactory(factory)
            .build();
    }

    public HealthResponseDto checkHealth() {
        return restClient.get()
            .uri("/health")
            .retrieve()
            .body(HealthResponseDto.class);
    }

    public CalculationResponseDto executeCalculation(CalculationRequestDto request) {
        int maxAttempts = 3;
        Exception lastEx = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return restClient.post()
                    .uri("/api/v1/calculate")
                    .body(request)
                    .retrieve()
                    .body(CalculationResponseDto.class);
            } catch (Exception e) {
                lastEx = e;
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        if (lastEx instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException("Quant engine calculation failed after retries", lastEx);
    }
}
