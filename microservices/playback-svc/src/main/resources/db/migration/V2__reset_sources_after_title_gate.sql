-- Every row so far was scored by a matcher that let a candidate become an AUTOMATIC match without
-- its title ever mentioning the song (the artist's own channel uploading a different song won on
-- channel + artist + "Official Video" alone). None of those matches can be trusted; drop them so
-- each track is re-resolved with the title gate on its next play.

DELETE FROM playback.track_sources;
