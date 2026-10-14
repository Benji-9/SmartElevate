package com.smartelevate.common.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Cumple {@link PasswordPolicy}. {@code null} es válido: lo informa {@code @NotNull}. */
@Documented
@Constraint(validatedBy = ValidPassword.Validator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface ValidPassword {

    String message() default PasswordPolicy.SIZE_MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPassword, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            String message = value == null ? null : PasswordPolicy.violation(value);
            if (message == null) {
                return true;
            }
            // Los mensajes son constantes sin { ni $, así que no se interpretan como plantilla.
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
            return false;
        }
    }
}
