"""Bundle the SDF glyph ranges MapLibre needs to draw labels offline.

MapLibre Native cannot rasterise Latin text itself; it fetches pre-baked signed
distance field glyphs from the style's `glyphs` URL. With no network on the boat
those requests have to resolve inside the APK, so the two ranges that cover
Turkish are downloaded once at build time and shipped as assets.

  0-255    Basic Latin + Latin-1  (covers c-cedilla, o-diaeresis, u-diaeresis)
  256-511  Latin Extended-A       (covers g-breve, dotless i, s-cedilla)
"""

import sys
from urllib.parse import quote

from config import ROOT
from common import http_get

BASE = "https://fonts.openmaptiles.org"
FONTSTACKS = ["Open Sans Regular", "Open Sans Bold"]
RANGES = ["0-255", "256-511"]

GLYPH_DIR = ROOT / "android" / "app" / "src" / "main" / "assets" / "glyphs"


def fetch(force=False):
    print("[glyphs]")
    total = 0
    for stack in FONTSTACKS:
        # MapLibre substitutes {fontstack} into the glyphs URL, and whether the
        # space in "Open Sans Regular" survives as a space or as %20 by the time
        # the asset file source resolves the path is not something the app can
        # control. Both spellings are written so the lookup cannot miss.
        out_dirs = [GLYPH_DIR / stack]
        encoded = quote(stack)
        if encoded != stack:
            out_dirs.append(GLYPH_DIR / encoded)

        for rng in RANGES:
            data = None
            for out_dir in out_dirs:
                out_dir.mkdir(parents=True, exist_ok=True)
                path = out_dir / f"{rng}.pbf"
                if path.exists() and path.stat().st_size > 0 and not force:
                    total += path.stat().st_size
                    print(f"    cache hit  {out_dir.name}/{rng}.pbf")
                    continue
                if data is None:
                    url = f"{BASE}/{quote(stack)}/{rng}.pbf"
                    data = http_get(url, timeout=60).content
                path.write_bytes(data)
                total += len(data)
                print(f"    fetched    {out_dir.name}/{rng}.pbf ({len(data) / 1024:.0f} KB)")
    print(f"    total      {total / 1e6:.2f} MB")
    print("    Open Sans is licensed under Apache 2.0.")


if __name__ == "__main__":
    fetch(force="--force" in sys.argv)
