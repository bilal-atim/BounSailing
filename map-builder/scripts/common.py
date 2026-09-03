"""Small helpers shared by the fetch and build scripts."""

import json
import time
from pathlib import Path

import requests

from config import COORD_PRECISION

# Overpass rejects requests without an identifying User-Agent with a bare 406.
HEADERS = {"User-Agent": "MarmarisNav-mapbuilder/1.0 (personal offline chart builder)"}


def http_get(url, params=None, timeout=180, retries=3, backoff=5, **kwargs):
    last = None
    for attempt in range(retries):
        try:
            r = requests.get(url, params=params, timeout=timeout, headers=HEADERS, **kwargs)
            r.raise_for_status()
            return r
        except Exception as exc:  # noqa: BLE001 - retry on anything transient
            last = exc
            if attempt < retries - 1:
                wait = backoff * (attempt + 1)
                print(f"    retry {attempt + 1}/{retries - 1} after {wait}s ({exc})")
                time.sleep(wait)
    raise RuntimeError(f"GET {url} failed after {retries} attempts: {last}")


def http_post(url, data, timeout=300, retries=3, backoff=10):
    last = None
    for attempt in range(retries):
        try:
            r = requests.post(url, data=data, timeout=timeout, headers=HEADERS)
            r.raise_for_status()
            return r
        except Exception as exc:  # noqa: BLE001
            last = exc
            if attempt < retries - 1:
                wait = backoff * (attempt + 1)
                print(f"    retry {attempt + 1}/{retries - 1} after {wait}s ({exc})")
                time.sleep(wait)
    raise RuntimeError(f"POST {url} failed after {retries} attempts: {last}")


def cached(path: Path, producer):
    """Return JSON at `path`, calling `producer()` to create it if missing."""
    if path.exists() and path.stat().st_size > 0:
        print(f"    cache hit  {path.name} ({path.stat().st_size / 1e6:.1f} MB)")
        return json.loads(path.read_text())
    data = producer()
    path.write_text(json.dumps(data))
    print(f"    cached     {path.name} ({path.stat().st_size / 1e6:.1f} MB)")
    return data


def _round_coords(obj, nd):
    if isinstance(obj, float):
        return round(obj, nd)
    if isinstance(obj, int):
        return obj
    if isinstance(obj, list):
        return [_round_coords(v, nd) for v in obj]
    return obj


def feature(geometry, properties):
    return {"type": "Feature", "geometry": geometry, "properties": properties}


def write_geojson(path: Path, features, precision=COORD_PRECISION):
    """Write a compact FeatureCollection, dropping empty properties and geometry."""
    clean = []
    for f in features:
        geom = f.get("geometry")
        if not geom or not geom.get("coordinates"):
            continue
        geom = dict(geom)
        geom["coordinates"] = _round_coords(geom["coordinates"], precision)
        props = {k: v for k, v in (f.get("properties") or {}).items()
                 if v is not None and v != ""}
        clean.append({"type": "Feature", "geometry": geom, "properties": props})

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(
        {"type": "FeatureCollection", "features": clean},
        separators=(",", ":"), ensure_ascii=False,
    ))
    print(f"    wrote      {path.name:28s} {len(clean):6d} features  "
          f"{path.stat().st_size / 1e6:6.2f} MB")
    return len(clean)
