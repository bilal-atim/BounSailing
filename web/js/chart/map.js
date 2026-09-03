// The Harita tab: the offline Marmaris chart plus a live GPS fix.
//
// The chart package is the same GeoJSON the Android client ships; MapLibre GL JS
// reads it unchanged, so both clients render from one source of truth.

import { LAYER_GROUPS, INSPECTABLE, L, SRC, buildStyle } from './style.js';
import { installIcons } from './icons.js';
import { PALETTES, THEMES, THEME_LABELS } from './palette.js';
import {
  MS_TO_KNOTS, compassPoint, formatBearing, formatDepth, formatDistanceNm,
  formatLatitude, formatLongitude, formatSpeed, initialBearing, distanceMeters,
} from './nav.js';

const STORE = 'bounsailing.chart';
const THEME_GLYPH = { day: '☀', dusk: '◐', night: '☾' };

const point = (lon, lat, props = {}) => ({
  type: 'Feature', geometry: { type: 'Point', coordinates: [lon, lat] }, properties: props,
});

export class ChartView {
  constructor(deps) {
    this.deps = deps;
    this.map = null;
    this.manifest = null;
    this.fix = null;
    this.watchId = null;
    this.follow = true;
    this.trail = [];

    const saved = load();
    this.theme = saved.theme || 'day';
    this.safetyDepth = saved.safetyDepth ?? 2.5;
    this.hidden = new Set(saved.hidden || []);
  }

  get palette() { return PALETTES[this.theme]; }

  async init() {
    this.manifest = await (await fetch('assets/maps/marmaris/manifest.json')).json();

    this.map = new maplibregl.Map({
      container: 'map',
      style: buildStyle(this.palette, this.safetyDepth),
      center: this.manifest.center,
      zoom: this.manifest.defaultZoom,
      minZoom: this.manifest.minZoom,
      maxZoom: this.manifest.maxZoom,
      maxBounds: padded(this.manifest.bounds),
      attributionControl: false,
      // The chart is a plan view; tilting it only makes soundings harder to read.
      pitchWithRotate: false,
      dragRotate: false,
      touchPitch: false,
    });

    this.map.addControl(new maplibregl.AttributionControl({
      compact: true,
      customAttribution: '© OpenStreetMap · OpenSeaMap · EMODnet — resmî harita değildir',
    }));
    this.map.addControl(new maplibregl.ScaleControl({ maxWidth: 110, unit: 'nautical' }), 'bottom-left');

    // A chart that silently fails to draw is worse than one that says so.
    this.map.on('error', (e) => {
      const message = (e && e.error && e.error.message) || String(e && e.error) || 'bilinmeyen hata';
      console.error('[chart]', message, e && e.error);
      this.deps.toast(`Harita hatası: ${message}`);
    });

    this.map.on('load', () => {
      console.log('[chart] yuklendi, katman:', this.map.getStyle().layers.length);
    });

    this.map.on('style.load', () => {
      console.log('[chart] stil yuklendi');
      installIcons(this.map, this.palette);
      this.applyLayerVisibility();
      this.redrawOverlays();
    });

    // Panning by hand means the helm wants to look somewhere else; stop chasing
    // the boat until they ask for it back.
    this.map.on('dragstart', () => this.setFollow(false));
    this.map.on('click', (e) => this.inspect(e));

    // Local development hook: lets the map be inspected from the console
    // without exposing the instance on a deployed origin.
    if (['127.0.0.1', 'localhost'].includes(location.hostname)) window.__chart = this;

    this.bindControls();
    this.startLocating();
  }

