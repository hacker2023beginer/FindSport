package by.findsport.service;

import by.findsport.config.AuthProperties;
import by.findsport.domain.User;
import by.findsport.repository.UserRepository;
import by.findsport.service.exception.LoginRejectedException;
import by.findsport.service.exception.LoginRejectedException.Reason;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Проверка учетных данных при входе (ТЗ п. 4.2.2, 4.2.3, 4.5.1).
 * <p>
 * Порядок проверок: временное ограничение → пароль → блокировка администратором → подтверждение e-mail.
 * Пока действует ограничение, пароль не проверяется, поэтому подбор невозможен. Статус учетной записи
 * сообщается только после верного пароля. После {@code maxFailedLogins} неуспешных попыток подряд
 * вход ограничивается на {@code lockoutDuration}, событие пишется в журнал безопасности.
 * <p>
 * Транзакция не откатывается при отказе во входе — иначе счетчик неуспешных попыток не сохранится.
 */
@Service
@Transactional(noRollbackFor = LoginRejectedException.class)
public class LoginService {

    private static final String BAD_CREDENTIALS = "Неверный e-mail или пароль";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final SecurityAudit audit;
    private final Clock clock;
    /** Хэш-заглушка: для неизвестного e-mail время ответа такое же, как для существующего. */
    private final String dummyHash;

    public LoginService(UserRepository users, PasswordEncoder passwordEncoder, AuthProperties properties,
                        SecurityAudit audit, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("findsport-timing-equalizer");
    }

    public User authenticate(String email, String password) {
        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            audit.loginFailedUnknownAccount();
            throw new LoginRejectedException(Reason.BAD_CREDENTIALS, BAD_CREDENTIALS);
        }
        User user = found.get();
        Instant now = clock.instant();
        if (user.isLockedAt(now)) {
            audit.loginFailed(user, "LOCKED");
            throw locked(user.getLockedUntil());
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            registerFailure(user, now);
        }
        if (user.isBlocked()) {
            audit.loginFailed(user, "BLOCKED");
            throw new LoginRejectedException(Reason.BLOCKED,
                    "Учетная запись заблокирована администратором. Обратитесь в поддержку");
        }
        if (!user.isEmailVerified()) {
            audit.loginFailed(user, "EMAIL_NOT_VERIFIED");
            throw new LoginRejectedException(Reason.EMAIL_NOT_VERIFIED,
                    "Подтвердите e-mail по ссылке из письма. Письмо можно отправить повторно");
        }
        if (user.getFailedLoginAttempts() > 0) {
            users.resetFailedLogins(user.getId());
        }
        audit.loginSucceeded(user);
        return user;
    }

    private void registerFailure(User user, Instant now) {
        users.incrementFailedLogins(user.getId());
        Instant until = now.plus(properties.lockoutDuration());
        if (users.lockIfThresholdReached(user.getId(), properties.maxFailedLogins(), until) > 0) {
            audit.accountLocked(user, until);
            throw locked(until);
        }
        audit.loginFailed(user, "BAD_PASSWORD");
        throw new LoginRejectedException(Reason.BAD_CREDENTIALS, BAD_CREDENTIALS);
    }

    private static LoginRejectedException locked(Instant until) {
        return new LoginRejectedException(Reason.LOCKED,
                "Вход временно ограничен из-за нескольких неудачных попыток. Повторите позже или восстановите "
                        + "доступ по e-mail", until);
    }
}
