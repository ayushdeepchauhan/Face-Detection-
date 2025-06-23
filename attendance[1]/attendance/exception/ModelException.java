package com.attendance.exception;

/**
 * Custom exception class for handling model-related errors in the AutoAttend system.
 */
public class ModelException extends Exception {
    
    public ModelException(String message) {
        super(message);
    }
    
    public ModelException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public ModelException(Throwable cause) {
        super(cause);
    }
}
