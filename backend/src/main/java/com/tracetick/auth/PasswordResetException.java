package com.tracetick.auth;

public class PasswordResetException extends RuntimeException {

    public enum Reason {
        INVALID,
        INVALIDATED,
        EXPIRED
    }

    private final Reason reason;

    private PasswordResetException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public static PasswordResetException invalid() {
        return new PasswordResetException(Reason.INVALID, "Invalid password reset token");
    }

    public static PasswordResetException invalidated() {
        return new PasswordResetException(Reason.INVALIDATED, "Password reset token has already been used");
    }

    public static PasswordResetException expired() {
        return new PasswordResetException(Reason.EXPIRED, "Password reset token has expired");
    }

    public Reason getReason() {
        return reason;
    }
}
