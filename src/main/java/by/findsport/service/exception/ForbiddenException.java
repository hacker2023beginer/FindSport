package by.findsport.service.exception;

/** Недостаточно полномочий для операции над конкретным объектом (HTTP 403), ТЗ п. 4.13.1. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
