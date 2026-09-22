package com.grun.calorietracker.exception;

/** Only explicitly safe approval messages may be returned to the admin client. */
public class ApprovalDecisionException extends IllegalArgumentException {
    public ApprovalDecisionException(String message) { super(message); }

    public static final class Expired extends ApprovalDecisionException {
        public Expired() { super("Approval request has expired. Create a new request if the change is still needed."); }
    }
}
