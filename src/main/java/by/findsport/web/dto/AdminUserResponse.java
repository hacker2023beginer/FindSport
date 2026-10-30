package by.findsport.web.dto;

import by.findsport.domain.UserRole;
import java.time.Instant;

/** Строка реестра учетных записей административной части (ТЗ п. 4.5.1, 4.5.2). */
public record AdminUserResponse(
        Long id,
        String email,
        UserRole role,
        String displayName,
        boolean emailVerified,
        boolean blocked,
        Instant lockedUntil,
        Instant deletionRequestedAt,
        Instant createdAt) {
}
