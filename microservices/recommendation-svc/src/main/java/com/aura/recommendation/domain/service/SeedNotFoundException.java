package com.aura.recommendation.domain.service;

/** The artist or track a feed was asked to be seeded from doesn't exist in the catalog. */
public class SeedNotFoundException extends RuntimeException {

    public SeedNotFoundException(String type, Object id) {
        super(type + " " + id + " was not found");
    }
}
