package com.aura.recommendation.domain.service;

import com.aura.recommendation.domain.model.Provider;

/** The taste-data provider is down, rate-limiting us, or the circuit breaker is open. Never cached as an answer. */
public class ProviderUnavailableException extends RuntimeException {

    private final transient Provider provider;

    public ProviderUnavailableException(Provider provider, Throwable cause) {
        super(provider + " is currently unavailable", cause);
        this.provider = provider;
    }

    public Provider provider() {
        return provider;
    }
}
