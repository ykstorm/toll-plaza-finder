package dev.lakshyaraj.tollplazas.geo;

import java.util.ArrayList;
import java.util.List;

// google's encoded polyline format. OSRM returns it (precision 6) and it's ~10x smaller than json coords
public final class PolylineCodec {

    private PolylineCodec() {
    }

    public static List<GeoPoint> decode(String encoded, int precision) {
        if (encoded == null || encoded.isEmpty()) {
            return List.of();
        }
        double factor = Math.pow(10, precision);
        List<GeoPoint> points = new ArrayList<>();
        int index = 0;
        long lat = 0;
        long lng = 0;
        while (index < encoded.length()) {
            long[] latResult = nextValue(encoded, index);
            lat += latResult[0];
            index = (int) latResult[1];
            long[] lngResult = nextValue(encoded, index);
            lng += lngResult[0];
            index = (int) lngResult[1];
            points.add(new GeoPoint(lat / factor, lng / factor));
        }
        return points;
    }

    public static String encode(List<GeoPoint> points, int precision) {
        double factor = Math.pow(10, precision);
        StringBuilder out = new StringBuilder();
        long prevLat = 0;
        long prevLng = 0;
        for (GeoPoint p : points) {
            long lat = Math.round(p.latitude() * factor);
            long lng = Math.round(p.longitude() * factor);
            writeValue(out, lat - prevLat);
            writeValue(out, lng - prevLng);
            prevLat = lat;
            prevLng = lng;
        }
        return out.toString();
    }

    // returns {decodedValue, nextIndex}
    private static long[] nextValue(String encoded, int index) {
        long result = 0;
        int shift = 0;
        int b;
        do {
            if (index >= encoded.length()) {
                throw new IllegalArgumentException("truncated polyline");
            }
            b = encoded.charAt(index++) - 63;
            result |= (long) (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        long value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new long[] {value, index};
    }

    private static void writeValue(StringBuilder out, long value) {
        long v = value < 0 ? ~(value << 1) : (value << 1);
        while (v >= 0x20) {
            out.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        out.append((char) (v + 63));
    }
}
