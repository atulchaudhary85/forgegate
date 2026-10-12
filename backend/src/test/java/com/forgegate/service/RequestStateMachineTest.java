package com.forgegate.service;

import com.forgegate.config.ApiException;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RequestStateMachineTest {
    private final RequestStateMachine machine=new RequestStateMachine();
    @Test void allowsEveryDocumentedTransition(){
        machine.assertTransition("PENDING_MANAGER","PENDING_SECURITY"); machine.assertTransition("PENDING_MANAGER","APPROVED"); machine.assertTransition("PENDING_MANAGER","REJECTED");
        machine.assertTransition("PENDING_SECURITY","APPROVED"); machine.assertTransition("PENDING_SECURITY","REJECTED"); machine.assertTransition("APPROVED","ACTIVE");
        machine.assertTransition("ACTIVE","EXPIRED"); machine.assertTransition("ACTIVE","REVOKED");
    }
    @Test void rejectsEveryIllegalTransitionIncludingRejectedTerminal(){
        Map<String,java.util.Set<String>> map=machine.allowedTransitions();
        for(String from:map.keySet()) for(String to:map.keySet()) {
            if(!map.get(from).contains(to)) assertThrows(ApiException.class,()->machine.assertTransition(from,to),from+" -> "+to);
        }
        assertTrue(map.get("REJECTED").isEmpty());
    }
}
