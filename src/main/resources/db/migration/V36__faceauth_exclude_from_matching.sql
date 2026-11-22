-- V36__faceauth_exclude_from_matching.sql
-- Lets worker tell faceauth "this worker's face template should no longer come up as a match" at
-- the moment it becomes true (today: worker soft-delete), WITHOUT faceauth ever having to ask worker
-- "who's still eligible" at match time - that second direction would make worker and faceauth depend
-- on each other (worker already depends on faceauth for the enrollment-status badge on a worker's own
-- view), which Spring Modulith's own ModularityTests.verifiesModularStructure correctly rejects as a
-- module cycle. The row itself is deliberately NOT deleted (same "preserve for audit" rule
-- V27__worker_soft_delete.sql already established for every other worker satellite table) - only
-- excluded from the candidate set identify()/the enrollment-time duplicate check ever compare against.
ALTER TABLE worker_face_enrollment
    ADD COLUMN excluded_from_matching_at TIMESTAMPTZ;
