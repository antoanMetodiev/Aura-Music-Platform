-- Second half of the V2 matcher fix: duration windows widened from 1s/3s to 2s/6s, so rows scored
-- in between (correct uploads a few seconds off the provider duration, stored as unverified
-- candidates) deserve another pass.

DELETE FROM playback.track_sources;
