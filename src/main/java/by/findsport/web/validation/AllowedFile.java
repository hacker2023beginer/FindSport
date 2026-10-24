package by.findsport.web.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Проверка загружаемого файла по расширению, MIME-типу, размеру и сигнатуре содержимого (ТЗ п. 4.13.7).
 * Пределы размера берутся из {@link by.findsport.config.FileUploadProperties}.
 */
@Documented
@Constraint(validatedBy = AllowedFileValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface AllowedFile {

    FileKind value();

    String message() default "Недопустимый файл";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
