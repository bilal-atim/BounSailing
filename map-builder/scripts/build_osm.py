"""Turn the raw Overpass response into the chart's vector layers.

Layers produced:

  land.geojson             land polygons reconstructed from natural=coastline
  inland_water.geojson     lakes, reservoirs, wide rivers
  seamarks.geojson         buoys, beacons, lights, and every other seamark point
  hazards.geojson          rocks, wrecks, obstructions, reefs, shoals
  areas.geojson            anchorages, restricted / military areas, fairways
  harbours.geojson         harbours, marinas, ferry terminals
  structures.geojson       piers, breakwaters, groynes, docks
  places.geojson           settlements, islands, capes, bays
  roads.geojson            the classified coastal road network

The land reconstruction is the only non-obvious part. OSM stores coastline as
directed open ways with *land on the left*. Cutting the bounding box with those
ways and polygonising the result yields a set of faces; each face is then
classified by probing a point a hair to the left of the coastline where the face
touches it. That handles bays, islands and inlets without needing the global
osmcoastline shapefiles.
"""

import math
from collections import defaultdict

from shapely.geometry import LineString, Point, Polygon, box, mapping, shape
from shapely.ops import linemerge, polygonize, unary_union

from config import ASSETS, BBOX
from common import feature, write_geojson
from fetch_osm import fetch

BBOX_POLY = box(BBOX["min_lon"], BBOX["min_lat"], BBOX["max_lon"], BBOX["max_lat"])

# Probe offset in degrees when deciding which side of the coastline is land
# (~11 cm). Small enough to stay inside the narrowest face, large enough to
# survive floating point noise.
PROBE_DEG = 1e-6

COAST_SIMPLIFY = 1.5e-5   # ~1.3 m
ROAD_SIMPLIFY = 5e-5      # ~4 m

# An untagged rock further inland than this is treated as terrain, not a
# navigation hazard (~55 m).
INLAND_HAZARD_TOLERANCE_DEG = 5e-4


# ---------------------------------------------------------------------------
# Overpass element helpers
# ---------------------------------------------------------------------------

def as_lines(geom):
    """Normalise any line-ish geometry into a list of LineStrings, merged where possible."""
    if geom.is_empty:
        return []
    if geom.geom_type == "LineString":
        return [geom]
    merged = linemerge(geom)
    if merged.is_empty:
        return []
    if merged.geom_type == "LineString":
        return [merged]
    return [g for g in merged.geoms if g.geom_type == "LineString" and not g.is_empty]


def el_geometry(el):
    """Return the element's coordinates as [[lon, lat], ...], or None."""
    if el["type"] == "node":
        return [[el["lon"], el["lat"]]]
    if "geometry" in el:
        return [[p["lon"], p["lat"]] for p in el["geometry"] if p]
    return None


def el_centroid(el):
    coords = el_geometry(el)
    if not coords:
        return None
    n = len(coords)
    return [sum(c[0] for c in coords) / n, sum(c[1] for c in coords) / n]


def is_closed(coords):
    return len(coords) > 3 and abs(coords[0][0] - coords[-1][0]) < 1e-12 \
        and abs(coords[0][1] - coords[-1][1]) < 1e-12


def rel_outer_rings(el):
    """Stitch a multipolygon relation's outer members into closed rings."""
    if el["type"] != "relation":
        return []
    segments = []
    for m in el.get("members", []):
        if m.get("role") not in ("outer", ""):
            continue
        g = m.get("geometry")
        if not g or len(g) < 2:
            continue
        segments.append(LineString([(p["lon"], p["lat"]) for p in g if p]))
    if not segments:
        return []
    lines = as_lines(unary_union(segments))
    rings = []
    for ln in lines:
        c = list(ln.coords)
        if len(c) < 4:
            continue
        if c[0] != c[-1]:
            c.append(c[0])
        rings.append(c)
    return rings


def polygon_geometry(el):
    """Best-effort polygon (or point) geometry for an area-ish element."""
    if el["type"] == "relation":
        rings = rel_outer_rings(el)
        if rings:
            return {"type": "MultiPolygon", "coordinates": [[r] for r in rings]}
        return None
    coords = el_geometry(el)
    if not coords:
        return None
    if el["type"] == "node":
        return {"type": "Point", "coordinates": coords[0]}
    if is_closed(coords):
        return {"type": "Polygon", "coordinates": [coords]}
    return {"type": "LineString", "coordinates": coords}


# ---------------------------------------------------------------------------
# Land reconstruction
# ---------------------------------------------------------------------------

