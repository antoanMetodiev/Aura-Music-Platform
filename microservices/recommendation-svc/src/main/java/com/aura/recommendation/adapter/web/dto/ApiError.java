package com.aura.recommendation.adapter.web.dto;

/** Uniform error body for every 4xx/5xx response (Project-Info.md §49). Never a stack trace. */
public record ApiError(String code, String message, String traceId) {
}
