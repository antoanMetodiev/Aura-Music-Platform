-- Keys are inserted by hand; don't make the operator mint the UUID.
ALTER TABLE playback.youtube_api_keys ALTER COLUMN id SET DEFAULT gen_random_uuid();
