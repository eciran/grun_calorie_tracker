package com.grun.calorietracker.exception;

public class DuplicateRecipePublicationRequestException extends RuntimeException {
    public DuplicateRecipePublicationRequestException(String message) {
        super(message);
    }
}
