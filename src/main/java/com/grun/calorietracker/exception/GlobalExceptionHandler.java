package com.grun.calorietracker.exception;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private ResponseEntity<ApiErrorResponseDto> buildResponse(HttpStatus status,
                                                              String errorCode,
                                                              String fallbackError,
                                                              String message,
                                                              HttpServletRequest request) {
        ApiErrorResponseDto body = new ApiErrorResponseDto(
                LocalDateTime.now(),
                status.value(),
                resolveMessage(errorCode, fallbackError, request),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }

    private String resolveMessage(String code, String fallback, HttpServletRequest request) {
        return messageSource.getMessage(code, null, fallback, RequestContextUtils.getLocale(request));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDto> handleUserNotFound(UsernameNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "error.user.not-found", "User not found", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponseDto> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "error.invalid.credentials", "Invalid credentials", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponseDto> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "error.invalid.credentials", "Invalid credentials", ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponseDto> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "error.access.denied", "Access denied", ex.getMessage(), request);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ApiErrorResponseDto> handleEmailNotVerified(EmailNotVerifiedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "error.email.not-verified", "Email not verified", ex.getMessage(), request);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDto> handleProductNotFoundException(ProductNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "error.product.not-found", "Product not found", ex.getMessage(), request);
    }

    @ExceptionHandler(ExerciseLogNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDto> handleExerciseLogNotFoundException(ExerciseLogNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "error.exercise-log.not-found", "Exercise log not found", ex.getMessage(), request);
    }

    @ExceptionHandler(ExerciseItemNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDto> handleExerciseItemNotFoundException(ExerciseItemNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "error.exercise-item.not-found", "Exercise item not found", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateExternalExerciseLogException.class)
    public ResponseEntity<ApiErrorResponseDto> handleDuplicateExternalExerciseLogException(DuplicateExternalExerciseLogException ex,
                                                                                          HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "error.duplicate.external-exercise-log", "Duplicate external exercise log", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateExerciseItemException.class)
    public ResponseEntity<ApiErrorResponseDto> handleDuplicateExerciseItemException(DuplicateExerciseItemException ex,
                                                                                   HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "error.duplicate.exercise-item", "Duplicate exercise item", ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDto> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "error.resource.not-found", "Resource not found", ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponseDto> handleNoResourceFoundException(NoResourceFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "error.resource.not-found", "Resource not found", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponseDto> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "error.invalid.request", "Invalid request", ex.getMessage(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponseDto> handleConstraintViolationException(ConstraintViolationException ex,
                                                                                  HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "error.validation", "Validation error", ex.getMessage(), request);
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ApiErrorResponseDto> handleDateTimeParseException(DateTimeParseException ex,
                                                                            HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "error.invalid.request", "Invalid request", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponseDto> handleGeneric(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "error.unexpected", "Unexpected error", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponseDto> handleValidationErrors(MethodArgumentNotValidException ex,
                                                                      HttpServletRequest request) {
        String field = Objects.requireNonNull(ex.getFieldError()).getField();
        String message = Objects.requireNonNull(ex.getBindingResult().getFieldError()).getDefaultMessage();
        String fullMessage = field + ": " + message;

        return buildResponse(HttpStatus.BAD_REQUEST, "error.validation", "Validation error", fullMessage, request);
    }
}
