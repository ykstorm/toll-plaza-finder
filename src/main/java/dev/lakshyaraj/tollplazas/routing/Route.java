package dev.lakshyaraj.tollplazas.routing;

import dev.lakshyaraj.tollplazas.geo.GeoPoint;
import dev.lakshyaraj.tollplazas.geo.PolylineCodec;
import dev.lakshyaraj.tollplazas.geo.RoutePath;

import java.util.List;

// geometry stays encoded (polyline6). it's what OSRM sends and what we store in the cache table
public record Route(double distanceMeters, String encodedPolyline6) {

    public static final int PRECISION = 6;

    public Route {
        if (distanceMeters < 0) {
            throw new IllegalArgumentException("negative distance");
        }
        if (encodedPolyline6 == null || encodedPolyline6.isEmpty()) {
            throw new IllegalArgumentException("route has no geometry");
        }
    }

    public static Route of(double distanceMeters, List<GeoPoint> points) {
        return new Route(distanceMeters, PolylineCodec.encode(points, PRECISION));
    }

    public RoutePath path() {
        return new RoutePath(PolylineCodec.decode(encodedPolyline6, PRECISION));
    }
}
