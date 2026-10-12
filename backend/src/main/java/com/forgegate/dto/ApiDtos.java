package com.forgegate.dto;

import jakarta.validation.constraints.*;
import java.util.Map;

public final class ApiDtos {
    private ApiDtos() {}
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    public record LoginResponse(String token, Map<String,Object> user) {}
    public record CreateRequest(@NotNull Long resourceId, @NotBlank @Size(min=8,max=2000) String reason, @Min(1) @Max(30) int durationDays, @NotBlank @Size(max=100) String idempotencyKey) {}
    public record DecisionRequest(@Size(max=2000) String comment) {}
    public record ResourceInput(@NotBlank String name, @NotBlank String type, @NotBlank String sensitivity, @NotBlank String ownerDepartment, Boolean isActive) {}
    public record PolicyInput(@NotNull Long resourceId, String allowedDepartment, @Min(1) @Max(30) int maxDurationDays, boolean requiresSecurityApproval, Boolean isActive) {}
    public record UserInput(@NotBlank String name, @Email @NotBlank String email, @NotBlank String department, Long managerId, @NotBlank String role, String password, Boolean isActive) {}
}
