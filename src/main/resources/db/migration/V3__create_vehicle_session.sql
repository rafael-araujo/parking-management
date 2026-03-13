CREATE TABLE IF NOT EXISTS vehicle_session (
    id               BIGINT AUTO_INCREMENT NOT NULL,
    license_plate    VARCHAR(20)           NOT NULL,
    entry_time       DATETIME              NOT NULL,
    exit_time        DATETIME,
    spot_id          BIGINT,
    sector           VARCHAR(10)           NOT NULL,
    price_multiplier DECIMAL(4, 2)         NOT NULL,
    amount_charged   DECIMAL(10, 2),
    status           ENUM('ENTERING', 'PARKED', 'EXITED') NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_session_plate_entry (license_plate, entry_time),
    CONSTRAINT fk_session_spot   FOREIGN KEY (spot_id) REFERENCES parking_spot (id),
    CONSTRAINT fk_session_sector FOREIGN KEY (sector)  REFERENCES garage_sector (sector)
);
