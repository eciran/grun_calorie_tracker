package com.grun.calorietracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Standard API error response.")
public class ApiErrorResponseDto {

    @Schema(description = "Server timestamp when the error was produced.")
    private LocalDateTime timestamp;

    @Schema(description = "HTTP status code.")
    private int status;

    @Schema(description = "Short error category.")
    private String error;

    @Schema(description = "Stable machine-readable error code.")
    private String code;

    @Schema(description = "Safe public field validation errors.")
    private List<FieldErrorDto> fieldErrors;

    @Schema(description = "Human-readable error detail.")
    private String message;

    @Schema(description = "Request path that produced the error.")
    private String path;

    @Schema(description = "Correlation id used to trace the request across logs and clients.")
    private String correlationId;

    public ApiErrorResponseDto() {
    }

    public ApiErrorResponseDto(LocalDateTime timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, null);
    }

    public ApiErrorResponseDto(LocalDateTime timestamp, int status, String error, String message, String path, String correlationId) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.correlationId = correlationId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<FieldErrorDto> getFieldErrors() {
        return fieldErrors;
    }

    public void setFieldErrors(List<FieldErrorDto> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public static class FieldErrorDto {
        private String field;
        private String code;

        public FieldErrorDto() {
        }

        public FieldErrorDto(String field, String code) {
            this.field = field;
            this.code = code;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }}
