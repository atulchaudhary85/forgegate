package com.forgegate.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgegate.config.ApiException;
import com.forgegate.dto.ApiDtos;
import com.forgegate.repository.ForgeGateRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;

@Service
public class ForgeGateService {
    private final JdbcTemplate db;
    private final ForgeGateRepository repo;
    private final PasswordEncoder passwords;
    private final RedisLockService locks;
    private final RequestStateMachine states;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final PolicyEngine policyEngine;
    private final ResourcePolicyCache policyCache;
    private final TransactionTemplate transactions;
    public ForgeGateService(JdbcTemplate db, ForgeGateRepository repo, PasswordEncoder passwords, RedisLockService locks, RequestStateMachine states, StringRedisTemplate redis, ObjectMapper mapper, PolicyEngine policyEngine, ResourcePolicyCache policyCache, TransactionTemplate transactions) {
        this.db=db; this.repo=repo; this.passwords=passwords; this.locks=locks; this.states=states; this.redis=redis; this.mapper=mapper; this.policyEngine=policyEngine; this.policyCache=policyCache; this.transactions=transactions;
    }
    public Map<String,Object> login(String email, String password) {
        Map<String,Object> u=repo.userByEmail(email);
        if (u==null || !Boolean.TRUE.equals(u.get("is_active")) || !passwords.matches(password, String.valueOf(u.get("password_hash")))) throw new ApiException(401,"Invalid email or password");
        return u;
    }
    public Map<String,Object> me(String email) { Map<String,Object> u=repo.userByEmail(email); if(u==null||!Boolean.TRUE.equals(u.get("is_active"))) throw ApiException.forbidden("User is inactive"); u.remove("password_hash"); return u; }
    public long userId(String email) { Map<String,Object> u=repo.userByEmail(email); if(u==null||!Boolean.TRUE.equals(u.get("is_active"))) throw ApiException.forbidden("User is inactive"); return ((Number)u.get("id")).longValue(); }
    public String role(String email) { return String.valueOf(me(email).get("role")); }

    public Map<String,Object> createRequest(String email, ApiDtos.CreateRequest b) {
        long uid=userId(email);
        return locks.withLock("idempotency:"+uid+":"+b.idempotencyKey(), Duration.ofSeconds(3),
            () -> locks.withLock("request:"+uid+":"+b.resourceId(), Duration.ofSeconds(3),
                () -> transactions.execute(tx -> createRequestLocked(uid,b))));
    }
    private Map<String,Object> createRequestLocked(long uid, ApiDtos.CreateRequest b) {
        List<Map<String,Object>> existing=db.queryForList("SELECT id,status FROM access_requests WHERE requester_id=? AND idempotency_key=?",uid,b.idempotencyKey());
        if(!existing.isEmpty()) return existing.get(0);
        Map<String,Object> user=repo.userById(uid);
        Map<String,Object> policy=policyCache.get(b.resourceId());
        policyEngine.validate(policy, String.valueOf(user.get("department")), b.durationDays());
        Integer active=db.queryForObject("SELECT count(*) FROM access_grants WHERE user_id=? AND resource_id=? AND status='ACTIVE'",Integer.class,uid,b.resourceId());
        Integer open=db.queryForObject("SELECT count(*) FROM access_requests WHERE requester_id=? AND resource_id=? AND status IN ('PENDING_MANAGER','PENDING_SECURITY','APPROVED')",Integer.class,uid,b.resourceId());
        if(active!=null&&active>0) throw ApiException.conflict("You already have active access to this resource");
        if(open!=null&&open>0) throw ApiException.conflict("You already have an open request for this resource");
        Object manager=user.get("manager_id"); long approver; String action;
        if(manager==null) {
            List<Map<String,Object>> admins=db.queryForList("SELECT u.id FROM users u JOIN roles r ON r.id=u.role_id WHERE u.is_active=true AND r.name='ADMIN' ORDER BY u.id LIMIT 1");
            if(admins.isEmpty()) throw ApiException.unavailable("No active ADMIN is available for null-manager escalation");
            approver=((Number)admins.get(0).get("id")).longValue(); action="MANAGER_ESCALATED_TO_ADMIN";
        } else { approver=((Number)manager).longValue(); action="REQUEST_CREATED"; }
        String status="PENDING_MANAGER";
        long id=db.queryForObject("INSERT INTO access_requests(requester_id,resource_id,reason,duration_days,status,idempotency_key) VALUES (?,?,?,?,?,?) RETURNING id",Long.class,uid,b.resourceId(),b.reason().trim(),b.durationDays(),status,b.idempotencyKey());
        db.update("INSERT INTO approvals(request_id,approver_id,stage,decision) VALUES (?,?,'MANAGER','PENDING')",id,approver);
        audit(uid,action,"access_request",id,Map.of("status",status,"approverId",approver));
        notifyUser(approver,"APPROVAL_REQUIRED","Access request #"+id+" needs manager-stage review");
        return Map.of("id",id,"status",status);
    }

