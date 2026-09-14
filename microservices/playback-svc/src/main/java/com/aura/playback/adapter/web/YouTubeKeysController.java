package com.aura.playback.adapter.web;

import com.aura.playback.adapter.provider.youtube.YouTubeApiKeySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Per-key YouTube quota balance (Project-Info.md §20: quota is critical infrastructure — make it visible). */
@RestController
@RequestMapping("/api/v1/playback/keys")
public class YouTubeKeysController {

    public record KeysResponse(int keys, int unitsRemainingTotal, int searchesRemainingTotal, List<YouTubeApiKeySource.KeyStatus> details) {
    }

    private final YouTubeApiKeySource keys;

    public YouTubeKeysController(YouTubeApiKeySource keys) {
        this.keys = keys;
    }

    @GetMapping("/status")
    public KeysResponse status() {
        List<YouTubeApiKeySource.KeyStatus> details = keys.status();
        int units = details.stream().filter(YouTubeApiKeySource.KeyStatus::enabled).mapToInt(YouTubeApiKeySource.KeyStatus::unitsRemaining).sum();
        int searches = details.stream().filter(YouTubeApiKeySource.KeyStatus::enabled).mapToInt(YouTubeApiKeySource.KeyStatus::searchesRemaining).sum();
        return new KeysResponse(details.size(), units, searches, details);
    }
}
