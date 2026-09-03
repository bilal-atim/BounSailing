"""Download the raw OpenStreetMap data needed for the Marmaris chart.

Everything is pulled from Overpass in a single query with `out geom`, so ways and
relations already carry their coordinates and no second node-resolution pass is
needed. The raw response is cached under map-builder/cache so that re-running the
builder does not hammer the public Overpass instances.
"""

import json
import sys

from config import BBOX, CACHE, OVERPASS_ENDPOINTS
from common import http_post

RAW_PATH = CACHE / "overpass_marmaris.json"

# Overpass bbox order is south,west,north,east.
BB = f"{BBOX['min_lat']},{BBOX['min_lon']},{BBOX['max_lat']},{BBOX['max_lon']}"

QUERY = f"""
[out:json][timeout:900];
(
  // --- coastline and land/water ---------------------------------------
  way["natural"="coastline"]({BB});
  nwr["natural"="water"]({BB});
  nwr["natural"="wetland"]({BB});
  nwr["landuse"="reservoir"]({BB});

  // --- everything tagged as a seamark ---------------------------------
  nwr["seamark:type"]({BB});

  // --- harbours, marinas, coastal infrastructure ----------------------
  nwr["leisure"="marina"]({BB});
  nwr["harbour"]({BB});
  nwr["amenity"="ferry_terminal"]({BB});
  nwr["man_made"="lighthouse"]({BB});
  nwr["man_made"="pier"]({BB});
  nwr["man_made"="breakwater"]({BB});
  nwr["man_made"="groyne"]({BB});
  nwr["waterway"="dock"]({BB});

  // --- hazards ---------------------------------------------------------
  nwr["natural"="reef"]({BB});
  nwr["natural"="shoal"]({BB});
  nwr["historic"="wreck"]({BB});
  nwr["natural"="rock"]({BB});
  nwr["natural"="bare_rock"]({BB});

  // --- named places and bays ------------------------------------------
  node["place"~"^(city|town|village|hamlet|suburb|neighbourhood|island|islet|locality)$"]({BB});
  node["natural"~"^(bay|cape|peak|beach|strait)$"]({BB});
  nwr["natural"="bay"]({BB});

  // --- coastal road network (kept to the classified network only) ------
  way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified)$"]({BB});
);
out geom qt;
"""


def fetch(force=False):
    if RAW_PATH.exists() and RAW_PATH.stat().st_size > 0 and not force:
        print(f"    cache hit  {RAW_PATH.name} ({RAW_PATH.stat().st_size / 1e6:.1f} MB)")
        return json.loads(RAW_PATH.read_text())

    last = None
    for endpoint in OVERPASS_ENDPOINTS:
        print(f"    POST       {endpoint}")
        try:
            r = http_post(endpoint, {"data": QUERY}, timeout=900, retries=2)
            data = r.json()
            if "elements" not in data:
                raise RuntimeError(f"unexpected Overpass payload: {list(data)[:5]}")
            RAW_PATH.write_text(json.dumps(data))
            print(f"    cached     {RAW_PATH.name} "
                  f"({RAW_PATH.stat().st_size / 1e6:.1f} MB, "
                  f"{len(data['elements'])} elements)")
            return data
        except Exception as exc:  # noqa: BLE001 - try the next mirror
            print(f"    failed     {exc}")
            last = exc
    raise RuntimeError(f"all Overpass endpoints failed: {last}")


if __name__ == "__main__":
    fetch(force="--force" in sys.argv)
