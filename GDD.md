# Marmaris Marine Navigation
## Game / Product Design Document

**Document:** GDD.md  
**Platform:** Android  
**Target Region:** Marmaris, Muğla, Türkiye and surrounding coastal waters  
**Primary User:** Personal use  
**Product Type:** Offline-first marine navigation / chartplotter application  
**Status:** Initial Technical & Product Design  
**Version:** 0.1

---

# 1. Product Vision

The goal is to build a lightweight, custom Android marine navigation application inspired by products such as Navionics, but scoped specifically for personal use in Marmaris and the surrounding coastal area.

The application should function primarily as an **offline marine chartplotter**.

The core experience is:

> Open the application without internet access, see the Marmaris marine chart, view the boat's live GPS position, monitor speed and course, create waypoints and routes, record tracks, and inspect relevant nautical information such as depth contours, buoys, lights, rocks, wrecks, anchorages, and restricted areas.

The project should intentionally avoid recreating the entire Navionics feature set.

The development strategy is to first create a reliable offline chart and GPS/navigation foundation, then add advanced features such as AIS, weather overlays, NMEA integration, and eventually assisted routing.

---

# 2. Core Product Principles

## 2.1 Offline First

The application's primary navigation functionality must work without an internet connection.

Core offline features:

- Marine map rendering
- GPS position
- Boat marker
- SOG
- COG
- Heading
- Waypoints
- Routes
- Distance and bearing
- ETA
- Track recording
- GPX import/export
- Anchor alarm
- Depth information
- Nautical hazards
- Navigation aids

Internet access should only be required for optional features such as:

- Weather
- Wind
- Wave forecasts
- Updated map packages
- Optional online services

---

## 2.2 Marmaris-First Scope

The application is not intended to initially cover the entire world or Türkiye.

The first map package should focus on:

- Marmaris
- Marmaris Bay
- İçmeler
- Turunç
- Kumlubük
- Amos
- Bozburun Peninsula
- Hisarönü Gulf
- Datça direction as needed
- Nearby bays and anchorages

The exact bounding box can later be expanded.

This limited geographic scope enables:

- Smaller offline packages
- Higher local detail
- Faster iteration
- Easier map validation
- Lower storage requirements
- Better optimization

---

# 3. Target Platform

## Platform

Android only for the initial project.

## Recommended Technology

- Kotlin
- Android Studio
- Jetpack Compose
- MapLibre Native Android
- Room / SQLite
- FusedLocationProviderClient
- Android Foreground Service
- PMTiles
- GeoJSON for small dynamic datasets
- QGIS
- GDAL
- Python preprocessing scripts

Unity is intentionally not recommended for the main application.

The dominant problems in this product are:

- GPS
- Android lifecycle
- background/foreground location
- vector map rendering
- offline map storage
- geospatial calculations
- SQLite persistence
- device sensors
- battery management

These are better aligned with native Android development.

---

# 4. Product Scope

## 4.1 Version 1 Features

| Feature | V1 |
|---|---|
| Offline marine map | Yes |
| GPS location | Yes |
| Boat icon | Yes |
| North Up | Yes |
| Course Up | Yes |
| Heading Up | Yes |
| SOG | Yes |
| COG | Yes |
| Heading | Yes |
| GPS accuracy | Yes |
| Create waypoint | Yes |
| Navigate to waypoint | Yes |
| Distance to waypoint | Yes |
| Bearing to waypoint | Yes |
| ETA | Yes |
| Manual route creation | Yes |
| Route navigation | Yes |
| Track recording | Yes |
| GPX import | Yes |
| GPX export | Yes |
| Depth contours | Yes |
| Depth areas | Yes |
| Nautical seamarks | Yes |
| Buoys | Yes |
| Beacons | Yes |
| Lights | Yes |
| Rocks | Yes |
| Wrecks | Yes |
| Obstructions | Yes |
| Anchor alarm | Yes |
| Night mode | Yes |
| Safety depth | Yes |
| Chart object inspector | Yes |
| AIS | Later |
| NMEA | Later |
| Weather | Later |
| Wind/waves | Later |
| Automatic routing | Later |

---

# 5. System Architecture

