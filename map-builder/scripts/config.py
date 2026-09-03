"""Shared configuration for the Marmaris chart builder."""

from pathlib import Path

# ---------------------------------------------------------------------------
# Region
# ---------------------------------------------------------------------------
# Covers Marmaris bay, Icmeler, Turunc, Kumlubuk, Amos, the Bozburun peninsula,
# the Hisaronu gulf and the eastern approaches to Datca.
BBOX = {
    "min_lon": 27.55,
    "min_lat": 36.35,
    "max_lon": 28.65,
    "max_lat": 37.10,
}

REGION_NAME = "Marmaris"
REGION_ID = "marmaris"
PACKAGE_VERSION = 1

# Centre used as the app's default camera target (Marmaris bay entrance).
DEFAULT_CENTER = {"lat": 36.8360, "lon": 28.2560}
DEFAULT_ZOOM = 11.5

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------
ROOT = Path(__file__).resolve().parents[2]
BUILDER = ROOT / "map-builder"
CACHE = BUILDER / "cache"
OUTPUT = BUILDER / "output"
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets" / "maps" / REGION_ID

for _p in (CACHE, OUTPUT, ASSETS):
    _p.mkdir(parents=True, exist_ok=True)

# ---------------------------------------------------------------------------
# Services
# ---------------------------------------------------------------------------
OVERPASS_ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
]

EMODNET_WFS = "https://ows.emodnet-bathymetry.eu/wfs"

# Depth contours to keep, in metres. EMODnet publishes a fixed contour set; we
# retain the shallow-water detail that matters for a yacht plus a few deep-water
# lines for orientation.
CONTOUR_DEPTHS = [
    2, 5, 10, 15, 20, 25, 30, 40, 50, 75,
    100, 150, 200, 300, 400, 500, 750, 1000,
]

# Coordinate precision in the emitted GeoJSON. 5 decimals is ~1.1 m at this
# latitude, far finer than either the source data or a phone GPS.
COORD_PRECISION = 5
