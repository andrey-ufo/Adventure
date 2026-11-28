# Theming

**Status:** stable (U13, 2026-11-28).

This document is the contract for app themes, reader themes and author
themes. U14 implements the runtime; U15 (Nice, roadmap) adds the author
theme editor. Anything not specified here is deliberately out of scope.

## Decisions

| Area | Decision |
|---|---|
| Token architecture | **2-tier flat semantics**: app tokens + reader tokens; no component-token tier. |
| Author theme file | `theme.json` beside the quest file (folder layout). |
| Quest packaging | **Folder-first**; ZIP is an import/export transport, not the live runtime format. |
| User themes | App-private storage is canonical; explicit JSON import/export. |
| Reader modes | **Author / Adaptive / Override**; Adaptive is the default. |
| Accessibility | User accessibility always wins over author and app themes. |

## Storage layout

Canonical quest folder (implemented by U20):

```text
Adventures/
  my-quest/
    quest.json          // later: manifest + volume-*.json
    theme.json
    assets/
    audio/
```

A quest theme belongs only to its folder. User themes never live in the
quest folder.

### Locator

U14 reads only `<quest-dir>/theme.json`. Flat `<quest-file>.theme.json`
files are **not** supported: alpha storage was migrated to folders in
U20, and there are no users to preserve. No file → default theme.

### ZIP transport

Folder remains the source of truth. A distributable archive is:

```text
manifest.json
quest.json              // or volume files later
theme.json
assets/...
audio/...
```

Import extracts into a managed folder/SAF destination; export builds the
same archive. The app does not run a quest directly from an unextracted
ZIP (no zip-bomb, partial-write or random-access risks). Archive support is
future work, not U14.

## User theme storage

| Option | Pros | Cons |
|---|---|---|
| **App-private DataStore + import/export (chosen)** | Atomic, private, no SAF dependency; portable only when the user exports | Import/export UI needed |
| App-private only | Simplest | Themes lost with app data; not portable |
| `themes/` in the adventure folder | Visible/portable | User can corrupt files; conflicts; extra validation |
| External SAF folder | Portable/syncable | Permissions, unstable URIs, failure modes |

Built-in themes are compiled. User themes use stable ids like
`custom_<uuid>`. Author themes are read-only sidecars and are **not**
automatically added to the user's theme list; “save author theme as my
theme” is a later feature.

App-private storage does not prevent a future “export all / import” flow;
it keeps the live selection independent of external file availability.

## Token contract

### Tier 1 — app semantics

These style app chrome (menus, settings, library, editor shell) and are
the fallback for the reader when no author theme applies.

**May change**

- Semantic colours: background, surface, surface variant, primary,
  on-primary, secondary, tertiary, error, outline, status colours.
- Interface font family and base scale.
- Shapes (corner radii), spacing/density.
- Reader default colours and typography.

**May not change**

- Navigation, back stack, screen layout or content.
- Focus order, TalkBack semantics, accessibility controls.
- Quest content, choices or assets.
- System bar behaviour.

The current `AppThemeConfig` already represents this tier.

### Tier 2 — reader semantics

These style the reading surface and its editor preview.

**May change**

- `background`, `text`, `muted`, `accent`.
- `choice_background`, `choice_text`, `choice_border`.
- `hud_background`, `hud_text`, `divider`, `selection`, `warning`.
- Reader font family, base scale, line-height scale (within limits).
- Paragraph spacing, content/column spacing, corner radii.
- Optional relative `assets/background_image` (U14 may defer rendering).

**May not change**

- App chrome outside the reader/preview.
- Navigation, screen structure, HUD position or behaviour.
- Focus order, semantics, disabled-choice announcements.
- Hiding accessibility controls or forcing reduced motion/high contrast.
- Arbitrary CSS/JS, remote URLs or network assets.

Author themes use the narrower `QuestThemeConfig`; they do not get the
full `AppThemeConfig` surface.

## Reader modes and priority

Modes apply to the reader and its editor preview:

| Mode | Author colours/shapes/spacing | Typography | Use |
|---|---|---|---|
| **Author** | author | author | Maximum atmosphere |
| **Adaptive** (default) | author | user font family + size | Accessible atmosphere |
| **Override** | ignored | user | Predictable personal reading |

