package by.findsport.service.exception;

/** Запрошенный ресурс не существует или недоступен текущему пользователю (HTTP 404). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
