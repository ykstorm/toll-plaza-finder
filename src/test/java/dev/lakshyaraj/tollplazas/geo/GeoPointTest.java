package dev.lakshyaraj.tollplazas.geo;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GeoPointTest {

    @ParameterizedTest
    @CsvSource({"91, 0", "-91, 0", "0, 181", "0, -181", "NaN, 0", "0, Infinity"})
    void rejectsImpossibleCoordinates(double lat, double lon) {
        assertThatIllegalArgumentException().isThrownBy(() -> GeoPoint.of(lat, lon));
    }
}
