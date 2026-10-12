package com.forgegate.controller;

import com.forgegate.service.ForgeGateService;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/grants")
public class GrantController {
    private final ForgeGateService service; public GrantController(ForgeGateService service){this.service=service;}
    @GetMapping("/all") @PreAuthorize("hasAnyRole('ADMIN','SECURITY_REVIEWER')") public List<Map<String,Object>> all(){return service.allActiveGrants();}
    @GetMapping("/mine") public List<Map<String,Object>> mine(Authentication a){return service.myGrants(a.getName(),false);}
    @GetMapping("/expiring-soon") public List<Map<String,Object>> soon(Authentication a){return service.myGrants(a.getName(),true);}
    @PostMapping("/{id}/revoke") @PreAuthorize("hasAnyRole('ADMIN','SECURITY_REVIEWER')") public Map<String,Object> revoke(@PathVariable long id,Authentication a){return service.revoke(a.getName(),id);}
}
