// Loader and Markdown engine for the library content, ported from the Android
// client's LibraryModel.kt / Markdown.kt. Both clients read the same asset tree,
// so a topic edited once shows up identically in each.

const ROOT = 'assets/library';

/** Matches `[[topic-id]]`, `[[topic-id|label]]` and the `src:` variants. */
export const LINK = /\[\[([^\]|]+)(?:\|([^\]]+))?\]\]/g;

/**
 * Lower-casing for search. `toLowerCase()` on a Turkish locale turns a dotted
 * capital I into "i̇", so "İSKOTA" and "iskota" stop matching. Folding the
 * Turkish pairs by hand keeps the index locale-independent.
 */
export function foldCase(s) {
  let out = '';
  for (const c of s) {
    out += { I: 'ı', 'İ': 'i', 'Ş': 'ş', 'Ğ': 'ğ', 'Ü': 'ü', 'Ö': 'ö', 'Ç': 'ç' }[c]
      || c.toLowerCase();
  }
  return out;
}

/**
 * Turkish sailing vocabulary is routinely typed without its diacritics
 * ("kavanca" for "kavança"), so both index and query are flattened to ASCII.
 */
export function deaccent(s) {
  let out = '';
  for (const c of s) {
    out += { 'ı': 'i', 'î': 'i', 'ş': 's', 'ğ': 'g', 'ü': 'u', 'û': 'u', 'ö': 'o', 'ç': 'c', 'â': 'a' }[c] || c;
  }
  return out;
}

function parseTopic(id, raw) {
  const text = raw.replace(/\r\n/g, '\n');
  if (!text.startsWith('---\n')) return null;
  const end = text.indexOf('\n---\n', 3);
  if (end < 0) return null;

  const front = {};
  for (const lineText of text.slice(4, end).split('\n')) {
    const sep = lineText.indexOf(':');
    if (sep > 0) front[lineText.slice(0, sep).trim()] = lineText.slice(sep + 1).trim();
  }
  if (!front.title) return null;

  const list = (v) => (v || '').split(',').map((x) => x.trim()).filter(Boolean);
  const body = text.slice(end + 5).trim();
  const topic = {
    id,
    title: front.title,
    categoryId: front.category || '',
    order: Number.parseInt(front.order, 10) || 999,
    summary: front.summary || '',
    keywords: list(front.keywords),
    sourceIds: list(front.sources),
    body,
  };
  topic.haystack = foldCase([topic.title, topic.summary, topic.keywords.join(' '), body].join('\n'));
  return topic;
}

export class Library {
  constructor(categories, topics, sources) {
    this.categories = categories;
    this.topics = topics;
    this.sources = sources;
    this._topics = new Map(topics.map((t) => [t.id, t]));
    this._sources = new Map(sources.map((s) => [s.id, s]));
  }

  topic(id) { return this._topics.get(id); }

  source(id) { return this._sources.get(id); }

  topicsIn(categoryId) {
    return this.topics.filter((t) => t.categoryId === categoryId)
      .sort((a, b) => a.order - b.order);
  }

  /** Topics that link to `id`, so a detail page can show what leads here. */
  backlinks(id) {
    return this.topics
      .filter((t) => t.id !== id && [...t.body.matchAll(LINK)]
        .some((m) => m[1].trim() === id))
      .sort((a, b) => a.title.localeCompare(b.title, 'tr'));
  }

  /** Resolves a link target to the title it should display. */
  labelFor(target) {
    if (target.startsWith('src:')) {
      const doc = this.source(target.slice(4));
      return doc ? doc.title : prettyLabel(target);
    }
    const t = this.topic(target);
    return t ? t.title : prettyLabel(target);
  }

  static async load() {
    const json = async (p) => (await fetch(p)).json();
    const text = async (p) => (await fetch(p)).text();

    const [categories, sourceIndex, assetIndex] = await Promise.all([
      json(`${ROOT}/categories.json`),
      json(`${ROOT}/sources/index.json`),
      json('assets/index.json'),
    ]);

    const sources = await Promise.all(sourceIndex.map(async (o) => {
      const body = await text(`${ROOT}/sources/${o.file}`);
      return { id: o.id, title: o.title, body, haystack: foldCase(`${o.title}\n${body}`) };
    }));

    // The asset listing is the manifest of what shipped, so the topic files do
    // not have to be enumerated a second time in a hand-kept index.
    const topicFiles = assetIndex.files.filter(
      (f) => f.startsWith(`${ROOT}/topics/`) && f.endsWith('.md'),
    );
    const topics = (await Promise.all(topicFiles.map(async (f) => {
      const id = f.slice(f.lastIndexOf('/') + 1, -3);
      return parseTopic(id, await text(f));
    }))).filter(Boolean).sort((a, b) => a.order - b.order);

    return new Library(categories, topics, sources);
  }
}

/** Fallback label for a link whose target the library could not resolve. */
export function prettyLabel(target) {
  return target.replace(/^src:/, '').split('-')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
}

// ------------------------------------------------------------------ markdown

const HEADING = /^(#{1,6})\s+(.*)$/;
const BULLET = /^\s*[-*]\s+(.*)$/;
const ORDERED = /^\s*(\d+)[.)]\s+(.*)$/;
const TABLE_DIVIDER = /^\|[\s:|-]+\|$/;

const splitRow = (s) => s.trim().replace(/^\||\|$/g, '').split('|').map((c) => c.trim());

/**
 * The small Markdown subset the library content is authored in. Parsing it here
 * rather than pulling in a renderer keeps the app dependency-free apart from
 * MapLibre, and the content is ours so the subset stays predictable.
 */
export function parseMarkdown(source) {
  const lines = source.replace(/\r\n/g, '\n').split('\n');
  const blocks = [];
  let paragraph = [];

  const flush = () => {
    const t = paragraph.join(' ').trim();
    if (t) blocks.push({ kind: 'p', text: t });
    paragraph = [];
  };

  for (let i = 0; i < lines.length; i += 1) {
    const raw = lines[i];
    const t = raw.trim();

    if (!t) { flush(); continue; }
    if (t === '---' || t === '***') { flush(); blocks.push({ kind: 'hr' }); continue; }

    const h = HEADING.exec(t);
    if (h) { flush(); blocks.push({ kind: 'h', level: h[1].length, text: h[2].trim() }); continue; }

    // A table is a run of pipe rows whose second line is the divider.
    if (t.startsWith('|') && i + 1 < lines.length && TABLE_DIVIDER.test(lines[i + 1].trim())) {
      flush();
      const header = splitRow(t);
      const rows = [];
      i += 2;
      while (i < lines.length && lines[i].trim().startsWith('|')) {
        rows.push(splitRow(lines[i].trim()));
        i += 1;
      }
      i -= 1;
      blocks.push({ kind: 'table', header, rows });
      continue;
    }

    if (t.startsWith('> ') || t === '>') {
      flush();
      const quote = [t.replace(/^>\s?/, '').trim()];
      while (i + 1 < lines.length && lines[i + 1].trim().startsWith('>')) {
        i += 1;
        quote.push(lines[i].trim().replace(/^>\s?/, '').trim());
      }
      blocks.push({ kind: 'quote', text: quote.join(' ').trim() });
      continue;
    }

    const o = ORDERED.exec(raw);
    if (o) { flush(); blocks.push({ kind: 'li', text: o[2].trim(), ordinal: o[1] }); continue; }

    const b = BULLET.exec(raw);
    if (b) { flush(); blocks.push({ kind: 'li', text: b[1].trim(), ordinal: null }); continue; }

    paragraph.push(t);
  }
  flush();
  return blocks;
}
