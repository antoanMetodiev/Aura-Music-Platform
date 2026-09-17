package com.aura.recommendation.adapter.web;

import com.aura.recommendation.adapter.web.dto.FeedResponse;
import com.aura.recommendation.adapter.web.dto.HomeResponse;
import com.aura.recommendation.adapter.web.dto.RecommendedTrackResponse;
import com.aura.recommendation.adapter.web.dto.TagResponse;
import com.aura.recommendation.domain.model.ArtistRef;
import com.aura.recommendation.domain.model.RecommendedTrack;
import com.aura.recommendation.domain.port.SimilarityGraphStore;
import com.aura.recommendation.domain.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Public recommendations API (Project-Info.md §10 mounts this behind the gateway at the same paths).
 * Every endpoint here is served from our own graph — none of them can reach an external provider,
 * which is why they are all a few milliseconds and none of them can exhaust anyone's quota.
 */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    /** "Fans also like" — the artist's neighbours in the taste graph, as catalog artists. */
    @GetMapping("/artists/{id}/similar")
    public List<ArtistRef> similarArtists(@PathVariable UUID id,
                                          @RequestParam(value = "limit", required = false, defaultValue = "0") int limit) {
        return service.similarArtists(id, limit);
    }

    /** The artist's radio: their own music and their neighbours', playable ones only. */
    @GetMapping("/artists/{id}/radio")
    public FeedResponse artistRadio(@PathVariable UUID id,
                                    @RequestParam(value = "limit", required = false, defaultValue = "0") int limit) {
        return new FeedResponse("ARTIST", id.toString(), toResponses(service.artistRadio(id, limit)));
    }

    /** "Recommended based on this track" — seeded from the track's artists, the track itself excluded. */
    @GetMapping("/tracks/{id}/radio")
    public FeedResponse trackRadio(@PathVariable UUID id,
                                   @RequestParam(value = "limit", required = false, defaultValue = "0") int limit) {
        return new FeedResponse("TRACK", id.toString(), toResponses(service.trackRadio(id, limit)));
    }

    /** The genre grid: tags the graph knows the most artists under. */
    @GetMapping("/tags")
    public List<TagResponse> tags(@RequestParam(value = "limit", required = false, defaultValue = "24") int limit) {
        return service.topTags(limit).stream().map(RecommendationController::toResponse).toList();
    }

    @GetMapping("/tags/{tag}/tracks")
    public FeedResponse tagTracks(@PathVariable String tag,
                                  @RequestParam(value = "limit", required = false, defaultValue = "0") int limit) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("'tag' must not be blank");
        }
        return new FeedResponse("TAG", tag, toResponses(service.tagTracks(tag, limit)));
    }

    /** The catalog's most popular playable tracks — no taste involved. */
    @GetMapping("/trending")
    public FeedResponse trending(@RequestParam(value = "limit", required = false, defaultValue = "0") int limit) {
        return new FeedResponse("NONE", null, toResponses(service.trending(limit)));
    }

    /**
     * The whole home page in one call. Signed out for now — the personalized version arrives with the
     * library and listening history (Project-Info.md §42), and adds sections rather than replacing these.
     */
    @GetMapping("/home")
    public HomeResponse home(@RequestParam(value = "limit", required = false, defaultValue = "0") int limit) {
        RecommendationService.HomeFeed feed = service.home(limit);
        return new HomeResponse(
                feed.sections().stream()
                        .map(s -> new HomeResponse.Section(s.key(), toResponses(s.tracks())))
                        .toList(),
                feed.genres().stream().map(RecommendationController::toResponse).toList());
    }

    private static List<RecommendedTrackResponse> toResponses(List<RecommendedTrack> tracks) {
        return tracks.stream()
                .map(t -> new RecommendedTrackResponse(t.track(), t.score(), t.reason(), t.playable()))
                .toList();
    }

    private static TagResponse toResponse(SimilarityGraphStore.TagCount tag) {
        return new TagResponse(tag.tag(), tag.artistCount());
    }
}
