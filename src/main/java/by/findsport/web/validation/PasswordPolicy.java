package by.findsport.web.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Требования к сложности пароля (ТЗ п. 6.7, «Валидация данных»): не короче 8 символов,
 * хотя бы одна буква и одна цифра. Верхняя граница — 72 байта UTF-8: BCrypt не учитывает остаток.
 */
@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({FIELD, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
public @interface PasswordPolicy {

    String message() default "Пароль должен содержать от 8 до 72 символов, хотя бы одну букву и одну цифру";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
