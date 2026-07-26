package com.invoiceapp.backend.shared.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class RateLimiterService {

    private final StringRedisTemplate redis;
    private final RedisScript<List> tokenBucketScript;

    @Value("${application.rate-limit.capacity}")
    private int capacity;

    @Value("${application.rate-limit.refill-per-second}")
    private double refillPerSecond;

    public RateLimiterService(StringRedisTemplate redis) {
        this.redis = redis;

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/token_bucket.lua")));
        script.setResultType(List.class);
        this.tokenBucketScript = script;
    }

    public Decision tryConsume(String callerId) {
        String key = "ratelimit:" + callerId;
        try {
            List<?> result = redis.execute(
                    tokenBucketScript,
                    List.of(key),
                    String.valueOf(capacity),
                    String.valueOf(refillPerSecond)
            );

            if (result == null || result.size() < 3) {
                return Decision.allowed(capacity);
            }

            boolean allowed = ((Number) result.get(0)).intValue() == 1;
            long remaining = ((Number) result.get(1)).longValue();
            long retryAfter = ((Number) result.get(2)).longValue();

            return new Decision(allowed, remaining, retryAfter, capacity);

        } catch (Exception e) {
            log.error("Rate limiter unavailable, allowing request (fail open)", e);
            return Decision.allowed(capacity);
        }
    }

    public record Decision(boolean allowed, long remaining, long retryAfterSeconds, long limit) {
        static Decision allowed(long limit) {
            return new Decision(true, limit, 0, limit);
        }
    }
}