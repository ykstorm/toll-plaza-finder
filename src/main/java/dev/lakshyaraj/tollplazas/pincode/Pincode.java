package dev.lakshyaraj.tollplazas.pincode;

import java.util.regex.Pattern;

// 6 digits, first one 1-9 (it's the postal zone, there's no zone 0).
// only checks the format. whether it exists is the directory's problem.
public record Pincode(String value) {

    public static final String REGEX = "^[1-9][0-9]{5}$";
    private static final Pattern PATTERN = Pattern.compile(REGEX);

    public Pincode {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new InvalidPincodeException(value);
        }
    }

    public static Pincode of(String raw) {
        return new Pincode(raw == null ? null : raw.trim());
    }

    @Override
    public String toString() {
        return value;
    }
}
