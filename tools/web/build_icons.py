#!/usr/bin/env python3
"""
Builds the app icons from the club logo.

The logo is a boat mark above a two-line wordmark. Only the mark is used: at
192 px the wordmark is a grey smear, and an icon has to read at a glance on a
home screen full of other icons.

Icons are drawn on an opaque background on purpose. iOS composites a home-screen
icon over black, so a transparent PNG would put the dark teal mark on a black
field and lose most of it.

    python3 tools/web/build_icons.py
"""
import os
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow gerekli:  python3 -m pip install --break-system-packages Pillow")

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LOGO = os.path.join(ROOT, "buyelken_logo.png")
OUT = os.path.join(ROOT, "web", "icons")

BACKGROUND = (255, 255, 255, 255)

# The mark sits above the wordmark; the split is the blank band between them.
MARK_BOTTOM_FRACTION = 0.70

# How much of the icon the mark fills. The mark is wider than it is tall, so
# these are generous: fitting the long side is what decides the size. A maskable
# icon may be cropped to a circle by the launcher, so its mark is kept well
# inside the 80% safe zone.
SCALE = {"normal": 0.88, "maskable": 0.68}


def mark(logo):
    """The boat mark alone, trimmed to its own bounds."""
    cropped = logo.crop((0, 0, logo.width, int(logo.height * MARK_BOTTOM_FRACTION)))
    box = cropped.getchannel("A").getbbox()
    if box is None:
        sys.exit("logoda saydam olmayan piksel bulunamadi")
    return cropped.crop(box)


def icon(art, size, scale):
    canvas = Image.new("RGBA", (size, size), BACKGROUND)
    target = int(size * scale)
    ratio = min(target / art.width, target / art.height)
    art = art.resize(
        (max(1, round(art.width * ratio)), max(1, round(art.height * ratio))),
        Image.LANCZOS,
    )
    canvas.alpha_composite(art, ((size - art.width) // 2, (size - art.height) // 2))
    return canvas.convert("RGB")


def main():
    if not os.path.exists(LOGO):
        sys.exit(f"logo yok: {LOGO}")
    os.makedirs(OUT, exist_ok=True)

    art = mark(Image.open(LOGO).convert("RGBA"))
    print(f"marka kirpildi: {art.width}x{art.height}")

    targets = [
        ("icon-192.png", 192, "normal"),
        ("icon-512.png", 512, "normal"),
        ("icon-180.png", 180, "normal"),        # apple-touch-icon
        ("icon-512-maskable.png", 512, "maskable"),
    ]
    for name, size, kind in targets:
        path = os.path.join(OUT, name)
        icon(art, size, SCALE[kind]).save(path, "PNG", optimize=True)
        print(f"  {name:24} {size}x{size}  {os.path.getsize(path) / 1024:5.1f} KB")


if __name__ == "__main__":
    main()
