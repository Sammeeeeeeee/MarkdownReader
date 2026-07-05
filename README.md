# Markdown Reader

A fast, expressive Android markdown viewer — built to be the default app for `.md` files and to showcase [Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive).

## What it does

- **Registers as a handler for markdown files** (`text/markdown`, `text/x-markdown`, and `*.md` / `*.markdown` / `*.mkd` path patterns over `content://` and `file://`), plus receives shared markdown text via the share sheet.
- **Full-spec rendering**: parses with [commonmark-java](https://github.com/commonmark/commonmark-java) (the reference CommonMark 0.31.2 implementation) with the GFM extension set — tables, strikethrough, autolinks, task lists, footnotes, alerts/callouts (`[!NOTE]`…`[!CAUTION]`), YAML front matter, `++ins++`, and image attributes — then renders every node natively in Jetpack Compose (no WebView).
- **Pinch to zoom**: two-finger pinch rescales the type with full reflow; single-finger scrolling is untouched.
- **Index**: a Contents sheet built from the document's headings, with GitHub-style anchor slugs so `[](#heading)` links jump correctly.
- **M3 Expressive throughout**: `MaterialExpressiveTheme` with springy motion, emphasized typography (Space Grotesk display + JetBrains Mono code), a vibrant violet/raspberry/amber scheme, shape-morphing buttons, `MaterialShapes` decorations, a morphing `LoadingIndicator`, a wavy reading-progress indicator, a flexible large top app bar with subtitle, and a FAB menu.

## Build & install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17+. The build targets compileSdk 36 with material3 1.4.0.

## Structure

- `markdown/` — parser façade over commonmark-java + the Compose renderer (blocks, inlines, tables, code with lightweight syntax highlighting).
- `reader/` — the reading screen: collapsing flexible app bar, wavy progress, pinch zoom, TOC sheet, FAB menu.
- `home/` — expressive landing screen with recents and a shortcut to the system default-app settings.
