// Geodesy and readout formatting, ported from Geodesy.kt and Format.kt so the
// two clients show the same numbers in the same shapes.

export const EARTH_RADIUS_M = 6371008.8;
export const METERS_PER_NM = 1852.0;
export const MS_TO_KNOTS = 3600.0 / METERS_PER_NM;

const rad = (d) => (d * Math.PI) / 180;
const deg = (r) => (r * 180) / Math.PI;

export function normalizeBearing(d) {
  const n = d % 360;
  return n < 0 ? n + 360 : n;
}

/** Great-circle distance, haversine. */
export function distanceMeters(lat1, lon1, lat2, lon2) {
  const dLat = rad(lat2 - lat1);
  const dLon = rad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2
    + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function initialBearing(lat1, lon1, lat2, lon2) {
  const dLon = rad(lon2 - lon1);
  const y = Math.sin(dLon) * Math.cos(rad(lat2));
  const x = Math.cos(rad(lat1)) * Math.sin(rad(lat2))
    - Math.sin(rad(lat1)) * Math.cos(rad(lat2)) * Math.cos(dLon);
  return normalizeBearing(deg(Math.atan2(y, x)));
}

/** Point at a bearing and distance from an origin. */
export function destination(lat, lon, bearingDeg, distanceM) {
  const d = distanceM / EARTH_RADIUS_M;
  const b = rad(bearingDeg);
  const lat1 = rad(lat);
  const lon1 = rad(lon);
  const lat2 = Math.asin(Math.sin(lat1) * Math.cos(d) + Math.cos(lat1) * Math.sin(d) * Math.cos(b));
  const lon2 = lon1 + Math.atan2(
    Math.sin(b) * Math.sin(d) * Math.cos(lat1),
    Math.cos(d) - Math.sin(lat1) * Math.sin(lat2),
  );
  return [deg(lat2), ((deg(lon2) + 540) % 360) - 180];
}

// --------------------------------------------------------------- formatting

const fixed = (n, places) => n.toFixed(places);
const pad = (n, width) => String(Math.trunc(n)).padStart(width, '0');

/** Distance in the units a chartplotter uses: metres up close, then NM. */
export function formatDistanceNm(meters) {
  if (meters == null) return '--';
  const nm = meters / METERS_PER_NM;
  if (meters < 1000) return `${fixed(meters, 0)} m`;
  return nm < 10 ? `${fixed(nm, 2)} NM` : `${fixed(nm, 1)} NM`;
}

export const formatBearing = (d) => (d == null ? '---°' : `${pad(Math.round(normalizeBearing(d)) % 360, 3)}°`);

export const formatSpeed = (kn) => (kn == null ? '--.- kn' : `${fixed(kn, 1)} kn`);

export const formatDepth = (m) => (m == null ? '--' : (m < 10 ? `${fixed(m, 1)} m` : `${fixed(m, 0)} m`));

function formatDm(value, hemisphere, degreeWidth = 2) {
  const abs = Math.abs(value);
  const degrees = Math.floor(abs);
  const minutes = (abs - degrees) * 60;
  return `${pad(degrees, degreeWidth)}° ${minutes.toFixed(3).padStart(6, '0')}' ${hemisphere}`;
}

export const formatLatitude = (lat) => formatDm(lat, lat >= 0 ? 'N' : 'S');
export const formatLongitude = (lon) => formatDm(lon, lon >= 0 ? 'E' : 'W', 3);

export function formatEta(seconds) {
  if (seconds == null) return '--:--';
  const total = Math.max(0, Math.trunc(seconds));
  const hours = Math.trunc(total / 3600);
  const minutes = Math.trunc((total % 3600) / 60);
  if (hours >= 24) return `${Math.trunc(hours / 24)}d ${pad(hours % 24, 2)}h`;
  return `${hours}:${pad(minutes, 2)}`;
}

const POINTS = ['N', 'NNE', 'NE', 'ENE', 'E', 'ESE', 'SE', 'SSE',
  'S', 'SSW', 'SW', 'WSW', 'W', 'WNW', 'NW', 'NNW'];

/** Compass point for a bearing, e.g. 247 -> WSW. */
export function compassPoint(d) {
  if (d == null) return '--';
  return POINTS[Math.trunc((normalizeBearing(d) + 11.25) / 22.5) % 16];
}

/** A circle on the ground, for the anchor watch overlay. */
export function circlePolygon(lat, lon, radiusM, steps = 64) {
  const ring = [];
  for (let i = 0; i <= steps; i += 1) {
    const [la, lo] = destination(lat, lon, (i * 360) / steps, radiusM);
    ring.push([lo, la]);
  }
  return { type: 'Polygon', coordinates: [ring] };
}
