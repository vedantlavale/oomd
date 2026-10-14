package com.vsms.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String what, Long id) {
        super(id == null ? what + " was not found." : what + " #" + id + " was not found.");
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
