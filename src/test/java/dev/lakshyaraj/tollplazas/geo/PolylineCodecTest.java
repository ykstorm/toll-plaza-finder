package dev.lakshyaraj.tollplazas.geo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PolylineCodecTest {

    // the worked example from Google's polyline algorithm docs
    private static final String GOOGLE_EXAMPLE = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";
    private static final List<GeoPoint> GOOGLE_POINTS = List.of(
            GeoPoint.of(38.5, -120.2), GeoPoint.of(40.7, -120.95), GeoPoint.of(43.252, -126.453));

    @Test
    void decodesGoogleReferenceExample() {
        assertThat(PolylineCodec.decode(GOOGLE_EXAMPLE, 5)).containsExactlyElementsOf(GOOGLE_POINTS);
    }

    @Test
    void encodesGoogleReferenceExample() {
        assertThat(PolylineCodec.encode(GOOGLE_POINTS, 5)).isEqualTo(GOOGLE_EXAMPLE);
    }

    @Test
    void roundTripsAtPrecisionSix() {
        List<GeoPoint> indianRoute = List.of(
                GeoPoint.of(13.107012, 77.568811), GeoPoint.of(15.364708, 75.123955), GeoPoint.of(18.570001, 73.766201));
        String encoded = PolylineCodec.encode(indianRoute, 6);
        assertThat(PolylineCodec.decode(encoded, 6)).containsExactlyElementsOf(indianRoute);
    }

    @Test
    void emptyInputDecodesToEmptyList() {
        assertThat(PolylineCodec.decode("", 6)).isEmpty();
        assertThat(PolylineCodec.decode(null, 6)).isEmpty();
    }

    @Test
    void truncatedInputFailsLoudly() {
        assertThatIllegalArgumentException().isThrownBy(() -> PolylineCodec.decode("_p~iF~ps|U_", 5));
    }
}
