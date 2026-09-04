// Ranked keyword search across the training topics and the original documents,
// ported from LibrarySearch.kt.
//
// Every query term must appear somewhere in the entry (AND), so typing two words
// narrows rather than widens. Where a term matches decides the score: the title
// outranks the declared keywords, which outrank the summary, which outranks the
// body.
//
// Source documents are searched too, but on a lower scale than the topics. A
// topic is written to answer a question; a source is the raw lecture it was
// drawn from, so when both match the topic should come first.

import { LINK, deaccent, foldCase } from './content.js';

export class LibrarySearch {
  constructor(library) {
    this.library = library;
    this.index = [
      ...library.topics.map((t) => ({
        route: { kind: 'topic', id: t.id },
        title: deaccent(foldCase(t.title)),
        displayTitle: t.title,
        keywords: t.keywords.map((k) => deaccent(foldCase(k))),
        rawKeywords: t.keywords,
        summary: deaccent(foldCase(t.summary)),
        fallback: t.summary,
        body: t.body,
        haystack: deaccent(t.haystack),
        titleWeight: 100,
        bodyWeight: 10,
      })),
      ...library.sources.map((d) => ({
        route: { kind: 'source', id: d.id },
        title: deaccent(foldCase(d.title)),
        displayTitle: d.title,
        keywords: [],
        rawKeywords: [],
        summary: '',
        fallback: '',
        body: d.body,
        haystack: deaccent(d.haystack),
        titleWeight: 55,
        bodyWeight: 4,
      })),
    ];
  }

  search(query) {
    const terms = deaccent(foldCase(query)).split(/\s+/).map((t) => t.trim()).filter(Boolean);
    if (!terms.length) return [];

    const hits = [];
    for (const entry of this.index) {
      // Every term has to land somewhere in this entry, or it is not a hit.
      if (terms.some((t) => !entry.haystack.includes(t))) continue;

      let score = 0;
      let matchedKeyword = null;
      for (const term of terms) {
        if (entry.title.includes(term)) {
          score += entry.titleWeight;
        } else {
          const kw = entry.keywords.findIndex((k) => k.includes(term));
          if (kw >= 0) {
            score += 50;
            if (matchedKeyword === null) matchedKeyword = entry.rawKeywords[kw];
          } else if (entry.summary && entry.summary.includes(term)) {
            score += 25;
          } else {
            score += entry.bodyWeight;
          }
        }
        // A whole-word hit beats an incidental substring ("trim" inside "trimci").
        const word = new RegExp(`(^|[^\\p{L}])${escapeRe(term)}([^\\p{L}]|$)`, 'u');
        if (word.test(entry.haystack)) score += 15;
      }

      hits.push({
        route: entry.route,
        title: entry.displayTitle,
        score,
        matchedKeyword,
        snippet: this.snippetFor(entry.body, terms[0], entry.fallback),
      });
    }

    return hits.sort((a, b) => b.score - a.score || a.title.localeCompare(b.title, 'tr'));
  }

  /**
   * A line of context around the first match, so the result list shows why the
   * entry came back rather than just repeating its summary.
   */
  snippetFor(body, term, fallback) {
    const plain = body.split('\n')
      .filter((l) => !l.startsWith('#') && !l.startsWith('|') && !l.startsWith('---'))
      .join(' ')
      .replace(LINK, (_, target, label) => label || this.library.labelFor(target.trim()))
      .replace(/[*`>]/g, '')
      .replace(/\s+/g, ' ')
      .trim();

    const at = deaccent(foldCase(plain)).indexOf(term);
    if (at < 0) return fallback;

    let start = Math.max(0, at - 60);
    if (start > 0) {
      const space = plain.indexOf(' ', start);
      if (space >= 0 && space <= at) start = space;
    }
    const end = Math.min(plain.length, at + term.length + 100);
    return (start > 0 ? '…' : '') + plain.slice(start, end).trim() + (end < plain.length ? '…' : '');
  }
}

const escapeRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
