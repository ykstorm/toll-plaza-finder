package dev.lakshyaraj.tollplazas.matching;

import dev.lakshyaraj.tollplazas.geo.GeoPoint;
import dev.lakshyaraj.tollplazas.geo.RoutePath;
import dev.lakshyaraj.tollplazas.tollplaza.TollPlaza;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class TollPlazaMatcherTest {

    private static final double METERS_PER_DEG_LAT = 111_320.0;

    private final TollPlazaMatcher matcher = new TollPlazaMatcher(500, 2000, 1000);

    // ~104 km straight road heading east along latitude 20.
    private static RoutePath road() {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            points.add(GeoPoint.of(20, 75 + i * 0.01));
        }
        return new RoutePath(points);
    }

    private static TollPlaza plaza(long id, String name, double metersNorthOfRoad, double longitude) {
        return new TollPlaza(id, name, "Maharashtra",
                GeoPoint.of(20 + metersNorthOfRoad / METERS_PER_DEG_LAT, longitude));
    }

    @Test
    void returnsPlazasOnTheRouteInDrivingOrder() {
        List<TollPlaza> candidates = List.of(
                plaza(1, "Far End Plaza", 100, 75.9),
                plaza(2, "Near Start Plaza", -150, 75.1),
                plaza(3, "Middle Plaza", 50, 75.5));

        List<MatchedTollPlaza> result = matcher.match(road(), candidates);

        assertThat(result).extracting(m -> m.plaza().name())
                .containsExactly("Near Start Plaza", "Middle Plaza", "Far End Plaza");
    }

    @Test
    void distanceFromSourceIsAlongTheRoad() {
        MatchedTollPlaza middle = matcher.match(road(), List.of(plaza(1, "Middle", 0, 75.5))).get(0);
        assertThat(middle.distanceFromSourceMeters()).isCloseTo(road().lengthMeters() / 2, within(50.0));
    }

    @Test
    void ignoresPlazasOutsideTheCorridor() {
        List<TollPlaza> candidates = List.of(
                plaza(1, "Parallel Road Plaza", 1_500, 75.5),
                plaza(2, "Just Inside", 490, 75.6));

        assertThat(matcher.match(road(), candidates)).extracting(m -> m.plaza().name())
                .containsExactly("Just Inside");
    }

    @Test
    void noPlazasNearTheRouteMeansEmptyList() {
        assertThat(matcher.match(road(), List.of(plaza(1, "Elsewhere", 50_000, 75.5)))).isEmpty();
        assertThat(matcher.match(road(), List.of())).isEmpty();
    }

    @Test
    void collapsesSameNamePlazaListedOncePerCarriageway() {
        List<TollPlaza> candidates = List.of(
                plaza(1, "Khed Shivapur Toll Plaza", 120, 75.40),
                plaza(2, "Khed Shivapur Toll Plaza", -30, 75.401), // other side, closer to the line
                plaza(3, "KHED-SHIVAPUR toll plaza", 200, 75.402));

        List<MatchedTollPlaza> result = matcher.match(road(), candidates);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).plaza().id()).isEqualTo(2L);
    }

    @Test
    void keepsSameNamePlazasThatAreFarApartAlongTheRoute() {
        List<TollPlaza> candidates = List.of(
                plaza(1, "City Toll Plaza", 0, 75.1),
                plaza(2, "City Toll Plaza", 0, 75.9));

        assertThat(matcher.match(road(), candidates)).hasSize(2);
    }

    @Test
    void keepsOnlyTheNearestOfTwoPlazasAtTheSameSpot() {
        // like the two Nelamangala plazas, 640 m apart where NH48 and NH75 split. you drive through one
        List<TollPlaza> candidates = List.of(
                plaza(1, "Banglaore-Nelamangala Toll Plaza", 150, 75.300),
                plaza(2, "Nelamangala Toll Plaza", 20, 75.3055));

        List<MatchedTollPlaza> result = matcher.match(road(), candidates);

        assertThat(result).extracting(m -> m.plaza().id()).containsExactly(2L);
    }

    @Test
    void differentlyNamedPlazasAFewKmApartAreBothKept() {
        // like Alep Ramp 284 and Alep Main 290 on an expressway, ~4 km apart
        List<TollPlaza> candidates = List.of(
                plaza(1, "Ramp Toll Plaza", 0, 75.30),
                plaza(2, "Main Toll Plaza", 0, 75.34));

        assertThat(matcher.match(road(), candidates)).hasSize(2);
    }

    @Test
    void corridorMustBePositive() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TollPlazaMatcher(0, 2000, 1000));
    }
}
