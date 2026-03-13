CREATE TABLE IF NOT EXISTS garage_sector (
    sector       VARCHAR(10)    NOT NULL,
    base_price   DECIMAL(10, 2) NOT NULL,
    max_capacity INT            NOT NULL,
    PRIMARY KEY (sector)
);
