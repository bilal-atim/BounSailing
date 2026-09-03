// Application shell: three tabs, a shared toast, and the service-worker
// registration that makes the whole thing usable with no signal.

import { LibraryView } from './library/view.js';
import { ChartView } from './chart/map.js';
import { NotesView } from './notes.js';
import { compassPoint, formatBearing, formatSpeed } from './chart/nav.js';

const $ = (id) => document.getElementById(id);
const TABS = ['library', 'chart', 'notes'];

let toastTimer = null;
function toast(message) {
  const node = $('toast');
  node.textContent = message;
  node.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { node.hidden = true; }, 2600);
}

// ------------------------------------------------------------------- sheets

function hideSheet() { $('chart-sheet').hidden = true; }

function sheetShell(title) {
  const sheet = $('chart-sheet');
  sheet.replaceChildren();
  sheet.hidden = false;

  const close = document.createElement('button');
  close.className = 'btn ghost sheet-close';
  close.textContent = 'Kapat';
  close.addEventListener('click', hideSheet);

  const h = document.createElement('h3');
  h.textContent = title;
  sheet.append(close, h);
  return sheet;
}

function showSheet(title, rows) {
  const sheet = sheetShell(title);
  const dl = document.createElement('dl');
  for (const [key, value] of rows) {
    const dt = document.createElement('dt');
    dt.textContent = key;
    const dd = document.createElement('dd');
    dd.textContent = value;
    dl.append(dt, dd);
  }
  sheet.appendChild(dl);
}

function showToggleSheet(title, items, onToggle) {
  const sheet = sheetShell(title);
  for (const item of items) {
    const row = document.createElement('div');
    row.className = 'toggle-row';
    const label = document.createElement('span');
    label.textContent = item.label;

    const wrap = document.createElement('label');
    wrap.className = 'switch';
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.checked = item.on;
    input.addEventListener('change', () => onToggle(item.id, input.checked));
    wrap.append(input, document.createElement('span'));

    row.append(label, wrap);
    sheet.appendChild(row);
  }
}

// ------------------------------------------------------------------ startup

const library = new LibraryView($('lib-scroll'), $('lib-search'), $('lib-search-clear'));

const chart = new ChartView({
  followBtn: $('btn-follow'),
  themeBtn: $('btn-theme'),
  themeGlyph: $('theme-glyph'),
  layersBtn: $('btn-layers'),
  toast,
  showSheet,
  showToggleSheet,
  hideSheet,
  setReadouts(fix) {
    $('ro-sog').textContent = formatSpeed(fix.sog).replace(' kn', '');
    $('ro-cog').textContent = fix.cog == null ? '---' : formatBearing(fix.cog).replace('°', '');
    $('ro-cog').title = compassPoint(fix.cog);
  },
  setFixState(label, detail, state) {
    $('ro-fix-label').textContent = label;
    $('ro-acc').textContent = detail;
    const box = document.querySelector('.readout.gps');
    box.classList.toggle('ok', state === 'ok');
    box.classList.toggle('stale', state === 'stale');
  },
});

const notes = new NotesView($('notes-list'), $('note-add'), toast);

let current = null;
let chartStarted = false;

function selectTab(name) {
  if (!TABS.includes(name)) return;
  const changed = current !== name;
  current = name;

  for (const tab of TABS) {
    $(`panel-${tab}`).hidden = tab !== name;
  }
  for (const button of document.querySelectorAll('.tab')) {
    button.setAttribute('aria-selected', String(button.dataset.tab === name));
  }
  localStorage.setItem('bounsailing.tab', name);

  if (name === 'chart') {
    // The map is only built the first time the tab is opened: parsing 7 MB of
    // GeoJSON on a cold start would delay the library for no reason.
    if (!chartStarted) {
      chartStarted = true;
      chart.init().catch((err) => {
        console.error(err);
        toast('Harita yüklenemedi');
      });
    } else if (changed) {
      chart.onShown();
    }
  }
  if (name === 'notes') notes.render();
}

for (const button of document.querySelectorAll('.tab')) {
  button.addEventListener('click', () => {
    const name = button.dataset.tab;
    // Tapping the tab you are already on returns it to its root.
    if (current === name && name === 'library') library.resetToHome();
    selectTab(name);
  });
}

// Hardware/gesture back walks the library's own stack before leaving the app.
history.replaceState({ depth: 0 }, '');
window.addEventListener('popstate', () => {
  if (current === 'library' && library.back()) {
    history.pushState({ depth: 1 }, '');
  }
});
$('lib-scroll').addEventListener('click', () => {
  if (library.stack.length > 1 && history.state?.depth !== 1) {
    history.pushState({ depth: 1 }, '');
  }
}, true);

selectTab(localStorage.getItem('bounsailing.tab') || 'library');
library.load().catch((err) => {
  console.error(err);
  toast('Kütüphane yüklenemedi');
});

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('sw.js').catch((err) => console.warn('sw', err));
  });
}
