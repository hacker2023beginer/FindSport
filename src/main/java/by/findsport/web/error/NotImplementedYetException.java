package by.findsport.web.error;

/** Заглушка эндпоинта, реализация которого запланирована на следующие этапы (HTTP 501). */
public class NotImplementedYetException extends RuntimeException {

    public NotImplementedYetException(String message) {
        super(message);
    }
}
