package com.forgegate.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class) ResponseEntity<Map<String,Object>> api(ApiException ex, HttpServletRequest req) { return body(ex.status(), ex.getMessage(), req); }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException ex, HttpServletRequest req) { return body(400, ex.getBindingResult().getFieldErrors().stream().findFirst().map(e -> e.getField()+": "+e.getDefaultMessage()).orElse("Invalid request"), req); }
    @ExceptionHandler(DuplicateKeyException.class) ResponseEntity<Map<String,Object>> duplicate(DuplicateKeyException ex, HttpServletRequest req) { return body(409, "A conflicting record already exists", req); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<Map<String,Object>> integrity(DataIntegrityViolationException ex, HttpServletRequest req) { return body(409, "The operation conflicts with a database constraint", req); }
    @ExceptionHandler(RedisConnectionFailureException.class) ResponseEntity<Map<String,Object>> redisUnavailable(RedisConnectionFailureException ex, HttpServletRequest req) { return body(503, "Redis is unavailable; operation was not processed safely", req); }
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ResponseEntity<Map<String,Object>> forbidden(Exception ex, HttpServletRequest req) { return body(403, "Access denied", req); }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String,Object>> unauthenticated(AuthenticationException ex, HttpServletRequest req) { return body(401, "Authentication required", req); }
    @ExceptionHandler(Exception.class) ResponseEntity<Map<String,Object>> unknown(Exception ex, HttpServletRequest req) { return body(500, "Internal server error", req); }
    private ResponseEntity<Map<String,Object>> body(int status, String message, HttpServletRequest req) {
        Map<String,Object> result = new LinkedHashMap<>(); result.put("timestamp", Instant.now()); result.put("status", status); result.put("message", message); result.put("path", req.getRequestURI());
        return ResponseEntity.status(status).body(result);
    }
}
