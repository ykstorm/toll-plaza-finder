package dev.lakshyaraj.tollplazas.geo;

import java.util.Collection;

public record BoundingBox(double minLatitude, double minLongitude, double maxLatitude, double maxLongitude) {

    private static final double METERS_PER_DEGREE = 111_320.0;

    public static BoundingBox around(Collection<GeoPoint> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("no points");
        }
        double minLat = Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;
        for (GeoPoint p : points) {
            minLat = Math.min(minLat, p.latitude());
            minLon = Math.min(minLon, p.longitude());
            maxLat = Math.max(maxLat, p.latitude());
            maxLon = Math.max(maxLon, p.longitude());
        }
        return new BoundingBox(minLat, minLon, maxLat, maxLon);
    }

    // pads every side by roughly this many meters
    public BoundingBox expandedBy(double meters) {
        double latPad = meters / METERS_PER_DEGREE;
        double worstLat = Math.max(Math.abs(minLatitude), Math.abs(maxLatitude));
        double lonPad = meters / (METERS_PER_DEGREE * Math.max(0.01, Math.cos(Math.toRadians(worstLat))));
        return new BoundingBox(minLatitude - latPad, minLongitude - lonPad, maxLatitude + latPad, maxLongitude + lonPad);
    }
}
