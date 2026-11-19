package dev.lakshyaraj.tollplazas.tollplaza;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

// runs on every startup. it's an upsert so the second run writes nothing
public class TollPlazaImporter {

    private static final Logger log = LoggerFactory.getLogger(TollPlazaImporter.class);

    private final TollPlazaCsvParser parser;
    private final TollPlazaRepository repository;

    public TollPlazaImporter(TollPlazaCsvParser parser, TollPlazaRepository repository) {
        this.parser = parser;
        this.repository = repository;
    }

    public ImportReport importFrom(Resource csv) {
        TollPlazaCsvParser.ParseResult parsed;
        try (Reader reader = new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8)) {
            parsed = parser.parse(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read toll plaza CSV " + csv.getDescription(), e);
        }

        int changed = repository.upsertAll(parsed.plazas());
        ImportReport report = new ImportReport(parsed.dataRows(), parsed.plazas().size(),
                parsed.duplicates(), parsed.rejected().size(), changed, repository.count());

        log.info("Toll plaza import: {}", report);
        parsed.rejected().forEach(reason -> log.warn("Rejected toll plaza row, {}", reason));
        return report;
    }

    public record ImportReport(int rowsRead, int uniquePlazas, int duplicatesSkipped,
                               int rowsRejected, int rowsChanged, long totalInDatabase) {
    }
}