Priority, highest first:

```text
1. User accessibility: high contrast, reduced motion, minimum font size,
   focus/semantics
2. Selected reader mode
3. Author theme (reader/preview tokens)
4. User app/reader theme
5. Built-in default
```

The app theme controls app chrome; the reader theme is the user's fallback
when the author theme is absent or Override is selected. A user must be
able to select both and to disable an author theme.

Per-property fallback is allowed: if an author token is missing or invalid,
use the next lower layer for only that token, not the whole theme.

## JSON schema

Quest theme example (also parsed by `QuestThemeConfigTest`):

```json
{
  "schema_version": 1,
  "id": "author_old_book",
  "display_name": "Старая книга",
  "light": {
    "background": "#FAF5EA",
    "text": "#1C1410",
    "muted": "#5D4037",
    "accent": "#5D4037",
    "choice_background": "#EFE5D5",
    "choice_text": "#1C1410",
    "choice_border": "#A1887F",
    "hud_background": "#EFE5D5",
    "hud_text": "#1C1410",
    "divider": "#D7CCC8",
    "selection": "#5D4037",
    "warning": "#8F5000"
  },
  "dark": {
    "background": "#000000",
    "text": "#E8DBC8",
    "muted": "#B0A398",
    "accent": "#D4B896",
    "choice_background": "#1C1815",
    "choice_text": "#E8DBC8",
    "choice_border": "#8A7D70",
    "hud_background": "#1C1815",
    "hud_text": "#E8DBC8",
    "divider": "#3A342E",
    "selection": "#D4B896",
    "warning": "#F0B060"
  },
  "typography": {
    "font_family_id": "serif",
    "base_scale": 1.05,
    "line_height_scale": 1.10
  },
  "shapes": {
    "cornerSmall": 3,
    "cornerMedium": 6,
    "cornerLarge": 12
  },
  "spacing": {
    "xs": 4,
    "sm": 8,
    "md": 16,
    "lg": 24,
    "xl": 32
  },
  "assets": {
    "background_image": "assets/paper.png"
  }
}
```

Rules:

- `schema_version` is required for new files; missing means version 0 and
  is upgraded/ignored according to the reader's migration policy.
- Colours are `#RRGGBB` or `#AARRGGBB` (the existing `HexColorSerializer`).
- `id` matches `[a-z0-9_]+`; reserve `author_*` for quest themes and
  `custom_*` for user themes.
- `display_name` is shown in theme selection/preview.
- `assets` paths are relative to the quest folder; no `..`, absolute
  paths, `file:`/`http:` URLs.
- Unknown fields are ignored for forward compatibility; unknown future
  schema versions fall back with a log, never crash.
- Invalid colours/JSON or a failed contrast check fall back per property
  and are logged; the reader remains usable.
- Font ids refer to `FontManager`; an unavailable font falls back to the
  user/default font.

## Accessibility and readability

- High contrast, reduced motion, minimum font size, focus and semantic
  labels are user-owned and cannot be disabled by an author.
- Author colours are checked for WCAG AA text contrast; failures produce a
  warning (U14) and fallback for the affected token.
- Author font family is used only in the reader/preview; it never changes
  app chrome.
- Author assets must be local and validated; no remote URLs.
- A missing/invalid theme must leave the reader fully usable.

## Implementation split

- **U13.1** storage layout + canonical locator + archive transport (this document).
- **U13.2** token lists and app/quest boundaries.
- **U13.3** user-theme storage/import/export and reader modes.
- **U13.4** JSON schema, example, fallback rules, parsing test.
- **U13.5** stable status, index link, checklist/progress log.

U14 implements `ThemeLocator`, `QuestThemeConfig` loading, mode selection,
preview, fallback and contrast warnings. U15 adds the author theme editor.

## Out of scope

- HTML/CSS/JS themes, WebView sandboxing or arbitrary runtime code.
- Per-component token tiers, 12-step palettes and OKLCH/HCT generation.
- Cloud sync, cross-device theme sync, marketplace, version history.
- Quest folder migration itself and ZIP import/export UI.
- Publishing quest themes to Library/user themes.
