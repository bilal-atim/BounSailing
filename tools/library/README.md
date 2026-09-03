# Library content pipeline

The Kütüphane tab reads two kinds of content from
`android/app/src/main/assets/library/`:

- `topics/*.md` — the curated training articles, hand written, with YAML-ish
  frontmatter (`title`, `category`, `order`, `keywords`, `summary`, `sources`)
  and `[[topic-id]]` / `[[src:source-id]]` cross-references. Edit these directly.
- `sources/*.md` — the club's original documents, generated from `Resources/`.
  **Do not edit these by hand**; they are overwritten.

## Regenerating the source documents

Run from the repository root after changing anything in `Resources/`:

```sh
python3 tools/library/build_sources.py
```

The script strips the OCR debris the PDF extraction left behind: `<!-- picture
text -->` blocks, `<br>` runs, and lines that are mostly punctuation or
two-letter fragments. Documents that extract to almost nothing are skipped and
reported — three of them are image-only scans with no recoverable text:

- `cift-yelkenli-ve-tek-omurgali-teknelerde-temel-trim-prensipleri.md`
- `demirleme.md`
- `dugum-1.md`

Their subject matter is covered in the topics from other sources.

## Checking the content

`LibraryContentTest` parses these assets straight off disk and fails the build
on a dead cross-reference, a missing frontmatter field, an unknown category, or
an orphaned topic:

```sh
cd android && ./gradlew testDebugUnitTest
```
