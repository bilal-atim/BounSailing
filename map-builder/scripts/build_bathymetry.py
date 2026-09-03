"""Turn the EMODnet DTM into the chart's depth layers.

Produces three layers from a single raster so that lines, areas and soundings can
never disagree with each other:

  depth_areas.geojson     filled bands (0-2, 2-5, 5-10, 10-20, 20-50, 50-200, 200+)
  depth_contours.geojson  contour lines at the depths in config.CONTOUR_DEPTHS
  soundings.geojson       the shallowest value in each coarse cell, chart-style

Depth is stored positive-down in metres. Elevations above the waterline are
masked out before contouring so that contours stop at the shore rather than
wrapping around hills.
"""

import numpy as np
import tifffile
from contourpy import contour_generator
from shapely.geometry import Polygon, mapping
from shapely.ops import unary_union

from config import ASSETS, BBOX, CONTOUR_DEPTHS
from common import feature, write_geojson
from fetch_bathymetry import fetch

# Band edges for the filled depth areas, positive-down metres.
DEPTH_BANDS = [0, 2, 5, 10, 20, 50, 200, 12000]

# Sounding grid: one value roughly every 0.008 deg (~700 m), which keeps the
# label density readable at zoom 14+ without flooding the tile.
SOUNDING_STEP_DEG = 0.008
SOUNDING_MAX_DEPTH = 120.0

# Simplification tolerance in degrees for contour/area geometry (~2 m).
SIMPLIFY_DEG = 2e-5
MIN_AREA_DEG2 = 2e-7   # drop slivers smaller than ~2 ha
MIN_LINE_LEN_DEG = 2e-4  # drop contour fragments shorter than ~20 m


def load_grid():
    path = fetch()
    with tifffile.TiffFile(path) as tf:
        elev = tf.pages[0].asarray().astype("float64")

    h, w = elev.shape
    # The WCS honours the requested subset exactly, so the grid maps linearly
    # onto the bbox with row 0 at the northern edge.
    lons = np.linspace(BBOX["min_lon"], BBOX["max_lon"], w)
    lats = np.linspace(BBOX["max_lat"], BBOX["min_lat"], h)
    print(f"    grid       {w} x {h}  "
          f"({(BBOX['max_lon'] - BBOX['min_lon']) / w * 111320 * 0.8:.0f} m/px)")
    return elev, lons, lats


def smooth(a, passes=1):
    """Light 3x3 box blur; removes raster stair-stepping from the contours."""
    out = a
    for _ in range(passes):
        p = np.pad(out, 1, mode="edge")
        out = (
            p[:-2, :-2] + p[:-2, 1:-1] + p[:-2, 2:] +
            p[1:-1, :-2] + p[1:-1, 1:-1] + p[1:-1, 2:] +
            p[2:, :-2] + p[2:, 1:-1] + p[2:, 2:]
        ) / 9.0
    return out


def _to_lonlat(seg, lons, lats):
    """contourpy returns (col, row) in index space; map it to lon/lat."""
    cols = np.clip(seg[:, 0], 0, len(lons) - 1)
    rows = np.clip(seg[:, 1], 0, len(lats) - 1)
    lon = np.interp(cols, np.arange(len(lons)), lons)
    lat = np.interp(rows, np.arange(len(lats)), lats)
    return np.column_stack([lon, lat])


def build_contours(depth, lons, lats):
    from shapely.geometry import LineString

    gen = contour_generator(z=depth, name="serial", line_type="SeparateCode")
    features = []
    for d in CONTOUR_DEPTHS:
        lines, _codes = gen.lines(float(d))
        kept = 0
        for seg in lines:
            if len(seg) < 3:
                continue
            pts = _to_lonlat(np.asarray(seg, dtype="float64"), lons, lats)
            line = LineString(pts).simplify(SIMPLIFY_DEG, preserve_topology=False)
            if line.is_empty or line.length < MIN_LINE_LEN_DEG or len(line.coords) < 2:
                continue
            features.append(feature(
                mapping(line),
                {
                    "depth": d,
                    # MapLibre's text-field needs a string; baking the label here
                    # avoids a toString coercion in every style expression.
                    "label": str(d),
                    "major": 1 if d in (10, 20, 50, 200, 1000) else 0,
                },
            ))
            kept += 1
        print(f"      {d:>5} m contour: {kept} lines")
    return features


