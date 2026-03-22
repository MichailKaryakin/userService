package org.example.user.dto;

import org.example.user.enums.RoleName;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        Set<RoleName> roles,
        boolean enabled,
        Instant createdAt
) {
}
