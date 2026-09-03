// Port of the Android client's ChartStyle.kt to the MapLibre GL JS style spec.
//
// The style is assembled in code rather than shipped as style-day.json /
// style-night.json because two things vary at runtime that a static file cannot
// express: the palette swap for night mode, and the safety-depth threshold,
// which depends on the vessel's draft.

const PKG = 'assets/maps/marmaris';

export const SRC = {
  land: 'src-land', inlandWater: 'src-inland-water',
  depthAreas: 'src-depth-areas', depthContours: 'src-depth-contours',
  soundings: 'src-soundings', seamarks: 'src-seamarks', hazards: 'src-hazards',
  areas: 'src-areas', harbours: 'src-harbours', structures: 'src-structures',
  places: 'src-places', roads: 'src-roads',
  track: 'src-track', route: 'src-route', waypoints: 'src-waypoints',
  anchor: 'src-anchor', bearing: 'src-bearing', boat: 'src-boat',
};

export const L = {
  background: 'l-background',
  depthArea: 'l-depth-area', depthUnsafe: 'l-depth-unsafe', depthCaution: 'l-depth-caution',
  depthContour: 'l-depth-contour', depthContourLabel: 'l-depth-contour-label',
  land: 'l-land', landOutline: 'l-land-outline', inlandWater: 'l-inland-water',
  structureFill: 'l-structure-fill', structureLine: 'l-structure-line',
  roads: 'l-roads',
  areaFill: 'l-area-fill', areaLine: 'l-area-line', areaLabel: 'l-area-label',
  soundings: 'l-soundings',
  hazards: 'l-hazards', hazardLabel: 'l-hazard-label',
  lightFlare: 'l-light-flare', seamarks: 'l-seamarks', seamarkLabel: 'l-seamark-label',
  harbours: 'l-harbours', harbourLabel: 'l-harbour-label',
  places: 'l-places',
  track: 'l-track', route: 'l-route', bearing: 'l-bearing',
  waypoints: 'l-waypoints', anchorCircle: 'l-anchor-circle', boat: 'l-boat',
};

/** Layers the chart-object inspector queries on tap, nearest first. */
export const INSPECTABLE = [
  L.waypoints, L.hazards, L.seamarks, L.harbours, L.soundings,
  L.areaFill, L.depthContour, L.places,
];

/** Which user-facing toggle controls which rendering layers. */
export const LAYER_GROUPS = [
  { id: 'depthAreas', label: 'Derinlik alanları', layers: [L.depthArea, L.depthUnsafe, L.depthCaution] },
  { id: 'depthContours', label: 'Derinlik eğrileri', layers: [L.depthContour, L.depthContourLabel] },
  { id: 'soundings', label: 'İskandil rakamları', layers: [L.soundings] },
  { id: 'seamarks', label: 'Şamandıra ve bikonlar', layers: [L.seamarks, L.seamarkLabel] },
  { id: 'lights', label: 'Fenerler', layers: [L.lightFlare] },
  { id: 'hazards', label: 'Tehlikeler', layers: [L.hazards, L.hazardLabel] },
  { id: 'areas', label: 'Demir yerleri / yasak bölgeler', layers: [L.areaFill, L.areaLine, L.areaLabel] },
  { id: 'harbours', label: 'Limanlar', layers: [L.harbours, L.harbourLabel] },
  { id: 'places', label: 'Yer adları', layers: [L.places] },
  { id: 'roads', label: 'Yollar', layers: [L.roads] },
  { id: 'track', label: 'İz kaydı', layers: [L.track] },
];

const geojson = (file) => ({
  type: 'geojson',
  data: `${PKG}/${file}`,
  buffer: 64,
  tolerance: 0.375,
});

const empty = () => ({
  type: 'geojson',
  data: { type: 'FeatureCollection', features: [] },
});

/** `restricted_area` and `military_area` draw red; everything else green. */
const RESTRICTED = ['any',
  ['==', ['get', 'stype'], 'restricted_area'],
  ['==', ['get', 'stype'], 'military_area'],
];

function seamarkIcon() {
  // Buoys pick a coloured sprite; fixed marks fall back to a shape symbol.
  const isOneOf = (...types) => ['any', ...types.map((t) => ['==', ['get', 'stype'], t])];
  return ['case',
    isOneOf('buoy_lateral', 'buoy_cardinal', 'buoy_safe_water', 'buoy_special_purpose',
      'buoy_isolated_danger', 'buoy_installation', 'mooring'),
    ['concat', 'icon-buoy-', ['coalesce', ['get', 'colour'], 'grey']],

    isOneOf('beacon_lateral', 'beacon_cardinal', 'beacon_special_purpose',
      'beacon_isolated_danger', 'beacon_safe_water', 'pile'),
    'icon-beacon',

    isOneOf('light_major', 'landmark'), 'icon-lighthouse',
    ['==', ['get', 'stype'], 'light_minor'], 'icon-beacon',
    ['==', ['get', 'stype'], 'anchorage'], 'icon-anchorage',
    'icon-generic-mark',
  ];
}