High-level architecture:

```text
                     ANDROID APP
                         |
        +----------------+----------------+
        |                |                |
        v                v                v
     MapLibre       Navigation         Local DB
        |             Engine             Room
        |                |
        |                +-- GPS
        |                +-- COG
        |                +-- SOG
        |                +-- Heading
        |                +-- Bearing
        |                +-- Distance
        |                +-- ETA
        |                +-- XTE
        |
        v
 Local Map Package
        |
        +-- basemap.pmtiles
        +-- nautical.pmtiles
        +-- bathymetry.pmtiles
        +-- style-day.json
        +-- style-night.json
```

The application should not require a backend for the initial version.

No user authentication is required.

No cloud database is required.

---

# 6. Map Rendering

## 6.1 Map Engine

Recommended map engine:

**MapLibre Native Android**

Reasons:

- Native Android support
- GPU-based vector rendering
- Open source
- Supports styled vector layers
- Suitable for offline map rendering
- Compatible with PMTiles-based architecture
- Supports dynamic sources and overlays

---

# 7. Offline Map Format

The preferred final map format is:

**PMTiles**

Example application map structure:

```text
/maps/marmaris/
    manifest.json
    basemap.pmtiles
    nautical.pmtiles
    bathymetry.pmtiles
    style-day.json
    style-night.json
    metadata.json
```

Example `manifest.json`:

```json
{
  "name": "Marmaris",
  "version": 1,
  "region": "Marmaris",
  "minZoom": 6,
  "maxZoom": 17
}
```

The application should be designed so that map packages can eventually be updated independently of the APK.

---

# 8. Source Data Strategy

The biggest technical and legal challenge of the project is not rendering a map.

It is obtaining, processing, licensing, validating, and presenting reliable marine chart data.

The application should use a staged data strategy.

---

# 9. Phase 1 Map Data

For the initial prototype:

## 9.1 OpenStreetMap

Use raw OpenStreetMap data for:

- Roads near the coast
- Settlements
- Coast-related POIs
- Harbours
- Marinas
- Land data
- General basemap information

Do not bulk-download tiles from the public `tile.openstreetmap.org` servers for offline use.

Instead:

```text
OSM raw data
    |
    v
Crop Marmaris region
    |
    v
Vector tiles
    |
    v
PMTiles
```

---

## 9.2 OpenSeaMap / OSM Seamarks

Use OpenStreetMap seamark tags for nautical navigation features.

Examples:

```text
seamark:type
seamark:light:colour
seamark:buoy_lateral:category
seamark:beacon_lateral:category
```

Potential features:

- Buoys
- Beacons
- Lights
- Marinas
- Harbour features
- Navigation aids

This data is appropriate for development and secondary-reference use.

It must not automatically be considered an official navigation-grade replacement for official charts.

---

## 9.3 Bathymetry

Use publicly available bathymetry data such as EMODnet when suitable.

Possible source format:

```text
GeoTIFF
```

Preprocessing flow:

```text
GeoTIFF
    |
    v
GDAL
    |
    v
Depth contours
    |
    +-- 1 m
    +-- 2 m
    +-- 5 m
    +-- 10 m
    +-- 20 m
    +-- 50 m
    +-- 100 m
```

Potential outputs:

- Depth raster
- Depth polygons
- Depth contours
- Safety-depth categories

Bathymetry from public datasets should not automatically be treated as official navigation-grade sounding data.

---

# 10. Phase 2 Official Marine Chart Data

For a more serious version, investigate licensed official ENC data.

Primary format:

**IHO S-57 ENC**

Related standards:

- S-57 — electronic hydrographic data transfer
- S-52 — ECDIS chart presentation
- S-63 — ENC protection and licensing

Potential official Turkish source:

- Turkish Naval Forces Office of Navigation, Hydrography and Oceanography
- Authorized ENC distributors
- IC-ENC distribution partners

Important:

Purchasing access to ENC data does not automatically mean the data can legally be decrypted, converted, redistributed, or embedded in a custom application.

Licensing conditions must be checked before incorporating official chart data.

---

# 11. S-57 Processing Strategy

Do not parse S-57 directly on Android in the first version.

Preferred workflow:

