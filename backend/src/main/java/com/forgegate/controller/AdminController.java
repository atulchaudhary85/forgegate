package com.forgegate.controller;

import com.forgegate.dto.ApiDtos;
import com.forgegate.service.ForgeGateService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map; import org.springframework.security.core.Authentication;

@RestController @RequestMapping("/api/admin") @PreAuthorize("hasRole('ADMIN')")
public class AdminController {
 private final ForgeGateService service; public AdminController(ForgeGateService service){this.service=service;}
 @GetMapping("/resources") public List<Map<String,Object>> resources(){return service.adminList("resources");}
 @PostMapping("/resources") public Map<String,Object> createResource(@Valid @RequestBody ApiDtos.ResourceInput b,Authentication a){return service.saveResource(a.getName(),null,b);}
 @PutMapping("/resources/{id}") public Map<String,Object> updateResource(@PathVariable long id,@Valid @RequestBody ApiDtos.ResourceInput b,Authentication a){return service.saveResource(a.getName(),id,b);}
 @DeleteMapping("/resources/{id}") public Map<String,Object> disableResource(@PathVariable long id,Authentication a){service.softDelete(a.getName(),"resources",id);return Map.of("id",id,"is_active",false);}
 @GetMapping("/policies") public List<Map<String,Object>> policies(){return service.adminList("policies");}
 @PostMapping("/policies") public Map<String,Object> createPolicy(@Valid @RequestBody ApiDtos.PolicyInput b,Authentication a){return service.savePolicy(a.getName(),null,b);}
 @PutMapping("/policies/{id}") public Map<String,Object> updatePolicy(@PathVariable long id,@Valid @RequestBody ApiDtos.PolicyInput b,Authentication a){return service.savePolicy(a.getName(),id,b);}
 @DeleteMapping("/policies/{id}") public Map<String,Object> disablePolicy(@PathVariable long id,Authentication a){service.softDelete(a.getName(),"policies",id);return Map.of("id",id,"is_active",false);}
 @GetMapping("/users") public List<Map<String,Object>> users(){return service.adminList("users");}
 @PostMapping("/users") public Map<String,Object> createUser(@Valid @RequestBody ApiDtos.UserInput b,Authentication a){return service.saveUser(a.getName(),null,b);}
 @PutMapping("/users/{id}") public Map<String,Object> updateUser(@PathVariable long id,@Valid @RequestBody ApiDtos.UserInput b,Authentication a){return service.saveUser(a.getName(),id,b);}
 @DeleteMapping("/users/{id}") public Map<String,Object> disableUser(@PathVariable long id,Authentication a){service.softDelete(a.getName(),"users",id);return Map.of("id",id,"is_active",false);}
}
