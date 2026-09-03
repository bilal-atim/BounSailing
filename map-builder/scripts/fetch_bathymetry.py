"""Download the EMODnet Bathymetry DTM for the Marmaris bbox as a GeoTIFF.

EMODnet publishes the composite DTM through an OGC WCS. Requesting a Lat/Long
subset gives back a float32 grid of *elevation* (negative below sea level) on a
regular 1/960 degree lattice, which is EMODnet's native ~115 m resolution here.

The response is not stored in the repository; it is cached under map-builder/cache.
"""

import sys

from config import BBOX, CACHE, EMODNET_WFS
from common import http_get

COVERAGE = "emodnet__mean"
WCS = "https://ows.emodnet-bathymetry.eu/wcs"
DTM_PATH = CACHE / "emodnet_dtm.tif"


def fetch(force=False):
    if DTM_PATH.exists() and DTM_PATH.stat().st_size > 0 and not force:
        print(f"    cache hit  {DTM_PATH.name} ({DTM_PATH.stat().st_size / 1e6:.1f} MB)")
        return DTM_PATH

    params = {
        "service": "WCS",
        "version": "2.0.1",
        "request": "GetCoverage",
        "coverageId": COVERAGE,
        "format": "image/tiff",
    }
    # `subset` appears twice, so it cannot go through the dict.
    url = (
        f"{WCS}?service=WCS&version=2.0.1&request=GetCoverage&coverageId={COVERAGE}"
        f"&subset=Lat({BBOX['min_lat']},{BBOX['max_lat']})"
        f"&subset=Long({BBOX['min_lon']},{BBOX['max_lon']})"
        f"&format=image/tiff"
    )
    print(f"    GET        EMODnet WCS {COVERAGE}")
    r = http_get(url, timeout=300)
    if not r.content.startswith((b"II", b"MM")):
        raise RuntimeError(f"WCS did not return a TIFF: {r.content[:300]!r}")
    DTM_PATH.write_bytes(r.content)
    print(f"    cached     {DTM_PATH.name} ({DTM_PATH.stat().st_size / 1e6:.1f} MB)")
    return DTM_PATH


if __name__ == "__main__":
    fetch(force="--force" in sys.argv)
