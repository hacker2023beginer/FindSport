package by.findsport.service.exception;

import java.time.Instant;

/**
 * Вход отклонен (ТЗ п. 4.2.2, 4.2.3, 4.5.1). Причина определяет HTTP-статус ответа;
 * для неверной пары e-mail/пароль не сообщается, что именно не совпало.
 */
public class LoginRejectedException extends RuntimeException {

    public enum Reason {
        /** Неверный e-mail или пароль (HTTP 401). */
        BAD_CREDENTIALS,
        /** Временное ограничение после серии неуспешных попыток (HTTP 423). */
        LOCKED,
        /** Адрес e-mail не подтвержден (HTTP 403). */
        EMAIL_NOT_VERIFIED,
        /** Учетная запись заблокирована администратором (HTTP 403). */
        BLOCKED
    }

    private final Reason reason;
    private final Instant lockedUntil;

    public LoginRejectedException(Reason reason, String message) {
        this(reason, message, null);
    }

    public LoginRejectedException(Reason reason, String message, Instant lockedUntil) {
        super(message);
        this.reason = reason;
        this.lockedUntil = lockedUntil;
    }

    public Reason getReason() {
        return reason;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
