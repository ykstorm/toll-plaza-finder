"""
Builds src/main/resources/data/pincode_centroids.csv from India Post's All India Pincode Directory.

Source: https://raw.githubusercontent.com/dropdevrahul/pincodes-india/main/pincode.csv
(a republication of data.gov.in's "All India Pincode Directory", GODL-India licence)

The raw file has one row per post office, and the coordinates are messy:
  - "NA" or empty lat/lon
  - lat and lon swapped (about 2.6k rows)
  - points outside India entirely
  - India Post's own test rows (pincode 999999 is "TEST OFFICE")
We fix swaps, drop anything outside India's bounding box, and keep the median
coordinate per pincode so one bad post office can't drag the centroid away.

Usage: python3 scripts/build_pincode_centroids.py pincode.csv src/main/resources/data/pincode_centroids.csv
"""
import collections
import csv
import statistics
import sys

LAT_RANGE = (6.0, 37.5)
LON_RANGE = (68.0, 97.5)


def to_float(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def in_india(lat, lon):
    return LAT_RANGE[0] <= lat <= LAT_RANGE[1] and LON_RANGE[0] <= lon <= LON_RANGE[1]


def main(src, dst):
    offices = collections.defaultdict(list)
    dropped = swapped = 0
    with open(src, encoding="utf-8", errors="replace") as f:
        for row in csv.DictReader(f):
            if "TEST" in row["OfficeName"].upper():
                dropped += 1
                continue
            lat, lon = to_float(row["Latitude"]), to_float(row["Longitude"])
            if lat is None or lon is None:
                dropped += 1
                continue
            if not in_india(lat, lon) and in_india(lon, lat):
                lat, lon = lon, lat
                swapped += 1
            if not in_india(lat, lon):
                dropped += 1
                continue
            offices[row["Pincode"].strip()].append((lat, lon, row["District"], row["StateName"]))

    with open(dst, "w", newline="", encoding="utf-8") as out:
        writer = csv.writer(out)
        writer.writerow(["pincode", "latitude", "longitude", "district", "state", "sample_size"])
        for pincode, rows in sorted(offices.items()):
            lat = statistics.median(r[0] for r in rows)
            lon = statistics.median(r[1] for r in rows)
            district = collections.Counter(r[2] for r in rows).most_common(1)[0][0].title()
            state = collections.Counter(r[3] for r in rows).most_common(1)[0][0].title()
            writer.writerow([pincode, round(lat, 5), round(lon, 5), district, state, len(rows)])

    print(f"pincodes={len(offices)} swapped_rows={swapped} dropped_rows={dropped}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
