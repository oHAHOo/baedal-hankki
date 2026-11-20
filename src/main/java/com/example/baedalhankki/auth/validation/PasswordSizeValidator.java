package com.example.baedalhankki.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordSizeValidator implements ConstraintValidator<PasswordSize, String> {
    private int minCharacters;
    private int maxBytes;

    @Override
    public void initialize(PasswordSize annotation) {
        minCharacters = annotation.minCharacters();
        maxBytes = annotation.maxBytes();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || (StandardCharsets.UTF_8.newEncoder().canEncode(value)
                && value.codePointCount(0, value.length()) >= minCharacters
                && value.getBytes(StandardCharsets.UTF_8).length <= maxBytes);
    }
}
