# toll-plaza-finder

Give it two Indian pincodes, it tells you which toll plazas you'll drive through and how far along the road each one is.

Built for the FreightFox backend assignment. Java 21, Spring Boot 3.5, Postgres.

**Live:** https://toll-plaza-finder.vercel.app (opens Swagger. It scales to zero when idle, so the first request after a quiet spell takes ~15s while Java starts)

```bash
curl -X POST https://toll-plaza-finder.vercel.app/api/v1/toll-plazas \
  -H 'Content-Type: application/json' \
  -d '{"sourcePincode": "560064", "destinationPincode": "411045"}'
```

```json
{
  "route": { "sourcePincode": "560064", "destinationPincode": "411045", "distanceInKm": 849.3 },
  "tollPlazas": [
    { "name": "Banglaore-Nelamangala Toll Plaza", "latitude": 13.09062, "longitude": 77.400019, "distanceFromSource": 25.5 },
    { "name": "Kulumapalya Toll Plaza", "latitude": 13.114934, "longitude": 77.375094, "distanceFromSource": 29.5 },
    { "name": "Chokkenahalli Toll Plaza", "latitude": 13.302016, "longitude": 77.162007, "distanceFromSource": 60.9 }
  ]
}
```

That's trimmed. The real response has 12 plazas.

## How it works

The pincodes get turned into coordinates using India Post's pincode directory, which ships with the app. I get a driving route from OSRM with the full road geometry. Then I pull the plazas near that route out of Postgres and snap each one onto the road. If it's within 750 m of the road, it's on the route. `distanceFromSource` is measured along the road from the start, not as a straight line, so the order is right even when the road bends.

Routes are cached in memory and in Postgres. Check the `X-Route-Source` header: `PROVIDER` on the first call, `MEMORY` after that. If the app restarted in between, you'll see `DATABASE`.

There's a longer write-up in [docs/DESIGN.md](docs/DESIGN.md), including what went wrong along the way.

## Things I ran into

The CSV has 2,385 rows but only 1,536 unique plazas. 849 rows are exact copies. It's also longitude first, which is backwards from what you'd expect. The parser reads columns by header name and drops duplicates. The import is an upsert, so restarting the app doesn't add anything.

Pincode `999999` passed my validation at first. India Post's directory has a row called "TEST OFFICE" with that pincode. It's filtered out now.

Port Blair (`744101`) gave me a 3,562 km route. OSRM snaps to the nearest road it knows, and for an island that was a road ~494 km away on the Myanmar coast. Now if either point has to move more than 25 km to reach a road, you get a 422.

I started with a 500 m corridor. Then I checked against NHAI's fee plaza list. Hitnal is on the same NH50 stretch as Shahapur and Vanagiri, which I was already matching. But its CSV coordinate is 587 m off the road, so I was missing it. I measured offsets across 8 long routes. Most plazas sit within 100 m, a few are off by 500 to 800 m because the data is sloppy. 750 m picks those up without catching plazas on parallel roads.

## Running it

You need Java 21 and either Docker or Postgres.

```bash
docker compose up --build
# then http://localhost:8080
```

or with your own Postgres (defaults are db `tolls`, user `tolls`, password `tolls`):

```bash
./mvnw spring-boot:run
```

## Tests

```bash
./mvnw verify
```

131 tests, 95% line coverage, and the build fails under 90%. The integration tests use Testcontainers, so Docker has to be running. If you don't have Docker, point them at any Postgres with `IT_DATABASE_URL=jdbc:postgresql://localhost:5432/tolls_it`.

There's also a Postman collection in `postman/` with every happy path and error case, with assertions on each.

## API

`POST /api/v1/toll-plazas` with `{"sourcePincode": "...", "destinationPincode": "..."}`

| Status | When |
| --- | --- |
| 200 | Found a route. `tollPlazas` can be empty |
| 429 | Too many requests from you, check `Retry-After` |
| 400 | Pincode is malformed, doesn't exist, or source equals destination |
| 422 | No road between them |
| 503 | OSRM is down or timed out |

Errors look like `{"error": "..."}`. For the cases in the spec, the messages match it exactly.

There's also a 429 if one client sends more than 60 requests a minute. Every new pincode pair is a call to the public OSRM server, and I don't want one script getting us banned. Each response carries an `X-Request-Id` (yours if you send one), and the same id is on every log line for that request.

## Config

Everything has a default. The ones you might change:

| Env var | Default |
| --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/tolls`. A `postgres://user:pass@host/db` URL works too |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | `tolls`, `tolls` |
| `OSRM_BASE_URL` | `https://router.project-osrm.org` |
| `TOLLS_CORRIDOR_METERS` | `750` |
| `TOLLS_RATE_LIMIT_PER_MINUTE` | `60` |

## Deploying

It's one Docker image plus a Postgres, so it runs anywhere that takes a Dockerfile.

**Vercel.** Import the repo. Vercel picks up the Dockerfile and runs it with its container runtime. Add Postgres from the Marketplace (Neon) and it sets `DATABASE_URL` for you. Paste-ready `postgres://` URLs work as they are, the app converts them.

**Render.** New > Blueprint > pick the repo. `render.yaml` creates the API and the database together.

## What's not great

For the Bengaluru to Pune pair in the assignment, my list won't match Mappls after about 170 km. That's not the matching. OSRM routes through Vijayapura and Baramati, and Mappls goes via Belagavi, Kolhapur and Satara on NH48. I asked OSRM for alternatives and it only has the one. Every plaza on the route OSRM picks is correct. Routing sits behind an interface, so a Mappls or Google provider is one class plus an API key. I didn't add one because then you'd need my key to run this.

The public OSRM server has no uptime guarantee either. For real use I'd self-host it with the India extract.

## Data

Toll plazas come from the assignment's CSV. Pincodes come from India Post's All India Pincode Directory on data.gov.in (GODL-India licence), cleaned by `scripts/build_pincode_centroids.py`. Routing is OSRM on OpenStreetMap data.
