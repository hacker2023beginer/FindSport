package by.findsport.service;

import by.findsport.domain.User;
import by.findsport.domain.UserRole;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Журнал событий безопасности (ТЗ п. 4.2.3, 4.13.4, 4.13.14) в отдельной категории
 * {@code by.findsport.security}. Учетная запись указывается только идентификатором: пароли,
 * токены и e-mail в журнал не пишутся (п. 4.13.13).
 */
@Component
public class SecurityAudit {

    private static final Logger log = LoggerFactory.getLogger("by.findsport.security");

    public void registered(User user) {
        log.info("REGISTERED userId={} role={}", user.getId(), user.getRole());
    }

    public void emailVerified(User user) {
        log.info("EMAIL_VERIFIED userId={}", user.getId());
    }

    public void loginSucceeded(User user) {
        log.info("LOGIN_SUCCESS userId={} role={}", user.getId(), user.getRole());
    }

    /** Неизвестный e-mail: адрес не журналируется, чтобы не копить чужие персональные данные. */
    public void loginFailedUnknownAccount() {
        log.warn("LOGIN_FAILURE unknown account");
    }

    public void loginFailed(User user, String reason) {
        log.warn("LOGIN_FAILURE userId={} reason={}", user.getId(), reason);
    }

    public void accountLocked(User user, Instant until) {
        log.warn("ACCOUNT_LOCKED userId={} until={} — превышено число неуспешных входов", user.getId(), until);
    }

    public void loggedOut(String principal) {
        log.info("LOGOUT session closed{}", principal == null ? "" : " for authenticated user");
    }

    public void passwordResetRequested(User user) {
        log.info("PASSWORD_RESET_REQUESTED userId={}", user.getId());
    }

    public void passwordReset(User user) {
        log.info("PASSWORD_RESET userId={}", user.getId());
    }

    public void passwordChanged(User user) {
        log.info("PASSWORD_CHANGED userId={}", user.getId());
    }

    public void deletionRequested(User user) {
        log.info("ACCOUNT_DELETION_REQUESTED userId={}", user.getId());
    }

    public void roleChanged(User admin, User target, UserRole from) {
        log.warn("ROLE_CHANGED userId={} {}->{} by adminId={}", target.getId(), from, target.getRole(), admin.getId());
    }

    public void blockChanged(User admin, User target) {
        log.warn("{} userId={} by adminId={}", target.isBlocked() ? "ACCOUNT_BLOCKED" : "ACCOUNT_UNBLOCKED",
                target.getId(), admin.getId());
    }

    public void rateLimited(String form) {
        log.warn("RATE_LIMITED form={} — превышена частота отправки публичной формы", form);
    }
}
