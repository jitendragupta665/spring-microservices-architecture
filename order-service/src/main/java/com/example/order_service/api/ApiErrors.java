package com.example.order_service.api;

import com.example.order_service.domain.UnknownUserException;
import com.example.order_service.domain.UserServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception -> HTTP status, in one place, as RFC 9457 problem JSON. The status tells the
 * client WHO must act:
 *   4xx = the request is the problem (fix it, do not retry as-is)
 *   5xx = the server side is the problem (retrying later may work)
 * Validation (400) and malformed JSON (400) come from Spring itself, in the same format,
 * because spring.mvc.problemdetails.enabled=true.
 */
@RestControllerAdvice
class ApiErrors {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail orderNotFound(OrderNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UnknownUserException.class)
    ProblemDetail unknownUser(UnknownUserException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    ResponseEntity<ProblemDetail> userServiceDown(UserServiceUnavailableException e) {
        // Full cause in OUR log; the client only gets a generic message (no internal host names).
        log.warn("order rejected, user-service unavailable", e);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "user-service is unavailable, try again later");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")   // hint: seconds before a retry makes sense
                .body(body);
    }
}