def build_land(elements):
    coast_ways = [
        el for el in elements
        if el["type"] == "way" and el.get("tags", {}).get("natural") == "coastline"
    ]
    print(f"    coastline  {len(coast_ways)} ways")
    if not coast_ways:
        return []

    # Keep the ways exactly as OSM stores them. shapely's linemerge is free to
    # reverse a segment when it stitches chains together, which would silently
    # flip the land/water side for a whole stretch of coast, so the directed
    # ways are never merged - only clipped.
    clipped = []
    for el in coast_ways:
        c = el_geometry(el)
        if not c or len(c) < 2:
            continue
        piece = LineString(c).intersection(BBOX_POLY)
        if piece.is_empty:
            continue
        if piece.geom_type == "LineString":
            clipped.append(piece)
        elif piece.geom_type == "MultiLineString":
            clipped.extend(g for g in piece.geoms if g.length > 0)
    print(f"    clipped    {len(clipped)} directed coastline segments")

    # Noding for polygonize is direction-agnostic, so a union here is safe.
    faces = list(polygonize(unary_union(clipped + [BBOX_POLY.exterior])))
    print(f"    polygonize {len(faces)} faces")

    land = []
    for face in faces:
        if classify_land(face, clipped):
            land.append(face)
    print(f"    classified {len(land)} land faces / {len(faces)}")

    merged_land = unary_union(land)
    if merged_land.is_empty:
        return []
    merged_land = merged_land.simplify(COAST_SIMPLIFY, preserve_topology=True)

    geoms = [merged_land] if merged_land.geom_type == "Polygon" else list(merged_land.geoms)
    coords = []
    for g in geoms:
        if g.geom_type != "Polygon" or g.is_empty:
            continue
        rings = [list(g.exterior.coords)] + [list(i.coords) for i in g.interiors]
        coords.append(rings)
    return [feature({"type": "MultiPolygon", "coordinates": coords}, {"kind": "land"})]


def classify_land(face, coast_lines, samples=240):
    """True when the face lies on the land side of the coastline bounding it."""
    ring = face.exterior
    votes_land = 0
    votes_sea = 0
    for i in range(samples):
        p = ring.interpolate(i / samples, normalized=True)
        line = min(coast_lines, key=lambda ln: ln.distance(p), default=None)
        if line is None or line.distance(p) > 1e-9:
            continue
        d = line.project(p)
        eps = min(1e-6, line.length / 4)
        a = line.interpolate(max(0.0, d - eps))
        b = line.interpolate(min(line.length, d + eps))
        dx, dy = b.x - a.x, b.y - a.y
        mag = math.hypot(dx, dy)
        if mag == 0:
            continue
        # Left-hand normal of the coastline direction points towards land.
        nx, ny = -dy / mag, dx / mag
        probe = Point(p.x + nx * PROBE_DEG, p.y + ny * PROBE_DEG)
        if face.contains(probe):
            votes_land += 1
        else:
            votes_sea += 1
    if votes_land + votes_sea == 0:
        return False  # face bounded only by the bbox: open sea
    return votes_land > votes_sea


# ---------------------------------------------------------------------------
# Seamarks and friends
# ---------------------------------------------------------------------------

# Seamark types promoted to their own rendering class.
HAZARD_SEAMARKS = {
    "rock", "wreck", "obstruction", "underwater_rock", "shoal", "reef",
    "foul_ground", "cable_submarine", "pipeline_submarine",
}
AREA_SEAMARKS = {
    "anchorage", "anchor_berth", "restricted_area", "military_area",
    "fairway", "separation_zone", "separation_lane", "precautionary_area",
    "seaplane_landing_area", "cable_area", "pipeline_area", "dredged_area",
    "marine_farm", "dumping_ground", "sea_area",
}
HARBOUR_SEAMARKS = {"harbour", "small_craft_facility", "mooring", "berth"}

# Tag prefixes copied into the feature so the in-app inspector can display them.
SEAMARK_PREFIXES = (
    "seamark:", "name", "ref", "depth", "height", "colour", "description",
)


def seamark_props(tags):
    out = {}
    for k, v in tags.items():
        if k.startswith("seamark:") or k in ("name", "ref", "depth", "height",
                                             "wikidata", "operator", "vhf",
                                             "website", "phone", "harbour"):
            out[k] = v
    return out


