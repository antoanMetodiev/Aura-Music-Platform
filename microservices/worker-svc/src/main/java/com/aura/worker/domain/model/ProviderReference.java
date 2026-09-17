package com.aura.worker.domain.model;

/** Link between one of our canonical entities and the provider's own identifier (Project-Info.md §13). */
public record ProviderReference(Provider provider, String providerResourceId) {
}
