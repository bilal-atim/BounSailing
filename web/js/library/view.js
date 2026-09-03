// The Kütüphane tab: category list, search results, topic and source pages.
// Mirrors the Android client's LibraryScreen.kt, including its own back stack.

import { LINK, Library, parseMarkdown, prettyLabel } from './content.js';
import { LibrarySearch } from './search.js';

const el = (tag, className, text) => {
  const n = document.createElement(tag);
  if (className) n.className = className;
  if (text != null) n.textContent = text;
  return n;
};

const ICON = {
  chevron: 'M16.59 8.59 12 13.17 7.41 8.59 6 10l6 6 6-6z',
  doc: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zm2 16H8v-2h8zm0-4H8v-2h8zm-3-5V3.5L18.5 9z',
  back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20z',
};

const svg = (path, cls = 'icon') => {
  const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  s.setAttribute('viewBox', '0 0 24 24');
  s.setAttribute('class', cls);
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  p.setAttribute('d', path);
  s.appendChild(p);
  return s;
};

export class LibraryView {
  constructor(root, searchInput, clearButton) {
    this.root = root;
    this.searchInput = searchInput;
    this.clearButton = clearButton;
    this.library = null;
    this.search = null;
    this.stack = [{ kind: 'home' }];
    this.expanded = null;
    this.query = '';

    searchInput.addEventListener('input', () => {
      this.query = searchInput.value;
      clearButton.hidden = !this.query;
      // Typing while on a detail page means "search the library", so go home.
      if (this.route().kind !== 'home') this.stack = [{ kind: 'home' }];
      this.render();
    });
    clearButton.addEventListener('click', () => {
      searchInput.value = '';
      this.query = '';
      clearButton.hidden = true;
      searchInput.focus();
      this.render();
    });
  }

  route() { return this.stack[this.stack.length - 1]; }

  async load() {
    this.library = await Library.load();
    this.search = new LibrarySearch(this.library);
    this.render();
  }

  push(route) {
    const top = this.route();
    if (top.kind === route.kind && top.id === route.id) return;
    this.stack.push(route);
    this.render();
    this.root.scrollTop = 0;
  }

  /** Returns false when the tab is already at its root. */
  back() {
    if (this.stack.length <= 1) return false;
    this.stack.pop();
    this.render();
    return true;
  }

  resetToHome() {
    this.stack = [{ kind: 'home' }];
    this.render();
  }

  openLink(target) {
    if (target.startsWith('src:')) this.push({ kind: 'source', id: target.slice(4) });
    else this.push({ kind: 'topic', id: target });
  }

  render() {
    if (!this.library) {
      this.root.replaceChildren(el('div', 'empty', 'Kütüphane yükleniyor…'));
      return;
    }
    const route = this.route();
    // The search bar belongs to the home screen only; a detail page has its own header.
    this.searchInput.parentElement.hidden = route.kind !== 'home';

    if (route.kind === 'home') this.renderHome();
    else if (route.kind === 'topic') this.renderTopic(route.id);
    else this.renderSource(route.id);
  }

  // ------------------------------------------------------------------ home

  renderHome() {
    const frag = document.createDocumentFragment();

    if (this.query.trim()) {
      const hits = this.search.search(this.query);
      if (!hits.length) {
        const empty = el('div', 'empty');
        empty.appendChild(el('strong', null, `"${this.query}" için sonuç yok`));
        empty.appendChild(el('div', null,
          'Terimi kısaltmayı deneyin. Arama Türkçe karakter farkını yok sayar; '
          + '"kavanca" ile "kavança" aynı sonucu verir.'));
        frag.appendChild(empty);
      } else {
        frag.appendChild(el('div', 'section-sub', `${hits.length} konu bulundu`));
        for (const hit of hits) frag.appendChild(this.hitRow(hit));
      }
      this.root.replaceChildren(frag);
      return;
    }

    frag.appendChild(el('div', 'section-head', 'Eğitim Konuları'));
    frag.appendChild(el('div', 'section-sub',
      `${this.library.topics.length} konu · ${this.library.sources.length} kaynak belge`));

    for (const category of this.library.categories) {
      const topics = this.library.topicsIn(category.id);
      const open = this.expanded === category.id;

      const row = el('button', 'row');
      row.setAttribute('aria-expanded', String(open));
      const grow = el('div', 'grow');
      grow.appendChild(el('div', 'title', category.title));
      if (category.subtitle) grow.appendChild(el('div', 'sub', category.subtitle));
      row.append(grow, el('span', 'count', String(topics.length)), svg(ICON.chevron, 'icon chev'));
      row.addEventListener('click', () => {
        this.expanded = open ? null : category.id;
        this.render();
      });
      frag.appendChild(row);

      if (open) for (const topic of topics) frag.appendChild(this.topicRow(topic));
    }

    frag.appendChild(el('div', 'section-head', 'Kaynak Belgeler'));
    frag.appendChild(el('div', 'section-sub',
      'Konuların derlendiği kulüp dokümanlarının tam metni'));
    for (const doc of this.library.sources) {
      const row = el('button', 'row');
      row.append(svg(ICON.doc), el('div', 'grow', doc.title));
      row.addEventListener('click', () => this.push({ kind: 'source', id: doc.id }));
      frag.appendChild(row);
    }

    this.root.replaceChildren(frag);
  }

