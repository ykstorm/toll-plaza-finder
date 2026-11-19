package dev.lakshyaraj.tollplazas.pincode;

import dev.lakshyaraj.tollplazas.geo.GeoPoint;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CsvPincodeDirectoryTest {

    @Test
    void looksUpPincodesFromCsv() {
        String csv = """
                pincode,latitude,longitude,district,state,sample_size
                560064,13.107,77.5688,Bengaluru,Karnataka,4
                411045,18.57,73.7662,Pune,Maharashtra,2
                """;
        CsvPincodeDirectory directory = new CsvPincodeDirectory(new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(directory.size()).isEqualTo(2);
        assertThat(directory.locate(Pincode.of("560064"))).contains(GeoPoint.of(13.107, 77.5688));
        assertThat(directory.locate(Pincode.of("999999"))).isEmpty();
    }

    @Test
    void bundledDirectoryCoversTheSpecExamples() {
        CsvPincodeDirectory directory = new CsvPincodeDirectory(new ClassPathResource("data/pincode_centroids.csv"));

        assertThat(directory.size()).isGreaterThan(19_000);
        // Connaught Place, New Delhi
        GeoPoint delhi = directory.locate(Pincode.of("110001")).orElseThrow();
        assertThat(delhi.latitude()).isCloseTo(28.62, within(0.05));
        assertThat(delhi.longitude()).isCloseTo(77.21, within(0.05));
        // Bengaluru GPO
        assertThat(directory.locate(Pincode.of("560001"))).isPresent();
        // the Mappls comparison pair from the assignment
        assertThat(directory.locate(Pincode.of("560064"))).isPresent();
        assertThat(directory.locate(Pincode.of("411045"))).isPresent();
    }
}
