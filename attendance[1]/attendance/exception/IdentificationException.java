package com.attendance.exception;

/**
 * Custom exception for errors occurring during the face identification process.
 */
public class IdentificationException extends Exception {

    public IdentificationException(String message) {
        super(message);
    }

    public IdentificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
