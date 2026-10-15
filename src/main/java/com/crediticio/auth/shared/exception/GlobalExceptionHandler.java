package com.crediticio.auth.shared.exception;

import com.crediticio.auth.shared.response.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(RolNotFoundException.class)
    public ResponseEntity<ApiError> handleRolNotFound(RolNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "ROL_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(RolNoPermitidoException.class)
    public ResponseEntity<ApiError> handleRolNoPermitido(RolNoPermitidoException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "ROL_NOT_ALLOWED", ex.getMessage());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiError> handleDuplicateEmail(DuplicateEmailException ex) {
        return buildResponse(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> details.put(error.getField(), error.getDefaultMessage()));

        ApiError apiError = ApiError.builder()
                .errorCode("VALIDATION_ERROR")
                .message("Error de validación en los datos de entrada")
                .details(details)
                .traceId(UUID.randomUUID().toString())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiError);
    }

    private ResponseEntity<ApiError> buildResponse(HttpStatus status, String errorCode, String message) {
        ApiError apiError = ApiError.builder()
                .errorCode(errorCode)
                .message(message)
                .traceId(UUID.randomUUID().toString())
                .build();
        return ResponseEntity.status(status).body(apiError);
    }
}
