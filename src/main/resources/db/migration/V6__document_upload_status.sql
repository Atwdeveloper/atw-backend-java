-- V6__document_upload_status.sql
-- Adds an upload lifecycle status to documents, so a metadata row and its blob can be reconciled
-- if the follow-up commit (PENDING -> COMPLETE) fails after the blob upload already succeeded.
-- See DocumentServiceImpl.upload() and DocumentUploadReconciliationJob.

ALTER TABLE documents ADD COLUMN upload_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETE';

-- The reconciliation job scans for PENDING rows older than a threshold, by created_at.
CREATE INDEX idx_documents_pending_reconciliation ON documents (upload_status, created_at);
