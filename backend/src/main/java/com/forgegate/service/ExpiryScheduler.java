package com.forgegate.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.forgegate.config.ApiException;
import java.time.Duration;

@Component
public class ExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(ExpiryScheduler.class);
    private final RedisLockService locks;
    private final ForgeGateService service;
    public ExpiryScheduler(RedisLockService locks, ForgeGateService service) { this.locks=locks; this.service=service; }
    @Scheduled(fixedDelayString="${forgegate.revoke-interval-ms}")
    public void expire() {
        try {
            locks.withLock("expiry-scheduler", Duration.ofMillis(100), () -> service.expireGrants());
        } catch (ApiException ex) {
            log.warn("Grant expiry cycle skipped: {}", ex.getMessage());
        }
    }
}
