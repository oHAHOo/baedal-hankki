package com.example.baedalhankki.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordSizeValidator.class)
public @interface PasswordSize {
    String message() default "비밀번호 길이를 확인해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int minCharacters();

    int maxBytes();
}
