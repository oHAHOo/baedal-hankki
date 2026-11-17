package com.example.baedalhankki.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> business(ApiException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus())
                .body(Map.of("code", e.getErrorCode().name(), "message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "code",
                                ErrorCode.INVALID_REQUEST.name(),
                                "message",
                                ErrorCode.INVALID_REQUEST.getMessage(),
                                "fieldErrors",
                                e.getBindingResult().getFieldErrors().stream()
                                        .map(
                                                f ->
                                                        Map.of(
                                                                "field",
                                                                f.getField(),
                                                                "message",
                                                                "입력값을 확인해 주세요."))
                                        .toList()));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<?> malformed(Exception e) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", ErrorCode.INVALID_REQUEST.name(), "message", ErrorCode.INVALID_REQUEST.getMessage()));
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e) {
        log.error("Unexpected API error", e);
        return ResponseEntity.internalServerError()
                .body(Map.of("code", ErrorCode.INTERNAL_ERROR.name(), "message", ErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
