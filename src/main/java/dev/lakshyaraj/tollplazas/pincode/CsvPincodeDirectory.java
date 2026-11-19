package dev.lakshyaraj.tollplazas.pincode;

import dev.lakshyaraj.tollplazas.geo.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// pincode -> lat/lng from India Post's pincode directory (data.gov.in). cleaned by
// scripts/build_pincode_centroids.py, which is where all the junk handling lives.
// i went offline instead of a geocoding API because Nominatim allows 1 req/sec,
// and this is a hashmap lookup with no key and no rate limit.
public class CsvPincodeDirectory implements PincodeDirectory {

    private static final Logger log = LoggerFactory.getLogger(CsvPincodeDirectory.class);

    private final Map<String, GeoPoint> byPincode;

    public CsvPincodeDirectory(Resource csv) {
        this.byPincode = load(csv);
        log.info("Loaded {} pincodes from {}", byPincode.size(), csv.getDescription());
    }

    @Override
    public Optional<GeoPoint> locate(Pincode pincode) {
        return Optional.ofNullable(byPincode.get(pincode.value()));
    }

    public int size() {
        return byPincode.size();
    }

    private static Map<String, GeoPoint> load(Resource csv) {
        Map<String, GeoPoint> map = new HashMap<>(32_000);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(",", 4);
                if (cols.length < 3) {
                    continue;
                }
                map.put(cols[0].trim(), new GeoPoint(Double.parseDouble(cols[1]), Double.parseDouble(cols[2])));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not read pincode directory " + csv.getDescription(), e);
        }
        return Map.copyOf(map);
    }
}
