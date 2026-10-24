package by.findsport.web.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Единый формат белорусского телефонного номера {@code +375XXXXXXXXX} (ТЗ п. 4.4.2, 4.4.6).
 * {@code null} допустим — обязательность задается отдельно.
 */
@Documented
@Pattern(regexp = "^\\+375\\d{9}$")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
public @interface BelarusPhone {

    String message() default "Телефон должен быть в формате +375XXXXXXXXX";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
