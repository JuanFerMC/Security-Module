package com.crediticio.auth.shared.exception;

import com.crediticio.auth.shared.response.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(RolNotFoundException.class)
    public ResponseEntity<ApiError> handleRolNotFound(RolNotFoundException ex) {
        // HU08 RF13: un idRol inexistente es una entrada invalida -> 400.
        return buildResponse(HttpStatus.BAD_REQUEST, "ROL_NOT_FOUND", ex.getMessage());
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

    /** JSON mal formado o con tipos invalidos (HU08 RF13 / HU09 RNF06): 400, sin detalles internos. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedBody(HttpMessageNotReadableException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "El cuerpo de la petición no es válido");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "El parámetro '" + ex.getName() + "' no es válido");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Método HTTP " + ex.getMethod() + " no soportado para esta ruta");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso no encontrado");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException ex) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Error interno no manejado", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno del servidor");
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
