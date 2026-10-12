package com.forgegate.controller;
import com.forgegate.service.ForgeGateService; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import java.util.Map;
@RestController @RequestMapping("/api/dashboard") public class DashboardController { private final ForgeGateService service; public DashboardController(ForgeGateService service){this.service=service;} @GetMapping("/summary") public Map<String,Object> summary(Authentication a){return service.summary(a.getName());} }
