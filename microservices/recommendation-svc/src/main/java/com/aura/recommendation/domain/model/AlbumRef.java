package com.aura.recommendation.domain.model;

import java.util.UUID;

public record AlbumRef(UUID id, String title, ArtistRef artist, Artwork artwork, Integer releaseYear) {
}
