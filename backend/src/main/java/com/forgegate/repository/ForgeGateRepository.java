package com.forgegate.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class ForgeGateRepository {
    private final JdbcTemplate jdbc;
    public ForgeGateRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public JdbcTemplate jdbc() { return jdbc; }
    public Map<String,Object> userByEmail(String email) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT u.id,u.name,u.email,u.password_hash,u.department,u.manager_id,u.is_active,r.name role FROM users u JOIN roles r ON r.id=u.role_id WHERE lower(u.email)=lower(?)", email);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public Map<String,Object> userById(long id) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT u.id,u.name,u.email,u.department,u.manager_id,u.is_active,r.name role FROM users u JOIN roles r ON r.id=u.role_id WHERE u.id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
