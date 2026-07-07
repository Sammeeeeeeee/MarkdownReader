# Markdown Reader 1.1

A big rendering + reading upgrade.

## New

- **In-document search** — a find bar in the reader with live match counts, previous/next navigation, and highlighted hits (including inside code blocks and tables).
- **Text selection** — long-press to select and copy text anywhere in a document.
- **LaTeX math** — `$inline$` and `$$display$$` equations render natively (offline) via JLaTeXMath, scale with pinch-zoom, and follow the theme color.
- **Subscript & superscript** — `H~2~O` and `19^th^` now shift the text instead of showing literal `~` / `^`.
- **Text colors** — inline HTML like `<span style="color:red">` and `<font color="#8000ff">` now colors the text (plus `background-color`, `font-weight`, `font-style`, `text-decoration`).
- **Interactive `<details>` dropdowns** — `<details><summary>` sections render as tappable, animated expanders with fully rendered markdown bodies (respects the `open` attribute).
- **Definition lists** — `Term` / `: definition` blocks render with proper styling.
- **Abbreviations** — `*[HTML]: HyperText Markup Language` definitions are picked up; occurrences are underlined and tappable to reveal the expansion.

## Improved

- **Only markdown opens** — non-markdown extensions and binary files are rejected with a clear error instead of rendering garbage.
- **Images** — a proper loading indicator while fetching, a tidy "image unavailable" card (with alt text and URL) instead of a broken-image glyph, and support for base64 `data:` URIs.
- **Footnotes** — `[^1]` references render as tappable superscript links that jump to the footnotes section (and unresolvable references degrade gracefully).

