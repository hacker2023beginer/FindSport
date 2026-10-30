package by.findsport.web.dto;

import by.findsport.web.validation.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Смена пароля в личном кабинете (ТЗ п. 4.10.2). */
public record PasswordChangeRequest(
        @NotBlank @Size(max = 200) String currentPassword,
        @PasswordPolicy String newPassword) {
}
