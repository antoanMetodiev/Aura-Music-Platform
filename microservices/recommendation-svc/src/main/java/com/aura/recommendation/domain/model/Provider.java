package com.aura.recommendation.domain.model;

/** External taste-data providers. Never leaks into API responses as a primary key. */
public enum Provider {
    /** Similar artists and community tags (https://www.last.fm/api). */
    LASTFM
}