  topicRow(topic) {
    const row = el('button', 'row topic-row');
    const grow = el('div', 'grow');
    grow.appendChild(el('div', 'title', topic.title));
    if (topic.summary) grow.appendChild(el('div', 'sub', topic.summary));
    row.appendChild(grow);
    row.addEventListener('click', () => this.push({ kind: 'topic', id: topic.id }));
    return row;
  }

  hitRow(hit) {
    const row = el('button', 'row hit');
    const grow = el('div', 'grow');
    grow.appendChild(el('div', 'title', hit.topic.title));
    if (hit.matchedKeyword) {
      grow.appendChild(el('div', 'kw', `anahtar kelime: ${hit.matchedKeyword}`));
    }
    grow.appendChild(el('div', 'snippet', hit.snippet));
    row.appendChild(grow);
    row.addEventListener('click', () => this.push({ kind: 'topic', id: hit.topic.id }));
    return row;
  }

  // ---------------------------------------------------------------- detail

  page(title, build) {
    const wrap = el('div', 'detail');
    const head = el('div', 'detail-head');
    const back = el('button', 'back-btn');
    back.setAttribute('aria-label', 'Geri');
    back.appendChild(svg(ICON.back));
    back.addEventListener('click', () => this.back());
    head.append(back, el('h1', null, title));

    const scroll = el('div', 'scroll');
    const body = el('div', 'detail-body');
    build(body);
    scroll.appendChild(body);
    wrap.append(head, scroll);
    this.root.replaceChildren(wrap);
    scroll.scrollTop = 0;
  }

  renderTopic(id) {
    const topic = this.library.topic(id);
    if (!topic) {
      this.page('Bulunamadı', (b) => b.appendChild(el('p', null, `Konu bulunamadı: ${id}`)));
      return;
    }
    this.page(topic.title, (body) => {
      if (topic.summary) body.appendChild(el('div', 'detail-summary', topic.summary));
      body.appendChild(this.markdown(topic.body));

      const backlinks = this.library.backlinks(topic.id);
      if (backlinks.length) {
        body.appendChild(el('div', 'rel-head', 'Bu konuya bağlanan konular'));
        for (const other of backlinks) body.appendChild(this.linkRow(other.title, () => this.push({ kind: 'topic', id: other.id })));
      }

      const sources = topic.sourceIds.map((s) => this.library.source(s)).filter(Boolean);
      if (sources.length) {
        body.appendChild(el('div', 'rel-head', 'Kaynaklar'));
        body.appendChild(el('div', 'rel-note', 'Bu konu aşağıdaki kulüp dokümanlarından derlendi.'));
        for (const doc of sources) {
          body.appendChild(this.linkRow(doc.title, () => this.push({ kind: 'source', id: doc.id }), true));
        }
      }
    });
  }

