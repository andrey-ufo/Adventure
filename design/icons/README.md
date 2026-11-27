# Editor save icons

Source SVGs live here; they are **not** compiled by Android. Convert them to
`app/src/main/res/drawable/ic_editor_*.xml` (`VectorDrawable`) before use.

## Format

- Canvas: `width="24" height="24" viewBox="0 0 24 24"`.
- Display size in the app: `24.dp`.
- Single colour: `#1F1F1F` (fallback; the UI tints the vector per state).
- Transparent background; no raster image, text, gradient, filter or shadow.
- Strokes: `2px`, `stroke-linecap="round"`, `stroke-linejoin="round"`.
- Keep the drawing inside a 20×20 safe area (2px padding from each edge).
- Use simple paths; avoid CSS classes and external `<defs>` references.

## Files

| File | Purpose | UI state |
|---|---|---|
| `ic_editor_draft_syncing.svg` | Two circular arrows | Draft autosave in progress |
| `ic_editor_draft_saved.svg` | Check mark (optionally circular) | Draft saved |
| `ic_editor_file_dirty.svg` | Floppy disk | File is older than the draft |
| `ic_editor_file_saved.svg` | Check with underline (distinct from the draft check) | File matches the draft |

The draft and file states are independent: a draft can be saved while the
file is still dirty. Colour and `contentDescription` supplement the icon;
they are not the only signal.

## Conversion

After the SVGs are here, they are converted to vector XML manually (paths
and `android:tint` compatibility checked), then referenced from Compose with
`painterResource(R.drawable.ic_editor_*)`.
