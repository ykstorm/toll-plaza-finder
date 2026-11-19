CREATE TABLE route_cache (
    source_pincode      CHAR(6)          NOT NULL,
    destination_pincode CHAR(6)          NOT NULL,
    distance_meters     DOUBLE PRECISION NOT NULL CHECK (distance_meters >= 0),
    polyline6           TEXT             NOT NULL,
    provider            TEXT             NOT NULL,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ      NOT NULL DEFAULT now(),
    PRIMARY KEY (source_pincode, destination_pincode)
);
