package com.example.user_service.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps exceptions to HTTP errors in ONE place, as RFC 9457 "problem details":
 * Content-Type application/problem+json, body {"type","title","status","detail","instance"}.
 * Every service uses the same error shape, so a client parses errors one way.
 * Framework errors (bad path variable, unknown path) get the same shape through
 * spring.mvc.problemdetails.enabled=true.
 */
@RestControllerAdvice
class ApiErrors {

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail userNotFound(UserNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
