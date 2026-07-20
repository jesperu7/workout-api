-- =============================================================================
-- V5  [v1.1] Optional workout name.
--
-- The client shows named sessions ("Push Day") on home/history and keys its
-- "repeat last workout" feature on the name. A real, nullable column (rather than
-- overloading `notes`) — NULL means untitled. Additive and backward-compatible;
-- no backfill. RLS on `workouts` (V4) is unaffected by adding a column.
-- =============================================================================

ALTER TABLE workouts ADD COLUMN name text;