export function buildStyle(p, safetyDepth) {
  const areaColour = (restrictedC, normalC) => ['case', RESTRICTED, restrictedC, normalC];

  return {
    version: 8,
    name: 'Boun Sailing Chart',
    glyphs: 'assets/glyphs/{fontstack}/{range}.pbf',
    sources: {
      [SRC.depthAreas]: geojson('depth_areas.geojson'),
      [SRC.depthContours]: geojson('depth_contours.geojson'),
      [SRC.land]: geojson('land.geojson'),
      [SRC.inlandWater]: geojson('inland_water.geojson'),
      [SRC.structures]: geojson('structures.geojson'),
      [SRC.roads]: geojson('roads.geojson'),
      [SRC.areas]: geojson('areas.geojson'),
      [SRC.soundings]: geojson('soundings.geojson'),
      [SRC.hazards]: geojson('hazards.geojson'),
      [SRC.seamarks]: geojson('seamarks.geojson'),
      [SRC.harbours]: geojson('harbours.geojson'),
      [SRC.places]: geojson('places.geojson'),
      [SRC.track]: empty(), [SRC.route]: empty(), [SRC.waypoints]: empty(),
      [SRC.anchor]: empty(), [SRC.bearing]: empty(), [SRC.boat]: empty(),
    },
    layers: [
      { id: L.background, type: 'background', paint: { 'background-color': p.deepWater } },

      // --- depth shading -------------------------------------------------
      {
        id: L.depthArea, type: 'fill', source: SRC.depthAreas,
        paint: {
          'fill-color': ['step', ['to-number', ['get', 'min_depth']],
            p.depth2, 2, p.depth5, 5, p.depth10, 10, p.depth20,
            20, p.depth50, 50, p.depth200, 200, p.deepWater],
          'fill-antialias': false,
        },
      },
      // Safety depth is shown in two tiers rather than one: washing every band
      // that merely touches the threshold turns the whole near-shore red and
      // stops meaning anything. Separating "all of this is too shallow" from
      // "the threshold falls inside this band" keeps the warning worth reacting to.
      {
        id: L.depthUnsafe, type: 'fill', source: SRC.depthAreas,
        filter: ['all', ['has', 'max_depth'],
          ['<=', ['to-number', ['get', 'max_depth']], safetyDepth]],
        paint: { 'fill-color': p.unsafeWater, 'fill-opacity': 0.42, 'fill-antialias': false },
      },
      {
        id: L.depthCaution, type: 'fill', source: SRC.depthAreas,
        filter: ['all',
          ['<', ['to-number', ['get', 'min_depth']], safetyDepth],
          ['any', ['!', ['has', 'max_depth']],
            ['>', ['to-number', ['get', 'max_depth']], safetyDepth]]],
        paint: { 'fill-color': p.cautionWater, 'fill-opacity': 0.26, 'fill-antialias': false },
      },

      // --- contours --------------------------------------------------------
      {
        id: L.depthContour, type: 'line', source: SRC.depthContours, minzoom: 8,
        paint: {
          'line-color': ['case', ['==', ['to-number', ['get', 'major']], 1], p.contourMajor, p.contour],
          'line-width': ['interpolate', ['linear'], ['zoom'], 9, 0.4, 12, 0.8, 15, 1.4, 18, 2.2],
          'line-opacity': ['interpolate', ['linear'], ['zoom'], 8, 0, 9.5, 0.9],
        },
        layout: { 'line-cap': 'round', 'line-join': 'round' },
      },
      {
        id: L.depthContourLabel, type: 'symbol', source: SRC.depthContours, minzoom: 11,
        filter: ['==', ['to-number', ['get', 'major']], 1],
        layout: {
          'text-field': ['get', 'label'], 'text-font': ['Open Sans Regular'], 'text-size': 10,
          'symbol-placement': 'line', 'symbol-spacing': 320, 'text-pitch-alignment': 'viewport',
        },
        paint: { 'text-color': p.contourLabel, 'text-halo-color': p.contourLabelHalo, 'text-halo-width': 1.2 },
      },

      // --- land ------------------------------------------------------------
      { id: L.land, type: 'fill', source: SRC.land, paint: { 'fill-color': p.land, 'fill-antialias': true } },
      {
        id: L.landOutline, type: 'line', source: SRC.land,
        paint: {
          'line-color': p.landOutline,
          'line-width': ['interpolate', ['linear'], ['zoom'], 8, 0.4, 14, 1.0, 18, 1.6],
        },
      },
      {
        id: L.inlandWater, type: 'fill', source: SRC.inlandWater, minzoom: 9,
        paint: { 'fill-color': p.inlandWater, 'fill-outline-color': p.contour },
      },

      // --- coastal structures ------------------------------------------------
      {
        id: L.structureFill, type: 'fill', source: SRC.structures, minzoom: 12,
        paint: { 'fill-color': p.structure, 'fill-opacity': 0.9 },
      },
      {
        id: L.structureLine, type: 'line', source: SRC.structures, minzoom: 12,
        layout: { 'line-cap': 'round' },
        paint: {
          'line-color': p.structure,
          'line-width': ['interpolate', ['linear'], ['zoom'], 12, 1.0, 17, 3.0],
        },
      },

      // --- roads -------------------------------------------------------------
      // Only the major network survives at low zoom, so the coast stays readable.
      {
        id: L.roads, type: 'line', source: SRC.roads, minzoom: 9,
        filter: ['any', ['>=', ['zoom'], 12],
          ['match', ['get', 'class'], ['motorway', 'trunk', 'primary', 'secondary'], true, false]],
        layout: { 'line-cap': 'round', 'line-join': 'round' },
        paint: {
          'line-color': ['match', ['get', 'class'],
            ['motorway', 'trunk', 'primary'], p.road, p.roadMinor],
          'line-width': ['interpolate', ['linear'], ['zoom'], 9, 0.4, 12, 1.0, 15, 2.0, 18, 4.0],
          'line-opacity': 0.75,
        },
      },

      // --- anchorages and restricted areas -------------------------------------
      {
        id: L.areaFill, type: 'fill', source: SRC.areas, minzoom: 10,
        paint: { 'fill-color': areaColour(p.restrictedFill, p.anchorageFill), 'fill-opacity': 0.16 },
      },
      {
        id: L.areaLine, type: 'line', source: SRC.areas, minzoom: 10,
        paint: {
          'line-color': areaColour(p.restrictedLine, p.anchorageLine),
          'line-width': 1.6, 'line-dasharray': [3, 2],
        },
      },
      {
        id: L.areaLabel, type: 'symbol', source: SRC.areas, minzoom: 12,
        filter: ['has', 'name'],
        layout: { 'text-field': ['get', 'name'], 'text-font': ['Open Sans Regular'], 'text-size': 11 },
        paint: {
          'text-color': areaColour(p.restrictedLine, p.anchorageLine),
          'text-halo-color': p.placeHalo, 'text-halo-width': 1.2,
        },
      },

      // --- soundings ------------------------------------------------------------
      {
        id: L.soundings, type: 'symbol', source: SRC.soundings, minzoom: 13,
        layout: {
          'text-field': ['get', 'label'], 'text-font': ['Open Sans Regular'],
          'text-size': ['interpolate', ['linear'], ['zoom'], 13, 9, 17, 12],
          'text-allow-overlap': false, 'text-padding': 3,
        },
        paint: { 'text-color': p.sounding, 'text-halo-color': p.soundingHalo, 'text-halo-width': 1.1 },
      },

      // --- hazards ----------------------------------------------------------------
      {
        id: L.hazards, type: 'symbol', source: SRC.hazards, minzoom: 10,
        layout: {
          'icon-image': ['match', ['get', 'stype'],
            'wreck', 'icon-wreck',
            'rock', 'icon-rock',
            ['underwater_rock', 'reef', 'shoal'], 'icon-rock-awash',
            'icon-obstruction'],
          'icon-size': ['interpolate', ['linear'], ['zoom'], 10, 0.7, 15, 1.0],
          'icon-allow-overlap': true, 'icon-ignore-placement': false,
        },
      },
      {
        id: L.hazardLabel, type: 'symbol', source: SRC.hazards, minzoom: 14,
        filter: ['has', 'name'],
        layout: {
          'text-field': ['get', 'name'], 'text-font': ['Open Sans Regular'], 'text-size': 10,
          'text-anchor': 'top', 'text-offset': [0, 0.9],
        },
        paint: { 'text-color': p.hazard, 'text-halo-color': p.placeHalo, 'text-halo-width': 1.1 },
      },

      // --- lights: the flare sits under the mark so the symbol stays readable ------
      {
        id: L.lightFlare, type: 'symbol', source: SRC.seamarks, minzoom: 9,
        filter: ['has', 'light'],
        layout: {
          'icon-image': 'icon-light',
          'icon-size': ['interpolate', ['linear'], ['zoom'], 9, 0.6, 15, 1.1],
          'icon-allow-overlap': true, 'icon-ignore-placement': true, 'icon-anchor': 'center',
        },
      },

      // --- seamarks ------------------------------------------------------------------
      {
        id: L.seamarks, type: 'symbol', source: SRC.seamarks, minzoom: 9,
        layout: {
          'icon-image': seamarkIcon(),
          'icon-size': ['interpolate', ['linear'], ['zoom'], 9, 0.65, 14, 1.0, 17, 1.2],
          'icon-allow-overlap': true, 'icon-anchor': 'center',
        },
      },
      {
        id: L.seamarkLabel, type: 'symbol', source: SRC.seamarks, minzoom: 14,
        layout: {
          'text-field': ['format',
            ['coalesce', ['get', 'name'], ''], {},
            ['case', ['has', 'light'], ['concat', '\n', ['get', 'light']], ''], { 'font-scale': 0.85 }],
          'text-font': ['Open Sans Regular'], 'text-size': 10,
          'text-anchor': 'left', 'text-offset': [0.9, 0], 'text-optional': true,
        },
        paint: { 'text-color': p.placeLabel, 'text-halo-color': p.placeHalo, 'text-halo-width': 1.2 },
      },

      // --- harbours ---------------------------------------------------------------------
      {
        id: L.harbours, type: 'symbol', source: SRC.harbours, minzoom: 9,
        layout: {
          'icon-image': ['case',
            ['==', ['get', 'stype'], 'landmark'], 'icon-lighthouse',
            ['==', ['get', 'category'], 'marina'], 'icon-marina',
            'icon-harbour'],
          'icon-size': ['interpolate', ['linear'], ['zoom'], 9, 0.6, 14, 1.0],
          'icon-allow-overlap': false,
        },
      },
      {
        id: L.harbourLabel, type: 'symbol', source: SRC.harbours, minzoom: 12,
        filter: ['has', 'name'],
        layout: {
          'text-field': ['get', 'name'], 'text-font': ['Open Sans Bold'], 'text-size': 11,
          'text-anchor': 'top', 'text-offset': [0, 0.9], 'text-optional': true,
        },
        paint: { 'text-color': p.anchorageLine, 'text-halo-color': p.placeHalo, 'text-halo-width': 1.3 },
      },

      // --- place names: progressive disclosure by rank -------------------------------------
      {
        id: L.places, type: 'symbol', source: SRC.places, minzoom: 7,
        filter: ['any',
          ['<', ['to-number', ['get', 'rank']], 2],
          ['all', ['>=', ['zoom'], 11], ['<', ['to-number', ['get', 'rank']], 4]],
          ['>=', ['zoom'], 13]],
        layout: {
          'text-field': ['get', 'name'], 'text-font': ['Open Sans Bold'],
          'text-size': ['interpolate', ['linear'], ['zoom'],
            8, ['step', ['to-number', ['get', 'rank']], 13, 1, 11, 3, 9],
            14, ['step', ['to-number', ['get', 'rank']], 17, 1, 14, 3, 12]],
          'text-padding': 4,
        },
        paint: { 'text-color': p.placeLabel, 'text-halo-color': p.placeHalo, 'text-halo-width': 1.4 },
      },

      // --- overlays, fed at runtime ---------------------------------------------------------
      {
        id: L.track, type: 'line', source: SRC.track,
        layout: { 'line-cap': 'round', 'line-join': 'round' },
        paint: { 'line-color': p.track, 'line-width': 2.5, 'line-opacity': 0.9 },
      },
      {
        id: L.route, type: 'line', source: SRC.route,
        layout: { 'line-cap': 'round', 'line-join': 'round' },
        paint: { 'line-color': p.route, 'line-width': 3, 'line-dasharray': [2, 1] },
      },
      {
        id: L.bearing, type: 'line', source: SRC.bearing,
        paint: { 'line-color': p.bearingLine, 'line-width': 2, 'line-dasharray': [3, 2] },
      },
      {
        id: L.anchorCircle, type: 'line', source: SRC.anchor,
        paint: { 'line-color': p.anchorCircle, 'line-width': 2 },
      },
      {
        id: L.waypoints, type: 'symbol', source: SRC.waypoints,
        layout: {
          'icon-image': 'icon-waypoint', 'icon-allow-overlap': true,
          'text-field': ['get', 'name'], 'text-font': ['Open Sans Regular'], 'text-size': 11,
          'text-anchor': 'top', 'text-offset': [0, 0.8], 'text-optional': true,
        },
        paint: { 'text-color': p.waypoint, 'text-halo-color': p.waypointHalo, 'text-halo-width': 1.3 },
      },
      {
        id: L.boat, type: 'symbol', source: SRC.boat,
        layout: {
          'icon-image': ['case', ['get', 'fix'], 'icon-boat', 'icon-boat-nofix'],
          'icon-rotate': ['get', 'heading'],
          'icon-rotation-alignment': 'map',
          'icon-allow-overlap': true, 'icon-ignore-placement': true,
        },
      },
    ],
  };
}
