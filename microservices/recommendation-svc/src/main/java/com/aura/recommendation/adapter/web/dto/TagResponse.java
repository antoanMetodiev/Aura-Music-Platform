package com.aura.recommendation.adapter.web.dto;

/** A genre/tag and how many of our artists carry it. */
public record TagResponse(String tag, int artistCount) {
}