  renderSource(id) {
    const doc = this.library.source(id);
    if (!doc) {
      this.page('Bulunamadı', (b) => b.appendChild(el('p', null, `Kaynak bulunamadı: ${id}`)));
      return;
    }
    const citing = this.library.topics.filter((t) => t.sourceIds.includes(id))
      .sort((a, b) => a.title.localeCompare(b.title, 'tr'));

    this.page(doc.title, (body) => {
      body.appendChild(el('div', 'src-note', 'Kulüp kaynak belgesi — tam metin'));
      if (citing.length) {
        body.appendChild(el('div', 'rel-head', 'Bu kaynağı kullanan eğitimler'));
        for (const topic of citing) {
          body.appendChild(this.linkRow(topic.title, () => this.push({ kind: 'topic', id: topic.id })));
        }
        body.appendChild(el('hr'));
      }
      body.appendChild(this.markdown(doc.body));
    });
  }

  linkRow(text, onClick, withIcon = false) {
    const b = el('button', 'rel-link');
    if (withIcon) b.appendChild(svg(ICON.doc));
    b.appendChild(el('span', null, text));
    b.addEventListener('click', onClick);
    return b;
  }

  // -------------------------------------------------------------- markdown

  markdown(source) {
    const wrap = el('div', 'md');
    let list = null;

    const closeList = () => { list = null; };

    for (const block of parseMarkdown(source)) {
      if (block.kind !== 'li') closeList();

      switch (block.kind) {
        case 'h': {
          const level = Math.min(block.level, 4);
          wrap.appendChild(this.inline(el(`h${level}`), block.text));
          break;
        }
        case 'p':
          wrap.appendChild(this.inline(el('p'), block.text));
          break;
        case 'li': {
          const ordered = block.ordinal != null;
          if (!list || list.ordered !== ordered) {
            const node = el(ordered ? 'ol' : 'ul');
            if (ordered) node.start = Number(block.ordinal);
            wrap.appendChild(node);
            list = { node, ordered };
          }
          list.node.appendChild(this.inline(el('li'), block.text));
          break;
        }
        case 'quote': {
          const q = el('blockquote');
          q.appendChild(this.inline(el('p'), block.text));
          wrap.appendChild(q);
          break;
        }
        case 'table': {
          const box = el('div', 'table-wrap');
          const table = el('table');
          const thead = el('thead');
          const hr = el('tr');
          for (const cell of block.header) hr.appendChild(this.inline(el('th'), cell));
          thead.appendChild(hr);
          const tbody = el('tbody');
          for (const row of block.rows) {
            const tr = el('tr');
            for (let i = 0; i < block.header.length; i += 1) {
              tr.appendChild(this.inline(el('td'), row[i] || ''));
            }
            tbody.appendChild(tr);
          }
          table.append(thead, tbody);
          box.appendChild(table);
          wrap.appendChild(box);
          break;
        }
        case 'hr':
          wrap.appendChild(el('hr'));
          break;
        default:
          break;
      }
    }
    return wrap;
  }

  /**
   * Inline markup into a node. Built by walking the matches rather than by
   * assigning innerHTML, so content can never inject markup into the page.
   */
  inline(node, raw) {
    const pattern = /\[\[([^\]|]+)(?:\|([^\]]+))?\]\]|\[([^\]]+)\]\(([^)]+)\)|\*\*([^*]+)\*\*|\*([^*]+)\*|`([^`]+)`/g;
    let cursor = 0;
    let m = pattern.exec(raw);
    while (m) {
      if (m.index > cursor) node.appendChild(document.createTextNode(raw.slice(cursor, m.index)));
      const [, target, label, linkText, href, bold, italic, code] = m;

      if (target) {
        const id = target.trim();
        const a = el('a', null, label || this.library.labelFor(id));
        a.addEventListener('click', (ev) => { ev.preventDefault(); this.openLink(id); });
        node.appendChild(a);
      } else if (linkText) {
        // Source documents carry the odd http link; the app is offline-first,
        // so these open in the browser rather than inside the tab.
        const a = el('a', 'external', linkText);
        a.href = href;
        a.target = '_blank';
        a.rel = 'noopener noreferrer';
        node.appendChild(a);
      } else if (bold) {
        node.appendChild(el('strong', null, bold));
      } else if (italic) {
        node.appendChild(el('em', null, italic));
      } else if (code) {
        node.appendChild(el('code', null, code));
      }
      cursor = m.index + m[0].length;
      m = pattern.exec(raw);
    }
    if (cursor < raw.length) node.appendChild(document.createTextNode(raw.slice(cursor)));
    return node;
  }
}

export { prettyLabel, LINK };
