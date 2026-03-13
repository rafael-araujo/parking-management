CREATE TABLE IF NOT EXISTS parking_spot (
    id       BIGINT          NOT NULL,
    sector   VARCHAR(10)     NOT NULL,
    lat      DECIMAL(10, 8)  NOT NULL,
    lng      DECIMAL(11, 8)  NOT NULL,
    occupied BOOLEAN         NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_spot_sector FOREIGN KEY (sector) REFERENCES garage_sector (sector)
);
