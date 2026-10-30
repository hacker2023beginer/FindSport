package by.findsport.service.exception;

/** Ссылка из письма не найдена, уже использована или истекла (HTTP 400). */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
