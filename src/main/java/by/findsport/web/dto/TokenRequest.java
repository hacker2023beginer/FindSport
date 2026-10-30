package by.findsport.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Токен из ссылки подтверждения e-mail. */
public record TokenRequest(@NotBlank @Size(max = 100) String token) {
}