def build_depth_areas(depth, lons, lats):
    """Filled depth bands.

    contourpy hands back rings in index space; they are turned into shapely
    polygons, repaired with buffer(0) (marching squares can emit rings that
    touch themselves at a saddle point) and then unioned per band so that each
    band is a single clean MultiPolygon with correctly nested holes.
    """
    gen = contour_generator(z=depth, name="serial", fill_type="ChunkCombinedOffsetOffset")
    features = []
    for lo, hi in zip(DEPTH_BANDS[:-1], DEPTH_BANDS[1:]):
        points_l, offsets_l, outer_l = gen.filled(float(lo), float(hi))
        parts = []
        for points, offsets, outer in zip(points_l, offsets_l, outer_l):
            if points is None:
                continue
            # `outer` indexes into `offsets`, which indexes into `points`.
            # Within one polygon, ring 0 is the exterior and the rest are holes.
            for oi in range(len(outer) - 1):
                rings = []
                for ri in range(outer[oi], outer[oi + 1]):
                    s, e = offsets[ri], offsets[ri + 1]
                    ring = _to_lonlat(points[s:e], lons, lats)
                    if len(ring) >= 4:
                        rings.append(ring)
                if not rings:
                    continue
                poly = Polygon(rings[0], rings[1:])
                if not poly.is_valid:
                    poly = poly.buffer(0)
                if poly.is_empty or poly.area < MIN_AREA_DEG2:
                    continue
                parts.append(poly)

        if not parts:
            print(f"      band {lo:>4}-{hi:<6}: empty")
            continue

        merged = unary_union(parts).simplify(SIMPLIFY_DEG, preserve_topology=True)
        if not merged.is_valid:
            merged = merged.buffer(0)
        if merged.is_empty:
            continue

        geom = mapping(merged)
        if geom["type"] == "Polygon":
            geom = {"type": "MultiPolygon", "coordinates": [geom["coordinates"]]}
        features.append(feature(geom, {
            "min_depth": lo,
            "max_depth": None if hi > 10000 else hi,
            "band": f"{lo}-{hi}" if hi <= 10000 else f"{lo}+",
        }))
        print(f"      band {lo:>4}-{hi:<6}: {len(geom['coordinates'])} polygons "
              f"(valid={merged.is_valid})")
    return features


def build_soundings(depth, lons, lats):
    """Chart-style soundings: the shallowest sample in each coarse cell."""
    step_x = max(1, int(round(SOUNDING_STEP_DEG / abs(lons[1] - lons[0]))))
    step_y = max(1, int(round(SOUNDING_STEP_DEG / abs(lats[1] - lats[0]))))
    h, w = depth.shape
    features = []
    for r0 in range(0, h - 1, step_y):
        for c0 in range(0, w - 1, step_x):
            block = depth[r0:r0 + step_y, c0:c0 + step_x]
            if not np.isfinite(block).any():
                continue
            # shallowest = smallest positive-down depth
            idx = np.nanargmin(np.where(np.isfinite(block), block, np.inf))
            br, bc = np.unravel_index(idx, block.shape)
            d = float(block[br, bc])
            if not np.isfinite(d) or d <= 0.2 or d > SOUNDING_MAX_DEPTH:
                continue
            lon = float(lons[c0 + bc])
            lat = float(lats[r0 + br])
            features.append(feature(
                {"type": "Point", "coordinates": [lon, lat]},
                {"depth": round(d, 1),
                 "label": f"{d:.0f}" if d >= 10 else f"{d:.1f}"},
            ))
    return features


def main():
    print("[bathymetry]")
    elev, lons, lats = load_grid()

    # Positive-down depth; land and the coastal fringe become NaN so that
    # contouring stops at the waterline.
    depth = -smooth(elev, passes=1)
    depth = np.where(depth > 0.0, depth, np.nan)

    print("    contours")
    contours = build_contours(depth, lons, lats)
    print("    depth areas")
    areas = build_depth_areas(depth, lons, lats)
    print("    soundings")
    soundings = build_soundings(depth, lons, lats)

    write_geojson(ASSETS / "depth_contours.geojson", contours)
    write_geojson(ASSETS / "depth_areas.geojson", areas)
    write_geojson(ASSETS / "soundings.geojson", soundings)


if __name__ == "__main__":
    main()
