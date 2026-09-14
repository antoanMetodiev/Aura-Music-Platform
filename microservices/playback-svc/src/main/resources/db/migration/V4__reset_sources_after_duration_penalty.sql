-- Last part of the matcher fix: a large duration mismatch is now a penalty and Cyrillic live/cover/
-- remix markers are recognised, so a same-title concert recording no longer wins on channel + artist.
-- Rows resolved in between (dev data only) get re-scored on next play.

DELETE FROM playback.track_sources;
