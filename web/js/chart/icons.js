// Port of the Android client's ChartIcons.kt.
//
// The symbols are drawn into canvases at runtime rather than shipped as a sprite
// sheet: several of them are tinted from the active palette, so a static sprite
// would mean three near-identical sheets to keep in step with the themes.

const BUOY_COLOURS = [
  'red', 'green', 'yellow', 'white', 'black', 'orange', 'blue', 'grey',
  'red-white', 'black-yellow', 'yellow-black', 'black-red', 'green-red',
];

const COLOUR_VALUE = {
  red: '#D32F2F', green: '#2E7D32', yellow: '#F9C900', white: '#FFFFFF',
  black: '#141414', orange: '#EF6C00', blue: '#1565C0', grey: '#9E9E9E',
};

export function normalizeColour(colour) {
  if (!colour || !colour.trim()) return 'grey';
  const first = colour.split(/[;,]/)[0].trim().toLowerCase();
  if (BUOY_COLOURS.includes(first)) return first;
  return { gray: 'grey', amber: 'orange', violet: 'blue' }[first] || 'grey';
}

const value = (name) => COLOUR_VALUE[name] || COLOUR_VALUE.grey;

function canvas(size) {
  const px = Math.max(8, Math.round(size));
  const el = document.createElement('canvas');
  el.width = px;
  el.height = px;
  return { ctx: el.getContext('2d'), s: px, el };
}

function stroke(ctx, colour, width) {
  ctx.strokeStyle = colour;
  ctx.lineWidth = width;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
}

const line = (ctx, x1, y1, x2, y2) => {
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.stroke();
};

const circle = (ctx, x, y, r, fill) => {
  ctx.beginPath();
  ctx.arc(x, y, r, 0, Math.PI * 2);
  if (fill) { ctx.fillStyle = fill; ctx.fill(); } else { ctx.stroke(); }
};

/** The vessel: a chart-style arrowhead so the bow direction is unambiguous. */
function boat(size, colour) {
  const { ctx, s, el } = canvas(size);
  ctx.beginPath();
  ctx.moveTo(s * 0.5, s * 0.06);
  ctx.lineTo(s * 0.82, s * 0.92);
  ctx.lineTo(s * 0.5, s * 0.72);
  ctx.lineTo(s * 0.18, s * 0.92);
  ctx.closePath();
  ctx.fillStyle = colour;
  ctx.fill();
  stroke(ctx, '#FFFFFF', s * 0.06);
  ctx.stroke();
  return el;
}

function boatNoFix(size, colour) {
  const { ctx, s, el } = canvas(size);
  circle(ctx, s / 2, s / 2, s * 0.32, 'rgba(150,150,150,0.47)');
  stroke(ctx, colour, s * 0.07);
  circle(ctx, s / 2, s / 2, s * 0.32);
  return el;
}

function waypoint(size, colour, halo, active = false) {
  const { ctx, s, el } = canvas(size);
  const r = s * (active ? 0.36 : 0.32);
  circle(ctx, s / 2, s / 2, r + s * 0.07, halo);
  circle(ctx, s / 2, s / 2, r, colour);
  circle(ctx, s / 2, s / 2, r * 0.42, halo);
  if (active) {
    stroke(ctx, colour, s * 0.06);
    circle(ctx, s / 2, s / 2, r + s * 0.12);
  }
  return el;
}

function anchorMark(size, colour, halo) {
  const { ctx, s, el } = canvas(size);
  const draw = () => {
    line(ctx, s * 0.5, s * 0.16, s * 0.5, s * 0.78);
    line(ctx, s * 0.27, s * 0.34, s * 0.73, s * 0.34);
    ctx.beginPath();
    ctx.moveTo(s * 0.18, s * 0.60);
    ctx.quadraticCurveTo(s * 0.24, s * 0.88, s * 0.5, s * 0.86);
    ctx.quadraticCurveTo(s * 0.76, s * 0.88, s * 0.82, s * 0.60);
    ctx.stroke();
    circle(ctx, s * 0.5, s * 0.16, s * 0.09);
  };
  stroke(ctx, halo, s * 0.22); draw();
  stroke(ctx, colour, s * 0.11); draw();
  return el;
}