    public Map<String,Object> decide(String email,long id,boolean approve,String comment) {
        List<Map<String,Object>> identity=db.queryForList("SELECT requester_id,resource_id FROM access_requests WHERE id=?",id);
        if(identity.isEmpty()) throw ApiException.missing("Request not found");
        long requester=((Number)identity.get(0).get("requester_id")).longValue();
        long resource=((Number)identity.get(0).get("resource_id")).longValue();
        return locks.withLock("request:"+requester+":"+resource, Duration.ofSeconds(3), () -> transactions.execute(tx -> decideLocked(email,id,approve,comment)));
    }
    private Map<String,Object> decideLocked(String email,long id,boolean approve,String comment) {
        long actor=userId(email);
        List<Map<String,Object>> rows=db.queryForList("SELECT ar.id,ar.requester_id,ar.resource_id,ar.duration_days,ar.status,u.manager_id,p.requires_security_approval,r.sensitivity FROM access_requests ar JOIN users u ON u.id=ar.requester_id JOIN resources r ON r.id=ar.resource_id LEFT JOIN LATERAL (SELECT p.requires_security_approval FROM policies p WHERE p.resource_id=r.id ORDER BY p.is_active DESC,p.id DESC LIMIT 1) p ON true WHERE ar.id=? FOR UPDATE OF ar",id);
        if(rows.isEmpty()) throw ApiException.missing("Request not found");
        Map<String,Object> r=rows.get(0); long requester=((Number)r.get("requester_id")).longValue(); String status=String.valueOf(r.get("status"));
        if(actor==requester) throw ApiException.forbidden("You cannot approve or reject your own request");
        if(!approve&&(comment==null||comment.trim().isEmpty())) throw ApiException.bad("A non-empty comment is required to reject a request");
        String stage,next; long assigned;
        if("PENDING_MANAGER".equals(status)) {
            stage="MANAGER"; Object manager=r.get("manager_id");
            if(manager==null) {
                List<Map<String,Object>> admins=db.queryForList("SELECT u.id FROM users u JOIN roles ro ON ro.id=u.role_id WHERE u.is_active=true AND ro.name='ADMIN' ORDER BY u.id LIMIT 1");
                if(admins.isEmpty()) throw ApiException.unavailable("No active ADMIN is available for null-manager escalation"); assigned=((Number)admins.get(0).get("id")).longValue();
            } else assigned=((Number)manager).longValue();
            if(actor!=assigned) throw ApiException.forbidden("Only the requester's assigned manager or escalated admin can review this stage");
            if(manager==null && !"ADMIN".equals(role(email))) throw ApiException.forbidden("Null-manager escalation is handled by ADMIN");
            next=!approve?"REJECTED":Boolean.TRUE.equals(r.get("requires_security_approval"))||"HIGH".equals(r.get("sensitivity"))?"PENDING_SECURITY":"APPROVED";
        } else if("PENDING_SECURITY".equals(status)) {
            stage="SECURITY"; if(!"SECURITY_REVIEWER".equals(role(email))) throw ApiException.forbidden("Only SECURITY_REVIEWER can decide the security stage");
            assigned=actor; next=approve?"APPROVED":"REJECTED";
        } else throw ApiException.conflict("Request is not awaiting approval");
        states.assertTransition(status,next);
        if ("PENDING_SECURITY".equals(next)) {
            List<Map<String,Object>> reviewers=db.queryForList("SELECT u.id FROM users u JOIN roles ro ON ro.id=u.role_id WHERE u.is_active=true AND ro.name='SECURITY_REVIEWER' ORDER BY u.id LIMIT 1");
            if(reviewers.isEmpty()) throw ApiException.unavailable("No active SECURITY_REVIEWER is available");
            long reviewer=((Number)reviewers.get(0).get("id")).longValue();
            db.update("UPDATE approvals SET approver_id=?,decision=?,comment=?,decided_at=now() WHERE request_id=? AND stage=? AND decision='PENDING'",actor,approve?"APPROVED":"REJECTED",comment,id,stage);
            db.update("INSERT INTO approvals(request_id,approver_id,stage,decision) VALUES (?,?,'SECURITY','PENDING')",id,reviewer);
        } else {
            db.update("UPDATE approvals SET approver_id=?,decision=?,comment=?,decided_at=now() WHERE request_id=? AND stage=? AND decision='PENDING'",actor,approve?"APPROVED":"REJECTED",comment,id,stage);
        }
        db.update("UPDATE access_requests SET status=?,updated_at=now() WHERE id=?",next,id);
        audit(actor,stage+"_"+(approve?"APPROVED":"REJECTED"),"access_request",id,Map.of("from",status,"to",next,"comment",comment==null?"":comment));
        notifyUser(requester,"REQUEST_UPDATE","Request #"+id+" is now "+("APPROVED".equals(next)?"ACTIVE":next));
        if("APPROVED".equals(next)) createGrantForApproved(id,requester,((Number)r.get("resource_id")).longValue(),((Number)r.get("duration_days")).intValue(),actor);
        if("PENDING_SECURITY".equals(next)) {
            Long reviewer=db.queryForObject("SELECT approver_id FROM approvals WHERE request_id=? AND stage='SECURITY'",Long.class,id);
            notifyUser(reviewer,"APPROVAL_REQUIRED","Request #"+id+" needs security review");
        }
        return Map.of("id",id,"status", "APPROVED".equals(next)?"ACTIVE":next);
    }
    private void createGrantForApproved(long requestId,long userId,long resourceId,int days,long actor) {
        states.assertTransition("APPROVED","ACTIVE");
        Timestamp expires=Timestamp.from(Instant.now().plus(Duration.ofDays(days)));
        long gid=db.queryForObject("INSERT INTO access_grants(request_id,user_id,resource_id,expires_at,status) VALUES (?,?,?,?,'ACTIVE') RETURNING id",Long.class,requestId,userId,resourceId,expires);
        db.update("UPDATE access_requests SET status='ACTIVE',updated_at=now() WHERE id=?",requestId);
        audit(actor,"ACCESS_GRANTED","access_grant",gid,Map.of("requestId",requestId,"expiresAt",expires.toInstant().toString()));
        notifyUser(userId,"ACCESS_GRANTED","Access granted for request #"+requestId);
    }

