package dev.lakshyaraj.tollplazas.route;

import dev.lakshyaraj.tollplazas.geo.BoundingBox;
import dev.lakshyaraj.tollplazas.geo.GeoPoint;
import dev.lakshyaraj.tollplazas.matching.TollPlazaMatcher;
import dev.lakshyaraj.tollplazas.pincode.InvalidPincodeException;
import dev.lakshyaraj.tollplazas.pincode.Pincode;
import dev.lakshyaraj.tollplazas.pincode.PincodeDirectory;
import dev.lakshyaraj.tollplazas.routing.NoRouteFoundException;
import dev.lakshyaraj.tollplazas.routing.Route;
import dev.lakshyaraj.tollplazas.routing.RouteCache;
import dev.lakshyaraj.tollplazas.routing.RouteKey;
import dev.lakshyaraj.tollplazas.routing.RouteSource;
import dev.lakshyaraj.tollplazas.tollplaza.TollPlaza;
import dev.lakshyaraj.tollplazas.tollplaza.TollPlazaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TollRouteServiceTest {

    private static final GeoPoint DELHI = GeoPoint.of(28.62, 77.21);
    private static final GeoPoint JAIPUR = GeoPoint.of(26.91, 75.79);
    private static final Route DELHI_JAIPUR = Route.of(270_000, List.of(DELHI, JAIPUR));

    @Mock
    private PincodeDirectory pincodes;
    @Mock
    private RouteCache routes;
    @Mock
    private TollPlazaRepository plazas;

    private TollRouteService service;

    @BeforeEach
    void setUp() {
        service = new TollRouteService(pincodes, routes, plazas, new TollPlazaMatcher(500, 2000, 1000), 500);
    }

    @Test
    void returnsMatchedPlazasInRouteOrder() {
        when(pincodes.locate(Pincode.of("110001"))).thenReturn(Optional.of(DELHI));
        when(pincodes.locate(Pincode.of("302001"))).thenReturn(Optional.of(JAIPUR));
        when(routes.get(any(), any())).thenReturn(new RouteCache.CachedRoute(DELHI_JAIPUR, RouteSource.PROVIDER));
        GeoPoint quarterWay = midpoint(DELHI, midpoint(DELHI, JAIPUR));
        GeoPoint threeQuarters = midpoint(JAIPUR, midpoint(DELHI, JAIPUR));
        GeoPoint offRoute = GeoPoint.of(27.5, 77.5);
        when(plazas.findWithin(any())).thenReturn(List.of(
                new TollPlaza(1, "Shahjahanpur Toll Plaza", "Rajasthan", threeQuarters),
                new TollPlaza(2, "Kherki Daula Toll Plaza", "Haryana", quarterWay),
                new TollPlaza(3, "Somewhere Else", "UP", offRoute)));

        TollRouteResult result = service.findTollPlazas("110001", "302001");

        assertThat(result.routeDistanceMeters()).isEqualTo(270_000);
        assertThat(result.routeSource()).isEqualTo(RouteSource.PROVIDER);
        assertThat(result.tollPlazas()).extracting(m -> m.plaza().name())
                .containsExactly("Kherki Daula Toll Plaza", "Shahjahanpur Toll Plaza");
    }

    @Test
    void asksTheRepositoryOnlyForPlazasAroundTheRoute() {
        when(pincodes.locate(any())).thenReturn(Optional.of(DELHI), Optional.of(JAIPUR));
        when(routes.get(any(), any())).thenReturn(new RouteCache.CachedRoute(DELHI_JAIPUR, RouteSource.MEMORY));
        when(plazas.findWithin(any())).thenReturn(List.of());

        service.findTollPlazas("110001", "302001");

        ArgumentCaptor<BoundingBox> box = ArgumentCaptor.forClass(BoundingBox.class);
        verify(plazas).findWithin(box.capture());
        assertThat(box.getValue().minLatitude()).isLessThan(JAIPUR.latitude()).isGreaterThan(26.8);
        assertThat(box.getValue().maxLatitude()).isGreaterThan(DELHI.latitude()).isLessThan(28.7);
    }

    @Test
    void routeWithNoTollsReturnsEmptyList() {
        when(pincodes.locate(any())).thenReturn(Optional.of(DELHI), Optional.of(JAIPUR));
        when(routes.get(any(), any())).thenReturn(new RouteCache.CachedRoute(DELHI_JAIPUR, RouteSource.DATABASE));
        when(plazas.findWithin(any())).thenReturn(List.of());

        assertThat(service.findTollPlazas("110001", "302001").tollPlazas()).isEmpty();
    }

    @Test
    void cacheKeyIsDirectional() {
        when(pincodes.locate(any())).thenReturn(Optional.of(DELHI), Optional.of(JAIPUR));
        when(routes.get(any(), any())).thenReturn(new RouteCache.CachedRoute(DELHI_JAIPUR, RouteSource.MEMORY));
        when(plazas.findWithin(any())).thenReturn(List.of());

        service.findTollPlazas("110001", "302001");

        verify(routes).get(new RouteKey(Pincode.of("110001"), Pincode.of("302001")),
                new RouteCache.RouteRequestPoints(DELHI, JAIPUR));
    }

    @Test
    void samePincodeIsRejectedBeforeAnyLookup() {
        assertThatThrownBy(() -> service.findTollPlazas("110001", "110001"))
                .isInstanceOf(SamePincodeException.class);
        verifyNoInteractions(pincodes, routes, plazas);
    }

    @Test
    void samePincodeWithWhitespaceIsStillTheSame() {
        assertThatThrownBy(() -> service.findTollPlazas("110001", " 110001 "))
                .isInstanceOf(SamePincodeException.class);
    }

    @Test
    void malformedPincodeIsRejected() {
        assertThatThrownBy(() -> service.findTollPlazas("11000", "302001"))
                .isInstanceOf(InvalidPincodeException.class);
        verifyNoInteractions(routes);
    }

    @Test
    void wellFormedButNonExistentPincodeIsRejected() {
        when(pincodes.locate(Pincode.of("110001"))).thenReturn(Optional.of(DELHI));
        when(pincodes.locate(Pincode.of("999999"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findTollPlazas("110001", "999999"))
                .isInstanceOf(InvalidPincodeException.class);
        verifyNoInteractions(routes);
    }

    @Test
    void noRouteIsPassedThrough() {
        when(pincodes.locate(any())).thenReturn(Optional.of(DELHI), Optional.of(GeoPoint.of(11.66, 92.74)));
        when(routes.get(any(), any())).thenThrow(new NoRouteFoundException("island"));

        assertThatThrownBy(() -> service.findTollPlazas("110001", "744101"))
                .isInstanceOf(NoRouteFoundException.class);
    }

    private static GeoPoint midpoint(GeoPoint a, GeoPoint b) {
        return GeoPoint.of((a.latitude() + b.latitude()) / 2, (a.longitude() + b.longitude()) / 2);
    }
}
