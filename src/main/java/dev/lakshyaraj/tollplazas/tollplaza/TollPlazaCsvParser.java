package dev.lakshyaraj.tollplazas.tollplaza;

import dev.lakshyaraj.tollplazas.geo.GeoPoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// things wrong with toll_plaza_india.csv:
// lon comes before lat, so columns are looked up by header name.
// 849 of 2385 rows are exact duplicates.
// bad rows get rejected with a reason, not guessed at.
public class TollPlazaCsvParser {

    private static final String LONGITUDE = "longitude";
    private static final String LATITUDE = "latitude";
    private static final String NAME = "toll_name";
    private static final String STATE = "geo_state";

    // generous box around India incl. islands. A plaza outside this is a data error.
    private static final double MIN_LAT = 6.0;
    private static final double MAX_LAT = 37.5;
    private static final double MIN_LON = 68.0;
    private static final double MAX_LON = 97.5;

    public ParseResult parse(Reader source) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return new ParseResult(List.of(), 0, 0, List.of());
        }
        Map<String, Integer> columns = indexHeader(headerLine);

        Map<String, TollPlazaRow> unique = new LinkedHashMap<>();
        List<String> rejected = new ArrayList<>();
        int dataRows = 0;
        int duplicates = 0;
        int lineNumber = 1;

        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            dataRows++;
            List<String> cells = splitCsvLine(line);
            try {
                TollPlazaRow row = toRow(cells, columns);
                if (unique.putIfAbsent(row.naturalKey(), row) != null) {
                    duplicates++;
                }
            } catch (IllegalArgumentException e) {
                rejected.add("line " + lineNumber + ": " + e.getMessage());
            }
        }
        return new ParseResult(List.copyOf(unique.values()), dataRows, duplicates, List.copyOf(rejected));
    }

    private static Map<String, Integer> indexHeader(String headerLine) {
        String clean = headerLine.startsWith("﻿") ? headerLine.substring(1) : headerLine;
        List<String> names = splitCsvLine(clean);
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            index.put(names.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        for (String required : List.of(LONGITUDE, LATITUDE, NAME)) {
            if (!index.containsKey(required)) {
                throw new IllegalArgumentException("toll CSV is missing column '" + required + "', got " + index.keySet());
            }
        }
        return index;
    }

    private static TollPlazaRow toRow(List<String> cells, Map<String, Integer> columns) {
        String name = cell(cells, columns.get(NAME));
        if (name.isEmpty()) {
            throw new IllegalArgumentException("missing toll name");
        }
        double lat = number(cell(cells, columns.get(LATITUDE)), "latitude");
        double lon = number(cell(cells, columns.get(LONGITUDE)), "longitude");
        if (lat < MIN_LAT || lat > MAX_LAT || lon < MIN_LON || lon > MAX_LON) {
            throw new IllegalArgumentException("'" + name + "' has coordinates outside India: " + lat + ", " + lon);
        }
        Integer stateIdx = columns.get(STATE);
        String state = stateIdx == null ? "" : cell(cells, stateIdx);
        return new TollPlazaRow(name, state,
                new GeoPoint(TollPlazaRow.round6(lat), TollPlazaRow.round6(lon)));
    }

    private static String cell(List<String> cells, Integer index) {
        if (index == null || index >= cells.size()) {
            return "";
        }
        return cells.get(index).trim();
    }

    private static double number(String raw, String field) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad " + field + " '" + raw + "'");
        }
    }

    // just enough CSV: quoted fields with commas and "" escapes. 6 plaza names have commas in them
    static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    // duplicates = rows dropped because the same plaza was already seen
    public record ParseResult(List<TollPlazaRow> plazas, int dataRows, int duplicates, List<String> rejected) {
    }
}
