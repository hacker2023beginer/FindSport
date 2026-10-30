package by.findsport.service.exception;

/** Операция недопустима в текущем состоянии объекта, например в текущем статусе карточки (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
