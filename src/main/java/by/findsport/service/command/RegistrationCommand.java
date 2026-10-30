package by.findsport.service.command;

import by.findsport.domain.Language;
import by.findsport.domain.UserRole;

/**
 * Регистрация по e-mail и паролю (ТЗ п. 4.2.2, 3.2.2): пользователь либо представитель
 * организации/тренер. Согласия проверены на входе — команда создается только при их принятии.
 */
public record RegistrationCommand(
        String email,
        String password,
        String displayName,
        UserRole role,
        Language preferredLanguage) {
}
