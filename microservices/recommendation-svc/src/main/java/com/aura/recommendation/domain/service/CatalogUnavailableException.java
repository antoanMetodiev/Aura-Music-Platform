package com.aura.recommendation.domain.service;

/** catalog-svc could not be reached. Without it we have no artists to recommend, only edges. */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(Throwable cause) {
        super("catalog-svc is currently unavailable", cause);
    }
}
