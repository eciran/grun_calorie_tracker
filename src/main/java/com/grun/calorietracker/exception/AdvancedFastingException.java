package com.grun.calorietracker.exception;

import com.grun.calorietracker.enums.AdvancedFastingErrorCode;
import lombok.Getter;

@Getter
public class AdvancedFastingException extends IllegalArgumentException {
    private final AdvancedFastingErrorCode code;

    public AdvancedFastingException(AdvancedFastingErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}