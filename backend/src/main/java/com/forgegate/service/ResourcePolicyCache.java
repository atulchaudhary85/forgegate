package com.forgegate.service;

import com.forgegate.config.ApiException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class ResourcePolicyCache {
    private final JdbcTemplate db;
    public ResourcePolicyCache(JdbcTemplate db) { this.db=db; }
    @Cacheable(cacheNames="resourcePolicy", key="#resourceId")
    public Map<String,Object> get(long resourceId) {
        List<Map<String,Object>> rows=db.queryForList("SELECT r.id resource_id,r.is_active resource_active,p.id policy_id,p.allowed_department,p.max_duration_days,p.requires_security_approval,p.is_active policy_active,r.sensitivity FROM resources r LEFT JOIN LATERAL (SELECT * FROM policies WHERE resource_id=r.id AND is_active=true ORDER BY id DESC LIMIT 1) p ON true WHERE r.id=?",resourceId);
        if(rows.isEmpty()||rows.get(0).get("policy_id")==null) throw ApiException.bad("Resource not found or active policy missing"); return rows.get(0);
    }
}
