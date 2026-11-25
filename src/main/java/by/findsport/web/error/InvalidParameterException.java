package by.findsport.web.error;

/**
 * Недопустимый параметр запроса: значение не входит в допустимый перечень (HTTP 422).
 * Используется для параметров, перечисление которых задано сервером, — например, порядка сортировки.
 */
public class InvalidParameterException extends RuntimeException {

    private final String field;

    public InvalidParameterException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
