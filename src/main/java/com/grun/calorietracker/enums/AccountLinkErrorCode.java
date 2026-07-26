package com.grun.calorietracker.enums;

import org.springframework.http.HttpStatus;

public enum AccountLinkErrorCode {
    INVALID_LINK_REQUEST(HttpStatus.BAD_REQUEST),
    REAUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED),
    LINK_AUTHORIZATION_INVALID(HttpStatus.UNAUTHORIZED),
    LINK_AUTHORIZATION_EXPIRED(HttpStatus.UNAUTHORIZED),
    LINK_AUTHORIZATION_ALREADY_USED(HttpStatus.UNAUTHORIZED),
    PROVIDER_ALREADY_LINKED(HttpStatus.CONFLICT),
    PROVIDER_IDENTITY_IN_USE(HttpStatus.CONFLICT),
    LAST_SIGN_IN_METHOD(HttpStatus.CONFLICT),
    PROVIDER_NOT_LINKED(HttpStatus.NOT_FOUND),
    PROVIDER_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    AccountLinkErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
