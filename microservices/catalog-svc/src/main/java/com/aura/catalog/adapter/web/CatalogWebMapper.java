package com.aura.catalog.adapter.web;

import com.aura.catalog.adapter.web.dto.AlbumResponse;
import com.aura.catalog.adapter.web.dto.AlbumSummaryResponse;
import com.aura.catalog.adapter.web.dto.ArtistResponse;
import com.aura.catalog.adapter.web.dto.ArtistSummaryResponse;
import com.aura.catalog.adapter.web.dto.ArtworkResponse;
import com.aura.catalog.adapter.web.dto.LyricsResponse;
import com.aura.catalog.adapter.web.dto.SearchResponse;
import com.aura.catalog.adapter.web.dto.TrackResponse;
import com.aura.catalog.domain.model.Album;
import com.aura.catalog.domain.model.Artist;
import com.aura.catalog.domain.model.Artwork;
import com.aura.catalog.domain.model.Lyrics;
import com.aura.catalog.domain.model.SearchResult;
import com.aura.catalog.domain.model.Track;
import org.springframework.stereotype.Component;

/** Domain records → the shapes the public API actually returns (no provider references leak out). */
@Component
public class CatalogWebMapper {

    public TrackResponse toResponse(Track track) {
        return new TrackResponse(
                track.id(),
                track.title(),
                track.version(),
                track.durationMs(),
                track.isrc(),
                track.explicit(),
                track.artists().stream().map(this::toSummary).toList(),
                track.album() == null ? null : toSummary(track.album()),
                toResponse(track.artwork()),
                track.volumeNumber(),
                track.trackNumber(),
                track.popularity(),
                track.createdAt()
        );
    }

    public AlbumResponse toFullResponse(Album album) {
        return new AlbumResponse(
                album.id(),
                album.title(),
                album.type().name(),
                album.releaseDate(),
                album.artist() == null ? null : toSummary(album.artist()),
                toResponse(album.artwork()),
                album.explicit(),
                album.numberOfTracks()
        );
    }

    public ArtistResponse toFullResponse(Artist artist) {
        return new ArtistResponse(artist.id(), artist.name(), toResponse(artist.artwork()), artist.popularity());
    }

    public SearchResponse toResponse(SearchResult result) {
        return new SearchResponse(
                result.query(),
                result.tracks().stream().map(this::toResponse).toList(),
                result.albums().stream().map(this::toSummary).toList(),
                result.artists().stream().map(this::toSummary).toList()
        );
    }

    private AlbumSummaryResponse toSummary(Album album) {
        Integer releaseYear = album.releaseDate() == null ? null : album.releaseDate().getYear();
        return new AlbumSummaryResponse(
                album.id(), album.title(), album.artist() == null ? null : toSummary(album.artist()),
                toResponse(album.artwork()), releaseYear
        );
    }

    private ArtistSummaryResponse toSummary(Artist artist) {
        return new ArtistSummaryResponse(artist.id(), artist.name(), toResponse(artist.artwork()));
    }

    private ArtworkResponse toResponse(Artwork artwork) {
        return artwork == null ? null : new ArtworkResponse(artwork.url(), artwork.width(), artwork.height());
    }

    public LyricsResponse toResponse(Lyrics lyrics) {
        return new LyricsResponse(
                lyrics.trackId(),
                lyrics.provider().name(),
                lyrics.instrumental(),
                lyrics.hasSynced()
                        ? lyrics.synced().stream().map(l -> new LyricsResponse.SyncedLineResponse(l.timeMs(), l.text())).toList()
                        : null,
                lyrics.hasPlain() ? lyrics.plain() : null
        );
    }
}
