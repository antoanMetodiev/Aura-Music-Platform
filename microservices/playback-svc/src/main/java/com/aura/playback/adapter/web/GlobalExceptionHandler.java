package com.aura.playback.adapter.web;

import com.aura.playback.adapter.provider.youtube.YouTubeApiClient;
import com.aura.playback.adapter.web.dto.ApiError;
import com.aura.playback.config.RequestIdFilter;
import com.aura.playback.domain.service.CatalogServiceUnavailableException;
import com.aura.playback.domain.service.NoConfidentPlaybackMatchException;
import com.aura.playback.domain.service.PlaybackProviderUnavailableException;
import com.aura.playback.domain.service.TrackNotFoundException;
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
 * Turns every exception the playback API can throw into the {@code {code, message, traceId}} shape
 * from Project-Info.md §49. Never forwards a stack trace to the client (§35, §49).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TrackNotFoundException.class)
    public ResponseEntity<ApiError> trackNotFound(TrackNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "TRACK_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(NoConfidentPlaybackMatchException.class)
    public ResponseEntity<ApiError> noConfidentMatch(NoConfidentPlaybackMatchException e) {
        return error(HttpStatus.NOT_FOUND, "PLAYBACK_UNAVAILABLE", "No confident playback source is available for this track");
    }

    @ExceptionHandler(PlaybackProviderUnavailableException.class)
    public ResponseEntity<ApiError> providerUnavailable(PlaybackProviderUnavailableException e) {
        log.warn("Upstream playback provider unavailable: {}", e.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "PROVIDER_UNAVAILABLE", "The playback source provider is currently unavailable");
    }

    @ExceptionHandler(CatalogServiceUnavailableException.class)
    public ResponseEntity<ApiError> catalogUnavailable(CatalogServiceUnavailableException e) {
        log.warn("catalog-svc unavailable: {}", e.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "CATALOG_UNAVAILABLE", "The music catalog is currently unavailable");
    }

    @ExceptionHandler(YouTubeApiClient.YouTubeApiException.class)
    public ResponseEntity<ApiError> providerRejectedRequest(YouTubeApiClient.YouTubeApiException e) {
        log.error("YouTube rejected a request: {}", e.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR", "The playback source provider rejected the request");
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
