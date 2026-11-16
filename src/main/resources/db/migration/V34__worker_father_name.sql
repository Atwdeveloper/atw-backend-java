-- V34__worker_father_name.sql
-- Adds father's/care-of name, capturable on a worker's core profile (plain text, not encrypted -
-- same sensitivity tier as the worker's own name/address, not a government ID number) and on the
-- Aadhaar e-KYC staging record it can be pre-filled from (closing a gap this project's own Sandbox
-- adapter explicitly flagged: "deliberately does NOT capture care_of/father's name... a gap not being
-- filled this round").
ALTER TABLE worker ADD COLUMN father_name VARCHAR(255);
ALTER TABLE worker_aadhaar_verification ADD COLUMN verified_father_name VARCHAR(255);
