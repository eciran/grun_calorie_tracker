package com.grun.calorietracker.exception;

public class AiOutputLanguageMismatchException extends AiProviderException {
    public AiOutputLanguageMismatchException(String expectedLanguage) {
        super("AI provider returned user-visible text outside the requested "
                + expectedLanguage + " language.");
    }
}
