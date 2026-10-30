package by.findsport.web.dto;

import by.findsport.domain.UserRole;
import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(@NotNull UserRole role) {
}
