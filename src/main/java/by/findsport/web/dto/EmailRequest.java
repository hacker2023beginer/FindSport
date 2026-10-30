package by.findsport.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Повторная отправка письма подтверждения или запрос восстановления доступа. */
public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
}
