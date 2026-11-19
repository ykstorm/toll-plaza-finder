package dev.lakshyaraj.tollplazas.geo;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class RoutePathTest {

    private static final double METERS_PER_DEG_LAT = 111_320.0;

    // straight road heading east along latitude 20, 1 degree long, 101 vertices.
    private static RoutePath eastboundRoad() {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            points.add(GeoPoint.of(20, 75 + i * 0.01));
        }
        return new RoutePath(points);
    }

    @Test
    void lengthIsSumOfSegments() {
        double expected = Haversine.distanceMeters(GeoPoint.of(20, 75), GeoPoint.of(20, 76));
        assertThat(eastboundRoad().lengthMeters()).isCloseTo(expected, within(1.0));
    }

    @Test
    void projectsPointBesideTheRoadToTheRightDistanceAlong() {
        RoutePath road = eastboundRoad();
        GeoPoint besideMidpoint = GeoPoint.of(20 + 200 / METERS_PER_DEG_LAT, 75.5);

        RoutePath.Projection p = road.project(besideMidpoint, 500).orElseThrow();

        assertThat(p.offsetMeters()).isCloseTo(200, within(2.0));
        assertThat(p.distanceAlongMeters()).isCloseTo(road.lengthMeters() / 2, within(50.0));
    }

    @Test
    void ignoresPointsOutsideTheCorridor() {
        GeoPoint twoKmAway = GeoPoint.of(20 + 2000 / METERS_PER_DEG_LAT, 75.5);
        assertThat(eastboundRoad().project(twoKmAway, 500)).isEmpty();
    }

    @Test
    void pointBeforeTheStartSnapsToTheStart() {
        GeoPoint justBehindStart = GeoPoint.of(20, 74.999);
        RoutePath.Projection p = eastboundRoad().project(justBehindStart, 500).orElseThrow();
        assertThat(p.distanceAlongMeters()).isZero();
    }

    @Test
    void whenTheRoadPassesTwiceTheCloserPassWins() {
        // out east along lat 20, then back west along lat 20.003 (~334 m north)
        List<GeoPoint> uTurn = List.of(
                GeoPoint.of(20, 75.0), GeoPoint.of(20, 75.1), GeoPoint.of(20.003, 75.1), GeoPoint.of(20.003, 75.0));
        RoutePath road = new RoutePath(uTurn);
        GeoPoint nearReturnLeg = GeoPoint.of(20.0029, 75.05);

        RoutePath.Projection p = road.project(nearReturnLeg, 500).orElseThrow();

        assertThat(p.offsetMeters()).isLessThan(20);
        // must be placed on the way back, i.e. after the first ~10.5 km leg
        assertThat(p.distanceAlongMeters()).isGreaterThan(10_000);
    }

    @Test
    void handlesSegmentsLongerThanAGridCell() {
        // one 50 km straight segment, crosses many 0.02 degree cells
        RoutePath road = new RoutePath(List.of(GeoPoint.of(21, 76), GeoPoint.of(21.45, 76)));
        assertThat(road.project(GeoPoint.of(21.3, 76.001), 300)).isPresent();
    }

    @Test
    void needsAtLeastTwoPoints() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RoutePath(List.of(GeoPoint.of(20, 75))));
    }

    @Test
    void boundingBoxCoversAllPoints() {
        BoundingBox box = eastboundRoad().boundingBox();
        assertThat(box.minLongitude()).isEqualTo(75);
        assertThat(box.maxLongitude()).isCloseTo(76, within(1e-9));
        assertThat(box.minLatitude()).isEqualTo(20);
    }
}
