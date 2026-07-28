package com.grun.calorietracker.exception;

import com.grun.calorietracker.enums.FastingSafetyErrorCode;
import lombok.Getter;

@Getter
public class FastingSafetyException extends RuntimeException {
    private final FastingSafetyErrorCode code;

    public FastingSafetyException(FastingSafetyErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
