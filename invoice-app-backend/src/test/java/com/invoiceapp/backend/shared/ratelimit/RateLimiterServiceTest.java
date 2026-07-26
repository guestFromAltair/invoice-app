package com.invoiceapp.backend.shared.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimiterService")
class RateLimiterServiceTest {

    @Mock private StringRedisTemplate redis;

    private RateLimiterService service;

    @BeforeEach
    void setUp() {
        service = new RateLimiterService(redis);
        ReflectionTestUtils.setField(service, "capacity", 60);
        ReflectionTestUtils.setField(service, "refillPerSecond", 1.0);
    }

    @Test
    @DisplayName("allows when a token was available")
    void allows() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(List.of(1L, 42L, 0L));

        var decision = service.tryConsume("user-1");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(42);
    }

    @Test
    @DisplayName("blocks and reports retry-after when the bucket is empty")
    void blocks() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenReturn(List.of(0L, 0L, 3L));

        var decision = service.tryConsume("user-1");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(3);
    }

    @Test
    @DisplayName("fails open when Redis is unreachable")
    void fails_open() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(service.tryConsume("user-1").allowed()).isTrue();
    }
}