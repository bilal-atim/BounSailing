"""Build the complete Marmaris offline chart package.

    python3 build_chart.py [--force]

Runs the OSM and bathymetry builders, then writes the manifest that the app
reads to learn the package extent, version, build date and data provenance.
"""

import json
import subprocess
import sys
from datetime import date

import build_bathymetry
import build_osm
from config import (ASSETS, BBOX, DEFAULT_CENTER, DEFAULT_ZOOM, PACKAGE_VERSION,
                    REGION_ID, REGION_NAME)

# Provenance shown in the app's "Chart source" screen. The app renders these as
# unofficial so that public data is never silently treated as an ENC.
SOURCES = [
    {
        "id": "osm",
        "name": "OpenStreetMap",
        "role": "Coastline, land, harbours, coastal roads, place names",
        "licence": "ODbL 1.0",
        "attribution": "© OpenStreetMap contributors",
        "official": False,
    },
    {
        "id": "openseamap",
        "name": "OpenSeaMap / OSM seamark tags",
        "role": "Buoys, beacons, lights, anchorages, restricted areas, hazards",
        "licence": "ODbL 1.0",
        "attribution": "© OpenStreetMap contributors",
        "official": False,
    },
    {
        "id": "emodnet",
        "name": "EMODnet Bathymetry DTM",
        "role": "Depth areas, depth contours, soundings",
        "licence": "CC BY 4.0",
        "attribution": "EMODnet Bathymetry Consortium",
        "official": False,
    },
]

LAYERS = [
    {"id": "land", "file": "land.geojson", "type": "polygon"},
    {"id": "inland_water", "file": "inland_water.geojson", "type": "polygon"},
    {"id": "depth_areas", "file": "depth_areas.geojson", "type": "polygon"},
    {"id": "depth_contours", "file": "depth_contours.geojson", "type": "line"},
    {"id": "soundings", "file": "soundings.geojson", "type": "point"},
    {"id": "seamarks", "file": "seamarks.geojson", "type": "point"},
    {"id": "hazards", "file": "hazards.geojson", "type": "point"},
    {"id": "areas", "file": "areas.geojson", "type": "polygon"},
    {"id": "harbours", "file": "harbours.geojson", "type": "point"},
    {"id": "structures", "file": "structures.geojson", "type": "mixed"},
    {"id": "places", "file": "places.geojson", "type": "point"},
    {"id": "roads", "file": "roads.geojson", "type": "line"},
]


def write_manifest():
    total = 0
    layers = []
    for layer in LAYERS:
        path = ASSETS / layer["file"]
        size = path.stat().st_size if path.exists() else 0
        total += size
        count = 0
        if path.exists():
            count = len(json.loads(path.read_text())["features"])
        layers.append({**layer, "bytes": size, "features": count})

    manifest = {
        "id": REGION_ID,
        "name": REGION_NAME,
        "version": PACKAGE_VERSION,
        "built": date.today().isoformat(),
        "bounds": [BBOX["min_lon"], BBOX["min_lat"], BBOX["max_lon"], BBOX["max_lat"]],
        "center": [DEFAULT_CENTER["lon"], DEFAULT_CENTER["lat"]],
        "defaultZoom": DEFAULT_ZOOM,
        "minZoom": 6,
        "maxZoom": 18,
        "official": False,
        "sources": SOURCES,
        "layers": layers,
        "totalBytes": total,
    }
    path = ASSETS / "manifest.json"
    path.write_text(json.dumps(manifest, indent=2))
    print(f"    wrote      manifest.json  (package {total / 1e6:.2f} MB)")


def main():
    force = "--force" in sys.argv
    if force:
        for name in ("overpass_marmaris.json",):
            print(f"[force] refreshing {name}")

    build_osm.main()
    build_bathymetry.main()
    print("[manifest]")
    write_manifest()

    print()
    subprocess.run([sys.executable, "validate_chart.py"], check=True,
                   cwd=str(__import__("pathlib").Path(__file__).parent))


if __name__ == "__main__":
    main()