def light_summary(tags):
    """Compose an IALA-style light description, e.g. 'Fl(2) R 10s 8M'."""
    ch = tags.get("seamark:light:character") or tags.get("seamark:light:1:character")
    if not ch:
        return None
    grp = tags.get("seamark:light:group") or tags.get("seamark:light:1:group")
    col = tags.get("seamark:light:colour") or tags.get("seamark:light:1:colour")
    per = tags.get("seamark:light:period") or tags.get("seamark:light:1:period")
    rng = tags.get("seamark:light:range") or tags.get("seamark:light:1:range")
    abbrev = {"white": "W", "red": "R", "green": "G", "yellow": "Y",
              "blue": "Bu", "orange": "Or", "violet": "Vi", "amber": "Am"}
    parts = [ch + (f"({grp})" if grp else "")]
    if col:
        parts.append(abbrev.get(col, col[:2].upper()))
    if per:
        parts.append(f"{per}s")
    if rng:
        parts.append(f"{rng}M")
    return " ".join(parts)


def top_colour(tags):
    """First declared colour, used to pick the buoy sprite."""
    for key in ("seamark:buoy_lateral:colour", "seamark:buoy_cardinal:colour",
                "seamark:buoy_safe_water:colour", "seamark:buoy_special_purpose:colour",
                "seamark:buoy_isolated_danger:colour", "seamark:beacon_lateral:colour",
                "seamark:beacon_cardinal:colour", "seamark:beacon_special_purpose:colour",
                "seamark:light:colour", "seamark:colour"):
        v = tags.get(key)
        if v:
            return v.split(";")[0]
    return None


def category_of(tags, stype):
    for key in (f"seamark:{stype}:category", "seamark:buoy_lateral:category",
                "seamark:beacon_lateral:category", "seamark:buoy_cardinal:category",
                "seamark:beacon_cardinal:category", "seamark:harbour:category",
                "seamark:rock:category", "seamark:wreck:category",
                "seamark:obstruction:category", "seamark:restricted_area:category",
                "seamark:anchorage:category"):
        v = tags.get(key)
        if v:
            return v
    return None


