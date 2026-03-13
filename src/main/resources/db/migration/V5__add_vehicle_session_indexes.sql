-- Index for the most frequent query: find active sessions by license_plate
CREATE INDEX idx_session_plate_status ON vehicle_session (license_plate, status);

-- Index for revenue aggregation query: filter by sector + exit_time date
CREATE INDEX idx_session_sector_exit ON vehicle_session (sector, exit_time);
