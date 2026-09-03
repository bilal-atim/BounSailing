"""Sanity checks on the generated chart package.

Land reconstruction and depth banding can both fail in ways that still produce a
plausible-looking file, so both are cross-checked against the EMODnet DTM, which
is an entirely independent source from the OSM coastline. Hand-picked probes are
kept only for a handful of places whose status is genuinely unambiguous.
"""

import json
import sys

import numpy as np
import tifffile
from shapely.geometry import Point, shape
from shapely.prepared import prep

from config import ASSETS, BBOX, CACHE

SAMPLES = 8000
# The OSM coastline and a ~93 m DTM cell cannot agree exactly at the waterline,
# so cells whose elevation sits inside this fringe are not counted either way.
LAND_FRINGE_M = 15.0
MIN_LAND_AGREEMENT = 0.99
MIN_DEPTH_COVERAGE = 0.97

NAMED_PROBES = [
    ("Marmaris town centre",  36.8552, 28.2718, True),
    ("Marmaris castle",       36.8506, 28.2734, True),
    ("Icmeler",               36.7900, 28.2200, True),
    ("Turunc",                36.7717, 28.2450, True),
    ("Bozburun",              36.6900, 28.0450, True),
    ("Marmaris bay, mid",     36.8200, 28.2600, False),
    ("Open sea S of Bozburun", 36.5000, 28.1000, False),
    ("Open sea SW",           36.4500, 27.7000, False),
]

EXPECTED_LAYERS = [
    "land.geojson", "inland_water.geojson", "depth_areas.geojson",
    "depth_contours.geojson", "soundings.geojson", "seamarks.geojson",
    "hazards.geojson", "areas.geojson", "harbours.geojson",
    "structures.geojson", "places.geojson", "roads.geojson",
    "manifest.json",
]


def load_dtm():
    elev = tifffile.imread(CACHE / "emodnet_dtm.tif").astype("float64")
    h, w = elev.shape

    def at(lat, lon):
        c = int(round((lon - BBOX["min_lon"]) / (BBOX["max_lon"] - BBOX["min_lon"]) * (w - 1)))
        r = int(round((BBOX["max_lat"] - lat) / (BBOX["max_lat"] - BBOX["min_lat"]) * (h - 1)))
        return float(elev[np.clip(r, 0, h - 1), np.clip(c, 0, w - 1)])

    return at


def check_layers():
    print("[layers]")
    bad = 0
    total = 0
    for name in EXPECTED_LAYERS:
        path = ASSETS / name
        if not path.exists():
            print(f"  FAIL {name:26s} missing")
            bad += 1
            continue
        size = path.stat().st_size
        total += size
        extra = ""
        if name.endswith(".geojson"):
            extra = f"{len(json.loads(path.read_text())['features']):6d} features"
        print(f"  ok   {name:26s} {size / 1e6:6.2f} MB  {extra}")
    print(f"  package total: {total / 1e6:.2f} MB")
    return bad


def check_land(elev_at, rng):
    land = shape(json.loads((ASSETS / "land.geojson").read_text())["features"][0]["geometry"])
    pl = prep(land)
    print(f"[land] valid={land.is_valid} parts={len(land.geoms)} area={land.area:.4f} deg^2")

    bad = 0
    for name, lat, lon, expect in NAMED_PROBES:
        got = pl.contains(Point(lon, lat))
        ok = got == expect
        bad += not ok
        print(f"  {'ok  ' if ok else 'FAIL'} {name:24s} "
              f"expected {'land' if expect else 'water':5s} got {'land' if got else 'water'}")

    agree = disagree = skipped = 0
    for _ in range(SAMPLES):
        lon = rng.uniform(BBOX["min_lon"], BBOX["max_lon"])
        lat = rng.uniform(BBOX["min_lat"], BBOX["max_lat"])
        e = elev_at(lat, lon)
        if -LAND_FRINGE_M < e < LAND_FRINGE_M:
            skipped += 1
            continue
        if pl.contains(Point(lon, lat)) == (e >= LAND_FRINGE_M):
            agree += 1
        else:
            disagree += 1
    ratio = agree / max(1, agree + disagree)
    ok = ratio >= MIN_LAND_AGREEMENT
    bad += not ok
    print(f"  {'ok  ' if ok else 'FAIL'} land/water agrees with EMODnet DTM on "
          f"{ratio * 100:.2f}% of {agree + disagree} unambiguous samples "
          f"({skipped} shoreline samples skipped)")
    return bad


def check_depth(elev_at, rng):
    fc = json.loads((ASSETS / "depth_areas.geojson").read_text())
    bands = []
    for f in fc["features"]:
        g = shape(f["geometry"])
        bands.append((f["properties"]["min_depth"], f["properties"].get("max_depth"),
                      g, prep(g)))
    print(f"[depth areas] {len(bands)} bands")
    bad = 0
    for mn, mx, g, _ in bands:
        if not g.is_valid:
            print(f"  FAIL band {mn}-{mx} has invalid geometry")
            bad += 1

    covered = uncovered = wrong = 0
    for _ in range(SAMPLES):
        lon = rng.uniform(BBOX["min_lon"], BBOX["max_lon"])
        lat = rng.uniform(BBOX["min_lat"], BBOX["max_lat"])
        e = elev_at(lat, lon)
        if e > -3.0:  # land or the very shallow fringe
            continue
        depth = -e
        p = Point(lon, lat)
        hit = next(((mn, mx) for mn, mx, _, pg in bands if pg.contains(p)), None)
        if hit is None:
            uncovered += 1
            continue
        covered += 1
        mn, mx = hit
        # A one-band slip is expected: the grid is smoothed before contouring.
        if not (mn - 5 <= depth <= (mx if mx else 1e9) + max(5, (mx or 0) * 0.5)):
            wrong += 1

    total = covered + uncovered
    ratio = covered / max(1, total)
    ok = ratio >= MIN_DEPTH_COVERAGE
    bad += not ok
    print(f"  {'ok  ' if ok else 'FAIL'} {ratio * 100:.2f}% of {total} sea samples "
          f"fall inside a depth band ({uncovered} uncovered)")
    ratio2 = 1 - wrong / max(1, covered)
    ok2 = ratio2 >= 0.97
    bad += not ok2
    print(f"  {'ok  ' if ok2 else 'FAIL'} {ratio2 * 100:.2f}% of covered samples land in a "
          f"band that brackets the DTM depth ({wrong} mismatches)")
    return bad


def check_soundings(elev_at):
    fc = json.loads((ASSETS / "soundings.geojson").read_text())
    bad = 0
    off = 0
    for f in fc["features"]:
        lon, lat = f["geometry"]["coordinates"]
        d = f["properties"]["depth"]
        e = elev_at(lat, lon)
        if abs(-e - d) > max(3.0, d * 0.3):
            off += 1
    ratio = 1 - off / max(1, len(fc["features"]))
    ok = ratio >= 0.95
    bad += not ok
    print(f"[soundings] {'ok  ' if ok else 'FAIL'} {ratio * 100:.2f}% of "
          f"{len(fc['features'])} soundings match the DTM within tolerance")
    return bad


def main():
    rng = np.random.default_rng(20240807)
    elev_at = load_dtm()
    bad = check_layers()
    bad += check_land(elev_at, rng)
    bad += check_depth(elev_at, rng)
    bad += check_soundings(elev_at)
    if bad:
        print(f"\n{bad} check(s) FAILED")
        sys.exit(1)
    print("\nall checks passed")


if __name__ == "__main__":
    main()
