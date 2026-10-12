package com.forgegate.security;

import com.forgegate.config.ApiException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class LoginRateLimiter {
    private final StringRedisTemplate redis;
    public LoginRateLimiter(StringRedisTemplate redis) { this.redis=redis; }
    public void check(String ip) {
        try {
            String key="forgegate:login-rate:"+ip;
            Long count=redis.opsForValue().increment(key);
            if(count==null) throw ApiException.unavailable("Login rate limiter did not return a counter; authentication is temporarily disabled");
            if(count==1) redis.expire(key,Duration.ofMinutes(1));
            if(count>10) throw new ApiException(429,"Too many login attempts; wait one minute");
        } catch(ApiException ex) { throw ex; }
        catch(Exception ex) { throw ApiException.unavailable("Login rate limiter is unavailable; authentication is temporarily disabled"); }
    }
}
