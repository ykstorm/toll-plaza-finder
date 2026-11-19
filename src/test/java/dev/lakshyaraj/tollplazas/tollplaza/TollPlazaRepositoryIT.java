package dev.lakshyaraj.tollplazas.tollplaza;

import dev.lakshyaraj.tollplazas.PostgresIntegrationTest;
import dev.lakshyaraj.tollplazas.geo.BoundingBox;
import dev.lakshyaraj.tollplazas.geo.GeoPoint;
import dev.lakshyaraj.tollplazas.routing.RouteProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "tolls.data.import-on-startup=false")
class TollPlazaRepositoryIT extends PostgresIntegrationTest {

    @Autowired
    private TollPlazaRepository repository;
    @Autowired
    private TollPlazaImporter importer;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private RouteProvider routeProvider;

    @BeforeEach
    void clean() {
        jdbc.update("TRUNCATE toll_plaza RESTART IDENTITY");
    }

    private static TollPlazaRow row(String name, String state, double lat, double lon) {
        return new TollPlazaRow(name, state, GeoPoint.of(lat, lon));
    }

    @Test
    void upsertIsIdempotent() {
        List<TollPlazaRow> rows = List.of(row("A Toll Plaza", "Goa", 15.1, 74.1), row("B Toll Plaza", "Goa", 15.2, 74.2));

        assertThat(repository.upsertAll(rows)).isEqualTo(2);
        assertThat(repository.upsertAll(rows)).isZero(); // nothing changed, nothing written
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void upsertUpdatesChangedAttributesInPlace() {
        repository.upsertAll(List.of(row("A Toll Plaza", "Gao", 15.1, 74.1)));
        Long idBefore = jdbc.queryForObject("SELECT id FROM toll_plaza", Long.class);

        assertThat(repository.upsertAll(List.of(row("A Toll Plaza", "Goa", 15.1, 74.1)))).isEqualTo(1);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT state FROM toll_plaza", String.class)).isEqualTo("Goa");
        assertThat(jdbc.queryForObject("SELECT id FROM toll_plaza", Long.class)).isEqualTo(idBefore);
    }

    @Test
    void findsOnlyPlazasInsideTheBox() {
        repository.upsertAll(List.of(
                row("Inside", "MH", 18.5, 73.9),
                row("North", "MH", 19.5, 73.9),
                row("East", "MH", 18.5, 75.0)));

        List<TollPlaza> found = repository.findWithin(new BoundingBox(18.0, 73.5, 19.0, 74.5));

        assertThat(found).extracting(TollPlaza::name).containsExactly("Inside");
        assertThat(found.get(0).location()).isEqualTo(GeoPoint.of(18.5, 73.9));
    }

    @Test
    void importingTheRealAssignmentFileDropsDuplicates() {
        var first = importer.importFrom(new ClassPathResource("data/toll_plaza_india.csv"));
        var second = importer.importFrom(new ClassPathResource("data/toll_plaza_india.csv"));

        assertThat(first.rowsRead()).isEqualTo(2385);
        assertThat(first.duplicatesSkipped()).isEqualTo(849);
        assertThat(first.rowsRejected()).isZero();
        assertThat(first.totalInDatabase()).isEqualTo(1536);
        assertThat(second.rowsChanged()).isZero();
        assertThat(second.totalInDatabase()).isEqualTo(1536);
    }
}