  bindControls() {
    const { followBtn, themeBtn, themeGlyph, layersBtn } = this.deps;

    followBtn.addEventListener('click', () => {
      this.setFollow(true);
      if (this.fix) this.map.easeTo({ center: [this.fix.lon, this.fix.lat], zoom: Math.max(this.map.getZoom(), 14) });
      else this.deps.toast('Henüz konum alınmadı');
    });

    themeGlyph.textContent = THEME_GLYPH[this.theme];
    themeBtn.addEventListener('click', () => {
      this.theme = THEMES[(THEMES.indexOf(this.theme) + 1) % THEMES.length];
      themeGlyph.textContent = THEME_GLYPH[this.theme];
      document.body.dataset.theme = this.theme;
      this.map.setStyle(buildStyle(this.palette, this.safetyDepth));
      this.persist();
      this.deps.toast(THEME_LABELS[this.theme]);
    });

    layersBtn.addEventListener('click', () => this.showLayerSheet());
    document.body.dataset.theme = this.theme;
    this.setFollow(true);
  }

  setFollow(on) {
    this.follow = on;
    this.deps.followBtn.setAttribute('aria-pressed', String(on));
  }

  // ------------------------------------------------------------- location

  startLocating() {
    if (!('geolocation' in navigator)) {
      this.deps.setFixState('yok', 'GPS desteklenmiyor', 'stale');
      return;
    }
    this.watchId = navigator.geolocation.watchPosition(
      (pos) => this.onFix(pos),
      (err) => {
        const message = err.code === err.PERMISSION_DENIED
          ? 'konum izni yok'
          : 'konum alınamıyor';
        this.deps.setFixState('GPS', message, 'stale');
      },
      { enableHighAccuracy: true, maximumAge: 2000, timeout: 30000 },
    );
  }

  onFix(pos) {
    const c = pos.coords;
    this.fix = {
      lat: c.latitude,
      lon: c.longitude,
      // The Geolocation API reports metres per second and degrees true.
      sog: c.speed == null ? null : c.speed * MS_TO_KNOTS,
      cog: c.heading == null || Number.isNaN(c.heading) ? null : c.heading,
      accuracy: c.accuracy,
      at: pos.timestamp,
    };

    // A breadcrumb trail, kept in memory only: a browser tab cannot record a
    // track in the background, so persisting a partial one would mislead.
    const last = this.trail[this.trail.length - 1];
    if (!last || distanceMeters(last[1], last[0], this.fix.lat, this.fix.lon) > 15) {
      this.trail.push([this.fix.lon, this.fix.lat]);
      if (this.trail.length > 5000) this.trail.shift();
    }

    this.deps.setReadouts(this.fix);
    this.deps.setFixState('GPS', `±${Math.round(c.accuracy)} m`, 'ok');
    this.redrawOverlays();

    if (this.follow) {
      this.map.easeTo({
        center: [this.fix.lon, this.fix.lat],
        duration: 600,
        zoom: this.map.getZoom() < 12 ? 14 : undefined,
      });
    }
  }

  redrawOverlays() {
    if (!this.map || !this.map.isStyleLoaded()) return;
    const boat = this.map.getSource(SRC.boat);
    if (!boat) return;

    boat.setData({
      type: 'FeatureCollection',
      features: this.fix
        ? [point(this.fix.lon, this.fix.lat, { fix: true, heading: this.fix.cog ?? 0 })]
        : [],
    });

    this.map.getSource(SRC.track).setData({
      type: 'FeatureCollection',
      features: this.trail.length > 1
        ? [{ type: 'Feature', geometry: { type: 'LineString', coordinates: this.trail }, properties: {} }]
        : [],
    });
  }

  // -------------------------------------------------------------- inspect

