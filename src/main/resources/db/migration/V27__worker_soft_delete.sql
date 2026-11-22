-- V27__worker_soft_delete.sql
-- Converts worker deletion from a hard delete to a soft delete: WorkerService.delete() no longer
-- issues a real SQL DELETE, only an UPDATE that sets deleted_at. This preserves a deleted worker's
-- full history (KYC profile, face-biometric enrollment, bank detail, address, notes, status history)
-- instead of losing it to the ON DELETE CASCADE chain those tables already have (see V22__worker.sql/
-- V23__kyc.sql/V25__faceauth.sql) - the original hard-delete slice (added earlier this session) was
-- explicitly provisional pending this decision; see WorkerService.delete()'s Javadoc for the full
-- reasoning.
--
-- deleted_at is the single source of truth - nullable, no separate boolean flag: NULL means active,
-- NOT NULL means deleted (and holds the deletion time). Worker.java's
-- @SQLRestriction("deleted_at is null") transparently excludes a deleted worker from every existing
-- query path (findById, Specification-based lists, the pessimistic-lock JPQL,
-- existsByMobileNumber/existsByMobileNumberAndIdNot) with no code changes needed anywhere else - this
-- is Hibernate's own documented mechanism for exactly this, not a custom framework.
ALTER TABLE worker ADD COLUMN deleted_at TIMESTAMPTZ;

-- mobile_number must stay unique only among ACTIVE (non-deleted) workers: a deleted worker's number
-- becomes available again for a genuinely new worker to register with (real-world SIM/number
-- recycling - a deliberate product decision, not an oversight). A plain global UNIQUE constraint
-- can't express "unique among a subset of rows"; PostgreSQL's partial unique index can.
ALTER TABLE worker DROP CONSTRAINT uq_worker_mobile_number;
CREATE UNIQUE INDEX uq_worker_mobile_number_active ON worker (mobile_number) WHERE deleted_at IS NULL;

-- Supports a future "deleted workers" admin/compliance report without a sequential scan, if one is
-- ever built - cheap to add now (partial index, tiny while deletions stay rare), costs nothing today.
CREATE INDEX idx_worker_deleted_at ON worker (deleted_at) WHERE deleted_at IS NOT NULL;
