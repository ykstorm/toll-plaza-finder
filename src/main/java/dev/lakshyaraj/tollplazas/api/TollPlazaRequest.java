package dev.lakshyaraj.tollplazas.api;

import dev.lakshyaraj.tollplazas.pincode.Pincode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TollPlazaRequest(
        @Schema(description = "6 digit Indian pincode of the starting point", example = "560064")
        @NotBlank
        @Pattern(regexp = Pincode.REGEX)
        String sourcePincode,

        @Schema(description = "6 digit Indian pincode of the destination", example = "411045")
        @NotBlank
        @Pattern(regexp = Pincode.REGEX)
        String destinationPincode) {
}
