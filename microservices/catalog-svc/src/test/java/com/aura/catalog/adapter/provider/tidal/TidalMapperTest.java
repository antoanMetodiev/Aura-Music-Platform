package com.aura.catalog.adapter.provider.tidal;

import com.aura.catalog.adapter.provider.tidal.dto.JsonApiDocument;
import com.aura.catalog.adapter.provider.tidal.dto.JsonApiResource;
import com.aura.catalog.domain.model.AlbumType;
import com.aura.catalog.domain.model.Provider;
import com.aura.catalog.domain.model.ProviderReference;
import com.aura.catalog.domain.port.ProviderAlbum;
import com.aura.catalog.domain.port.ProviderTrack;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure parsing test: one fully-included JSON:API document (track + album + artist + artworks all
 * present, as if every hydration step had already happened) → verifies {@link TidalMapper} reads
 * TIDAL's field names correctly and {@link TidalMapper#pickRendition} picks the right artwork size.
 */
class TidalMapperTest {

    private final ObjectMapper objectMapper = new JsonMapper();
    private final TidalMapper mapper = new TidalMapper(objectMapper);

    @Test
    void mapsATrackWithAlbumAndArtistArtwork() {
        JsonApiDocument doc = TidalFixtures.load(objectMapper, "track-full.json");
        ResourceIndex index = new ResourceIndex();
        index.add(doc, objectMapper);

        JsonApiResource trackResource = index.get("tracks", "251380837").orElseThrow();
        ProviderTrack track = mapper.toTrack(trackResource, index);

        assertThat(track.ref()).isEqualTo(new ProviderReference(Provider.TIDAL, "251380837"));
        assertThat(track.title()).isEqualTo("BIRDS OF A FEATHER");
        assertThat(track.version()).isNull();
        assertThat(track.durationMs()).isEqualTo(Duration.ofMinutes(3).plusSeconds(30).toMillis());
        assertThat(track.isrc()).isEqualTo("USUM72400123");
        assertThat(track.explicit()).isFalse();
        assertThat(track.popularity()).isEqualTo(0.92);

        ProviderAlbum album = track.album();
        assertThat(album).isNotNull();
        assertThat(album.title()).isEqualTo("HIT ME HARD AND SOFT");
        assertThat(album.type()).isEqualTo(AlbumType.ALBUM);
        assertThat(album.releaseDate()).isEqualTo(LocalDate.of(2024, 5, 17));
        assertThat(album.numberOfTracks()).isEqualTo(10);
        // 320/640/1280 available; 640 is the smallest rendition at or above the preferred width.
        assertThat(album.artwork()).isNotNull();
        assertThat(album.artwork().url()).isEqualTo("https://resources.tidal.com/covers/album-640.jpg");
        assertThat(album.artwork().width()).isEqualTo(640);
        assertThat(album.artist()).isNotNull();
        assertThat(album.artist().name()).isEqualTo("Billie Eilish");

        assertThat(track.artists()).hasSize(1);
        assertThat(track.artists().getFirst().name()).isEqualTo("Billie Eilish");
        assertThat(track.artists().getFirst().artwork().url()).isEqualTo("https://resources.tidal.com/images/artist-750.jpg");
    }

    @Test
    void missingRelationshipsDegradeToNullInsteadOfThrowing() {
        JsonApiDocument doc = TidalFixtures.load(objectMapper, "track-basic.json");
        ResourceIndex index = new ResourceIndex();
        index.add(doc, objectMapper);

        JsonApiResource trackResource = index.get("tracks", "251380837").orElseThrow();
        ProviderTrack track = mapper.toTrack(trackResource, index);

        // track-basic's album has no coverArt relationship — must degrade gracefully, not NPE.
        assertThat(track.album()).isNotNull();
        assertThat(track.album().artwork()).isNull();
        assertThat(track.album().artist()).isNull();
    }
}
