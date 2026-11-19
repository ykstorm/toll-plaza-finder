package dev.lakshyaraj.tollplazas.pincode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PincodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"110001", "560064", "411045", "999999"})
    void acceptsSixDigitCodes(String raw) {
        assertThat(Pincode.of(raw).value()).isEqualTo(raw);
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(Pincode.of("  560064 ").value()).isEqualTo("560064");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"012345", "11000", "1100011", "11000a", "110 001", "-11000", "११०००१"})
    void rejectsMalformedCodes(String raw) {
        assertThatThrownBy(() -> Pincode.of(raw)).isInstanceOf(InvalidPincodeException.class);
    }
}
