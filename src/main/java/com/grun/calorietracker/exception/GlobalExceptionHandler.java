package com.grun.calorietracker.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String error,
            String message,
            String path
    ) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(path)
                .build();

        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<ApiErrorResponse> buildValidationResponse(
            HttpStatus status,
            String error,
            String message,
            String path,
            Map<String, String> validationErrors
    ) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(path)
                .validationErrors(validationErrors)
                .build();

        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(
            UsernameNotFoundException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "User not found",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProductNotFound(
            ProductNotFoundException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "Product not found",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(ExerciseLogNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleExerciseLogNotFound(
            ExerciseLogNotFoundException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "Exercise log not found",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(ExerciseItemNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleExerciseItemNotFound(
            ExerciseItemNotFoundException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "Exercise item not found",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Illegal argument",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            validationErrors.put(
                    fieldError.getField(),
                    fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value"
            );
        }

        return buildValidationResponse(
                HttpStatus.BAD_REQUEST,
                "Validation error",
                "Request validation failed",
                request.getRequestURI(),
                validationErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();

        ex.getConstraintViolations().forEach(violation -> {
            String fieldPath = violation.getPropertyPath() != null
                    ? violation.getPropertyPath().toString()
                    : "unknown";
            validationErrors.put(fieldPath, violation.getMessage());
        });

        return buildValidationResponse(
                HttpStatus.BAD_REQUEST,
                "Validation error",
                "Constraint validation failed",
                request.getRequestURI(),
                validationErrors
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        String message = "Invalid value for parameter: " + ex.getName();

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Type mismatch",
                message,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        String message = "Missing required parameter: " + ex.getParameterName();

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Missing request parameter",
                message,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Malformed JSON request",
                "Request body is missing or invalid",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        logger.warn("Data integrity violation at [{}]: {}", request.getRequestURI(), ex.getMessage());

        return buildResponse(
                HttpStatus.CONFLICT,
                "Data integrity violation",
                "The request conflicts with existing data or database constraints",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        String message = "HTTP method not supported for this endpoint";

        return buildResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Method not allowed",
                message,
                request.getRequestURI()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.FORBIDDEN,
                "Access denied",
                "You do not have permission to access this resource",
                request.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        logger.error("Unhandled exception at [{}]", request.getRequestURI(), ex);

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error",
                "An unexpected error occurred",
                request.getRequestURI()
        );
    }
}