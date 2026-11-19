package dev.lakshyaraj.tollplazas.geo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// the route's geometry plus two precomputed things:
// cumulative distance at each point, so distance from source is along the road,
// and a ~2 km grid of segments, so snapping a plaza checks a few segments instead of all 9000+
public final class RoutePath {

    private static final double CELL_DEGREES = 0.02; // ~2.2 km at the equator
    private static final double METERS_PER_DEGREE = 111_320.0;

    private final List<GeoPoint> points;
    private final double[] cumulativeMeters;
    private final Map<Long, List<Integer>> segmentGrid = new HashMap<>();

    public RoutePath(List<GeoPoint> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("a route path needs at least two points");
        }
        this.points = List.copyOf(points);
        this.cumulativeMeters = new double[this.points.size()];
        for (int i = 1; i < this.points.size(); i++) {
            cumulativeMeters[i] = cumulativeMeters[i - 1]
                    + Haversine.distanceMeters(this.points.get(i - 1), this.points.get(i));
        }
        indexSegments();
    }

    public List<GeoPoint> points() {
        return points;
    }

    public double lengthMeters() {
        return cumulativeMeters[cumulativeMeters.length - 1];
    }

    public BoundingBox boundingBox() {
        return BoundingBox.around(points);
    }

    // if the road passes the point twice (u-turn, loop) the closer pass wins
    public Optional<Projection> project(GeoPoint point, double maxOffsetMeters) {
        Projection best = null;
        for (int segment : candidateSegments(point, maxOffsetMeters)) {
            Projection p = projectOntoSegment(point, segment);
            if (p.offsetMeters() <= maxOffsetMeters && (best == null || p.offsetMeters() < best.offsetMeters())) {
                best = p;
            }
        }
        return Optional.ofNullable(best);
    }

    private Projection projectOntoSegment(GeoPoint p, int segment) {
        GeoPoint a = points.get(segment);
        GeoPoint b = points.get(segment + 1);

        // treat the segment as flat. they're short so the error is tiny
        double cosLat = Math.cos(Math.toRadians((a.latitude() + b.latitude()) / 2));
        double bx = (b.longitude() - a.longitude()) * cosLat;
        double by = b.latitude() - a.latitude();
        double px = (p.longitude() - a.longitude()) * cosLat;
        double py = p.latitude() - a.latitude();

        double lengthSq = bx * bx + by * by;
        double t = lengthSq == 0 ? 0 : clamp((px * bx + py * by) / lengthSq);

        double dx = px - t * bx;
        double dy = py - t * by;
        double offsetMeters = Math.sqrt(dx * dx + dy * dy) * METERS_PER_DEGREE;

        double segmentLength = cumulativeMeters[segment + 1] - cumulativeMeters[segment];
        double along = cumulativeMeters[segment] + t * segmentLength;
        return new Projection(offsetMeters, along);
    }

    private List<Integer> candidateSegments(GeoPoint p, double radiusMeters) {
        double latPad = radiusMeters / METERS_PER_DEGREE;
        double lonPad = radiusMeters / (METERS_PER_DEGREE * Math.max(0.01, Math.cos(Math.toRadians(p.latitude()))));
        int minLat = cell(p.latitude() - latPad);
        int maxLat = cell(p.latitude() + latPad);
        int minLon = cell(p.longitude() - lonPad);
        int maxLon = cell(p.longitude() + lonPad);

        List<Integer> result = new ArrayList<>();
        for (int la = minLat; la <= maxLat; la++) {
            for (int lo = minLon; lo <= maxLon; lo++) {
                List<Integer> bucket = segmentGrid.get(key(la, lo));
                if (bucket != null) {
                    result.addAll(bucket);
                }
            }
        }
        return result;
    }

    private void indexSegments() {
        for (int i = 0; i < points.size() - 1; i++) {
            GeoPoint a = points.get(i);
            GeoPoint b = points.get(i + 1);
            int minLat = cell(Math.min(a.latitude(), b.latitude()));
            int maxLat = cell(Math.max(a.latitude(), b.latitude()));
            int minLon = cell(Math.min(a.longitude(), b.longitude()));
            int maxLon = cell(Math.max(a.longitude(), b.longitude()));
            for (int la = minLat; la <= maxLat; la++) {
                for (int lo = minLon; lo <= maxLon; lo++) {
                    segmentGrid.computeIfAbsent(key(la, lo), k -> new ArrayList<>()).add(i);
                }
            }
        }
    }

    private static int cell(double degrees) {
        return (int) Math.floor(degrees / CELL_DEGREES);
    }

    private static long key(int latCell, int lonCell) {
        return ((long) latCell << 32) | (lonCell & 0xffffffffL);
    }

    private static double clamp(double t) {
        return Math.max(0, Math.min(1, t));
    }

        public record Projection(double offsetMeters, double distanceAlongMeters) {
    }
}
