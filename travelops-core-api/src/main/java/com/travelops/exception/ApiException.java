package com.travelops.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown by services for expected failures. The handler turns it into
 * {"error": code, "message": message} with the given status.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
