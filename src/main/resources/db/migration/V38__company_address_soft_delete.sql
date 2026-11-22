-- V38__company_address_soft_delete.sql
-- Adds soft-delete to company_address, mirroring company/company_user's deleted_at + @SQLRestriction
-- mechanism (V37/V29): CompanyAddressService.delete() no longer issues a real SQL DELETE, it sets
-- deleted_at instead (CompanyAddress.markDeleted) so address history is preserved the same way every
-- other entity in this module already behaves.
--
-- The existing "exactly one primary address per company" partial unique index
-- (uq_company_address_primary, V37) must be redefined to also require deleted_at IS NULL - a
-- soft-deleted row must never count toward that constraint. Without this, a soft-deleted former
-- primary address would permanently occupy the "is_primary = TRUE" slot for its company and block
-- any replacement address from ever being marked primary again.

ALTER TABLE company_address ADD COLUMN deleted_at TIMESTAMPTZ;

DROP INDEX uq_company_address_primary;
CREATE UNIQUE INDEX uq_company_address_primary
    ON company_address (company_id) WHERE is_primary = TRUE AND deleted_at IS NULL;
