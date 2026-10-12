package com.forgegate.service;

import com.forgegate.config.ApiException;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class PolicyEngine {
    public void validate(Map<String,Object> policy, String department, int durationDays) {
        if (!Boolean.TRUE.equals(policy.get("resource_active")) || !Boolean.TRUE.equals(policy.get("policy_active"))) throw ApiException.bad("Resource or policy is inactive");
        if (durationDays!=1 && durationDays!=7 && durationDays!=30) throw ApiException.bad("durationDays must be 1, 7, or 30");
        if (durationDays>((Number)policy.get("max_duration_days")).intValue()) throw ApiException.bad("Requested duration exceeds policy maximum");
        Object allowed=policy.get("allowed_department");
        if (allowed!=null && !allowed.toString().equals(department)) throw ApiException.forbidden("Department is not allowed by policy");
    }
}
