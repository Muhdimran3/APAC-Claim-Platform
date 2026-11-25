package com.example.claims.common.web;

/**
 * Exception types the API maps to HTTP status codes (see {@link GlobalExceptionHandler}).
 * Modules throw these (or subclasses) so the web layer never depends on module internals.
 */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    /** 401: missing or unknown X-User header. */
    public static class UnauthenticatedException extends RuntimeException {
        public UnauthenticatedException(String message) {
            super(message);
        }
    }

    /** 403: caller is known but not allowed to do this. */
    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) {
            super(message);
        }
    }

    /** 404: resource does not exist, or the caller may not know it exists. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    /** 409: request is valid but conflicts with the resource's current state. */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    /** 422: well-formed request that breaks a business rule (e.g. amount above sum insured). */
    public static class BusinessRuleException extends RuntimeException {
        public BusinessRuleException(String message) {
            super(message);
        }
    }

    /** 400: malformed input not covered by bean validation (e.g. unknown filter value). */
    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }
}
