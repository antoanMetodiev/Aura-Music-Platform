package com.aura.catalog.adapter.provider.tidal;

import com.aura.catalog.domain.model.SearchType;
import com.aura.catalog.domain.port.ProviderSearchResult;
import com.aura.catalog.domain.port.ProviderTrack;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /searchResults} returns thin, title-only hits in relevance order; this verifies that order
 * survives the batch-hydrate step ({@link TidalHydrator}) that fills in album artwork afterwards.
 */
class TidalSearchProviderTest {

    private final ObjectMapper objectMapper = new JsonMapper();
    private final TidalMapper mapper = new TidalMapper(objectMapper);
    private final TidalApiClient client = mock(TidalApiClient.class);
    private final TidalProperties properties = new TidalProperties(
            true, "test-client-id", "test-client-secret",
            "https://openapi.tidal.com/v2", "https://auth.tidal.com/v1/oauth2/token",
            "US", Duration.ofSeconds(60), 20
    );
    private final TidalSearchProvider provider = new TidalSearchProvider(client, mapper, properties, objectMapper, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());

    @Test
    void search_preservesRelevanceOrderAndHydratesAlbumArtwork() {
        when(client.search("billie eilish", Set.of("tracks")))
                .thenReturn(TidalFixtures.load(objectMapper, "search-results.json"));
        when(client.tracksByIds(List.of("251380837", "999888777"), TidalIncludes.TRACK))
                .thenReturn(TidalFixtures.load(objectMapper, "tracks-batch-hydrated.json"));
        when(client.albumsByIds(List.of("360095579"), TidalIncludes.ALBUM))
                .thenReturn(TidalFixtures.load(objectMapper, "albums-batch-hydrated.json"));
        when(client.artistsByIds(List.of("7514330"), TidalIncludes.ARTIST))
                .thenReturn(TidalFixtures.load(objectMapper, "artists-batch-hydrated.json"));

        ProviderSearchResult result = provider.search("billie eilish", Set.of(SearchType.TRACKS), 10);

        assertThat(result.tracks()).extracting(ProviderTrack::title)
                .containsExactly("BIRDS OF A FEATHER", "WILDFLOWER");
        assertThat(result.tracks().get(0).album().artwork()).isNotNull();
        assertThat(result.tracks().get(1).album().artwork())
                .as("both tracks share one album — a single batched call must hydrate it for both")
                .isEqualTo(result.tracks().get(0).album().artwork());
        assertThat(result.tracks().get(0).artists().getFirst().artwork())
                .as("shared artist should be hydrated with profile art by one batched call")
                .isNotNull();
        assertThat(result.albums()).isEmpty();
        assertThat(result.artists()).isEmpty();

        verify(client).artistsByIds(List.of("7514330"), TidalIncludes.ARTIST);
    }

    @Test
    void search_withNoRequestedTypes_returnsEmptyWithoutCallingTidal() {
        ProviderSearchResult result = provider.search("anything", Set.of(), 10);

        assertThat(result.tracks()).isEmpty();
        assertThat(result.albums()).isEmpty();
        assertThat(result.artists()).isEmpty();
    }
}
