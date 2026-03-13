-- Add control_type to garage_sector (default PHYSICAL for backwards compatibility)
ALTER TABLE garage_sector
    ADD COLUMN control_type ENUM('PHYSICAL', 'LOGICAL') NOT NULL DEFAULT 'PHYSICAL';

-- Create parking_gate (cancela) table
CREATE TABLE IF NOT EXISTS parking_gate (
    id        VARCHAR(20)                   NOT NULL,
    sector    VARCHAR(10)                   NOT NULL,
    gate_type ENUM('ENTRY', 'EXIT', 'BOTH') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_gate_sector FOREIGN KEY (sector) REFERENCES garage_sector (sector)
);

-- Add gate_id to vehicle_session for traceability
ALTER TABLE vehicle_session
    ADD COLUMN gate_id VARCHAR(20),
    ADD CONSTRAINT fk_session_gate FOREIGN KEY (gate_id) REFERENCES parking_gate (id);
