package com.travelplatform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// @ResponseStatus is a defensive backstop: GlobalExceptionHandler also maps this to 404,
// but if the exception ever bubbles up from a context not covered by @RestControllerAdvice
// (e.g. an async event listener), Spring still translates it to 404 instead of 500.
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
