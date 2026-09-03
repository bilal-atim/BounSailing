/*
 * Offline is the whole point: the chart has to come up in a cove with no signal.
 *
 * The app shell and the content packages are precached on install, so a cold
 * start in aeroplane mode renders exactly what a warm one does.
 */

// The cache name is baked in by tools/web/sync_assets.py rather than kept in a
// mutable global: a service worker is stopped and restarted freely, and a
// restarted worker that fell back to a default name would read and write a
// different cache than the one it filled.
importScripts('sw-version.js');

const CACHE = `bounsailing-${self.CACHE_VERSION}`;

const SHELL = [
  './',
  'index.html',
  'manifest.webmanifest',
  'sw-version.js',
  'css/app.css',
  'js/app.js',
  'js/notes.js',
  'js/library/content.js',
  'js/library/search.js',
  'js/library/view.js',
  'js/chart/map.js',
  'js/chart/style.js',
  'js/chart/icons.js',
  'js/chart/nav.js',
  'js/chart/palette.js',
  'vendor/maplibre-gl.js',
  'vendor/maplibre-gl.css',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/icon-180.png',
  'icons/logo-wordmark.png',
];

async function precache() {
  const cache = await caches.open(CACHE);
  const index = await (await fetch('assets/index.json', { cache: 'no-cache' })).json();
  const urls = [...SHELL, 'assets/index.json', ...index.files];

  // Added one at a time rather than with addAll: a single missing file rejects
  // a whole addAll batch, which would silently drop the rest of that batch.
  const failed = [];
  const queue = urls.slice();
  const worker = async () => {
    while (queue.length) {
      const url = queue.shift();
      try {
        const response = await fetch(url, { cache: 'no-cache' });
        if (!response.ok) throw new Error(String(response.status));
        await cache.put(url, response);
      } catch (err) {
        failed.push(`${url} (${err.message})`);
      }
    }
  };
  // Six at a time keeps the install quick without swamping a phone connection.
  await Promise.all(Array.from({ length: 6 }, worker));

  if (failed.length) console.warn('[sw] onbellege alinamadi:', failed);
  console.log(`[sw] ${urls.length - failed.length}/${urls.length} dosya onbellekte (${CACHE})`);
}

self.addEventListener('install', (event) => {
  event.waitUntil(precache().then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    for (const key of await caches.keys()) {
      if (key.startsWith('bounsailing-') && key !== CACHE) await caches.delete(key);
    }
    await self.clients.claim();
  })());
});

/*
 * Two strategies, because the two kinds of file age differently.
 *
 * The app shell changes whenever the app is rebuilt, and a stale copy of it is a
 * bug the user cannot clear: network-first keeps it current when there is signal
 * and still works from cache when there is none.
 *
 * The content packages are immutable between releases and are the expensive part
 * to fetch, so they are served cache-first.
 */
const SHELL_RE = /\.(html|js|css|webmanifest)$/;

function isShell(url) {
  if (url.pathname.startsWith('/vendor/')) return false; // large and pinned
  return url.pathname === '/' || url.pathname.endsWith('/') || SHELL_RE.test(url.pathname);
}

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  event.respondWith(isShell(url) ? networkFirst(request) : cacheFirst(request, event));
});

async function networkFirst(request) {
  try {
    const response = await fetch(request);
    if (response.ok) (await caches.open(CACHE)).put(request, response.clone());
    return response;
  } catch (err) {
    const cached = await caches.match(request, { ignoreSearch: true });
    if (cached) return cached;
    // A navigation with nothing cached still deserves the app shell.
    if (request.mode === 'navigate') {
      const fallback = await caches.match('index.html');
      if (fallback) return fallback;
    }
    throw err;
  }
}

async function cacheFirst(request, event) {
  const cached = await caches.match(request, { ignoreSearch: true });
  if (cached) return cached;
  const response = await fetch(request);
  if (response.ok) {
    const copy = response.clone();
    event.waitUntil(caches.open(CACHE).then((c) => c.put(request, copy)));
  }
  return response;
}
