package br.com.platform.order.application;

import br.com.platform.order.application.dto.OrderResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void checkAndLock(String idempotencyKey, String userId) {
        String key = "idempotency:" + userId + ":" + idempotencyKey;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "PROCESSING", Duration.ofHours(24));
        if (Boolean.FALSE.equals(acquired)) {
            String value = redisTemplate.opsForValue().get(key);
            if ("PROCESSING".equals(value)) {
                throw new RuntimeException("Request is already processing");
            } else {
                throw new RuntimeException("Request already processed. Response: " + value);
            }
        }
    }

    public void markSuccess(String idempotencyKey, String userId, OrderResponse response) {
        try {
            String key = "idempotency:" + userId + ":" + idempotencyKey;
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, Duration.ofHours(24));
        } catch (Exception e) {
            throw new RuntimeException("Failed to save idempotency response", e);
        }
    }
}