```text
S-57 ENC
    |
    v
Desktop preprocessing
    |
    v
GDAL
    |
    v
GeoPackage
    |
    v
MVT / Vector Tiles
    |
    v
PMTiles
    |
    v
Android
```

Advantages:

- Lower Android complexity
- Faster map loading
- Smaller runtime codebase
- Easier styling
- Easier debugging
- Easier data filtering
- Easier versioning

---

# 12. Important S-57 Objects

Do not implement every S-57 feature initially.

Prioritize the most useful marine navigation objects.

```text
DEPARE    Depth Area
DEPCNT    Depth Contour
SOUNDG    Sounding

COALNE    Coastline
LNDARE    Land Area

LIGHTS    Lights

BOYLAT    Lateral Buoy
BOYCAR    Cardinal Buoy
BOYSAW    Safe Water Buoy

BCNLAT    Lateral Beacon
BCNCAR    Cardinal Beacon

WRECKS    Wreck
OBSTRN    Obstruction
UWTROC    Underwater Rock

ACHARE    Anchorage Area
RESARE    Restricted Area
```

Additional object classes can be introduced later.

---

# 13. Desktop Map Builder

A separate desktop-side map builder should exist outside the Android runtime.

Suggested repository structure:

```text
MarineNavigation/
|
+-- android/
|
+-- map-builder/
    |
    +-- source/
    +-- osm/
    +-- enc/
    +-- bathymetry/
    +-- processed/
    +-- scripts/
    +-- output/
```

Core pipeline:

```text
RAW DATA
    |
    v
Crop Marmaris Area
    |
    v
Normalize CRS / attributes
    |
    v
GeoPackage
    |
    v
Vector Tiles
    |
    v
PMTiles
    |
    v
Android Map Package
```

Recommended desktop tools:

- QGIS
- GDAL
- ogr2ogr
- tippecanoe or equivalent vector tile tooling
- Python
- GeoPackage

---

# 14. Map Layers

The chart should use independent vector layers rather than being treated as one large image.

Suggested layers:

```text
land
water

depth-area
depth-contours
soundings

rocks
wrecks
obstructions

restricted-areas
anchorage-areas

buoys
beacons
lights

harbours
marinas

route
waypoints
track

boat
ais-targets
```

Benefits:

- Individual layer visibility
- Better performance
- Zoom-based styling
- Night mode
- Safety-depth highlighting
- Interactive object selection

---

# 15. Map Layer Controls

The user should eventually be able to enable/disable groups such as:

- Depth soundings
- Depth contours
- Seamarks
- Lights
- Hazards
- Anchorages
- Restricted areas
- Track
- AIS
- Weather

Example:

```text
Chart Layers

[x] Depth contours
[x] Soundings
[x] Buoys & beacons
[x] Lights
[x] Hazards
[ ] AIS
[ ] Weather
```

---

# 16. Zoom-Based Information Density

Map content should change based on zoom.

## Zoom 6–9

Display:

- Coastline
- Land
- Water
- Major depth areas
- Major navigation marks
- Main settlements

## Zoom 10–13

Display:

- Depth contours
- Rocks
- Wrecks
- Lights
- Buoys
- Restricted areas
- Anchorages

## Zoom 14+

Display:

- Depth soundings
- Detailed marina information
- Seamark labels
- Light characteristics
- Minor hazards
- Detailed coastal objects

This improves readability and rendering performance.

---

# 17. Location System

Use Android's modern location stack.

Recommended API:

```text
FusedLocationProviderClient
```

Navigation mode should receive continuous location updates.

Initial target:

```text
1 Hz location updates
```

Each location point should contain:

```text
latitude
longitude
altitude if available
accuracy
speed
bearing
timestamp
```

The system should reject or de-prioritize obviously inaccurate GPS readings.

Example rule:

```text
if accuracy > configuredThreshold
    mark GPS as low confidence
```

---

# 18. Location Permissions

Expected Android permissions may include:

```text
ACCESS_FINE_LOCATION
ACCESS_COARSE_LOCATION
FOREGROUND_SERVICE
FOREGROUND_SERVICE_LOCATION
```

Background location permission should be avoided unless genuinely required.