def make_seamark(el, stype):
    coords = el_centroid(el)
    if not coords:
        return None
    tags = el.get("tags", {})
    props = {
        "stype": stype,
        "name": tags.get("seamark:name") or tags.get("name"),
        "category": category_of(tags, stype),
        "colour": top_colour(tags),
        "shape": tags.get(f"seamark:{stype}:shape"),
        "light": light_summary(tags),
        "depth": tags.get("seamark:wreck:depth") or tags.get("seamark:rock:water_level")
                 or tags.get("depth"),
        "osm": f"{el['type']}/{el['id']}",
    }
    props.update({k: v for k, v in seamark_props(tags).items() if k not in props})
    return feature({"type": "Point", "coordinates": coords}, props)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    print("[osm]")
    data = fetch()
    elements = data["elements"]
    print(f"    elements   {len(elements)}")

    land = build_land(elements)
    land_shape = shape(land[0]["geometry"]) if land else None
    dropped_inland = 0

    inland_water, seamarks, hazards, areas = [], [], [], []
    harbours, structures, places, roads = [], [], [], []
    counts = defaultdict(int)

    for el in elements:
        tags = el.get("tags") or {}
        if not tags:
            continue

        stype = tags.get("seamark:type")

        # --- seamarks -------------------------------------------------
        if stype:
            counts[f"seamark:{stype}"] += 1
            if stype in AREA_SEAMARKS:
                geom = polygon_geometry(el)
                # OSM tags plenty of anchorages as a bare node. Those have no
                # extent to fill or outline, so they go to the symbol layer
                # instead - otherwise they end up in a line layer that cannot
                # draw them and they vanish from the chart entirely.
                if geom and geom["type"] == "Point":
                    f = make_seamark(el, stype)
                    if f:
                        seamarks.append(f)
                    continue
                if geom:
                    areas.append(feature(geom, {
                        "stype": stype,
                        "name": tags.get("seamark:name") or tags.get("name"),
                        "category": category_of(tags, stype),
                        "restriction": tags.get("seamark:restricted_area:restriction"),
                        "osm": f"{el['type']}/{el['id']}",
                    }))
                continue
            if stype in HAZARD_SEAMARKS:
                f = make_seamark(el, stype)
                if f:
                    hazards.append(f)
                continue
            if stype in HARBOUR_SEAMARKS:
                f = make_seamark(el, stype)
                if f:
                    harbours.append(f)
                continue
            f = make_seamark(el, stype)
            if f:
                seamarks.append(f)
            continue

        natural = tags.get("natural")
        man_made = tags.get("man_made")

        # --- hazards without a seamark tag ----------------------------
        if natural in ("reef", "shoal", "rock", "bare_rock") or tags.get("historic") == "wreck":
            coords = el_centroid(el)
            # OSM tags plenty of inland crags as natural=bare_rock. Those are
            # hillsides, not things you can hit in a boat, so anything well
            # inside the coastline is dropped. Rocks within a stone's throw of
            # the shore are kept: they are frequently real marine hazards that
            # nobody has given a seamark tag.
            if coords and land_shape is not None:
                point = Point(coords[0], coords[1])
                if land_shape.contains(point) and \
                        land_shape.boundary.distance(point) > INLAND_HAZARD_TOLERANCE_DEG:
                    dropped_inland += 1
                    continue
            if coords:
                hazards.append(feature({"type": "Point", "coordinates": coords}, {
                    "stype": {"reef": "reef", "shoal": "shoal", "rock": "rock",
                              "bare_rock": "rock"}.get(natural, "wreck"),
                    "name": tags.get("name"),
                    "source_tag": natural or "historic=wreck",
                    "osm": f"{el['type']}/{el['id']}",
                }))
                counts["hazard(osm)"] += 1
            continue

        # --- inland water ---------------------------------------------
        if natural in ("water", "wetland") or tags.get("landuse") == "reservoir":
            geom = polygon_geometry(el)
            if geom and geom["type"] in ("Polygon", "MultiPolygon"):
                inland_water.append(feature(geom, {"name": tags.get("name")}))
                counts["inland_water"] += 1
            continue

        # --- harbours / marinas ---------------------------------------
        if tags.get("leisure") == "marina" or tags.get("harbour") or \
                tags.get("amenity") == "ferry_terminal" or man_made == "lighthouse":
            coords = el_centroid(el)
            if coords:
                harbours.append(feature({"type": "Point", "coordinates": coords}, {
                    "stype": ("landmark" if man_made == "lighthouse" else
                              "ferry" if tags.get("amenity") == "ferry_terminal" else
                              "harbour"),
                    "category": "marina" if tags.get("leisure") == "marina" else None,
                    "name": tags.get("name"),
                    "vhf": tags.get("vhf"),
                    "phone": tags.get("phone"),
                    "website": tags.get("website"),
                    "light": light_summary(tags),
                    "osm": f"{el['type']}/{el['id']}",
                }))
                counts["harbour"] += 1
            continue

        # --- coastal structures ---------------------------------------
        if man_made in ("pier", "breakwater", "groyne") or tags.get("waterway") == "dock":
            geom = polygon_geometry(el)
            if geom:
                structures.append(feature(geom, {
                    "kind": man_made or "dock", "name": tags.get("name"),
                }))
                counts["structure"] += 1
            continue

        # --- places ----------------------------------------------------
        if tags.get("place") or natural in ("bay", "cape", "peak", "beach", "strait"):
            coords = el_centroid(el)
            name = tags.get("name")
            if coords and name:
                kind = tags.get("place") or natural
                places.append(feature({"type": "Point", "coordinates": coords}, {
                    "kind": kind,
                    "name": name,
                    "rank": {"city": 0, "town": 1, "village": 2, "suburb": 2,
                             "island": 2, "bay": 3, "cape": 3, "hamlet": 4,
                             "islet": 4, "neighbourhood": 4, "beach": 4,
                             "locality": 5, "peak": 5, "strait": 3}.get(kind, 5),
                }))
                counts[f"place:{kind}"] += 1
            continue

        # --- roads ------------------------------------------------------
        hw = tags.get("highway")
        if hw and el["type"] == "way":
            coords = el_geometry(el)
            if coords and len(coords) >= 2:
                simplified = LineString(coords).simplify(ROAD_SIMPLIFY, preserve_topology=False)
                roads.append(feature(mapping(simplified), {
                    "class": hw, "name": tags.get("name"), "ref": tags.get("ref"),
                }))
                counts[f"road:{hw}"] += 1

    print(f"    dropped    {dropped_inland} inland rock features")
    print("    breakdown")
    for k, v in sorted(counts.items(), key=lambda kv: -kv[1])[:30]:
        print(f"      {k:38s} {v}")

    write_geojson(ASSETS / "land.geojson", land)
    write_geojson(ASSETS / "inland_water.geojson", inland_water)
    write_geojson(ASSETS / "seamarks.geojson", seamarks)
    write_geojson(ASSETS / "hazards.geojson", hazards)
    write_geojson(ASSETS / "areas.geojson", areas)
    write_geojson(ASSETS / "harbours.geojson", harbours)
    write_geojson(ASSETS / "structures.geojson", structures)
    write_geojson(ASSETS / "places.geojson", places)
    write_geojson(ASSETS / "roads.geojson", roads)


if __name__ == "__main__":
    main()
