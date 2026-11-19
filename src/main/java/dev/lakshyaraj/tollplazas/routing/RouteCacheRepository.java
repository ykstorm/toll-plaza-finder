package dev.lakshyaraj.tollplazas.routing;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.util.Optional;

// second cache level. free hosting sleeps when idle and loses memory, this survives that
public class RouteCacheRepository {

    private final JdbcClient jdbc;

    public RouteCacheRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Route> find(RouteKey key, Duration maxAge) {
        return jdbc.sql("""
                        SELECT distance_meters, polyline6
                        FROM route_cache
                        WHERE source_pincode = :source
                          AND destination_pincode = :destination
                          AND updated_at > now() - make_interval(secs => :maxAgeSeconds)
                        """)
                .param("source", key.source().value())
                .param("destination", key.destination().value())
                .param("maxAgeSeconds", maxAge.toSeconds())
                .query((rs, n) -> new Route(rs.getDouble("distance_meters"), rs.getString("polyline6")))
                .optional();
    }

    public void save(RouteKey key, Route route, String provider) {
        jdbc.sql("""
                        INSERT INTO route_cache (source_pincode, destination_pincode, distance_meters, polyline6, provider)
                        VALUES (:source, :destination, :distance, :polyline, :provider)
                        ON CONFLICT (source_pincode, destination_pincode) DO UPDATE
                           SET distance_meters = EXCLUDED.distance_meters,
                               polyline6       = EXCLUDED.polyline6,
                               provider        = EXCLUDED.provider,
                               updated_at      = now()
                        """)
                .param("source", key.source().value())
                .param("destination", key.destination().value())
                .param("distance", route.distanceMeters())
                .param("polyline", route.encodedPolyline6())
                .param("provider", provider)
                .update();
    }
}