Active navigation should run through a foreground location service.

---

# 19. Navigation Foreground Service

Create:

```text
NavigationService
```

Responsibilities:

- Continue GPS updates
- Continue track recording
- Continue anchor monitoring
- Maintain navigation calculations
- Keep navigation alive when screen is locked
- Provide persistent notification

Example notification:

```text
Navigation Active
6.3 kn · 247°
WP01 · 2.4 NM
```

---

# 20. Navigation Data

The core navigation engine should remain independent from MapLibre.

Input:

```text
Current GPS position
Current speed
Current course
Current heading
Active route
Active waypoint
```

Output:

```text
SOG
COG
Heading
Bearing
Distance
ETA
XTE
Waypoint arrival
Route progress
```

---

# 21. Navigation Calculations

## 21.1 Distance

Calculate geographic distance between:

```text
Boat Position
     |
     v
Waypoint
```

Display primarily in nautical miles.

Example:

```text
2.34 NM
```

---

## 21.2 Bearing

Calculate initial bearing to active waypoint.

Example:

```text
247°
```

---

## 21.3 ETA

Example:

```text
Distance: 2.4 NM
SOG:      6.0 kn

ETA: 24 min
```

ETA should gracefully handle very low speed.

---

## 21.4 Cross Track Error

Calculate XTE for route legs.

Example:

```text
XTE: 0.08 NM Port
```

XTE is particularly useful when following a planned route.

---

# 22. Heading vs COG

Do not treat Heading and COG as the same value.

## Heading

Direction the bow/device is facing.

Possible source:

- Android rotation vector sensor
- External NMEA compass later

## COG

Direction the vessel is actually moving over the ground.

Possible source:

- GPS

Suggested logic:

```text
if SOG > 2 kn
    use COG for Course Up
else
    prefer heading
```

The exact threshold should be configurable after real-world testing.

---

# 23. Magnetic vs True North

Phone sensors may report magnetic heading.

Navigation charts generally need true-north-aware presentation.

The application should support magnetic declination correction.

Values:

```text
Magnetic Heading
Magnetic Declination
True Heading
```

Where appropriate:

```text
True Heading = Magnetic Heading + Declination
```

with normalization to 0–359°.

---

# 24. Map Orientation Modes

The application must support three modes.

## 24.1 North Up

North remains fixed at the top.

```text
North ↑
```

## 24.2 Course Up

The vessel's COG points upward.

```text
COG ↑
```

## 24.3 Heading Up

The vessel's heading points upward.

```text
Heading ↑
```

---

# 25. Look-Ahead Camera

In Course Up and Heading Up modes, the boat should not remain exactly in the screen center.

Preferred layout:

```text
       More map ahead





            ▲
           Boat


```

This creates more usable navigation space in front of the vessel.

---

# 26. Main Navigation Screen

Suggested layout:

```text
+--------------------------------+
| 6.4 kn      247°       GPS 3m |
+--------------------------------+
|                                |
|                                |
|               ▲                |
|              BOAT              |
|                                |
|                       WP01 ●    |
|                                |
|                                |
+--------------------------------+
| WP01     2.4 NM      ETA 0:24 |
+--------------------------------+
| ☰      +WP      ROUTE      ◎  |
+--------------------------------+
```

The map should occupy as much screen space as possible.

Primary information should remain glanceable.

---

# 27. Waypoints

A waypoint represents a user-defined geographic position.

Creation options:

- Long press on map
- Current position
- Enter coordinates manually
- Import from GPX

Example interaction:

```text
Long Press
    |
    v
Create Waypoint
    |
    v
Name
Latitude
Longitude
Notes
Icon
```

Suggested model:

```text
Waypoint
--------
id
name
latitude
longitude
icon
notes
createdAt
updatedAt
```

Storage:

**Room / SQLite**

---

# 28. Direct-To Navigation

The user should be able to select any waypoint and choose:

```text
Navigate To
```

The navigation UI should then display:

- waypoint name
- bearing
- distance
- ETA
- relative direction
- current SOG
- current COG

---

# 29. Routes

A route is an ordered collection of waypoints.

Example:

```text
Route
 |
 +-- WP1
 +-- WP2
 +-- WP3
 +-- WP4
```

Rendered:

```text
WP1 ----- WP2
           \
            \
             WP3 ----- WP4
```

Initial routes should be manually authored.

Do not implement auto-routing before reliable depth and hazard data is available.

---

# 30. Route Navigation

Route navigation states:

```text
Inactive
Active
Paused
Completed
```

For each route leg:

```text
Previous Waypoint
        |
        v
Current Target Waypoint
```

Display:

- current leg
- distance remaining
- bearing
- XTE
- total distance remaining
- ETA

Automatic waypoint advancement can occur when entering a configurable arrival radius.

---

# 31. Track Recording

When enabled, record GPS positions over time.

Suggested model:

```text
TrackPoint
----------
id
trackId
latitude
longitude
timestamp
speed
course
accuracy
```

Rendered as a polyline:

```text
Start ●~~~~~~~~~~~~~~~▲ Boat
```

Track functions:

- Start recording
- Pause recording
- Resume
- Stop
- Rename
- Delete
- Export GPX

---

# 32. GPX Support

Support:

- GPX waypoint import
- GPX route import
- GPX track import
- GPX export

This provides interoperability with:

- Other marine apps
- Desktop navigation tools
- GPS tools
- Route planners

---

# 33. Anchor Alarm

Anchor alarm should be considered a high-value V1 feature.

Workflow:

```text
Drop Anchor
    |
    v
Save current GPS position
    |
    v
Choose radius
    |
    v
Monitor vessel position
```

Example:

```text
Anchor Position
Radius = 50 m
```

Trigger:

```text
distance(boat, anchorPosition) > radius
```

Result:

- visual alarm
- audio alarm
- vibration
- foreground notification

Optional additions later:

- GPS accuracy compensation
- delayed confirmation
- anchor swing visualization

---

# 34. Safety Depth

The user should be able to define vessel draft and safety margin.

Example:

```text
Boat Draft:      1.8 m
Safety Margin:   1.2 m

Safety Depth:    3.0 m
```

Derived:

```text
Safety Depth = Draft + Margin
```

Potential map styling:

```text
0–3 m     Dangerous / shallow
3–5 m     Caution
5m+       Normal
```

This styling should be clearly described as informational unless the underlying depth source is suitable for navigation.

---

# 35. Chart Object Inspector

The user should be able to tap chart objects.

Example buoy:

```text
Lateral Buoy

Color: Red
Category: Port
Light: Fl R 4s
```

Example wreck:

```text
Wreck

Depth: 4.2 m
Category: Dangerous
```

Example light:

```text
Light

Character: Fl
Color: White
Period: 5s
Range: 8 NM
```

The inspector should show only fields that are available in the source dataset.

---

# 36. Night Mode

Two separate chart themes should be supported:

```text
style-day.json
style-night.json
```

Night mode goals:

- Minimize eye strain
- Avoid bright backgrounds
- Preserve important navigation colors
- Keep essential text readable
- Reduce overall perceived brightness

Suggested design:

- Dark water
- Very dark land
- Low-luminance labels
- Dimmed UI
- Red/orange secondary UI accents

The exact palette should be validated during real night use.

---

# 37. AIS — Future Phase

AIS is not required for V1.

Potential data sources:

- Wi-Fi
- TCP
- UDP
- Bluetooth
- USB
- NMEA gateway

Common AIS sentences:

```text
!AIVDM,...
!AIVDO,...
```

AIS target model:

```text
AISTarget
---------
mmsi
name
latitude
longitude
sog
cog
heading
shipType
timestamp
```

Map:

```text
         ▲ YOU


  ▶ vessel        ▲ tanker


                  ◀ vessel
```

Future calculations:

- CPA
- TCPA
- Collision risk
- Lost target timeout

---

# 38. NMEA — Future Phase

Future support can include NMEA 0183 and potentially NMEA 2000 through external gateways.

Possible fields:

- GPS
- Heading
- Depth
- Wind
- AIS
- Water speed
- Temperature

Keep NMEA integration isolated behind an input-provider interface.

Example architecture:

```text
NavigationInputProvider
    |
    +-- AndroidGpsProvider
    +-- Nmea0183Provider
    +-- FutureNmea2000Provider
```

---

# 39. Weather — Future Phase

Weather should remain optional and separate from the core offline chart.

Possible overlays:

- Wind
- Waves
- Current
- Rain
- Pressure

Behavior:

```text
Internet available
    -> Fetch updated data

Internet unavailable
    -> Show last cached data
```

Weather data must always show timestamp / age.

---

# 40. Automatic Routing — Future Phase

Automatic marine routing should not be an early feature.

Marine routing differs fundamentally from road routing because there is no simple road graph.

A future routing system can use a navigability grid.

Example resolution:

```text
25 m x 25 m
```

Each grid cell receives a classification:

```text
LAND         Blocked
ROCK         Blocked
WRECK        Blocked / conditional
SHALLOW      Blocked / high cost
DEEP         Normal
RESTRICTED   Blocked
UNKNOWN      High penalty
```

---

# 41. Auto-Route Cost Model

Potential cost:

```text
cost =
    distance
    + shallowPenalty
    + coastlinePenalty
    + obstaclePenalty
    + restrictedAreaPenalty
    + turnPenalty
```

Pathfinding algorithm:

```text
A*
```

Alternative algorithms may later be evaluated.

---

# 42. Draft-Aware Routing

Example:

```text
Draft = 1.8 m
Safety Margin = 1.2 m

Minimum Safe Depth = 3.0 m
```

Then:

```text
depth < 3 m
    -> blocked
```

Possible penalty model:

```text
3–4 m    cost x10
4–5 m    cost x3
5m+      cost x1
```

This feature must not be presented as safety-critical unless the underlying chart data, update process, and navigation logic are sufficiently validated.

---

# 43. Data Reliability Levels

The application should internally distinguish data sources.

Example:

```text
Source Type
-----------
OSM
OpenSeaMap
Public Bathymetry
Official ENC
User Data
```

Potential visual or metadata indicator:

```text
Chart Source:
Public / Unofficial
```

or:

```text
Chart Source:
Licensed ENC
```

This prevents unofficial and official information from being silently treated as equivalent.

---

# 44. Safety Positioning

The application should be treated as a custom navigation aid.

During the prototype stage it should not be treated as the sole source of navigation information.

Particular caution is required for:

- depth
- underwater hazards
- restricted zones
- rapidly changing navigation marks
- chart update dates
- GPS accuracy
- auto-routing

The application should expose map package date/version where practical.

---

# 45. Local Database

Recommended:

**Room**

Suggested tables:

```text
waypoints
routes
route_waypoints
tracks
track_points
settings
map_packages
navigation_sessions
```

Potential future tables:

```text
ais_favorites
weather_cache
nmea_devices
anchor_sessions
```

---

# 46. Suggested Android Package Structure

```text
com.yourname.marmarisnav

data/
    map/
    local/
    model/

database/
    AppDatabase
    WaypointDao
    RouteDao
    TrackDao
    SettingsDao

location/
    LocationProvider
    NavigationService

sensors/
    HeadingProvider

map/
    MapController
    MapStyleManager
    LayerManager
    ChartObjectInspector

navigation/
    NavigationEngine
    BearingCalculator
    DistanceCalculator
    RouteManager
    TrackRecorder
    AnchorAlarm

gpx/
    GpxImporter
    GpxExporter

ui/
    chart/
    waypoint/
    route/
    track/
    settings/
    layers/

service/
    NavigationNotificationManager
```

---

# 47. Navigation Engine Separation

Do not place navigation logic directly inside the map UI.

Preferred architecture:

```text
GPS / Sensors
     |
     v
Navigation Engine
     |
     +--> Navigation State
     |
     +--> Route State
     |
     +--> Track Recorder
     |
     +--> Anchor Alarm
     |
     v
UI / MapLibre
```

This provides:

- testability
- easier debugging
- cleaner architecture
- later NMEA support
- independent route calculation

---

# 48. Navigation State Example

