package by.findsport.domain;

/** Назначение одноразовой ссылки из письма. */
public enum TokenPurpose {
    /** Подтверждение адреса после регистрации (ТЗ п. 4.2.2). */
    EMAIL_VERIFICATION,
    /** Восстановление доступа — установка нового пароля. */
    PASSWORD_RESET
}
