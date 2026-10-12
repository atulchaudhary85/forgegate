package com.forgegate.controller;

import com.forgegate.dto.ApiDtos;
import com.forgegate.security.JwtService;
import com.forgegate.service.ForgeGateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;


@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final ForgeGateService service; private final JwtService jwt; private final com.forgegate.security.LoginRateLimiter rateLimiter;
    public AuthController(ForgeGateService service,JwtService jwt,com.forgegate.security.LoginRateLimiter rateLimiter){this.service=service;this.jwt=jwt;this.rateLimiter=rateLimiter;}
    @PostMapping("/login") public ApiDtos.LoginResponse login(@Valid @RequestBody ApiDtos.LoginRequest body,HttpServletRequest request){
        rateLimiter.check(request.getHeader("X-Real-IP") == null ? request.getRemoteAddr() : request.getHeader("X-Real-IP"));
        Map<String,Object> user=service.login(body.email(),body.password());
        String token=jwt.issue(String.valueOf(user.get("email")),((Number)user.get("id")).longValue(),String.valueOf(user.get("role")));
        user.remove("password_hash"); return new ApiDtos.LoginResponse(token,user);
    }
    @GetMapping("/me") public Map<String,Object> me(org.springframework.security.core.Authentication auth){return service.me(auth.getName());}
}
