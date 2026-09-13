package com.aura.playback.domain.service;

/** Raised when catalog-svc can't be reached to fetch the canonical track we need to resolve. */
public class CatalogServiceUnavailableException extends RuntimeException {

    public CatalogServiceUnavailableException(Throwable cause) {
        super("catalog-svc is currently unavailable", cause);
    }
}
