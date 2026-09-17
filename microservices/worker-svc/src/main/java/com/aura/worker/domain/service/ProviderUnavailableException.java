package com.aura.worker.domain.service;

import com.aura.worker.domain.model.Provider;

/** Raised when a provider call fails after retries / circuit is open. Callers may fall back to local data. */
public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException(Provider provider, Throwable cause) {
        super(provider + " is currently unavailable", cause);
    }
}
