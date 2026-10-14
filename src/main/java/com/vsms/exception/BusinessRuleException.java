package com.vsms.exception;

/**
 * Thrown when a request is valid but breaks a business rule (duplicate registration number,
 * not enough stock, invalid status change, ...). The message is safe to show to the user.
 */
public class BusinessRuleException extends RuntimeException {

    private final String field;

    public BusinessRuleException(String message) {
        this(null, message);
    }

    /** @param field name of the form field the message belongs to, or null for a general error */
    public BusinessRuleException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