```kotlin
data class NavigationState(
    val latitude: Double,
    val longitude: Double,
    val sogKnots: Double,
    val cogDegrees: Double?,
    val headingDegrees: Double?,
    val gpsAccuracyMeters: Float?,
    val activeWaypointId: Long?,
    val distanceToWaypointNm: Double?,
    val bearingToWaypointDegrees: Double?,
    val etaSeconds: Long?,
    val xteNm: Double?
)
```

Exact structure may change during implementation.

---

# 49. Performance Goals

The application should remain smooth on a modern Android device while displaying the Marmaris chart.

Initial targets:

```text
Map rendering: 60 FPS where practical
GPS updates: 1 Hz
Navigation calculations: <= 1 Hz initially
Track persistence: batched where possible
Offline map startup: fast
No network dependency for chart display
```

Avoid unnecessary UI recomposition on every map frame.

---

# 50. Battery Strategy

Navigation can be battery-intensive.

Battery-saving principles:

- Avoid overly frequent GPS updates
- Avoid unnecessary sensor sampling
- Stop heading sensor when not needed
- Batch database writes where practical
- Avoid constant network requests
- Suspend weather updates in background
- Keep map rendering independent from GPS frequency

Possible profiles later:

```text
High Accuracy
Balanced
Anchor Watch
```

---

# 51. First Development Milestone

Goal:

> Show a fully offline Marmaris map with live boat position.

Tasks:

```text
1. Create Android Studio project
2. Add MapLibre
3. Prepare Marmaris OSM source data
4. Crop Marmaris region
5. Generate vector tiles
6. Create PMTiles package
7. Bundle/load PMTiles
8. Render map offline
9. Request GPS permission
10. Read live position
11. Draw boat marker
12. Display GPS accuracy
13. Display SOG
14. Display COG
15. Add North Up
16. Add Course Up
```

Success criteria:

The user can go offline, launch the app, and see their actual position moving on the Marmaris map.

---

# 52. Sprint 1 — Offline Map + GPS

## Scope

- Android project
- MapLibre
- Offline PMTiles
- Marmaris basemap
- GPS permission
- Live position
- Boat marker
- GPS accuracy
- SOG
- COG
- North Up
- Course Up
- Heading provider prototype

## Deliverable

A basic offline moving-map application.

---

# 53. Sprint 2 — Navigation

## Scope

- Waypoints
- Direct-to waypoint
- Distance
- Bearing
- ETA
- Routes
- Route legs
- XTE
- Track recording
- GPX import/export

## Deliverable

A functional manual-navigation application.

---

# 54. Sprint 3 — Nautical Chart Layers

## Scope

- Depth areas
- Depth contours
- Soundings
- Rocks
- Wrecks
- Obstructions
- Buoys
- Beacons
- Lights
- Anchorages
- Restricted areas
- Layer visibility controls

## Deliverable

A map that visually behaves like a basic marine chart.

---

# 55. Sprint 4 — Marine Utility Features

## Scope

- Anchor alarm
- Safety depth
- Night mode
- Chart object inspector
- Map package information
- Navigation settings
- Look-ahead camera
- Heading Up mode

## Deliverable

A usable personal marine navigation tool.

---

# 56. Sprint 5 — External Data

Optional.

## Scope

- AIS
- NMEA
- Weather
- Wind
- Waves
- Currents

## Deliverable

Enhanced situational awareness.

---

# 57. Sprint 6 — Assisted Routing

Optional.

## Scope

- Navigability grid
- Depth constraints
- Hazard constraints
- A*
- Coastline clearance
- Restricted area avoidance
- Route simplification
- Draft-aware routing

## Deliverable

Experimental assisted route generation.

---

# 58. Estimated Development Time

Approximate effort for one experienced developer:

| Phase | Estimate |
|---|---:|
| MapLibre + GPS prototype | 2–4 days |
| Offline Marmaris map package | 3–7 days |
| Waypoint / route / navigation | 4–7 days |
| Track + GPX + anchor | 2–4 days |
| Nautical layers | 5–10 days |
| UI polish | 3–7 days |
| AIS / NMEA | 5–10 days |
| Auto-routing | 1–3+ weeks |

A useful V1 can realistically be created in approximately:

**2–4 weeks**

A polished mini-Navionics-style product can become a multi-month project.

---

