package dev.lakshyaraj.tollplazas.routing.osrm;

import dev.lakshyaraj.tollplazas.geo.GeoPoint;
import dev.lakshyaraj.tollplazas.geo.PolylineCodec;
import dev.lakshyaraj.tollplazas.routing.NoRouteFoundException;
import dev.lakshyaraj.tollplazas.routing.Route;
import dev.lakshyaraj.tollplazas.routing.RoutingUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OsrmRouteProviderTest {

    private static final GeoPoint BENGALURU = GeoPoint.of(13.107, 77.5688);
    private static final GeoPoint PUNE = GeoPoint.of(18.57, 73.7662);

    private MockRestServiceServer server;
    private OsrmRouteProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://osrm.test");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OsrmRouteProvider(builder.build(), 25_000);
    }

    @Test
    void sendsLonLatOrderAndAsksForFullPolyline6Geometry() {
        String geometry = PolylineCodec.encode(List.of(BENGALURU, GeoPoint.of(15.0, 75.0), PUNE), 6);
        server.expect(requestTo(allOf(
                        containsString("/route/v1/driving/77.568800,13.107000;73.766200,18.570000"),
                        containsString("overview=full"),
                        containsString("geometries=polyline6"))))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"code":"Ok","routes":[{"geometry":"%s","distance":842311.5,"duration":40000.1,"weight":1}],
                         "waypoints":[]}""".formatted(geometry.replace("\\", "\\\\")), MediaType.APPLICATION_JSON));

        Route route = provider.route(BENGALURU, PUNE);

        assertThat(route.distanceMeters()).isEqualTo(842311.5);
        assertThat(route.path().points()).hasSize(3);
        assertThat(route.path().points().get(0)).isEqualTo(BENGALURU);
        server.verify();
    }

    @Test
    void noRouteFromOsrmBecomesNoRouteFound() {
        server.expect(requestTo(containsString("/route/v1/driving/")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"NoRoute\",\"message\":\"Impossible route between points\"}"));

        assertThatThrownBy(() -> provider.route(BENGALURU, GeoPoint.of(11.66, 92.74)))
                .isInstanceOf(NoRouteFoundException.class);
    }

    @Test
    void unsnappablePointBecomesNoRouteFound() {
        server.expect(requestTo(containsString("/route/v1/driving/")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"NoSegment\",\"message\":\"Could not find a matching segment\"}"));

        assertThatThrownBy(() -> provider.route(BENGALURU, PUNE)).isInstanceOf(NoRouteFoundException.class);
    }

    @Test
    void serverErrorIsReportedAsUnavailable() {
        server.expect(requestTo(containsString("/route/v1/driving/")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> provider.route(BENGALURU, PUNE)).isInstanceOf(RoutingUnavailableException.class);
    }

    @Test
    void timeoutIsReportedAsUnavailable() {
        server.expect(requestTo(containsString("/route/v1/driving/")))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> provider.route(BENGALURU, PUNE)).isInstanceOf(RoutingUnavailableException.class);
    }

    @Test
    void okWithoutRoutesIsReportedAsUnavailable() {
        server.expect(requestTo(containsString("/route/v1/driving/")))
                .andRespond(withSuccess("{\"code\":\"Ok\",\"routes\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.route(BENGALURU, PUNE)).isInstanceOf(RoutingUnavailableException.class);
    }

    @Test
    void unknownErrorCodeIsReportedAsUnavailable() {
        server.expect(requestTo(containsString("/route/v1/driving/")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"TooBig\"}"));

        assertThatThrownBy(() -> provider.route(BENGALURU, PUNE)).isInstanceOf(RoutingUnavailableException.class);
    }

    @Test
    void pointThatHadToSnapTooFarIsUnreachable() {
        // real OSRM behaviour for Port Blair: snapped ~494 km away and still returned "Ok"
        String geometry = PolylineCodec.encode(List.of(BENGALURU, PUNE), 6).replace("\\", "\\\\");
        server.expect(requestTo(containsString("/route/v1/driving/")))
                .andRespond(withSuccess("""
                        {"code":"Ok","routes":[{"geometry":"%s","distance":3562200,"duration":1}],
                         "waypoints":[{"distance":16.2},{"distance":494584.1}]}""".formatted(geometry),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.route(BENGALURU, GeoPoint.of(11.6658, 92.7411)))
                .isInstanceOf(NoRouteFoundException.class)
                .hasMessageContaining("495 km");
    }

    @Test
    void hasAName() {
        assertThat(provider.name()).isEqualTo("osrm");
    }
}
