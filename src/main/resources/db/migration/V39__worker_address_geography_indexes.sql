-- V39__worker_address_geography_indexes.sql
-- worker_address.state_id/city_id (V22__worker.sql) were never indexed, unlike the equivalent
-- admin_user_address.state_id/city_id (V18__admin_user_address.sql) - a pre-existing gap found
-- while adding scoped filter-option lookups (distinct states/cities actually used by workers), but
-- it already affects the existing stateId/cityId worker-list filters today too. Additive only - no
-- data change.

CREATE INDEX idx_worker_address_state_id ON worker_address (state_id);
CREATE INDEX idx_worker_address_city_id ON worker_address (city_id);
