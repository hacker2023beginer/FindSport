package by.findsport.service;

import by.findsport.config.AuthProperties;
import by.findsport.domain.TokenPurpose;
import by.findsport.domain.User;
import by.findsport.domain.UserRole;
import by.findsport.repository.UserRepository;
import by.findsport.service.command.RegistrationCommand;
import by.findsport.service.exception.ConflictException;
import by.findsport.service.mail.AccountMailService;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Регистрация и подтверждение адреса e-mail (ТЗ п. 4.2.2, Приложение 3 «Регистрация пользователя»):
 * после регистрации приходит письмо, войти можно только после перехода по ссылке.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RegistrationService {

    /** Модератора и администратора назначает только администратор (п. 4.5.2). */
    public static final Set<UserRole> SELF_REGISTRATION_ROLES = EnumSet.of(UserRole.USER, UserRole.PROVIDER);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final AccountMailService mail;
    private final AuthProperties properties;
    private final SecurityAudit audit;
    private final Clock clock;

    public User register(RegistrationCommand command) {
        if (!SELF_REGISTRATION_ROLES.contains(command.role())) {
            throw new IllegalArgumentException("Роль недоступна при регистрации: " + command.role());
        }
        if (users.existsByEmail(command.email())) {
            throw new ConflictException("Учетная запись с таким e-mail уже зарегистрирована");
        }
        User user = new User(command.email(), passwordEncoder.encode(command.password()), command.role());
        user.setDisplayName(command.displayName());
        user.setPreferredLanguage(command.preferredLanguage());
        user.setConsentsAcceptedAt(clock.instant());
        users.save(user);
        audit.registered(user);
        mail.sendEmailVerification(user, tokens.issue(user, TokenPurpose.EMAIL_VERIFICATION,
                properties.emailVerificationTtl()));
        return user;
    }

    public User verifyEmail(String token) {
        User user = tokens.consume(token, TokenPurpose.EMAIL_VERIFICATION);
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            audit.emailVerified(user);
        }
        return user;
    }

    /**
     * Повторная отправка письма. Ответ не зависит от существования адреса,
     * чтобы форма не раскрывала зарегистрированные e-mail.
     */
    public void resendVerification(String email) {
        users.findByEmail(email)
                .filter(user -> !user.isEmailVerified() && !user.isBlocked())
                .ifPresent(user -> mail.sendEmailVerification(user, tokens.issue(user,
                        TokenPurpose.EMAIL_VERIFICATION, properties.emailVerificationTtl())));
    }
}
