# Design notes

How I thought about this, what I picked and why, and what broke along the way.

## The actual problem

On paper it's "find toll plazas between two pincodes". The line that matters is the one at the bottom of the PDF. Your output should match Mappls on 80 to 90 percent of tolls. That makes it a geometry and data quality problem. Finding some tolls is easy. Finding the right ones, in order, with the right distances, is the work.

Some assumptions I made:

- Distance from source is road distance. The spec's example has Delhi to Bengaluru at 2100 km with a plaza 1400 km in, and that only works as road distance.
- An unknown pincode gets the same error as a malformed one, because that's what the spec shows.
- A to B and B to A are different routes. Divided highways and one-ways mean they can be.
- Cache the route, not the toll list. Matching takes milliseconds, so if the toll data changes the next request just reflects it.

## Request flow

```
POST /api/v1/toll-plazas
  validate pincodes (format, then not the same)
  pincode -> lat/lng from the bundled directory         unknown -> 400
  route: memory cache -> postgres cache -> OSRM         no road -> 422, OSRM down -> 503
  plazas inside the route's bounding box (indexed query)
  snap each plaza onto the road, keep the ones within 750 m
  sort by distance along the road, merge duplicates
  respond, with X-Route-Source: MEMORY / DATABASE / PROVIDER
```

## Code layout

```
geo/        coordinates, haversine, polyline codec, RoutePath (snapping + grid index)
pincode/    Pincode value object, the offline directory
routing/    RouteProvider interface, OSRM client, two-level route cache
tollplaza/  CSV parsing and cleaning, upsert import, bounding-box query
matching/   which plazas are on the route, ordering, de-dup
route/      the use case, ties it all together
api/        controller, DTOs, error mapping
config/     spring wiring and typed properties
```

None of the domain classes have Spring annotations. The wiring is all in `AppConfig`, so I can test any of them with `new`. ArchUnit tests keep it that way: `geo` and `matching` can't import Spring, nothing outside `api` can import `api`, and there are no cycles between packages.

`RouteProvider` is an interface because I knew from the start that OSRM's route choice might not match Mappls (it doesn't, more below). Swapping providers shouldn't mean touching the matching.

## Matching

A long route from OSRM is around 9,000 to 20,000 points. There are 1,536 plazas.

First I take the route's bounding box, pad it by the corridor, and query plazas inside it. There's an index on `(latitude, longitude)`, and this cuts 1,536 down to whatever's plausibly near.

