package com.forgegate.service;

import com.forgegate.config.ApiException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class RedisLockService {
    private static final Logger log = LoggerFactory.getLogger(RedisLockService.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private final StringRedisTemplate redis;
    public RedisLockService(StringRedisTemplate redis) { this.redis = redis; }

    public <T> T withLock(String key, Duration wait, java.util.function.Supplier<T> action) {
        String redisKey = "forgegate:lock:" + key;
        String value = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + wait.toNanos();
        boolean acquired = false;
        do {
            try {
                Boolean result = redis.opsForValue().setIfAbsent(redisKey, value, Duration.ofMinutes(2));
                acquired = Boolean.TRUE.equals(result);
            } catch (Exception ex) {
                throw ApiException.unavailable("Redis is unavailable; request was not processed safely");
            }
            if (acquired) break;
            if (System.nanoTime() >= deadline) break;
            try {
                Thread.sleep(40);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ApiException.unavailable("Interrupted while waiting for access lock");
            }
        } while (System.nanoTime() < deadline);

        if (!acquired) throw ApiException.conflict("Another operation is in progress for this user and resource; retry shortly");

        Throwable actionFailure = null;
        try {
            return action.get();
        } catch (RuntimeException | Error ex) {
            actionFailure = ex;
            throw ex;
        } finally {
            try {
                redis.execute(RELEASE, List.of(redisKey), value);
            } catch (Exception releaseFailure) {
                // The action may already have committed; don't report failure after success.
                if (actionFailure == null) {
                    log.warn("Redis lock release failed for key {}; lock will expire by TTL", redisKey, releaseFailure);
                } else {
                    log.warn("Redis lock release failed for key {} while handling an action failure", redisKey, releaseFailure);
                }
            }
        }
    }
}
