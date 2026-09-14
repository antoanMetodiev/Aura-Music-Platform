-- Per-key daily quota accounting (YouTube Data API: 10 000 units/day per project; search.list costs
-- 100, videos.list 1). `quota_day` is the Pacific-time date the counter belongs to — YouTube resets
-- at midnight Pacific — so a counter from an earlier day simply reads as zero (lazy reset, no job).
ALTER TABLE playback.youtube_api_keys
    ADD COLUMN daily_quota_units INTEGER NOT NULL DEFAULT 10000,
    ADD COLUMN units_used_today  INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN quota_day         DATE;