    @Transactional
    public Map<String,Object> revoke(String email,long grantId) {
        long actor=userId(email); String role=role(email); if(!Set.of("ADMIN","SECURITY_REVIEWER").contains(role)) throw ApiException.forbidden("ADMIN or SECURITY_REVIEWER role required");
        List<Map<String,Object>> rows=db.queryForList("SELECT id,request_id,user_id,status FROM access_grants WHERE id=? FOR UPDATE",grantId);
        if(rows.isEmpty()) throw ApiException.missing("Grant not found"); Map<String,Object> g=rows.get(0);
        if(!"ACTIVE".equals(g.get("status"))) throw ApiException.conflict("Grant is not active");
        states.assertTransition("ACTIVE","REVOKED");
        db.update("UPDATE access_grants SET status='REVOKED',revoked_at=now() WHERE id=?",grantId);
        db.update("UPDATE access_requests SET status='REVOKED',updated_at=now() WHERE id=?",g.get("request_id"));
        audit(actor,"ACCESS_REVOKED","access_grant",grantId,Map.of("requestId",g.get("request_id")));
        notifyUser(((Number)g.get("user_id")).longValue(),"ACCESS_REVOKED","Access grant #"+grantId+" was revoked");
        return Map.of("id",grantId,"status","REVOKED");
    }

