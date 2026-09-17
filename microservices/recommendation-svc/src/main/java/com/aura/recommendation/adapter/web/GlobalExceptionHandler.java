package com.aura.recommendation.adapter.web;

import com.aura.recommendation.adapter.web.dto.ApiError;
import com.aura.recommendation.config.RequestIdFilter;
import com.aura.recommendation.domain.service.CatalogUnavailableException;
import com.aura.recommendation.domain.service.ProviderUnavailableException;
import com.aura.recommendation.domain.service.SeedNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns every exception this API can throw into the {@code {code, message, traceId}} shape from
 * Project-Info.md §49. Never forwards a stack trace to the client (§35, §49).
 *
 * <p>Note what is <em>not</em> here: playback-svc being down is not an error at this layer. A feed
 * that cannot be checked for playability is served unfiltered instead (§48), so it never reaches here.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SeedNotFoundException.class)
    public ResponseEntity<ApiError> seedNotFound(SeedNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "SEED_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(CatalogUnavailableException.class)
    public ResponseEntity<ApiError> catalogUnavailable(CatalogUnavailableException e) {
        log.warn("catalog-svc unavailable: {}", e.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "CATALOG_UNAVAILABLE", "The music catalog is currently unavailable");
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    public ResponseEntity<ApiError> providerUnavailable(ProviderUnavailableException e) {
        log.warn("Taste-data provider unavailable: {}", e.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "PROVIDER_UNAVAILABLE", "The recommendation data provider is currently unavailable");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> internalError(Exception e) {
        log.error("Unhandled exception", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong on our side");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        String traceId = MDC.get(RequestIdFilter.MDC_KEY);
        return ResponseEntity.status(status).body(new ApiError(code, message, traceId));
    }
}
