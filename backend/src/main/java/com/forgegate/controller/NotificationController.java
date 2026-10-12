package com.forgegate.controller;
import com.forgegate.service.ForgeGateService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
@RestController @RequestMapping("/api/notifications")
public class NotificationController {
 private final ForgeGateService service; public NotificationController(ForgeGateService service){this.service=service;}
 @GetMapping public List<Map<String,Object>> list(Authentication a){return service.notifications(a.getName());}
 @PostMapping("/{id}/read") public Map<String,Object> read(@PathVariable long id,Authentication a){service.markNotificationRead(a.getName(),id);return Map.of("id",id,"is_read",true);}
}
