package com.forgegate.service;

import com.forgegate.config.ApiException;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

@Component
public class RequestStateMachine {
    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "PENDING_MANAGER", Set.of("PENDING_SECURITY","APPROVED","REJECTED"),
        "PENDING_SECURITY", Set.of("APPROVED","REJECTED"),
        "APPROVED", Set.of("ACTIVE"),
        "ACTIVE", Set.of("EXPIRED","REVOKED"),
        "REJECTED", Set.of(), "EXPIRED", Set.of(), "REVOKED", Set.of()
    );
    public void assertTransition(String from, String to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) throw ApiException.conflict("Illegal request status transition: "+from+" -> "+to);
    }
    public boolean allowed(String from, String to) { return ALLOWED.getOrDefault(from, Set.of()).contains(to); }
    public Map<String,Set<String>> allowedTransitions() { return ALLOWED; }
}
