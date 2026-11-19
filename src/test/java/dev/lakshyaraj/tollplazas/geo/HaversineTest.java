package dev.lakshyaraj.tollplazas.geo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HaversineTest {

    @Test
    void zeroForSamePoint() {
        GeoPoint p = GeoPoint.of(12.97, 77.59);
        assertThat(Haversine.distanceMeters(p, p)).isZero();
    }

    @Test
    void delhiToMumbaiIsAbout1148Km() {
        double meters = Haversine.distanceMeters(GeoPoint.of(28.6139, 77.2090), GeoPoint.of(19.0760, 72.8777));
        assertThat(meters / 1000).isCloseTo(1148, within(5.0));
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        double meters = Haversine.distanceMeters(GeoPoint.of(20, 77), GeoPoint.of(21, 77));
        assertThat(meters).isCloseTo(111_195, within(100.0));
    }

    @Test
    void symmetric() {
        GeoPoint a = GeoPoint.of(13.1, 77.5);
        GeoPoint b = GeoPoint.of(18.5, 73.8);
        assertThat(Haversine.distanceMeters(a, b)).isEqualTo(Haversine.distanceMeters(b, a));
    }
}
