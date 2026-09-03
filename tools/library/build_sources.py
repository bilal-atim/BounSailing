import os, re, json, unicodedata

SRC = "Resources"
DST = "android/app/src/main/assets/library/sources"

TR = str.maketrans("çğıöşüÇĞİÖŞÜ", "cgiosucgiosu")

def slug(name):
    s = os.path.splitext(name)[0].translate(TR)
    # Some titles carry Turkish letters as base + combining mark (I + U+0307);
    # decompose first so the marks can be dropped instead of splitting the slug.
    s = unicodedata.normalize("NFKD", s)
    s = "".join(c for c in s if not unicodedata.combining(c))
    s = s.replace("\u0131", "i").lower()
    s = re.sub(r"[^a-z0-9]+", "-", s).strip("-")
    return s

# Human titles for the noisier filenames.
TITLES = {
    "1 yıldız teorik kitabı.md": "1. Yıldız Teorik Kitabı",
    "2 yıldız teorik kitabı.md": "2. Yıldız Teorik Kitabı",
    "DENİZDE ÇATIŞMAYI ÖNLEME KURALLARI.md": "Denizde Çatışmayı Önleme Kuralları",
    "HABERLEŞME.md": "Haberleşme",
    "ilk yardım.md": "İlk Yardım",
    "Yarışçılık-1.md": "Yarışçılık",
    "Yelken Fiziği.md": "Yelken Fiziği",
    "Yelken Tarihi ve Tekne Donanımları.md": "Yelken Tarihi ve Tekne Donanımları",
    "Seyirler ve Manevralar.md": "Seyirler ve Manevralar",
    "Meteoroloji.md": "Meteoroloji",
    "Balon.md": "Balon",
    "acil-durum-senaryolari.md": "Acil Durum Senaryoları",
    "basustunun-el-kitabi.md": "Başüstünün El Kitabı",
    "cift-yelkenli-ve-tek-omurgali-teknelerde-temel-trim-prensipleri.md": "Temel Trim Prensipleri",
    "demirleme.md": "Demirleme",
    "dugum-1.md": "Düğümler 1",
    "dugum-2.md": "Düğümler 2",
    "gece-seyrinde-dikkat-edilmesi-gereken-hususlar.md": "Gece Seyri",
    "gezi ve yelken seyri.md": "Gezi ve Yelken Seyri",
    "gezi-egitimleri-el-kitabi.md": "Gezi Eğitimleri El Kitabı",
    "gezi.md": "Gezi Eğitimleri (Sunum)",
    "sert hava seyri.md": "Sert Hava Seyri",
    "trim.md": "Yelken Trimi",
    "gezi-programi-ve-checklist.md": "Örnek Gezi Programı ve Checklist",
    "temel-denizcilik-terimleri.md": "Temel Denizcilik Terimleri",
}

NORM_TITLES = {unicodedata.normalize("NFC", k): v for k, v in TITLES.items()}

PIC = re.compile(r"<!-- Start of picture text -->.*?<!-- End of picture text -->", re.S)
# A line that is mostly OCR debris: few real words, lots of punctuation/short tokens.
def is_noise(line):
    s = line.strip()
    if not s:
        return False
    if s.startswith("#") or s.startswith("|") or s.startswith(">"):
        return False
    letters = sum(c.isalpha() for c in s)
    if len(s) > 20 and letters / len(s) < 0.55:
        return True
    words = [w for w in re.split(r"\s+", s) if w]
    if len(words) >= 6:
        short = sum(1 for w in words if len(w) <= 2)
        if short / len(words) > 0.45:
            return True
    return False

index = []
for name in sorted(os.listdir(SRC)):
    if not name.endswith(".md"):
        continue
    raw = open(os.path.join(SRC, name), encoding="utf-8").read()
    raw = PIC.sub("", raw)
    raw = raw.replace("<br>", " ").replace("<mark>", "").replace("</mark>", "")
    raw = re.sub(r"</?u>", "", raw)
    out, blank = [], 0
    for line in raw.split("\n"):
        if is_noise(line):
            continue
        if not line.strip():
            blank += 1
            if blank > 1:
                continue
        else:
            blank = 0
        out.append(line.rstrip())
    text = "\n".join(out).strip() + "\n"
    if len(text.strip()) < 400:
        print(f"      -  SKIPPED (metin yok, gorunti tabanli): {name}")
        continue
    s = slug(name)
    title = NORM_TITLES.get(unicodedata.normalize("NFC", name), os.path.splitext(name)[0])
    open(os.path.join(DST, s + ".md"), "w", encoding="utf-8").write(text)
    index.append({"id": s, "title": title, "file": s + ".md", "chars": len(text)})

open(os.path.join(DST, "index.json"), "w", encoding="utf-8").write(
    json.dumps(index, ensure_ascii=False, indent=2)
)
for e in index:
    print(f"{e['chars']:>7}  {e['id']}  —  {e['title']}")
