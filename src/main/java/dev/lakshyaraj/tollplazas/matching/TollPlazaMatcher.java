package dev.lakshyaraj.tollplazas.matching;

import dev.lakshyaraj.tollplazas.geo.Haversine;
import dev.lakshyaraj.tollplazas.geo.RoutePath;
import dev.lakshyaraj.tollplazas.tollplaza.TollPlaza;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// a plaza counts if it's within corridorMeters of the road. the CSV coords aren't exact,
// a few are 500-800 m off (Hitnal sits 587 m off NH50 and it's definitely on it), so the
// corridor can't be tight. too wide and you start catching plazas on parallel roads.
//
// then two kinds of dupes get merged, keeping the one closest to the road:
//  - same name close together along the route (some plazas are listed once per side)
//  - any two plazas within sameSiteMeters. Nelamangala has two plazas 640 m apart where
//    NH48 and NH75 split, and you only drive through one of them
public class TollPlazaMatcher {

    private final double corridorMeters;
    private final double duplicateWindowMeters;
    private final double sameSiteMeters;

    public TollPlazaMatcher(double corridorMeters, double duplicateWindowMeters, double sameSiteMeters) {
        if (corridorMeters <= 0) {
            throw new IllegalArgumentException("corridor must be positive");
        }
        this.corridorMeters = corridorMeters;
        this.duplicateWindowMeters = duplicateWindowMeters;
        this.sameSiteMeters = sameSiteMeters;
    }

    public List<MatchedTollPlaza> match(RoutePath path, List<TollPlaza> candidates) {
        List<MatchedTollPlaza> hits = new ArrayList<>();
        for (TollPlaza plaza : candidates) {
            path.project(plaza.location(), corridorMeters).ifPresent(p ->
                    hits.add(new MatchedTollPlaza(plaza, p.distanceAlongMeters(), p.offsetMeters())));
        }
        hits.sort(Comparator.comparingDouble(MatchedTollPlaza::distanceFromSourceMeters));
        return collapseDuplicates(hits);
    }

    private List<MatchedTollPlaza> collapseDuplicates(List<MatchedTollPlaza> sorted) {
        List<MatchedTollPlaza> result = new ArrayList<>();
        for (MatchedTollPlaza hit : sorted) {
            int existing = findDuplicate(result, hit);
            if (existing < 0) {
                result.add(hit);
            } else if (hit.offsetMeters() < result.get(existing).offsetMeters()) {
                result.set(existing, hit); // keep the one that sits closest to the road
            }
        }
        return result;
    }

    private int findDuplicate(List<MatchedTollPlaza> kept, MatchedTollPlaza hit) {
        String name = normalise(hit.plaza().name());
        double window = Math.max(duplicateWindowMeters, sameSiteMeters);
        // kept is in route order, so only the tail can be within the window
        for (int i = kept.size() - 1; i >= 0; i--) {
            MatchedTollPlaza other = kept.get(i);
            if (hit.distanceFromSourceMeters() - other.distanceFromSourceMeters() > window) {
                break;
            }
            boolean sameName = normalise(other.plaza().name()).equals(name);
            boolean sameSite = Haversine.distanceMeters(other.plaza().location(), hit.plaza().location()) < sameSiteMeters;
            if (sameName || sameSite) {
                return i;
            }
        }
        return -1;
    }

    private static String normalise(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
