package com.aura.playback.adapter.provider.youtube;

import com.aura.playback.adapter.provider.youtube.dto.YouTubeSearchResponse;
import com.aura.playback.adapter.provider.youtube.dto.YouTubeVideosResponse;
import com.aura.playback.domain.model.CanonicalTrack;
import com.aura.playback.domain.model.PlaybackProvider;
import com.aura.playback.domain.model.VideoCandidate;
import com.aura.playback.domain.port.VideoSearchProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link VideoSearchProvider} backed by the YouTube Data API v3. {@code search.list} gives us
 * candidate video ids with only a snippet (no duration, no embeddability), so every search is
 * followed by one {@code videos.list} batch call to hydrate the fields the matcher actually scores
 * on (Project-Info.md §17).
 */
@Component
@ConditionalOnProperty(prefix = "music.providers.youtube", name = "enabled", havingValue = "true", matchIfMissing = true)
public class YouTubeVideoSearchProvider implements VideoSearchProvider {

    private final YouTubeApiClient client;

    public YouTubeVideoSearchProvider(YouTubeApiClient client) {
        this.client = client;
    }

    @Override
    public PlaybackProvider provider() {
        return PlaybackProvider.YOUTUBE;
    }

    @Override
    public List<VideoCandidate> search(CanonicalTrack track) {
        String query = (track.primaryArtist() + " " + track.title()).trim();
        YouTubeSearchResponse searchResults = client.search(query);

        List<String> videoIds = searchResults.items().stream()
                .map(item -> item.id().videoId())
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (videoIds.isEmpty()) return List.of();

        YouTubeVideosResponse videos = client.videosByIds(videoIds);
        Map<String, YouTubeVideosResponse.Item> byId = new LinkedHashMap<>();
        videos.items().forEach(item -> byId.put(item.id(), item));

        // Keep search.list's relevance order; drop any id videos.list didn't return (e.g. deleted
        // between the two calls).
        return videoIds.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toCandidate)
                .toList();
    }

    private VideoCandidate toCandidate(YouTubeVideosResponse.Item item) {
        long durationMs = parseDurationMs(item.contentDetails() == null ? null : item.contentDetails().duration());
        boolean embeddable = item.status() != null && item.status().embeddable();
        var snippet = item.snippet();
        return new VideoCandidate(
                item.id(),
                snippet == null ? "" : snippet.title(),
                snippet == null ? "" : snippet.description(),
                snippet == null ? null : snippet.channelId(),
                snippet == null ? "" : snippet.channelTitle(),
                durationMs,
                embeddable
        );
    }

    private static long parseDurationMs(String iso8601Duration) {
        if (iso8601Duration == null || iso8601Duration.isBlank()) return 0L;
        try {
            return Duration.parse(iso8601Duration).toMillis();
        } catch (java.time.format.DateTimeParseException e) {
            return 0L;
        }
    }
}
