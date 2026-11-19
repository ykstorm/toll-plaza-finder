package dev.lakshyaraj.tollplazas.tollplaza;

import dev.lakshyaraj.tollplazas.geo.GeoPoint;

import java.util.Locale;

public record TollPlazaRow(String name, String state, GeoPoint location) {

    // rounded to 6 decimals (~10 cm) so float noise can't make a "new" plaza
    public String naturalKey() {
        return name.toLowerCase(Locale.ROOT) + "|" + round6(location.latitude()) + "|" + round6(location.longitude());
    }

    public static double round6(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }
}