function harbour(size, colour, halo) {
  const { ctx, s, el } = canvas(size);
  circle(ctx, s / 2, s / 2, s * 0.40, halo);
  circle(ctx, s / 2, s / 2, s * 0.34, colour);
  stroke(ctx, halo, s * 0.10);
  line(ctx, s * 0.5, s * 0.24, s * 0.5, s * 0.70);
  line(ctx, s * 0.32, s * 0.38, s * 0.68, s * 0.38);
  return el;
}

function marina(size, colour, halo) {
  const { ctx, s, el } = canvas(size);
  circle(ctx, s / 2, s / 2, s * 0.42, halo);
  circle(ctx, s / 2, s / 2, s * 0.36, colour);
  // A small sloop: mast plus mainsail.
  ctx.beginPath();
  ctx.moveTo(s * 0.50, s * 0.22);
  ctx.lineTo(s * 0.50, s * 0.62);
  ctx.lineTo(s * 0.30, s * 0.62);
  ctx.closePath();
  ctx.fillStyle = halo;
  ctx.fill();
  stroke(ctx, halo, s * 0.09);
  line(ctx, s * 0.24, s * 0.70, s * 0.76, s * 0.70);
  return el;
}

function lighthouse(size, colour, halo) {
  const { ctx, s, el } = canvas(size);
  ctx.beginPath();
  ctx.moveTo(s * 0.40, s * 0.86);
  ctx.lineTo(s * 0.44, s * 0.36);
  ctx.lineTo(s * 0.56, s * 0.36);
  ctx.lineTo(s * 0.60, s * 0.86);
  ctx.closePath();
  ctx.fillStyle = halo;
  ctx.fill();
  stroke(ctx, colour, s * 0.07);
  ctx.stroke();
  circle(ctx, s * 0.5, s * 0.26, s * 0.13, colour);
  stroke(ctx, colour, s * 0.06);
  line(ctx, s * 0.20, s * 0.16, s * 0.34, s * 0.24);
  line(ctx, s * 0.80, s * 0.16, s * 0.66, s * 0.24);
  return el;
}

/** The magenta flare charts use to mark any lit object. */
function lightFlare(size, colour) {
  const { ctx, s, el } = canvas(size);
  ctx.beginPath();
  ctx.moveTo(s * 0.5, s * 0.5);
  ctx.lineTo(s * 0.86, s * 0.06);
  ctx.quadraticCurveTo(s * 0.96, s * 0.30, s * 0.90, s * 0.52);
  ctx.closePath();
  ctx.fillStyle = colour;
  ctx.fill();
  circle(ctx, s * 0.5, s * 0.5, s * 0.11, colour);
  return el;
}

function beacon(size, colour, halo) {
  const { ctx, s, el } = canvas(size);
  ctx.beginPath();
  ctx.moveTo(s * 0.5, s * 0.12);
  ctx.lineTo(s * 0.68, s * 0.82);
  ctx.lineTo(s * 0.32, s * 0.82);
  ctx.closePath();
  ctx.fillStyle = halo;
  ctx.fill();
  stroke(ctx, colour, s * 0.09);
  ctx.stroke();
  return el;
}

function buoy(size, colourName) {
  const { ctx, s, el } = canvas(size * 1.3);
  const parts = colourName.split('-');
  const primary = value(parts[0]);
  const secondary = parts.length > 1 ? value(parts[1]) : primary;

  const box = { l: s * 0.28, t: s * 0.24, r: s * 0.72, b: s * 0.78 };
  const oval = (l, t, r, b) => {
    ctx.beginPath();
    ctx.ellipse((l + r) / 2, (t + b) / 2, (r - l) / 2, (b - t) / 2, 0, 0, Math.PI * 2);
  };

  oval(box.l - s * 0.06, box.t - s * 0.06, box.r + s * 0.06, box.b + s * 0.06);
  ctx.fillStyle = '#FFFFFF';
  ctx.fill();

  oval(box.l, box.t, box.r, box.b);
  ctx.fillStyle = primary;
  ctx.fill();

  if (parts.length > 1) {
    ctx.save();
    ctx.beginPath();
    ctx.rect(box.l, (box.t + box.b) / 2, box.r - box.l, (box.b - box.t) / 2);
    ctx.clip();
    oval(box.l, box.t, box.r, box.b);
    ctx.fillStyle = secondary;
    ctx.fill();
    ctx.restore();
  }

  stroke(ctx, '#202020', s * 0.045);
  oval(box.l, box.t, box.r, box.b);
  ctx.stroke();
  // Mooring line stub, so the symbol reads as floating rather than fixed.
  stroke(ctx, '#202020', s * 0.05);
  line(ctx, s * 0.5, s * 0.78, s * 0.5, s * 0.92);
  return el;
}

