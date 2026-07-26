package com.grun.calorietracker.exception;

import com.grun.calorietracker.enums.AccountLinkErrorCode;
import lombok.Getter;

@Getter
public class AccountLinkException extends RuntimeException {

    private final AccountLinkErrorCode code;

    public AccountLinkException(AccountLinkErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
