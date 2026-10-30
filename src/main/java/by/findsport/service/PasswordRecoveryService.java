package by.findsport.service;

import by.findsport.config.AuthProperties;
import by.findsport.domain.TokenPurpose;
import by.findsport.domain.User;
import by.findsport.repository.UserRepository;
import by.findsport.service.mail.AccountMailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Восстановление доступа по ссылке из письма. Новый пароль снимает временное ограничение входа (п. 4.2.3),
 * но не блокировку администратором (п. 4.5.1).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PasswordRecoveryService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final AccountMailService mail;
    private final AuthProperties properties;
    private final SecurityAudit audit;

    /** Ответ не зависит от существования адреса — форма не раскрывает зарегистрированные e-mail. */
    public void requestReset(String email) {
        users.findByEmail(email)
                .filter(user -> !user.isBlocked())
                .ifPresent(user -> {
                    audit.passwordResetRequested(user);
                    mail.sendPasswordReset(user, tokens.issue(user, TokenPurpose.PASSWORD_RESET,
                            properties.passwordResetTtl()));
                });
    }

    public void resetPassword(String token, String newPassword) {
        User user = tokens.consume(token, TokenPurpose.PASSWORD_RESET);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.clearLoginFailures();
        audit.passwordReset(user);
    }
}
