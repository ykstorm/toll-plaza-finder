package dev.lakshyaraj.tollplazas.geo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class BoundingBoxTest {

    @Test
    void expandsByRoughlyTheGivenDistance() {
        BoundingBox box = BoundingBox.around(List.of(GeoPoint.of(20, 75), GeoPoint.of(21, 76)));
        BoundingBox grown = box.expandedBy(1113.2); // ~0.01 degree of latitude

        assertThat(grown.minLatitude()).isCloseTo(19.99, within(1e-4));
        assertThat(grown.maxLatitude()).isCloseTo(21.01, within(1e-4));
        // longitude padding must be wider than latitude padding away from the equator
        assertThat(box.minLongitude() - grown.minLongitude()).isGreaterThan(0.01);
    }

    @Test
    void emptyInputIsAnError() {
        assertThatIllegalArgumentException().isThrownBy(() -> BoundingBox.around(List.of()));
    }
}
