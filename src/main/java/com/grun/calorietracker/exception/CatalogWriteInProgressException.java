package com.grun.calorietracker.exception;

public class CatalogWriteInProgressException extends RuntimeException {
    public CatalogWriteInProgressException() {
        super("Another catalog update is currently in progress.");
    }
}
