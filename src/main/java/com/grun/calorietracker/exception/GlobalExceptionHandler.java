package com.grun.calorietracker.exception;

import com.grun.calorietracker.config.LocaleConfig;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.enums.ApiErrorCode;
import com.grun.calorietracker.security.CorrelationIdFilter;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;
    private final boolean includeInternalDetails;

    public GlobalExceptionHandler(MessageSource messageSource,
                                  @Value("${grun.errors.include-internal-details:false}") boolean includeInternalDetails) {
        this.messageSource = messageSource;
        this.includeInternalDetails = includeInternalDetails;
    }

    private ResponseEntity<ApiErrorResponseDto> buildResponse(HttpStatus status,
                                                              String errorCode,
                                                              String fallbackError,
                                                              String message,
                                                              HttpServletRequest request) {
        ApiErrorResponseDto body = new ApiErrorResponseDto(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                resolveMessage(errorCode, fallbackError, request),
                request.getRequestURI(),
                correlationId(request)
        );
        body.setCode(ApiErrorCode.fromMessageKey(errorCode).name());
        return ResponseEntity.status(status).body(body);
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER) : value.toString();
    }

    private String resolveMessage(String code, String fallback, HttpServletRequest request) {
        return messageSource.getMessage(code, null, fallback, LocaleConfig.resolveSupportedLocale(RequestContextUtils.getLocale(request)));
    }
    private boolean isAiMealDraftConfirmRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.matches(".*/api/v1/ai/meal-drafts/\\d+/confirm$");
    }

    private ResponseEntity<ApiErrorResponseDto> buildDomainResponse(HttpStatus status,
                                                                    String code,
                                                                    String message,
                                                                    List<ApiErrorResponseDto.FieldErrorDto> fieldErrors,
                                                                    HttpServletRequest request) {
        ApiErrorResponseDto body = new ApiErrorResponseDto(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                correlationId(request)
        );
        body.setCode(code);
        body.setFieldErrors(fieldErrors);
        return ResponseEntity.status(status).body(body);
    }

    private String safeFieldName(String field) {
        if (field == null || field.isBlank()) {
            return "request";
        }
        String stripped = field.replaceAll("\\[[0-9]+]", "");
        int dot = stripped.lastIndexOf('.');
        return dot >= 0 ? stripped.substring(dot + 1) : stripped;
    }

    private String validationCode(FieldError fieldError) {
        String field = safeFieldName(fieldError.getField());
        String constraint = fieldError.getCode();
        if ("items".equals(field)) {
            return "Size".equals(constraint) ? "TOO_MANY_ITEMS" : "MEAL_DRAFT_ITEMS_REQUIRED";
        }
        if ("mealType".equals(field)) {
            return "NotBlank".equals(constraint) ? "MEAL_TYPE_REQUIRED" : "MEAL_TYPE_INVALID";
        }
        if ("logDate".equals(field)) {
            return "LOG_DATE_REQUIRED";
        }
        if ("portionSize".equals(field)) {
            return "PORTION_SIZE_INVALID";
        }
        if ("portionUnit".equals(field)) {
            return "PORTION_UNIT_REQUIRED";
        }
        if ("foodItemId".equals(field)) {
            return "FOOD_ITEM_NOT_FOUND";
        }
        if ("estimatedFoodName".equals(field)) {
            return "ESTIMATED_FOOD_NAME_INVALID";
        }
        if (field != null && field.startsWith("estimated")) {
            return "ESTIMATED_NUTRITION_INVALID";
        }
        return "INVALID_REQUEST";
    }

    private String publicFieldErrorCode(FieldError fieldError) {
        String constraint = fieldError.getCode();
        if ("NotNull".equals(constraint) || "NotBlank".equals(constraint) || "NotEmpty".equals(constraint)) {
            return "REQUIRED";
        }
        if ("Positive".equals(constraint) || "PositiveOrZero".equals(constraint) || "Size".equals(constraint)) {
            return "OUT_OF_RANGE";
        }
        return "INVALID";
    }

    private String confirmExceptionCode(String message) {
        if (message == null || message.isBlank()) {
            return "INVALID_REQUEST";
        }
        return switch (message) {
            case "INVALID_REQUEST_ID", "DRAFT_NOT_FOUND", "DRAFT_NOT_CONFIRMABLE",
                    "MEAL_DRAFT_ITEMS_REQUIRED", "TOO_MANY_ITEMS", "INVALID_MEAL_DRAFT_ITEM",
                    "INCONSISTENT_MEAL_CONTEXT", "MEAL_TYPE_REQUIRED", "MEAL_TYPE_INVALID",
                    "LOG_DATE_REQUIRED", "PORTION_SIZE_INVALID", "PORTION_UNIT_REQUIRED",
                    "ITEM_SOURCE_INVALID", "FOOD_ITEM_NOT_FOUND", "ESTIMATED_FOOD_NAME_INVALID",
                    "ESTIMATED_NUTRITION_INVALID", "CONFIDENCE_INVALID" -> message;
            default -> "INVALID_REQUEST";
        };
    }

    private HttpStatus confirmExceptionStatus(String code) {
        return switch (code) {
            case "DRAFT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "DRAFT_NOT_CONFIRMABLE" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDto> handleUserNotFound(UsernameNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "error.user.not-found", "User not found", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponseDto> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "error.invalid.credentials", "Invalid credentials", ex.getMessage(), request);
    }

    @ExceptionHandler(AccountLinkException.class)
    public ResponseEntity<ApiErrorResponseDto> handleAccountLinkException(
            AccountLinkException ex,
            HttpServletRequest request
    ) {
        return buildDomainResponse(ex.getCode().status(), ex.getCode().name(), ex.getMessage(), List.of(), request);
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

    @ExceptionHandler(ProgressLogNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDto> handleProgressLogNotFoundException(ProgressLogNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "error.progress-log.not-found", "Progress log not found", ex.getMessage(), request);
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

    @ExceptionHandler(DuplicateRecipePublicationRequestException.class)
    public ResponseEntity<ApiErrorResponseDto> handleDuplicateRecipePublicationRequestException(DuplicateRecipePublicationRequestException ex,
                                                                                                HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "error.duplicate.recipe-publication-request", "Duplicate recipe publication request", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateManualStepLogException.class)
    public ResponseEntity<ApiErrorResponseDto> handleDuplicateManualStepLogException(DuplicateManualStepLogException ex,
                                                                                    HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "error.duplicate.manual-step-log", "Duplicate manual step log", ex.getMessage(), request);
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDto> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "error.resource.not-found", "Resource not found", ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponseDto> handleNoResourceFoundException(NoResourceFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "error.resource.not-found", "Resource not found", ex.getMessage(), request);
    }

    
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponseDto> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException ex,
                                                                                 HttpServletRequest request) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "error.method-not-allowed", "Method not allowed", ex.getMessage(), request);
    }

    @ExceptionHandler(AiProviderTimeoutException.class)
    public ResponseEntity<ApiErrorResponseDto> handleAiProviderTimeoutException(
            AiProviderTimeoutException ex, HttpServletRequest request) {
        log.warn(
                "AI provider timeout correlationId={} path={}",
                correlationId(request),
                request.getRequestURI()
        );
        return buildResponse(
                HttpStatus.GATEWAY_TIMEOUT,
                "error.ai-timeout",
                "AI request timed out",
                "AI analysis took too long. Your credit was not charged; retry when ready.",
                request
        );
    }
    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ApiErrorResponseDto> handleAiProviderException(AiProviderException ex, HttpServletRequest request) {
        log.warn(
                "AI provider exception correlationId={} path={} message={}",
                correlationId(request),
                request.getRequestURI(),
                ex.getMessage()
        );
        return buildResponse(
                HttpStatus.BAD_GATEWAY,
                "error.ai-provider",
                "AI provider error",
                "AI analysis could not be completed. Please try again with a different input.",
                request
        );
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponseDto> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        if (isAiMealDraftConfirmRequest(request)) {
            String code = confirmExceptionCode(ex.getMessage());
            return buildDomainResponse(
                    confirmExceptionStatus(code),
                    code,
                    "The request could not be processed.",
                    List.of(),
                    request
            );
        }
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

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponseDto> handleMissingRequestHeader(
            MissingRequestHeaderException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "error.invalid.request",
                "Invalid request",
                "Required request header is missing: " + ex.getHeaderName(),
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponseDto> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex,
                                                                                    HttpServletRequest request) {
        if (isAiMealDraftConfirmRequest(request)) {
            return buildDomainResponse(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "The request could not be processed.",
                    List.of(),
                    request
            );
        }
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "error.invalid.request",
                "Invalid request",
                "Malformed JSON request. Check date/time formats and field types.",
                request
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponseDto> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
                                                                                   HttpServletRequest request) {
        return buildResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "error.upload.too-large",
                "Upload too large",
                "Uploaded file exceeds the maximum allowed size.",
                request
        );
    }

    @ExceptionHandler(RequestConflictException.class)
    public ResponseEntity<ApiErrorResponseDto> handleRequestConflictException(
            RequestConflictException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "error.request-conflict",
                "Request conflict",
                ex.getMessage(),
                request
        );
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponseDto> handleDataIntegrityViolationException(DataIntegrityViolationException ex,
                                                                                    HttpServletRequest request) {
        log.warn(
                "Data integrity violation correlationId={} path={}",
                correlationId(request),
                request.getRequestURI()
        );
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "error.data-integrity",
                "Invalid request",
                "Request conflicts with existing data.",
                request
        );
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiErrorResponseDto> handleOptimisticLockException(Exception ex,
                                                                            HttpServletRequest request) {
        log.warn(
                "Optimistic locking conflict correlationId={} path={}",
                correlationId(request),
                request.getRequestURI()
        );
        return buildResponse(
                HttpStatus.CONFLICT,
                "error.concurrent-update",
                "Concurrent update",
                "Resource was updated by another request. Please reload and retry.",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponseDto> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error(
                "Unhandled exception correlationId={} path={}",
                correlationId(request),
                request.getRequestURI(),
                ex
        );
        ResponseEntity<ApiErrorResponseDto> response = buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "error.unexpected",
                "Unexpected error",
                null,
                request);
        if (includeInternalDetails && response.getBody() != null) {
            response.getBody().setMessage(ex.getMessage());
        }
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponseDto> handleValidationErrors(MethodArgumentNotValidException ex,
                                                                      HttpServletRequest request) {
        if (isAiMealDraftConfirmRequest(request)) {
            FieldError first = Objects.requireNonNull(ex.getFieldError());
            List<ApiErrorResponseDto.FieldErrorDto> fieldErrors = ex.getFieldErrors().stream()
                    .map(error -> new ApiErrorResponseDto.FieldErrorDto(safeFieldName(error.getField()), publicFieldErrorCode(error)))
                    .toList();
            return buildDomainResponse(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    validationCode(first),
                    "The request could not be processed.",
                    fieldErrors,
                    request
            );
        }

        List<ApiErrorResponseDto.FieldErrorDto> fieldErrors = ex.getFieldErrors().stream()
                .map(error -> new ApiErrorResponseDto.FieldErrorDto(
                        safeFieldName(error.getField()),
                        publicFieldErrorCode(error)))
                .toList();
        return buildDomainResponse(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.VALIDATION_ERROR.name(),
                resolveMessage("error.validation", "Validation error", request),
                fieldErrors,
                request
        );
    }
}
