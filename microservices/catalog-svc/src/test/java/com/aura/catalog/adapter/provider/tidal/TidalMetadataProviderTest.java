package com.aura.catalog.adapter.provider.tidal;

import com.aura.catalog.domain.port.ProviderTrack;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the real two-call hydration flow (TIDAL's {@code include} is top-level only, so a track
 * fetch alone never carries album artwork — see {@link TidalIncludes}'s Javadoc): mocks only
 * {@link TidalApiClient}, so {@link ResourceIndex} merging and {@link TidalHydrator} batching run
 * for real against recorded fixtures. No network, no TIDAL credentials needed.
 */
class TidalMetadataProviderTest {

    private final ObjectMapper objectMapper = new JsonMapper();
    private final TidalMapper mapper = new TidalMapper(objectMapper);
    private final TidalApiClient client = mock(TidalApiClient.class);
    private final TidalProperties properties = new TidalProperties(
            true, "test-client-id", "test-client-secret",
            "https://openapi.tidal.com/v2", "https://auth.tidal.com/v1/oauth2/token",
            "US", Duration.ofSeconds(60), 20
    );
    private final TidalMetadataProvider provider = new TidalMetadataProvider(client, mapper, properties, objectMapper, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());

    @Test
    void getTrack_fetchesTrackThenBatchHydratesItsAlbum() {
        when(client.track("251380837", TidalIncludes.TRACK))
                .thenReturn(Optional.of(TidalFixtures.load(objectMapper, "track-basic.json")));
        when(client.albumsByIds(List.of("360095579"), TidalIncludes.ALBUM))
                .thenReturn(TidalFixtures.load(objectMapper, "albums-batch-hydrated.json"));
        when(client.artistsByIds(List.of("7514330"), TidalIncludes.ARTIST))
                .thenReturn(TidalFixtures.load(objectMapper, "artists-batch-hydrated.json"));

        Optional<ProviderTrack> result = provider.getTrack("251380837");

        assertThat(result).isPresent();
        ProviderTrack track = result.orElseThrow();
        assertThat(track.title()).isEqualTo("BIRDS OF A FEATHER");
        assertThat(track.album()).isNotNull();
        assertThat(track.album().artwork()).as("album artwork should come from the hydration call, not the track fetch").isNotNull();
        assertThat(track.album().artwork().url()).isEqualTo("https://resources.tidal.com/covers/album-640.jpg");
        assertThat(track.album().artist().name()).isEqualTo("Billie Eilish");
        assertThat(track.artists().getFirst().artwork())
                .as("artist profile art should come from the batched artist hydration call")
                .isNotNull();
        assertThat(track.artists().getFirst().artwork().url()).isEqualTo("https://resources.tidal.com/images/artist-750.jpg");

        verify(client).track("251380837", TidalIncludes.TRACK);
        verify(client).albumsByIds(List.of("360095579"), TidalIncludes.ALBUM);
        verify(client).artistsByIds(List.of("7514330"), TidalIncludes.ARTIST);
    }

    @Test
    void getTrack_returnsEmptyWhenTidalHasNoSuchTrack() {
        when(client.track("does-not-exist", TidalIncludes.TRACK)).thenReturn(Optional.empty());

        assertThat(provider.getTrack("does-not-exist")).isEmpty();
    }
}