  inspect(e) {
    const layers = INSPECTABLE.filter((id) => this.map.getLayer(id));
    const box = 8;
    const features = this.map.queryRenderedFeatures(
      [[e.point.x - box, e.point.y - box], [e.point.x + box, e.point.y + box]],
      { layers },
    );
    if (!features.length) { this.deps.hideSheet(); return; }

    const f = features[0];
    const p = f.properties || {};
    const rows = [];

    const name = p.name || p.label || labelForType(p.stype) || 'İşaretlenmemiş';
    if (p.stype) rows.push(['Tür', labelForType(p.stype) || p.stype]);
    if (p.category) rows.push(['Kategori', labelForType(p.category) || p.category]);
    if (p.light) rows.push(['Fener', p.light]);
    if (p.colour) rows.push(['Renk', p.colour]);
    if (p.depth != null) rows.push(['Derinlik', formatDepth(Number(p.depth))]);
    if (p.min_depth != null) {
      rows.push(['Derinlik aralığı',
        `${formatDepth(Number(p.min_depth))} – ${p.max_depth != null ? formatDepth(Number(p.max_depth)) : '…'}`]);
    }

    const [lon, lat] = f.geometry.type === 'Point' ? f.geometry.coordinates : [e.lngLat.lng, e.lngLat.lat];
    rows.push(['Mevki', `${formatLatitude(lat)}  ${formatLongitude(lon)}`]);
    if (this.fix) {
      const d = distanceMeters(this.fix.lat, this.fix.lon, lat, lon);
      const b = initialBearing(this.fix.lat, this.fix.lon, lat, lon);
      rows.push(['Tekneden', `${formatDistanceNm(d)} · ${formatBearing(b)} ${compassPoint(b)}`]);
    }
    this.deps.showSheet(name, rows);
  }

  // --------------------------------------------------------------- layers

  showLayerSheet() {
    this.deps.showToggleSheet('Katmanlar', LAYER_GROUPS.map((g) => ({
      id: g.id,
      label: g.label,
      on: !this.hidden.has(g.id),
    })), (id, on) => {
      if (on) this.hidden.delete(id); else this.hidden.add(id);
      this.applyLayerVisibility();
      this.persist();
    });
  }

  applyLayerVisibility() {
    for (const group of LAYER_GROUPS) {
      const visible = !this.hidden.has(group.id);
      for (const layer of group.layers) {
        if (this.map.getLayer(layer)) {
          this.map.setLayoutProperty(layer, 'visibility', visible ? 'visible' : 'none');
        }
      }
    }
  }

  persist() {
    save({ theme: this.theme, safetyDepth: this.safetyDepth, hidden: [...this.hidden] });
  }

  /** MapLibre needs a nudge when the container was hidden while the tab was away. */
  onShown() {
    if (this.map) this.map.resize();
  }
}

/** A little slack around the package bounds so the edge is reachable. */
function padded([w, s, e, n]) {
  const pad = 0.05;
  return [[w - pad, s - pad], [e + pad, n + pad]];
}

const TYPE_LABELS = {
  buoy_lateral: 'Yanal şamandıra', buoy_cardinal: 'Kardinal şamandıra',
  buoy_safe_water: 'Emniyetli su şamandırası', buoy_special_purpose: 'Özel maksat şamandırası',
  buoy_isolated_danger: 'İzole tehlike şamandırası', buoy_installation: 'Tesis şamandırası',
  mooring: 'Bağlama şamandırası', beacon_lateral: 'Yanal bikon', beacon_cardinal: 'Kardinal bikon',
  beacon_special_purpose: 'Özel maksat bikonu', beacon_isolated_danger: 'İzole tehlike bikonu',
  beacon_safe_water: 'Emniyetli su bikonu', pile: 'Kazık',
  light_major: 'Fener', light_minor: 'Küçük fener', landmark: 'Nirengi',
  anchorage: 'Demir yeri', restricted_area: 'Yasak bölge', military_area: 'Askerî bölge',
  wreck: 'Batık', rock: 'Kaya', underwater_rock: 'Su altı kayası',
  obstruction: 'Engel', reef: 'Resif', shoal: 'Sığlık',
  harbour: 'Liman', marina: 'Marina',
};
const labelForType = (t) => TYPE_LABELS[t];

function load() {
  try { return JSON.parse(localStorage.getItem(STORE)) || {}; } catch { return {}; }
}
function save(state) {
  try { localStorage.setItem(STORE, JSON.stringify(state)); } catch { /* private mode */ }
}
