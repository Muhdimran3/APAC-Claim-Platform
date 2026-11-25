package com.example.claims.common.web;

import com.example.claims.common.web.ApiExceptions.BadRequestException;
import com.example.claims.common.web.ApiExceptions.BusinessRuleException;
import com.example.claims.common.web.ApiExceptions.ConflictException;
import com.example.claims.common.web.ApiExceptions.ForbiddenException;
import com.example.claims.common.web.ApiExceptions.NotFoundException;
import com.example.claims.common.web.ApiExceptions.UnauthenticatedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps exceptions to RFC 7807 problem details. Status codes:
 * 400 validation, 401 no/unknown user, 403 not allowed, 404 not found (or not yours),
 * 409 wrong state / concurrent update, 422 business rule.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnauthenticatedException.class)
    ProblemDetail unauthenticated(UnauthenticatedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden(ForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail businessRule(BusinessRuleException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail badRequest(BadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "The claim was changed by someone else. Reload and try again.");
    }

    /** Safety net for DB constraints (e.g. two concurrent info requests); the service should catch most cases first. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException ex) {
        log.warn("Constraint violation reached the API layer", ex);
        return problem(HttpStatus.CONFLICT, "The request conflicts with the current data.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errors.putIfAbsent(e.getObjectName(), e.getDefaultMessage()));
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        return pd;
    }
}
