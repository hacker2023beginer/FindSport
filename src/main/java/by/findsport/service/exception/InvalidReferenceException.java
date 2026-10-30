package by.findsport.service.exception;

/** Запрос ссылается на несуществующий элемент справочника (HTTP 422). */
public class InvalidReferenceException extends RuntimeException {

    private final String field;

    public InvalidReferenceException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
