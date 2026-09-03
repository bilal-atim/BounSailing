// The Notlar tab. Notes live in this browser only: there is no account and no
// sync, which is also why they are never silently discarded — deleting is an
// explicit action.

const STORE = 'bounsailing.notes';

const load = () => {
  try { return JSON.parse(localStorage.getItem(STORE)) || []; } catch { return []; }
};
const save = (notes) => {
  try { localStorage.setItem(STORE, JSON.stringify(notes)); } catch { /* private mode */ }
};

const stamp = (ms) => new Date(ms).toLocaleDateString('tr-TR', {
  day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit',
});

export class NotesView {
  constructor(root, addButton, toast) {
    this.root = root;
    this.toast = toast;
    this.notes = load();
    addButton.addEventListener('click', () => this.add());
  }

  add() {
    this.notes.unshift({ id: Date.now(), text: '', at: Date.now() });
    save(this.notes);
    this.render();
    const first = this.root.querySelector('textarea');
    if (first) first.focus();
  }

  render() {
    const frag = document.createDocumentFragment();

    if (!this.notes.length) {
      const empty = document.createElement('div');
      empty.className = 'empty';
      empty.innerHTML = '<strong>Henüz not yok</strong>'
        + 'Gezi sırasında aklında kalanları buraya yaz. '
        + 'Notlar yalnızca bu cihazda, tarayıcıda saklanır.';
      frag.appendChild(empty);
    }

    for (const note of this.notes) {
      const row = document.createElement('div');
      row.className = 'note';

      const area = document.createElement('textarea');
      area.value = note.text;
      area.rows = 1;
      area.placeholder = 'Not…';
      const grow = () => {
        area.style.height = 'auto';
        area.style.height = `${area.scrollHeight}px`;
      };
      area.addEventListener('input', () => {
        note.text = area.value;
        note.at = Date.now();
        save(this.notes);
        grow();
      });
      requestAnimationFrame(grow);

      const time = document.createElement('time');
      time.textContent = stamp(note.at);

      const del = document.createElement('button');
      del.className = 'btn ghost';
      del.textContent = 'Sil';
      del.addEventListener('click', () => {
        this.notes = this.notes.filter((n) => n.id !== note.id);
        save(this.notes);
        this.render();
        this.toast('Not silindi');
      });

      row.append(area, time, del);
      frag.appendChild(row);
    }

    this.root.replaceChildren(frag);
  }
}
