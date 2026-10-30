package by.findsport.service.exception;

/** Значение поля отклонено бизнес-проверкой сервиса, например неверный текущий пароль (HTTP 422). */
public class FieldRejectedException extends RuntimeException {

    private final String field;

    public FieldRejectedException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
