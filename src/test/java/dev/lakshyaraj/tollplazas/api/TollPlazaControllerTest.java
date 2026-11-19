package dev.lakshyaraj.tollplazas.api;

import dev.lakshyaraj.tollplazas.geo.GeoPoint;
import dev.lakshyaraj.tollplazas.matching.MatchedTollPlaza;
import dev.lakshyaraj.tollplazas.pincode.InvalidPincodeException;
import dev.lakshyaraj.tollplazas.pincode.Pincode;
import dev.lakshyaraj.tollplazas.route.SamePincodeException;
import dev.lakshyaraj.tollplazas.route.TollRouteResult;
import dev.lakshyaraj.tollplazas.route.TollRouteService;
import dev.lakshyaraj.tollplazas.routing.NoRouteFoundException;
import dev.lakshyaraj.tollplazas.routing.RouteSource;
import dev.lakshyaraj.tollplazas.routing.RoutingUnavailableException;
import dev.lakshyaraj.tollplazas.tollplaza.TollPlaza;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TollPlazaController.class)
class TollPlazaControllerTest {

    private static final String URL = "/api/v1/toll-plazas";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TollRouteService service;

    private ResultActions postJson(String body) throws Exception {
        return mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(String source, String destination) {
        return "{\"sourcePincode\":\"" + source + "\",\"destinationPincode\":\"" + destination + "\"}";
    }

    @Test
    void returnsRouteAndPlazasInSpecShape() throws Exception {
        TollPlaza hoskote = new TollPlaza(7, "Hoskote Toll Plaza", "Karnataka", GeoPoint.of(13.0789, 77.7921));
        when(service.findTollPlazas("560064", "411045")).thenReturn(new TollRouteResult(
                Pincode.of("560064"), Pincode.of("411045"), 842_349.0,
                List.of(new MatchedTollPlaza(hoskote, 38_449.0, 42.0)), RouteSource.PROVIDER));

        postJson(body("560064", "411045"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Route-Source", "PROVIDER"))
                .andExpect(jsonPath("$.route.sourcePincode").value("560064"))
                .andExpect(jsonPath("$.route.destinationPincode").value("411045"))
                .andExpect(jsonPath("$.route.distanceInKm").value(842.3))
                .andExpect(jsonPath("$.tollPlazas[0].name").value("Hoskote Toll Plaza"))
                .andExpect(jsonPath("$.tollPlazas[0].latitude").value(13.0789))
                .andExpect(jsonPath("$.tollPlazas[0].longitude").value(77.7921))
                .andExpect(jsonPath("$.tollPlazas[0].distanceFromSource").value(38.4))
                // internal details stay internal
                .andExpect(jsonPath("$.tollPlazas[0].offsetMeters").doesNotExist())
                .andExpect(jsonPath("$.tollPlazas[0].id").doesNotExist());
    }

    @Test
    void noTollsIsStillA200WithEmptyList() throws Exception {
        when(service.findTollPlazas("110001", "560001")).thenReturn(new TollRouteResult(
                Pincode.of("110001"), Pincode.of("560001"), 2_100_000.0, List.of(), RouteSource.MEMORY));

        postJson(body("110001", "560001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.distanceInKm").value(2100.0))
                .andExpect(jsonPath("$.tollPlazas").isArray())
                .andExpect(jsonPath("$.tollPlazas").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"11000", "abcdef", "0110001", "", "012345"})
    void malformedPincodeGivesSpecErrorWithoutCallingTheService(String bad) throws Exception {
        postJson(body(bad, "560001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid source or destination pincode"));
        verifyNoInteractions(service);
    }

    @Test
    void pincodesSentAsJsonNumbersStillWork() throws Exception {
        when(service.findTollPlazas("110001", "560001")).thenReturn(new TollRouteResult(
                Pincode.of("110001"), Pincode.of("560001"), 2_100_000.0, List.of(), RouteSource.MEMORY));

        postJson("{\"sourcePincode\":110001,\"destinationPincode\":560001}").andExpect(status().isOk());
    }

    @Test
    void missingFieldIsInvalidPincode() throws Exception {
        postJson("{\"sourcePincode\":\"110001\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid source or destination pincode"));
    }

    @Test
    void unknownPincodeGivesSpecError() throws Exception {
        when(service.findTollPlazas(anyString(), anyString())).thenThrow(new InvalidPincodeException("999999"));

        postJson(body("110001", "999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid source or destination pincode"));
    }

    @Test
    void samePincodeGivesSpecError() throws Exception {
        when(service.findTollPlazas("110001", "110001")).thenThrow(new SamePincodeException("110001"));

        postJson(body("110001", "110001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Source and destination pincodes cannot be the same"));
    }

    @Test
    void islandWithoutRoadGives422() throws Exception {
        when(service.findTollPlazas(anyString(), anyString())).thenThrow(new NoRouteFoundException("NoRoute"));

        postJson(body("110001", "744101"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("No drivable route found between the given pincodes"));
    }

    @Test
    void routingOutageGives503() throws Exception {
        when(service.findTollPlazas(anyString(), anyString())).thenThrow(new RoutingUnavailableException("timeout"));

        postJson(body("110001", "560001"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void unexpectedFailureGives500WithoutLeakingDetails() throws Exception {
        when(service.findTollPlazas(anyString(), anyString())).thenThrow(new IllegalStateException("db password is hunter2"));

        postJson(body("110001", "560001"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Something went wrong on our side"));
    }

    @Test
    void malformedJsonGives400() throws Exception {
        postJson("{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void wrongContentTypeGives415() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content("110001,560001"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void getIsNotAllowed() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isMethodNotAllowed());
    }
}
