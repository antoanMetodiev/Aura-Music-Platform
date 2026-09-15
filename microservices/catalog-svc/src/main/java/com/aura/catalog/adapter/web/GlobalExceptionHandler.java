package com.aura.catalog.adapter.web;

import com.aura.catalog.adapter.provider.lrclib.LrclibApiClient;
import com.aura.catalog.adapter.provider.tidal.TidalApiClient;
import com.aura.catalog.adapter.web.dto.ApiError;
import com.aura.catalog.config.RequestIdFilter;
import com.aura.catalog.domain.service.CatalogEntityNotFoundException;
import com.aura.catalog.domain.service.ProviderUnavailableException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns every exception the catalog API can throw into the {@code {code, message, traceId}} shape
 * from Project-Info.md §49. Never forwards a stack trace to the client (§35, §49).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CatalogEntityNotFoundException.class)
    public ResponseEntity<ApiError> notFound(CatalogEntityNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.code(), e.getMessage());
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    public ResponseEntity<ApiError> providerUnavailable(ProviderUnavailableException e) {
        log.warn("Upstream provider unavailable: {}", e.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "PROVIDER_UNAVAILABLE", "The music metadata provider is currently unavailable");
    }

    @ExceptionHandler(LrclibApiClient.LrclibApiException.class)
    public ResponseEntity<ApiError> lyricsProviderRejectedRequest(LrclibApiClient.LrclibApiException e) {
        log.error("LRCLIB rejected a request: {}", e.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR", "The lyrics provider rejected the request");
    }

    @ExceptionHandler(TidalApiClient.TidalApiException.class)
    public ResponseEntity<ApiError> providerRejectedRequest(TidalApiClient.TidalApiException e) {
        log.error("TIDAL rejected a request: {}", e.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR", "The music metadata provider rejected the request");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage());
    }

    /** A path/query value that can't be converted (e.g. a non-UUID id) is the caller's mistake, not ours. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid value for '" + e.getName() + "'");
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
