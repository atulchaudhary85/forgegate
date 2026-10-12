package com.forgegate;

import com.forgegate.dto.ApiDtos;
import com.forgegate.service.ForgeGateService;
import com.forgegate.service.ExpiryScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ForgeGateIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("forgegate").withUsername("forgegate").withPassword("forgegate");
    @Container static final GenericContainer<?> redis=new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",postgres::getJdbcUrl); r.add("spring.datasource.username",postgres::getUsername); r.add("spring.datasource.password",postgres::getPassword);
        r.add("spring.data.redis.host",redis::getHost); r.add("spring.data.redis.port",()->redis.getMappedPort(6379)); r.add("forgegate.jwt.secret",()->"test-secret-for-forgegate-at-least-32-bytes");
    }
    @Autowired JdbcTemplate db; @Autowired ForgeGateService service; @Autowired MockMvc mvc; @Autowired ExpiryScheduler expiryScheduler;

    @BeforeEach void cleanWorkflowRows(){
        db.update("DELETE FROM access_grants");
        db.update("DELETE FROM approvals");
        db.update("DELETE FROM access_requests");
        db.update("DELETE FROM notifications");
    }


    @Test void seededDevelopmentPasswordAuthenticatesWithBcrypt() {
        Map<String,Object> user=service.login("employee@forgegate.local","password");
        assertEquals("EMPLOYEE",user.get("role"));
        assertTrue(String.valueOf(user.get("password_hash")).startsWith("$2a$"));
    }

    @Test void securityReviewerCannotSkipManagerStage() {
        Map<String,Object> created=service.createRequest("employee@forgegate.local",new ApiDtos.CreateRequest(3L,"Need dashboard access for quarterly review",7,UUID.randomUUID().toString()));
        long id=((Number)created.get("id")).longValue();
        assertEquals(403,assertThrows(com.forgegate.config.ApiException.class,()->service.decide("security@forgegate.local",id,true,null)).status());
    }

    @Test void idempotencyReturnsSameRequest(){
        String key=UUID.randomUUID().toString(); ApiDtos.CreateRequest b=new ApiDtos.CreateRequest(1L,"Need repository access for incident review",7,key);
        Map<String,Object> one=service.createRequest("employee@forgegate.local",b); Map<String,Object> two=service.createRequest("employee@forgegate.local",b);
        assertEquals(one.get("id"),two.get("id"));
    }
    @Test void partialIndexRejectsSecondActiveGrant(){
        long user=db.queryForObject("SELECT id FROM users WHERE email='employee@forgegate.local'",Long.class);
        long resource=db.queryForObject("SELECT id FROM resources WHERE name='payments-repo'",Long.class);
        long request1=db.queryForObject("INSERT INTO access_requests(requester_id,resource_id,reason,duration_days,status,idempotency_key) VALUES (?,?,?,1,'ACTIVE',?) RETURNING id",Long.class,user,resource,"idx-"+UUID.randomUUID(),"idx1-"+UUID.randomUUID());
        long request2=db.queryForObject("INSERT INTO access_requests(requester_id,resource_id,reason,duration_days,status,idempotency_key) VALUES (?,?,?,1,'ACTIVE',?) RETURNING id",Long.class,user,resource,"idx-"+UUID.randomUUID(),"idx2-"+UUID.randomUUID());
        db.update("INSERT INTO access_grants(request_id,user_id,resource_id,expires_at,status) VALUES (?,?,?,?,'ACTIVE')",request1,user,resource,Timestamp.from(Instant.now().plusSeconds(3600)));
        assertThrows(DataAccessException.class,()->db.update("INSERT INTO access_grants(request_id,user_id,resource_id,expires_at,status) VALUES (?,?,?,?,'ACTIVE')",request2,user,resource,Timestamp.from(Instant.now().plusSeconds(3600))));
    }
    @Test void auditTableRejectsUpdateAndDelete(){
        db.update("INSERT INTO audit_logs(action,entity_type,entity_id,details) VALUES ('TEST','test',1,'{}'::jsonb)");
        long id=db.queryForObject("SELECT max(id) FROM audit_logs WHERE action='TEST'",Long.class);
        assertThrows(DataAccessException.class,()->db.update("UPDATE audit_logs SET action='CHANGED' WHERE id=?",id));
        assertThrows(DataAccessException.class,()->db.update("DELETE FROM audit_logs WHERE id=?",id));
    }
    @Test void fullApprovalGrantAndExpiryPath(){
        String key=UUID.randomUUID().toString(); Map<String,Object> created=service.createRequest("employee@forgegate.local",new ApiDtos.CreateRequest(1L,"Need repository access for incident response",1,key));
        long id=((Number)created.get("id")).longValue();
        assertEquals("PENDING_SECURITY",service.decide("manager@forgegate.local",id,true,null).get("status"));
        assertEquals("ACTIVE",service.decide("security@forgegate.local",id,true,null).get("status"));
        Long grant=db.queryForObject("SELECT id FROM access_grants WHERE request_id=?",Long.class,id);
        db.update("UPDATE access_grants SET granted_at=now()-interval '2 seconds', expires_at=now()-interval '1 second' WHERE id=?",grant);
        expiryScheduler.expire();
        assertEquals("EXPIRED",db.queryForObject("SELECT status FROM access_requests WHERE id=?",String.class,id));
    }
    @Test void rejectionRequiresCommentAndRejectsSelfApproval(){
        Map<String,Object> created=service.createRequest("employee@forgegate.local",new ApiDtos.CreateRequest(3L,"Need dashboard access for quarterly review",7,UUID.randomUUID().toString()));
        long id=((Number)created.get("id")).longValue();
        assertEquals(400,org.junit.jupiter.api.Assertions.assertThrows(com.forgegate.config.ApiException.class,()->service.decide("manager@forgegate.local",id,false," ")).status());
        assertEquals(403,org.junit.jupiter.api.Assertions.assertThrows(com.forgegate.config.ApiException.class,()->service.decide("employee@forgegate.local",id,true,null)).status());
        assertEquals("REJECTED",service.decide("manager@forgegate.local",id,false,"Business need not demonstrated").get("status"));
    }

    @Test void wrongManagerCannotApprove(){
        Map<String,Object> created=service.createRequest("employee@forgegate.local",new ApiDtos.CreateRequest(3L,"Need dashboard access for quarterly review",7,UUID.randomUUID().toString()));
        long id=((Number)created.get("id")).longValue();
        assertEquals(403,org.junit.jupiter.api.Assertions.assertThrows(com.forgegate.config.ApiException.class,()->service.decide("admin@forgegate.local",id,true,null)).status());
    }
    @Test void nullManagerEscalatesToAdminAndAuditsDecision(){
        long employeeRole=db.queryForObject("SELECT id FROM roles WHERE name='EMPLOYEE'",Long.class);
        long user=db.queryForObject("INSERT INTO users(name,email,password_hash,department,manager_id,role_id,is_active) VALUES (?,?,?,?,NULL,?,true) RETURNING id",Long.class,"No Manager Employee","nomgr-"+UUID.randomUUID()+"@forgegate.local","$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy","Engineering",employeeRole);
        Map<String,Object> created=service.createRequest(db.queryForObject("SELECT email FROM users WHERE id=?",String.class,user),new ApiDtos.CreateRequest(3L,"Need dashboard access for quarterly review",7,UUID.randomUUID().toString()));
        long id=((Number)created.get("id")).longValue();
        assertEquals(4L,db.queryForObject("SELECT approver_id FROM approvals WHERE request_id=? AND stage='MANAGER'",Long.class,id));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM audit_logs WHERE action='MANAGER_ESCALATED_TO_ADMIN' AND entity_id=?",Integer.class,id));
        assertEquals("ACTIVE",service.decide("admin@forgegate.local",id,true,null).get("status"));
    }
    @Test void employeeCannotReadAuditLog() throws Exception {
        var response=mvc.perform(get("/api/audit").header("Authorization","Bearer "+tokenForEmployee())).andReturn().getResponse();
        assertEquals(403,response.getStatus());
    }
    private String tokenForEmployee(){
        // Use the configured JWT service so this test exercises the real authorization chain.
        return jwt.issue("employee@forgegate.local",1,"EMPLOYEE");
    }

    @Autowired com.forgegate.security.JwtService jwt;
    @Test void unauthenticatedRequestReturns401() throws Exception { mvc.perform(get("/api/requests/mine")).andExpect(status().isUnauthorized()); }
}
