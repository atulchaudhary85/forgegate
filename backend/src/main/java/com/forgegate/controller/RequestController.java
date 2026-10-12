package com.forgegate.controller;

import com.forgegate.dto.ApiDtos;
import com.forgegate.service.ForgeGateService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/requests")
public class RequestController {
    private final ForgeGateService service;
    public RequestController(ForgeGateService service){this.service=service;}
    @PostMapping public Map<String,Object> create(@Valid @RequestBody ApiDtos.CreateRequest body, Authentication auth){return service.createRequest(auth.getName(),body);}
    @GetMapping("/mine") public List<Map<String,Object>> mine(Authentication auth){return service.myRequests(auth.getName());}
    @GetMapping("/pending-for-me") public List<Map<String,Object>> pending(Authentication auth){return service.pendingForMe(auth.getName());}
    @PostMapping("/{id}/approve") public Map<String,Object> approve(@PathVariable long id,Authentication auth){return service.decide(auth.getName(),id,true,null);}
    @PostMapping("/{id}/reject") public Map<String,Object> reject(@PathVariable long id,@Valid @RequestBody ApiDtos.DecisionRequest body,Authentication auth){return service.decide(auth.getName(),id,false,body.comment());}
}
