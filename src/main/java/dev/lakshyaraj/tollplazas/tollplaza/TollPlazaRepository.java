package dev.lakshyaraj.tollplazas.tollplaza;

import dev.lakshyaraj.tollplazas.geo.BoundingBox;
import dev.lakshyaraj.tollplazas.geo.GeoPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

// plain JDBC. the upsert and the bbox query read better as SQL, and batch upserts through hibernate are a pain
public class TollPlazaRepository {

    private static final int BATCH_SIZE = 500;

    private static final RowMapper<TollPlaza> MAPPER = (rs, n) -> new TollPlaza(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("state"),
            new GeoPoint(rs.getDouble("latitude"), rs.getDouble("longitude")));

    private final JdbcTemplate jdbc;

    public TollPlazaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // returns rows actually inserted or changed. re-importing the same file returns 0
    public int upsertAll(List<TollPlazaRow> rows) {
        int[][] results = jdbc.batchUpdate("""
                        INSERT INTO toll_plaza (name, state, latitude, longitude)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (name, latitude, longitude) DO UPDATE
                           SET state = EXCLUDED.state,
                               updated_at = now()
                         WHERE toll_plaza.state IS DISTINCT FROM EXCLUDED.state
                        """,
                rows, BATCH_SIZE, (ps, row) -> {
                    ps.setString(1, row.name());
                    ps.setString(2, row.state());
                    ps.setDouble(3, row.location().latitude());
                    ps.setDouble(4, row.location().longitude());
                });
        int changed = 0;
        for (int[] batch : results) {
            for (int count : batch) {
                // some drivers report SUCCESS_NO_INFO (-2) for batched statements
                changed += Math.max(count, 0);
            }
        }
        return changed;
    }

    public List<TollPlaza> findWithin(BoundingBox box) {
        return jdbc.query("""
                        SELECT id, name, state, latitude, longitude
                        FROM toll_plaza
                        WHERE latitude BETWEEN ? AND ?
                          AND longitude BETWEEN ? AND ?
                        """,
                MAPPER,
                box.minLatitude(), box.maxLatitude(), box.minLongitude(), box.maxLongitude());
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM toll_plaza", Long.class);
        return count == null ? 0 : count;
    }
}
