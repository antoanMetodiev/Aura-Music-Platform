package com.aura.worker.domain.model;

/** A single artwork rendition. `url` is provider-hosted; we never mirror image bytes (Project-Info.md §34). */
public record Artwork(String url, int width, int height) {
}
