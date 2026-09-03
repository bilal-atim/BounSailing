// Ranked keyword search across the training topics, ported from LibrarySearch.kt.
//
// Every query term must appear somewhere in the topic (AND), so typing two words
// narrows rather than widens. Where a term matches decides the score: the title
// outranks the declared keywords, which outrank the summary, which outranks the
// body.

import { LINK, deaccent, foldCase } from './content.js';

export class LibrarySearch {
  constructor(library) {
    this.library = library;
    this.index = library.topics.map((t) => ({
      topic: t,
      title: deaccent(foldCase(t.title)),
      keywords: t.keywords.map((k) => deaccent(foldCase(k))),
      summary: deaccent(foldCase(t.summary)),
      haystack: deaccent(t.haystack),
    }));
  }

  search(query) {
    const terms = deaccent(foldCase(query)).split(/\s+/).map((t) => t.trim()).filter(Boolean);
    if (!terms.length) return [];

    const hits = [];
    for (const entry of this.index) {
      // Every term has to land somewhere in this topic, or it is not a hit.
      if (terms.some((t) => !entry.haystack.includes(t))) continue;

      let score = 0;
      let matchedKeyword = null;
      for (const term of terms) {
        if (entry.title.includes(term)) {
          score += 100;
        } else {
          const kw = entry.keywords.findIndex((k) => k.includes(term));
          if (kw >= 0) {
            score += 50;
            if (matchedKeyword === null) matchedKeyword = entry.topic.keywords[kw];
          } else if (entry.summary.includes(term)) {
            score += 25;
          } else {
            score += 10;
          }
        }
        // A whole-word hit beats an incidental substring ("trim" inside "trimci").
        const word = new RegExp(`(^|[^\\p{L}])${escapeRe(term)}([^\\p{L}]|$)`, 'u');
        if (word.test(entry.haystack)) score += 15;
      }

      hits.push({
        topic: entry.topic,
        score,
        matchedKeyword,
        snippet: this.snippetFor(entry.topic, terms[0]),
      });
    }

    return hits.sort((a, b) => b.score - a.score || a.topic.title.localeCompare(b.topic.title, 'tr'));
  }

  /**
   * A line of context around the first match, so the result list shows why the
   * topic came back rather than just repeating its summary.
   */
  snippetFor(topic, term) {
    const plain = topic.body.split('\n')
      .filter((l) => !l.startsWith('#') && !l.startsWith('|') && !l.startsWith('---'))
      .join(' ')
      .replace(LINK, (_, target, label) => label || this.library.labelFor(target.trim()))
      .replace(/[*`>]/g, '')
      .replace(/\s+/g, ' ')
      .trim();

    const at = deaccent(foldCase(plain)).indexOf(term);
    if (at < 0) return topic.summary;

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
