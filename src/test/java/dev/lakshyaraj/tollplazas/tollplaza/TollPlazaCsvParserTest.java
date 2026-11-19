package dev.lakshyaraj.tollplazas.tollplaza;

import dev.lakshyaraj.tollplazas.geo.GeoPoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TollPlazaCsvParserTest {

    private final TollPlazaCsvParser parser = new TollPlazaCsvParser();

    private TollPlazaCsvParser.ParseResult parse(String csv) throws IOException {
        return parser.parse(new StringReader(csv));
    }

    @Test
    void readsLongitudeFirstColumnsCorrectly() throws IOException {
        var result = parse("""
                longitude,latitude,toll_name,geo_state
                87.9669882,24.8223539,18Th Mile Toll Plaza,West Bengal
                """);

        TollPlazaRow row = result.plazas().get(0);
        assertThat(row.name()).isEqualTo("18Th Mile Toll Plaza");
        assertThat(row.state()).isEqualTo("West Bengal");
        assertThat(row.location()).isEqualTo(GeoPoint.of(24.822354, 87.966988));
    }

    @Test
    void mapsColumnsByHeaderNameNotPosition() throws IOException {
        var result = parse("""
                toll_name,geo_state,latitude,longitude
                Aaini Toll Plaza,Uttar Pradesh,27.2970385,81.541637
                """);
        assertThat(result.plazas().get(0).location()).isEqualTo(GeoPoint.of(27.297039, 81.541637));
    }

    @Test
    void dropsExactDuplicatesAndCountsThem() throws IOException {
        var result = parse("""
                longitude,latitude,toll_name,geo_state
                81.541637,27.2970385,Aaini Toll Plaza,Uttar Pradesh
                81.541637,27.2970385,Aaini Toll Plaza,Uttar Pradesh
                78.792226,27.4866364,Aaspur Toll Plaza,Uttar Pradesh
                78.792226,27.4866364,aaspur toll plaza,Uttar Pradesh
                """);

        assertThat(result.dataRows()).isEqualTo(4);
        assertThat(result.plazas()).extracting(TollPlazaRow::name)
                .containsExactly("Aaini Toll Plaza", "Aaspur Toll Plaza");
        assertThat(result.duplicates()).isEqualTo(2);
    }

    @Test
    void keepsSameNameAtDifferentLocations() throws IOException {
        // two genuinely different plazas can share a generic name
        var result = parse("""
                longitude,latitude,toll_name,geo_state
                77.1,28.1,Bypass Toll Plaza,Haryana
                75.1,26.1,Bypass Toll Plaza,Rajasthan
                """);
        assertThat(result.plazas()).hasSize(2);
    }

    @Test
    void rejectsBadRowsWithAReasonInsteadOfGuessing() throws IOException {
        var result = parse("""
                longitude,latitude,toll_name,geo_state
                ,,No Coordinates Plaza,Goa
                10.0,10.0,Somewhere In Africa,Nowhere
                77.0,abc,Typo Plaza,Delhi
                77.2,28.6,,Delhi
                77.2,28.6,Good Plaza,Delhi
                """);

        assertThat(result.plazas()).extracting(TollPlazaRow::name).containsExactly("Good Plaza");
        assertThat(result.rejected()).hasSize(4);
        assertThat(result.rejected()).anySatisfy(r -> assertThat(r).contains("outside India"));
        assertThat(result.rejected()).anySatisfy(r -> assertThat(r).startsWith("line 4"));
    }

    @Test
    void handlesBomQuotesAndBlankLines() throws IOException {
        var result = parse("﻿longitude,latitude,toll_name,geo_state\n"
                + "\n"
                + "78.1,\"27.4\",\"Kherki Daula, NH48\",\"Haryana\"\n"
                + "   \n");

        assertThat(result.dataRows()).isEqualTo(1);
        assertThat(result.plazas().get(0).name()).isEqualTo("Kherki Daula, NH48");
    }

    @Test
    void stateColumnIsOptional() throws IOException {
        var result = parse("""
                longitude,latitude,toll_name
                77.2,28.6,Good Plaza
                """);
        assertThat(result.plazas().get(0).state()).isEmpty();
    }

    @Test
    void missingRequiredColumnFailsFast() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> parse("lng,lat,name\n77,28,X\n"))
                .withMessageContaining("longitude");
    }

    @Test
    void emptyFileIsJustEmpty() throws IOException {
        assertThat(parse("").plazas()).isEmpty();
    }

    @Test
    void splitsEscapedQuotes() {
        assertThat(TollPlazaCsvParser.splitCsvLine("a,\"say \"\"hi\"\"\",c"))
                .containsExactly("a", "say \"hi\"", "c");
    }
}
