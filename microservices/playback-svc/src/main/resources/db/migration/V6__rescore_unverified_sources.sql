-- Matcher now credits label channels, any "Official …" title marker and a wider duration window;
-- outcomes that ended as unverified (candidate / nothing found) deserve a second look on next play.
-- Verified matches are untouched.

DELETE FROM playback.track_sources WHERE is_verified = false;
