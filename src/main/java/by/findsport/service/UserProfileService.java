package by.findsport.service;

import by.findsport.domain.User;
import by.findsport.repository.UserRepository;
import by.findsport.service.command.ProfileCommand;
import by.findsport.service.exception.FieldRejectedException;
import by.findsport.service.exception.NotFoundException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Личный кабинет: профиль, настройки и согласия, смена пароля, запрос на удаление
 * (ТЗ п. 4.2.5, 4.6.6, 4.10.1, 4.10.2). Все операции выполняются только над учетной записью
 * текущего пользователя — идентификатор берется из сессии, а не из запроса.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserProfileService {

    private final UserRepository users;
    private final ReferenceResolver references;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAudit audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public User profile(String email) {
        return requireWithProfile(email);
    }

    public User updateProfile(String email, ProfileCommand command) {
        User user = requireWithProfile(email);
        user.setDisplayName(command.displayName());
        user.setPhone(command.phone());
        user.setRegion(command.regionId() == null ? null : references.region(command.regionId()));
        user.getSportInterests().clear();
        user.getSportInterests().addAll(references.sports(command.sportIds()));
        user.setPreferredLanguage(command.preferredLanguage());
        return user;
    }

    /** Согласие на персонализацию (п. 4.8.1) и e-mail-уведомления (п. 4.2.11). */
    public User updateSettings(String email, boolean personalizationConsent, boolean emailNotifications) {
        User user = requireWithProfile(email);
        user.setPersonalizationConsent(personalizationConsent);
        user.setEmailNotifications(emailNotifications);
        return user;
    }

    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = requireWithProfile(email);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new FieldRejectedException("currentPassword", "Неверный текущий пароль");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        audit.passwordChanged(user);
    }

    /** Запрос фиксируется, данные удаляются по регламенту (п. 4.10.2); повторный запрос не меняет дату. */
    public User requestDeletion(String email) {
        User user = requireWithProfile(email);
        if (user.getDeletionRequestedAt() == null) {
            user.setDeletionRequestedAt(clock.instant());
            audit.deletionRequested(user);
        }
        return user;
    }

    private User requireWithProfile(String email) {
        return users.findWithProfileByEmail(email)
                .orElseThrow(() -> new NotFoundException("Учетная запись не найдена"));
    }
}
