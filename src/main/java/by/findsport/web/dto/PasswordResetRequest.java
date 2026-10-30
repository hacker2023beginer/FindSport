package by.findsport.web.dto;

import by.findsport.web.validation.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Новый пароль по ссылке восстановления доступа. */
public record PasswordResetRequest(@NotBlank @Size(max = 100) String token, @PasswordPolicy String newPassword) {
}
