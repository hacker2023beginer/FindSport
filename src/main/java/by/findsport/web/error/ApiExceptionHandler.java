package by.findsport.web.error;

import by.findsport.service.exception.ConflictException;
import by.findsport.service.exception.FieldRejectedException;
import by.findsport.service.exception.ForbiddenException;
import by.findsport.service.exception.InvalidReferenceException;
import by.findsport.service.exception.InvalidTokenException;
import by.findsport.service.exception.LoginRejectedException;
import by.findsport.service.exception.NotFoundException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Единая структура ошибок API в формате RFC 7807 (ТЗ п. 4.4.2).
 * Ошибки валидации — 422 Unprocessable Entity со списком полей (п. 6.7, «Валидация данных»).
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBodyValidation(MethodArgumentNotValidException e) {
        List<Map<String, String>> errors = e.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError fe
                        ? fe.isBindingFailure()
                                ? Map.of("field", fe.getField(), "message", "Некорректный формат значения")
                                : fieldError(fe.getField(), fe)
                        : fieldError(error.getObjectName(), error))
                .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleParameterValidation(HandlerMethodValidationException e) {
        List<Map<String, String>> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> fieldError(result.getMethodParameter().getParameterName(), error)))
                .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler(InvalidReferenceException.class)
    public ProblemDetail handleInvalidReference(InvalidReferenceException e) {
        return validationProblem(List.of(Map.of("field", e.getField(), "message", e.getMessage())));
    }

    @ExceptionHandler(FieldRejectedException.class)
    public ProblemDetail handleFieldRejected(FieldRejectedException e) {
        return validationProblem(List.of(Map.of("field", e.getField(), "message", e.getMessage())));
    }

    /**
     * Отказ во входе: неверные данные — 401 (ТЗ п. 6.7), временное ограничение — 423 с моментом окончания
     * (п. 4.2.3), блокировка или неподтвержденный e-mail — 403. Поле {@code reason} позволяет интерфейсу
     * предложить нужное действие (повторить письмо, восстановить доступ).
     */
    @ExceptionHandler(LoginRejectedException.class)
    public ProblemDetail handleLoginRejected(LoginRejectedException e) {
        HttpStatus status = switch (e.getReason()) {
            case BAD_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case LOCKED -> HttpStatus.LOCKED;
            case EMAIL_NOT_VERIFIED, BLOCKED -> HttpStatus.FORBIDDEN;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setTitle("Вход не выполнен");
        problem.setProperty("reason", e.getReason());
        if (e.getLockedUntil() != null) {
            problem.setProperty("lockedUntil", e.getLockedUntil());
        }
        return problem;
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidTokenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadSize(MaxUploadSizeExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Превышен допустимый размер файла");
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Данные были изменены другим пользователем, обновите страницу и повторите действие");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException e) {
        log.warn("Нарушение ограничения целостности БД: {}", e.getMostSpecificCause().getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Операция нарушает целостность данных (например, удаляется предложение с поданными заявками)");
    }

    @ExceptionHandler(NotImplementedYetException.class)
    public ProblemDetail handleNotImplemented(NotImplementedYetException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED, e.getMessage());
    }

    private static ProblemDetail validationProblem(List<Map<String, String>> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "Проверьте правильность заполнения полей");
        problem.setTitle("Ошибка валидации");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static Map<String, String> fieldError(String field, MessageSourceResolvable error) {
        return Map.of("field", field, "message", String.valueOf(error.getDefaultMessage()));
    }
}
