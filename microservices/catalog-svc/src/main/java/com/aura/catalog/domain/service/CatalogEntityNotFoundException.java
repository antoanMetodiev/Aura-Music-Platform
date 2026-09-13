package com.aura.catalog.domain.service;

public class CatalogEntityNotFoundException extends RuntimeException {

    private final String code;

    public CatalogEntityNotFoundException(String entity, Object id) {
        super(entity + " " + id + " was not found");
        this.code = entity.toUpperCase() + "_NOT_FOUND";
    }

    public String code() {
        return code;
    }
}