# 59. Recommended Final Technology Stack

## Android

```text
Kotlin
Jetpack Compose
MapLibre Native
Room
FusedLocationProviderClient
Foreground Location Service
Android Sensor APIs
```

## Map

```text
PMTiles
Vector Tiles
GeoJSON
GeoPackage
```

## Desktop Preprocessing

```text
QGIS
GDAL
Python
ogr2ogr
Vector tile tooling
```

## Initial Data

```text
OpenStreetMap
OpenSeaMap seamarks
Public bathymetry such as EMODnet
```

## Advanced Data

```text
Licensed S-57 ENC
Official Turkish hydrographic data
```

---

# 60. Recommended Development Order

Do not start with:

- AIS
- weather
- S-63
- full S-52 renderer
- automatic route planning
- global map coverage

Start with:

```text
Offline Marmaris Map
        |
        v
GPS
        |
        v
Boat Marker
        |
        v
SOG / COG
        |
        v
Waypoint
        |
        v
Route
        |
        v
Track
        |
        v
Nautical Layers
        |
        v
Anchor Alarm
        |
        v
Safety Depth
        |
        v
AIS / NMEA
        |
        v
Auto Routing
```

---

# 61. Immediate Next Technical Task

The first concrete technical objective should be:

> Produce a real `marmaris.pmtiles` file and render it offline inside a minimal Android application.

Implementation sequence:

```text
Android Studio
    |
    v
Kotlin project
    |
    v
MapLibre
    |
    v
Marmaris OSM extract
    |
    v
Vector tiles
    |
    v
PMTiles
    |
    v
Offline render
    |
    v
GPS
    |
    v
Boat marker
```

This should be treated as the first proof-of-concept.

---

# 62. Definition of V1 Done

V1 is considered usable when the following scenario works:

1. User installs the APK.
2. User places the Android phone/tablet on the boat.
3. Internet is disabled.
4. Application launches.
5. Marmaris marine map appears.
6. Boat position is visible.
7. GPS accuracy is visible.
8. SOG and COG update while moving.
9. User can switch North Up / Course Up / Heading Up.
10. User can create a waypoint.
11. User can navigate toward the waypoint.
12. Distance, bearing, ETA, and XTE are shown.
13. User can create a manual multi-point route.
14. User can record the trip.
15. Track can be exported as GPX.
16. Depth contours are visible.
17. Buoys, lights, rocks, wrecks, and restricted areas can be displayed.
18. Anchor alarm works while the application is running as an active foreground navigation service.
19. Day and night chart styles are available.
20. Core navigation does not depend on internet connectivity.

---

# 63. Future Expansion

Possible future features:

- Turkish coastline map packages
- Mediterranean map packages
- Downloadable regional charts
- Chart update manager
- Sonar/depth integration
- NMEA 2000 gateway support
- AIS alarms
- CPA/TCPA
- MOB button
- Weather routing
- Tide/current information
- Route history
- Favorite anchorages
- User POIs
- Photos and notes
- Depth shading presets
- Tablet-specific UI
- External GPS support
- Bluetooth GPS
- Compass calibration UI
- MOB emergency navigation
- Backup/export of all user data

---

# 64. Key Project Decision Summary

The recommended implementation strategy is:

```text
Native Android
+
Kotlin
+
MapLibre
+
Offline PMTiles
+
Room
+
Android GPS
+
Desktop map preprocessing
```

The initial chart data should use open datasets for development.

Official licensed ENC data should only be introduced after the application's core navigation functionality is stable and the relevant licensing terms are understood.

The application should remain modular so that the underlying map source can later be replaced without rewriting the navigation engine.

---

# 65. Final Product Goal

The long-term target is not to reproduce every Navionics feature.

The target is to create a focused personal chartplotter optimized for Marmaris:

```text
Reliable Offline Map
        +
Accurate GPS Display
        +
Simple Navigation
        +
Useful Marine Layers
        +
Personal Waypoints
        +
Routes
        +
Tracks
        +
Anchor Alarm
        +
Optional AIS / Weather
```

The most important foundation is the combination of:

**reliable map data + offline rendering + clean navigation architecture.**

Everything else should be layered on top of that foundation.
