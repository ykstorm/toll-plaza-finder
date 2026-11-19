CREATE TABLE toll_plaza (
    id          BIGSERIAL        PRIMARY KEY,
    name        TEXT             NOT NULL,
    state       TEXT             NOT NULL DEFAULT '',
    latitude    DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude   DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    -- natural key for the upsert. Same name at the same spot is the same plaza.
    CONSTRAINT uq_toll_plaza_name_location UNIQUE (name, latitude, longitude)
);

-- route lookups filter by a lat/lon bounding box
CREATE INDEX idx_toll_plaza_lat_lon ON toll_plaza (latitude, longitude);
