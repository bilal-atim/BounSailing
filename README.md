# Marmaris Nav

An offline marine chartplotter for Android, scoped to Marmaris and the
surrounding coastal waters. Built to the design in [GDD.md](GDD.md).

> **This is not an official chart.** The package is generated from
> OpenStreetMap, OpenSeaMap seamark tags and the EMODnet Bathymetry model. It has
> not been checked by a hydrographic office. Depths, hazards and navigation marks
> may be wrong, missing or out of date. Use it alongside official charts, never
> instead of them.

---

## Layout

```
SeaNavigation/
├── GDD.md                   design document
├── android/                 the Android application
│   └── app/src/main/
│       ├── java/com/bilal/marmarisnav/
│       │   ├── data/        settings, chart manifest
│       │   ├── database/    Room entities and DAOs
│       │   ├── gpx/         GPX 1.1 import and export
│       │   ├── location/    fused location, foreground service
│       │   ├── map/         MapLibre style, symbols, overlays, inspector
│       │   ├── navigation/  geodesy and the navigation engine
│       │   ├── sensors/     compass heading with declination
│       │   ├── service/     notifications
│       │   └── ui/          Compose screens
│       └── assets/
│           ├── glyphs/      SDF glyphs so labels render offline
│           └── maps/marmaris/   the chart package
├── map-builder/             desktop chart pipeline (Python)
│   ├── cache/               raw downloads, not committed
│   └── scripts/
└── tools/                   local JDK 21 used by the Gradle build
```

## Building the APK

The build uses a JDK 21 kept under `tools/`, wired up through
`android/gradle.properties`, so it does not depend on whatever JDK happens to be
on the path.

```sh
cd android
./gradlew assembleRelease      # signed with keystore.jks if signing.properties exists
./gradlew assembleDebug
./gradlew test                 # geodesy and formatting unit tests
```

The release APK lands in `android/app/build/outputs/apk/release/`.

### Signing

`app/build.gradle.kts` picks up a release keystore from Gradle properties. Put
them in `android/signing.properties` (git-ignored) or pass them on the command
line:

```
MARMARISNAV_STORE_FILE=/absolute/path/keystore.jks
MARMARISNAV_STORE_PASSWORD=...
MARMARISNAV_KEY_ALIAS=marmarisnav
MARMARISNAV_KEY_PASSWORD=...
```

Without them the release build is produced unsigned.

## Rebuilding the chart package

```sh
cd map-builder/scripts
python3 -m pip install requests shapely numpy tifffile contourpy
python3 fetch_glyphs.py        # once; SDF glyphs for offline labels
python3 build_chart.py         # OSM + EMODnet -> GeoJSON + manifest, then validates
```

`build_chart.py` writes straight into `android/app/src/main/assets/maps/marmaris/`
and finishes by running `validate_chart.py`. Raw downloads are cached under
`map-builder/cache/`; delete a cache file or pass `--force` to refresh it.

Change the covered area by editing `BBOX` in `scripts/config.py` and re-running.

### What the pipeline does

| Step | Source | Output |
|---|---|---|
| `fetch_osm.py` | Overpass API | raw OSM elements for the bbox |
| `build_osm.py` | the above | land, inland water, seamarks, hazards, areas, harbours, structures, places, roads |
| `fetch_bathymetry.py` | EMODnet Bathymetry WCS | float32 DTM GeoTIFF at ~93 m |
| `build_bathymetry.py` | the above | depth areas, depth contours, soundings |
| `build_chart.py` | all | `manifest.json` and validation |

Land polygons are reconstructed from `natural=coastline`, which OSM stores as
directed open ways with land on the left. The bbox is cut by those ways,
polygonised, and each resulting face is classified by probing a point just to the
left of the coastline where the face touches it. The ways are deliberately never
passed through `linemerge` first, because that is free to reverse a segment and
would silently flip land and water for a whole stretch of coast.

### Validation

`validate_chart.py` checks the package against the EMODnet DTM, which is an
entirely independent source from the OSM coastline:

```
[land]  land/water agrees with EMODnet DTM on 99.92% of 7357 unambiguous samples
[depth] 99.84% of 5077 sea samples fall inside a depth band
[depth] 99.88% of covered samples land in a band that brackets the DTM depth
[soundings] 97.00% match the DTM within tolerance
```

It exits non-zero on failure, so it can gate a rebuild.

## Design notes

**Why GeoJSON assets rather than PMTiles.** The GDD calls for PMTiles. For a
single bay-sized region the whole chart is 7.6 MB of GeoJSON, which MapLibre
tiles internally at load time, and it removes the tippecanoe/pmtiles toolchain
plus the question of PMTiles protocol support in MapLibre Native Android. The
sources are declared in one place (`ChartStyle.chartSources`), so swapping in
tiled sources later does not touch the navigation engine or the UI.

**Why the style is built in code.** Two things must vary at runtime that a static
`style-day.json` / `style-night.json` pair cannot express: the palette swap for
the three themes, and the safety-depth threshold, which depends on the vessel's
draft. Building the style in `ChartStyle` keeps one definition instead of six
near-identical documents.

**Why glyphs are bundled.** MapLibre Native cannot rasterise Latin text itself;
it fetches pre-baked SDF glyphs from the style's `glyphs` URL. With no network on
the boat, the two ranges that cover Turkish (`0-255` and `256-511`) ship inside
the APK. They are written under both `Open Sans Regular` and
`Open%20Sans%20Regular` so the lookup resolves whichever spelling MapLibre
produces when it substitutes `{fontstack}`.

**Why the navigation engine is UI-free.** `NavigationEngine` holds no reference
to MapLibre or to any Android UI type, so the geodesy is unit-testable and an
NMEA input provider can be added later without touching its consumers.

## Deviations from the GDD

- Chart data ships as GeoJSON rather than PMTiles (see above).
- Settings live in DataStore instead of a Room `settings` table; the rest of the
  schema follows GDD section 45.
- The `navigation_sessions` table is not implemented; nothing in V1 reads it.
- A third chart theme, **dusk**, sits between day and night.

## Not implemented (GDD marks these "later")

AIS, NMEA 0183/2000, weather and wind overlays, and automatic routing.
`NavigationEngine` takes position and heading through plain data classes, which
is the seam an `Nmea0183Provider` would plug into.

## Licences

- OpenStreetMap and OpenSeaMap data — ODbL 1.0, © OpenStreetMap contributors
- EMODnet Bathymetry DTM — CC BY 4.0, EMODnet Bathymetry Consortium
- Open Sans — Apache 2.0
- MapLibre Native — BSD 2-Clause