function rock(size, colour, awash) {
  const { ctx, s, el } = canvas(size);
  if (awash) {
    // Awash: cross inside a circle, per chart convention.
    stroke(ctx, colour, s * 0.07);
    circle(ctx, s * 0.5, s * 0.5, s * 0.40);
  }
  stroke(ctx, colour, s * 0.12);
  line(ctx, s * 0.5, s * 0.18, s * 0.5, s * 0.82);
  line(ctx, s * 0.18, s * 0.5, s * 0.82, s * 0.5);
  return el;
}

function wreck(size, colour) {
  const { ctx, s, el } = canvas(size);
  stroke(ctx, colour, s * 0.10);
  line(ctx, s * 0.12, s * 0.50, s * 0.88, s * 0.50);
  line(ctx, s * 0.32, s * 0.28, s * 0.32, s * 0.72);
  line(ctx, s * 0.56, s * 0.34, s * 0.56, s * 0.66);
  return el;
}

function obstruction(size, colour) {
  const { ctx, s, el } = canvas(size);
  stroke(ctx, colour, s * 0.09);
  circle(ctx, s * 0.5, s * 0.5, s * 0.38);
  line(ctx, s * 0.28, s * 0.28, s * 0.72, s * 0.72);
  line(ctx, s * 0.72, s * 0.28, s * 0.28, s * 0.72);
  return el;
}

function genericMark(size, colour, halo) {
  const { ctx, s, el } = canvas(size);
  circle(ctx, s / 2, s / 2, s * 0.34, halo);
  circle(ctx, s / 2, s / 2, s * 0.26, colour);
  return el;
}

/** Adds every symbol the style references to the map for the given palette. */
export function installIcons(map, p) {
  const dpr = Math.min(window.devicePixelRatio || 1, 3);
  const base = 22 * dpr;
  const add = (id, el) => {
    const { width, height } = el;
    const data = el.getContext('2d').getImageData(0, 0, width, height);
    if (map.hasImage(id)) map.removeImage(id);
    map.addImage(id, { width, height, data: data.data }, { pixelRatio: dpr });
  };

  add('icon-boat', boat(base * 1.5, p.boat));
  add('icon-boat-nofix', boatNoFix(base * 1.5, p.boat));
  add('icon-waypoint', waypoint(base, p.waypoint, p.waypointHalo));
  add('icon-waypoint-active', waypoint(base, p.routeActiveLeg, p.waypointHalo, true));
  add('icon-anchor-mark', anchorMark(base, p.anchorCircle, p.placeHalo));
  add('icon-harbour', harbour(base, p.anchorageLine, p.placeHalo));
  add('icon-marina', marina(base, p.anchorageLine, p.placeHalo));
  add('icon-lighthouse', lighthouse(base, p.hazard, p.placeHalo));
  add('icon-light', lightFlare(base * 1.4, p.route));
  add('icon-beacon', beacon(base, p.structure, p.placeHalo));
  add('icon-rock', rock(base, p.hazard, false));
  add('icon-rock-awash', rock(base, p.hazard, true));
  add('icon-wreck', wreck(base, p.hazard));
  add('icon-obstruction', obstruction(base, p.hazard));
  add('icon-anchorage', anchorMark(base, p.anchorageLine, p.placeHalo));
  add('icon-generic-mark', genericMark(base, p.structure, p.placeHalo));
  for (const name of BUOY_COLOURS) add(`icon-buoy-${name}`, buoy(base, name));
}
