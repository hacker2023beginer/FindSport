package by.findsport.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import by.findsport.domain.UserRole;
import by.findsport.web.dto.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Модульные проверки форм регистрации (ТЗ п. 4.2.13, 6.7 «Валидация данных»): e-mail, сложность пароля, согласия. */
class AuthRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final PasswordPolicyValidator passwordPolicy = new PasswordPolicyValidator();

    @ParameterizedTest
    @ValueSource(strings = {"Secret-123", "пароль2026", "abcdefg1", "Ab1Ab1Ab1Ab1"})
    void acceptsPasswordsWithLettersAndDigits(String password) {
        assertThat(passwordPolicy.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short1", "12345678", "onlyletters", "with space 1"})
    void rejectsWeakPasswords(String password) {
        assertThat(passwordPolicy.isValid(password, null)).isFalse();
    }

    @Test
    void rejectsNullAndPasswordsLongerThanBcryptLimit() {
        assertThat(passwordPolicy.isValid(null, null)).isFalse();
        // 40 кириллических символов — 80 байт UTF-8: BCrypt учел бы только первые 72
        assertThat(passwordPolicy.isValid("я".repeat(39) + "1", null)).isFalse();
        assertThat(passwordPolicy.isValid("a".repeat(71) + "1", null)).isTrue();
    }

    @Test
    void validRegistrationPasses() {
        assertThat(validator.validate(registration("user@example.by", UserRole.USER, true, true))).isEmpty();
        assertThat(validator.validate(registration("coach@example.by", UserRole.PROVIDER, true, true))).isEmpty();
    }

    @Test
    void registrationRequiresEmailFormatAndBothConsents() {
        assertThat(fields(validator.validate(registration("user@", UserRole.USER, false, false))))
                .containsExactlyInAnyOrder("email", "termsAccepted", "privacyPolicyAccepted");
    }

    @Test
    void administrativeRolesCannotBeSelfAssigned() {
        assertThat(fields(validator.validate(registration("user@example.by", UserRole.MODERATOR, true, true))))
                .containsExactly("accountTypeAllowed");
        assertThat(fields(validator.validate(registration("user@example.by", UserRole.ADMIN, true, true))))
                .containsExactly("accountTypeAllowed");
    }

    private static RegisterRequest registration(String email, UserRole role, boolean terms, boolean privacy) {
        return new RegisterRequest(email, "Secret-123", "Мария", role, terms, privacy);
    }

    private static Set<String> fields(Set<ConstraintViolation<RegisterRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