    @Transactional
    public int expireGrants() {
        List<Map<String,Object>> expired=db.queryForList("SELECT id,request_id,user_id FROM access_grants WHERE status='ACTIVE' AND expires_at<=now() FOR UPDATE SKIP LOCKED");
        int count=0;
        for(Map<String,Object> g:expired) {
            long id=((Number)g.get("id")).longValue(), req=((Number)g.get("request_id")).longValue(), uid=((Number)g.get("user_id")).longValue();
            states.assertTransition("ACTIVE","EXPIRED");
            if(db.update("UPDATE access_grants SET status='EXPIRED',revoked_at=now() WHERE id=? AND status='ACTIVE'",id)>0) {
                db.update("UPDATE access_requests SET status='EXPIRED',updated_at=now() WHERE id=? AND status='ACTIVE'",req);
                audit(null,"ACCESS_EXPIRED","access_grant",id,Map.of("source","scheduler","requestId",req)); notifyUser(uid,"ACCESS_EXPIRED","Access grant #"+id+" expired"); count++;
            }
        }
        return count;
    }

    public List<Map<String,Object>> myRequests(String email) { return db.queryForList("SELECT ar.id,ar.reason,ar.duration_days,ar.status,ar.created_at,ar.updated_at,r.name resource,r.sensitivity FROM access_requests ar JOIN resources r ON r.id=ar.resource_id WHERE ar.requester_id=? ORDER BY ar.created_at DESC",userId(email)); }
    public List<Map<String,Object>> pendingForMe(String email) { long id=userId(email); return db.queryForList("SELECT DISTINCT ar.id,ar.reason,ar.duration_days,ar.status,ar.created_at,u.name requester,r.name resource,r.sensitivity FROM access_requests ar JOIN users u ON u.id=ar.requester_id JOIN resources r ON r.id=ar.resource_id JOIN approvals a ON a.request_id=ar.id AND a.decision='PENDING' WHERE (a.approver_id=? OR (ar.status='PENDING_SECURITY' AND EXISTS (SELECT 1 FROM users reviewer JOIN roles ro ON ro.id=reviewer.role_id WHERE reviewer.id=? AND reviewer.is_active=true AND ro.name='SECURITY_REVIEWER'))) ORDER BY ar.created_at",id,id); }
    public List<Map<String,Object>> allActiveGrants() { return db.queryForList("SELECT g.id,g.request_id,g.user_id,g.granted_at,g.expires_at,g.status,r.name resource,u.name user_name,u.email FROM access_grants g JOIN resources r ON r.id=g.resource_id JOIN users u ON u.id=g.user_id WHERE g.status='ACTIVE' ORDER BY g.expires_at"); }
    public List<Map<String,Object>> myGrants(String email,boolean soon) { String q="SELECT g.id,g.request_id,g.granted_at,g.expires_at,g.status,r.name resource,r.type,u.name user_name FROM access_grants g JOIN resources r ON r.id=g.resource_id JOIN users u ON u.id=g.user_id WHERE g.user_id=? AND g.status='ACTIVE'"+(soon?" AND g.expires_at<=now()+interval '7 days'":"")+" ORDER BY g.expires_at"; return db.queryForList(q,userId(email)); }
    public List<Map<String,Object>> notifications(String email) { return db.queryForList("SELECT id,type,message,is_read,created_at FROM notifications WHERE user_id=? ORDER BY created_at DESC LIMIT 100",userId(email)); }
    @Transactional public void markNotificationRead(String email,long id) { int n=db.update("UPDATE notifications SET is_read=true WHERE id=? AND user_id=?",id,userId(email)); if(n==0) throw ApiException.missing("Notification not found"); }
    public Map<String,Object> summary(String email) { long uid=userId(email); Map<String,Object> m=new LinkedHashMap<>(); m.put("myRequests",db.queryForObject("SELECT count(*) FROM access_requests WHERE requester_id=?",Integer.class,uid)); m.put("pendingForMe",db.queryForObject("SELECT count(DISTINCT a.request_id) FROM approvals a JOIN access_requests ar ON ar.id=a.request_id WHERE a.decision='PENDING' AND (a.approver_id=? OR (ar.status='PENDING_SECURITY' AND EXISTS (SELECT 1 FROM users reviewer JOIN roles ro ON ro.id=reviewer.role_id WHERE reviewer.id=? AND reviewer.is_active=true AND ro.name='SECURITY_REVIEWER'))) ",Integer.class,uid,uid)); m.put("activeGrants",db.queryForObject("SELECT count(*) FROM access_grants WHERE user_id=? AND status='ACTIVE'",Integer.class,uid)); m.put("expiringSoon",myGrants(email,true)); m.put("unreadNotifications",db.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND is_read=false",Integer.class,uid)); return m; }
    public List<Map<String,Object>> audit(String actor,String action,String entity,int page,int size,String from,String to) {
        StringBuilder q=new StringBuilder("SELECT a.id,a.actor_id,u.name actor_name,a.action,a.entity_type,a.entity_id,a.details,a.created_at FROM audit_logs a LEFT JOIN users u ON u.id=a.actor_id WHERE 1=1"); List<Object> args=new ArrayList<>();
        if(actor!=null&&!actor.isBlank()){q.append(" AND (u.email ILIKE ? OR cast(a.actor_id as text)=?)");args.add("%"+actor+"%");args.add(actor);}
        if(action!=null&&!action.isBlank()){q.append(" AND a.action ILIKE ?");args.add("%"+action+"%");}
        if(entity!=null&&!entity.isBlank()){q.append(" AND (a.entity_type ILIKE ? OR cast(a.entity_id as text)=?)");args.add("%"+entity+"%");args.add(entity);}
        if(from!=null&&!from.isBlank()){q.append(" AND a.created_at>=?::timestamptz");args.add(from);}
        if(to!=null&&!to.isBlank()){q.append(" AND a.created_at<=?::timestamptz");args.add(to);}
        q.append(" ORDER BY a.created_at DESC LIMIT ? OFFSET ?");args.add(Math.max(1,Math.min(size,100)));args.add(Math.max(0,page)*Math.max(1,Math.min(size,100)));return db.queryForList(q.toString(),args.toArray());
    }

    public Map<String,Object> resourcePolicy(long resourceId) { return policyCache.get(resourceId); }
    @CacheEvict(cacheNames="resourcePolicy", allEntries=true)
    @Transactional public Map<String,Object> saveResource(String email,Long id,ApiDtos.ResourceInput b) {
        boolean creating=id==null;
        String type=b.type().toUpperCase(Locale.ROOT), sensitivity=b.sensitivity().toUpperCase(Locale.ROOT);
        if(!Set.of("GITHUB_REPO","DATABASE","DASHBOARD","CLOUD","OTHER").contains(type)||!Set.of("LOW","MEDIUM","HIGH").contains(sensitivity)) throw ApiException.bad("Invalid resource type or sensitivity");
        if(id==null) id=db.queryForObject("INSERT INTO resources(name,type,sensitivity,owner_department,is_active) VALUES (?,?,?,?,?) RETURNING id",Long.class,b.name(),type,sensitivity,b.ownerDepartment(),b.isActive()==null||b.isActive());
        else if(db.update("UPDATE resources SET name=?,type=?,sensitivity=?,owner_department=?,is_active=?,updated_at=now() WHERE id=?",b.name(),type,sensitivity,b.ownerDepartment(),b.isActive()==null||b.isActive(),id)==0) throw ApiException.missing("Resource not found");
        audit(userId(email),creating?"RESOURCE_CREATED":"RESOURCE_UPDATED","resource",id,Map.of("name",b.name())); return db.queryForMap("SELECT * FROM resources WHERE id=?",id);
    }
    @CacheEvict(cacheNames="resourcePolicy", allEntries=true)
    @Transactional public Map<String,Object> savePolicy(String email,Long id,ApiDtos.PolicyInput b) {
        boolean creating=id==null;
        if(b.maxDurationDays()!=1&&b.maxDurationDays()!=7&&b.maxDurationDays()!=30) throw ApiException.bad("maxDurationDays must be 1, 7, or 30");
        if(db.queryForObject("SELECT count(*) FROM resources WHERE id=?",Integer.class,b.resourceId())==0) throw ApiException.missing("Resource not found");
        if(id==null) id=db.queryForObject("INSERT INTO policies(resource_id,allowed_department,max_duration_days,requires_security_approval,is_active) VALUES (?,?,?,?,?) RETURNING id",Long.class,b.resourceId(),b.allowedDepartment(),b.maxDurationDays(),b.requiresSecurityApproval(),b.isActive()==null||b.isActive());
        else if(db.update("UPDATE policies SET resource_id=?,allowed_department=?,max_duration_days=?,requires_security_approval=?,is_active=?,updated_at=now() WHERE id=?",b.resourceId(),b.allowedDepartment(),b.maxDurationDays(),b.requiresSecurityApproval(),b.isActive()==null||b.isActive(),id)==0) throw ApiException.missing("Policy not found");
        audit(userId(email),creating?"POLICY_CREATED":"POLICY_UPDATED","policy",id,Map.of("resourceId",b.resourceId()));return db.queryForMap("SELECT * FROM policies WHERE id=?",id);
    }
    @Transactional public Map<String,Object> saveUser(String email,Long id,ApiDtos.UserInput b) {
        boolean creating=id==null;
        Long roleId; try { roleId=db.queryForObject("SELECT id FROM roles WHERE name=?",Long.class,b.role().toUpperCase(Locale.ROOT)); } catch(Exception ex) { throw ApiException.bad("Invalid role"); }
        if(b.managerId()!=null&&db.queryForObject("SELECT count(*) FROM users WHERE id=?",Integer.class,b.managerId())==0) throw ApiException.bad("managerId does not exist");
        if(id==null) { if(b.password()==null||b.password().length()<12) throw ApiException.bad("New users need a password of at least 12 characters"); id=db.queryForObject("INSERT INTO users(name,email,password_hash,department,manager_id,role_id,is_active) VALUES (?,?,?,?,?,?,?) RETURNING id",Long.class,b.name(),b.email(),passwords.encode(b.password()),b.department(),b.managerId(),roleId,b.isActive()==null||b.isActive()); }
        else { if(db.queryForObject("SELECT count(*) FROM users WHERE id=?",Integer.class,id)==0) throw ApiException.missing("User not found"); if(b.password()!=null&&!b.password().isBlank()) { if(b.password().length()<12) throw ApiException.bad("Password must be at least 12 characters"); db.update("UPDATE users SET name=?,email=?,department=?,manager_id=?,role_id=?,is_active=?,password_hash=?,updated_at=now() WHERE id=?",b.name(),b.email(),b.department(),b.managerId(),roleId,b.isActive()==null||b.isActive(),passwords.encode(b.password()),id); } else db.update("UPDATE users SET name=?,email=?,department=?,manager_id=?,role_id=?,is_active=?,updated_at=now() WHERE id=?",b.name(),b.email(),b.department(),b.managerId(),roleId,b.isActive()==null||b.isActive(),id); }
        audit(userId(email),creating?"USER_CREATED":"USER_UPDATED","user",id,Map.of("email",b.email(),"role",b.role()));return repo.userById(id);
    }
    public List<Map<String,Object>> adminList(String table) { if(!Set.of("resources","policies","users").contains(table)) throw ApiException.bad("Unsupported admin table"); if(table.equals("users")) return db.queryForList("SELECT u.id,u.name,u.email,u.department,u.manager_id,u.is_active,r.name role FROM users u JOIN roles r ON r.id=u.role_id ORDER BY u.id"); return db.queryForList("SELECT * FROM "+table+" ORDER BY id DESC"); }
    @CacheEvict(cacheNames="resourcePolicy", allEntries=true)
    @Transactional public void softDelete(String email,String table,long id) { if(!Set.of("resources","policies","users").contains(table)) throw ApiException.bad("Unsupported admin table"); int n=db.update("UPDATE "+table+" SET is_active=false,updated_at=now() WHERE id=?",id); if(n==0) throw ApiException.missing("Record not found"); audit(userId(email),table.toUpperCase(Locale.ROOT)+"_DEACTIVATED",table,id,Map.of("is_active",false)); }
    private void audit(Long actor,String action,String type,long id,Map<String,Object> details) {
        try { db.update("INSERT INTO audit_logs(actor_id,action,entity_type,entity_id,details) VALUES (?,?,?,?,?::jsonb)",actor,action,type,id,mapper.writeValueAsString(details)); }
        catch(JsonProcessingException ex) { throw new IllegalStateException("Could not serialize audit details",ex); }
    }
    private void notifyUser(long user,String type,String message) { db.update("INSERT INTO notifications(user_id,type,message) VALUES (?,?,?)",user,type,message); }
}
