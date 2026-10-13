package com.smartelevate.common.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * El texto, codificado en UTF-8, no pasa de {@code value} bytes. Existe por BCrypt, que tira error con más de
 * 72 bytes, y una "ñ" ocupa 2: contar caracteres no alcanza. Se usa junto con {@code @Size(max = value)}, que
 * es el que informa cuando ya se pasó de caracteres.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "El texto es demasiado largo.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