`RoutePath` puts every road segment into a grid of 0.02 degree cells, about 2 km. For each plaza I only check segments in the neighbouring cells. I project the plaza onto each one, treating the segment as flat (they're short, so the error is tiny), and keep the closest. If that's within 750 m, the plaza is on the route. Its distance from source is the cumulative road distance up to that segment plus how far along the segment it lands.

Then I sort by that distance and merge duplicates. Same name close together along the route happens because some plazas are listed once per carriageway. I also merge any two plazas within 1 km of each other. That one's a judgement call. Nelamangala has two separate plazas 640 m apart. NHAI's FASTag data shows them with very different traffic, so they're real and different. They sit where NH48 and NH75 split, and you only drive through one. Keeping the one closest to the road gets the right answer for the route.

Delhi to Bengaluru takes about 300 ms with a cold cache, most of it the OSRM call. From cache it's about 20 ms.

### Why 750 m

I started at 500. Then I checked my Bengaluru routes against NHAI's fee plaza list. Hitnal is on the same Hungund to Hospet section as Shahapur and Vanagiri, which I was already matching, but I wasn't getting Hitnal. Its CSV coordinate is 587 m off the road.

So I measured. Across 8 long routes, every plaza within 3 km of the road:

| Offset from road | Plazas |
| --- | --- |
| 0 to 100 m | 61 |
| 100 to 300 m | 21 |
| 300 to 500 m | 4 |
| 500 to 750 m | 2 |
| 750 to 1000 m | 3 |
| 1 to 3 km | 21 |

Most sit right on the road. There's a thin tail of real plazas with bad coordinates (Hitnal 587 m, Kurnool 548 m), and beyond about 1 km it's mostly plazas on other roads. 750 m catches the tail without much risk. I also checked Mumbai to Pune specifically, because the old NH48 and the Expressway run side by side there. No false hits. It's configurable via `TOLLS_CORRIDOR_METERS` if someone has better data.

## Data quality

**The toll CSV.** 2,385 rows, 1,536 unique plazas, so 849 rows are exact duplicates. Columns are longitude first. Six names have commas in them. The parser reads columns by header name, handles quoted fields, drops duplicates on (name, lat, lon) and rejects bad rows with a reason instead of guessing. Postgres has a unique constraint on the same key, and the import is `INSERT ... ON CONFLICT DO UPDATE ... WHERE state IS DISTINCT FROM`. Restart the app and it writes zero rows. There's a test that imports the real file twice and checks that.

I didn't fix typos like "Banglaore" or "Pllaza". The CSV is the source of truth here, and quietly renaming things would be worse.

**The pincode directory.** India Post's file has 157k post office rows covering 19,300 pincodes. About 10.9k rows have missing or junk coordinates, and 2.6k have lat and lon swapped. `scripts/build_pincode_centroids.py` fixes the swaps, drops anything outside India, drops India Post's test office (pincode 999999), and takes the median coordinate per pincode so one bad post office can't drag it. That leaves 19,269 pincodes in under 1 MB.

I went offline instead of a geocoding API because Nominatim allows one request a second, which would have been the bottleneck of the whole thing. This is a hashmap lookup.

## Caching

Caffeine in memory, 10k routes, 6 hour TTL. Behind it, a `route_cache` table in Postgres with a 30 day TTL that stores the geometry as an encoded polyline, so a long route is tens of KB. The Postgres level is there because free hosting sleeps when idle and loses memory.

I'm using Caffeine's async cache. My first version used the sync `cache.get(key, loader)`, and on a re-read I realised it holds a lock on a hash bin while the loader runs. My loader is an HTTP call to OSRM that can take seconds, so unrelated routes sharing that bin would have waited. With the async cache the map just holds a future. Ten requests for the same route share one OSRM call, and nobody else is blocked. There's a test that fires 8 concurrent requests and checks OSRM was called once.

Switching to async surfaced a second problem through a test. Caffeine drops failed futures, but not instantly. A retry right after a failure could get the same failure back. Now I evict it myself on failure, and I ran that test 30 times in a row to make sure.

If Postgres is down, the cache logs a warning and goes straight to OSRM. Failures never get cached.

## Errors

| Case | Status |
| --- | --- |
| Malformed, missing or unknown pincode | 400, `Invalid source or destination pincode` |
| Same source and destination (after trimming spaces) | 400, `Source and destination pincodes cannot be the same` |
| Route exists but has no tolls | 200 with an empty list |
| No road (islands) | 422 |
| OSRM down, slow or returns junk | 503 |
| Bad JSON, wrong content type, wrong method | 400, 415, 405 |
| Anything else | 500 with a generic message. Details only go to the logs |

The island case: OSRM always snaps a point to the nearest road it has, no matter how far. Port Blair got snapped ~494 km away to the Myanmar coast and came back as a 3,562 km "route" with 29 toll plazas on it. OSRM's response says how far it moved each point, so anything over 25 km is now a 422.

## Rate limiting and request ids

The cache handles repeat routes, but every new pincode pair is a call to the public OSRM server. A script looping over pincodes would get us blocked there. So there's a token bucket per client IP, 60 a minute by default. Over that you get a 429 with `Retry-After`. Docs and health checks aren't limited. The IP comes from `getRemoteAddr`, which is the real client because forward-headers handling is on behind Render's proxy. The buckets live in a Caffeine cache that expires idle clients, so memory doesn't grow forever. The tests drive it with a fake clock, so nothing sleeps.

Every request gets an `X-Request-Id`, or keeps the one the caller sent if it looks sane. It goes into the logging MDC, so every log line for one request shares an id. I reject ids with newlines or odd characters so nobody can forge log lines through a header.

I'd have used Bucket4j in a bigger service. Here it's 40 lines and one less dependency.

## Testing

131 tests: unit, a web slice, integration with Testcontainers Postgres, and ArchUnit. Line coverage is 95%, and the build fails below 90%.

The ones I think matter most:

- the real CSV imported twice, asserting 849 duplicates dropped and zero rows written the second time
- a full-stack test using the real data and real pincode directory with only OSRM faked
- the U-turn case, where the road passes a plaza twice and the closer pass has to win
- OSRM's NoRoute, NoSegment, far-snap, 5xx and timeout responses
- the concurrency test on the cache

Tests caught real bugs. Spring was percent-encoding the `,` and `;` in the OSRM URL, which OSRM rejects. And the failed-future race above.

## Against Mappls, honestly

For the assignment's pair (560064 to 411045), OSRM takes NH48 to Chitradurga, then NH50 and NH965 through Vijayapura and Baramati. Mappls almost certainly stays on NH48 through Belagavi, Kolhapur and Satara. I checked a public toll listing for that route. The first ~170 km match (Nelamangala, Kulumapalya, Chokkenahalli, Karjeevanhally). After that the routes are different roads, so the lists are different.

I asked OSRM for alternative routes and it returned only one. So this is a routing difference, not a matching one. Every plaza on the route OSRM actually picks is right. A Mappls provider would fix it, and it's one class behind `RouteProvider`. I didn't ship one because a reviewer would need my API key to run the project.

## Known gaps

- A road passing over or under a tolled highway right next to a plaza could match by mistake. Toll plazas are rarely near interchanges, and fixing it properly needs plaza direction data the CSV doesn't have.
- No toll prices or vehicle classes. The CSV has neither.
- The public OSRM server has no SLA. In production I'd self-host it with the India extract.

## If I kept going

A Mappls provider plus an automated accuracy test against its toll list. Then PostGIS. `ST_DWithin` and `ST_LineLocatePoint` would do the matching in one query. At 1.5k plazas the Java version is simpler and fast enough, but with prices, vehicle classes and every state highway it'd be worth it.
