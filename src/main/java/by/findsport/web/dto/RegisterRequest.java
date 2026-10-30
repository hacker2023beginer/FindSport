package by.findsport.web.dto;

import by.findsport.domain.UserRole;
import by.findsport.service.RegistrationService;
import by.findsport.web.validation.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Регистрация по e-mail и паролю (ТЗ п. 4.2.2). Тип учетной записи — пользователь или представитель
 * организации/тренер (п. 3.2.2). Без принятия соглашения и политики обработки ПД регистрация невозможна
 * (п. 4.6.8, 4.13.10).
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @PasswordPolicy String password,
        @Size(max = 100) String displayName,
        @Schema(allowableValues = {"USER", "PROVIDER"}) @NotNull UserRole accountType,
        @AssertTrue(message = "Необходимо принять пользовательское соглашение") boolean termsAccepted,
        @AssertTrue(message = "Необходимо дать согласие на обработку персональных данных") boolean privacyPolicyAccepted) {

    @Schema(hidden = true)
    @AssertTrue(message = "Можно зарегистрироваться как пользователь или как организация/тренер")
    public boolean isAccountTypeAllowed() {
        return accountType == null || RegistrationService.SELF_REGISTRATION_ROLES.contains(accountType);
    }
}
