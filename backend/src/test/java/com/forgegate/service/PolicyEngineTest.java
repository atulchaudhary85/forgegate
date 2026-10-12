package com.forgegate.service;

import com.forgegate.config.ApiException;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PolicyEngineTest {
    private final PolicyEngine engine=new PolicyEngine();
    private Map<String,Object> policy(){Map<String,Object> p=new HashMap<>();p.put("resource_active",true);p.put("policy_active",true);p.put("max_duration_days",7);p.put("allowed_department","Engineering");return p;}
    @Test void allowsMatchingDepartmentAndAllowedDuration(){assertDoesNotThrow(()->engine.validate(policy(),"Engineering",7));}
    @Test void blocksDepartmentMismatch(){assertEquals(403,assertThrows(ApiException.class,()->engine.validate(policy(),"Finance",1)).status());}
    @Test void blocksDurationOverPolicyMaximum(){assertEquals(400,assertThrows(ApiException.class,()->engine.validate(policy(),"Engineering",30)).status());}
    @Test void blocksUnsupportedDuration(){assertThrows(ApiException.class,()->engine.validate(policy(),"Engineering",2));}
    @Test void blocksInactiveResource(){Map<String,Object> p=policy();p.put("resource_active",false);assertThrows(ApiException.class,()->engine.validate(p,"Engineering",1));}
    @Test void blocksInactivePolicy(){Map<String,Object> p=policy();p.put("policy_active",false);assertThrows(ApiException.class,()->engine.validate(p,"Engineering",1));}
    @Test void nullDepartmentPolicyAllowsAllDepartments(){Map<String,Object> p=policy();p.put("allowed_department",null);assertDoesNotThrow(()->engine.validate(p,"Finance",1));}
}
