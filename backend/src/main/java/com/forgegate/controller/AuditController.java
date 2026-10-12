package com.forgegate.controller;
import com.forgegate.service.ForgeGateService; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/audit") @PreAuthorize("hasAnyRole('ADMIN','SECURITY_REVIEWER')")
public class AuditController {
 private final ForgeGateService service; public AuditController(ForgeGateService service){this.service=service;}
 @GetMapping public Map<String,Object> list(@RequestParam(required=false) String actor,@RequestParam(required=false) String action,@RequestParam(required=false) String entity,@RequestParam(required=false) String from,@RequestParam(required=false) String to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){List<Map<String,Object>> rows=service.audit(actor,action,entity,Math.max(0,page),Math.max(1,Math.min(100,size)),from,to);return Map.of("items",rows,"page",Math.max(0,page),"size",Math.max(1,Math.min(100,size)));}
}
