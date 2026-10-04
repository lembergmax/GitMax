# GitMax – Design

[Deutsch](DESIGN.de.md)

The built system, not the draft: every value is exactly as in `src/main/res/values*` and in the code. Tone according to
`PRODUCT.md`: **soft and friendly**. Mode (Impeccable): **Operate**; Material 3 governs structure and operation, the brand
lives in colour roles, typeface, shape and the pebble.

## Colour

Strategy **Committed**: a tinted ground instead of neutral grey, one accent that deliberately differs from the status colours.

| Role | Light | Dark |
|---|---|---|
| Ground / surface | `#F1F9F4` (sage) | `#0F1611` (forest grey) |
| Text | `#112117` | `#DFE9E2` |
| Accent `primary` (berry / plum, hue ≈ 330°) | `#8D1767` | `#F19BCC` |
| `primary_container` | `#FDD3EA` | `#661B4B` |
| `secondary` / container (sage green) | `#31573F` / `#C2E5CD` | `#A0CAAD` / `#24422F` |
| `tertiary` (blue) | `#015493` | `#88C0F9` |
| `error` | `#B00C15` | `#FA887D` |
| Git: added (green) | `#016129` | `#75D78D` |
| Git: changed (amber) | `#734C02` | `#F5B75B` |
| Git: conflict (coral) | `#953B01` | `#FF9A6B` |

- All colours are Material 3 roles from `colors.xml` and `values-night/colors.xml`; layouts and code only use `?attr/…`,
  never hex values. Dark is its own scheme, not an inverted light.
- Status is **never carried by colour alone**: the pebble glyph, an icon and text always come with it.
- The accent is deliberately not green, amber, red or blue, because exactly those four carry status.
- Contrast: text on surface at least 4.5 : 1, controls 3 : 1 (computed with `palette.py`); the syntax colours in the viewer
  reach at least 5.3 : 1 against the ground.
- System colours (Dynamic Color) are a display option and off by default so the brand stays.

## Typography

- **Figtree** (variable) for the interface, **Commit Mono** for code, hashes and paths; both SIL OFL 1.1, bundled under
  `res/font/`, licence texts in the About screen.
- Material 3 roles (`textAppearanceDisplay…Label`), sizes as in Material, weights: headlines 700, titles 600, text 400,
  labels 600. The family lives in every text appearance, not in the theme (see `CLAUDE.md`, “No theme-wide fontFamily”).
- Hashes, paths and counters are set in Commit Mono (equal width by nature); live-changing numbers (progress) use
  `fontFeatureSettings="tnum"`.
- Headlines and titles wrap balanced (`breakStrategy="balanced"`), never italic.

## Shape and space

- 4 dp grid: `space_1…12` = 4, 8, 12, 16, 20, 24, 32, 40, 48 dp. Side margin 16 dp; from 600 dp width the content stays
  limited to 640 dp.
- Radii `xs 8 · s 12 · m 16 · l 24 · xl 28 · full`, **concentric**: outer radius = inner radius + padding. Banners and cards
  use `m`, the tonal buttons in the repo header `l`.
- Tonal elevation instead of free shadows.
- Touch targets at least 48 dp; decorative icons (24 dp) are not focusable.
- From 600 dp width the bottom bar becomes a **navigation rail** (96 dp wide so “Settings” is not cut off); it appears only on
  the four main destinations, sub-pages have a back arrow.

## The pebble (signature)

Every repo is a soft pebble with a status ring (`PebbleView`): clean (check), changed (pencil), ahead (arrow), behind,
diverged, conflict (dashed ring, warning sign). The same ring is the **progress indicator** during clone and update
(determinate or circling, 1.4 s) and tips into a check mark at the end. It appears in the list, the detail header, Activity
and the notification. Success stays quiet: a change of state, no confetti.

## Motion

All durations are tokens in `integers.xml`, easing in `res/interpolator`, access through `ui/common/Motion`.

| Token | Value | Use |
|---|---|---|
| `motion_micro` | 80 ms | press-in (scale 0.96) |
| `motion_quick` | 150 ms | closing sheets and dialogs, release after press |
| `motion_fast` | 250 ms | opening sheets and dialogs, page changes, icon swap, pebble |
| `smooth_out` | cubic-bezier(0.22, 1, 0.36, 1) | default for surface motion |
| `ease_in_out` | cubic-bezier(0.42, 0, 0.58, 1) | icon and text swap |

- **Opening is slower than closing:** a sheet slides in over 250 ms and out over 150 ms; dialogs cross-fade with scale 0.96
  (250 ms in, 150 ms out). Page changes and icon swaps are symmetric (250 ms).
- Drilling into a page: Shared Axis X with only **8 dp** of travel (`motion_distance_page`); between the main destinations:
  Fade-Through.
- Press feedback (`press_scale`, 0.96) only on primary buttons, the FAB and navigation chips, never on list rows.
- Selection check and icon swap: scale 0.25 → 1, alpha 0 → 1, blur 4 → 0 dp.
- High-frequency taps (staging, row selection) have no animation of their own; no entrance animation on first draw.
- “Remove animations” in the system settings affects all animators (duration 0); the pebble then stays still.
- Deliberately not done: blur on page changes (Material's transitions have none), staggered entrances (the app has no rare,
  large entrances).

## Components and states

Every operable component knows default, pressed, focus, disabled, loading, error, success and selected as far as they make
sense for it. Lists have loading, empty, error and offline states with a next action (e.g. “Try again”). Transient feedback
comes as a snackbar (with action), dialogs only for decisions that must interrupt; destructive actions ask and say what is
lost; irreversible ones (force push, delete from device) ask for the repo name to be typed.

- **Banners:** errors and obstacles in `colorErrorContainer`, hints (e.g. Git LFS) in `colorSecondaryContainer`; text and
  action are stacked so nothing breaks mid-word at large type.
- **Action rows** (Update · Commit · Push) are stacked from font scale 1.5 (`AdaptiveRow`).
- Viewer and editor: line numbers, syntax colouring from theme attributes (`colorSyntax…`, chosen separately for light and dark).

## Accessibility

- Type up to 2.0 without clipping (checked on Local, repo detail, commit sheet, Discover, Activity, Settings).
- Every operable element carries text or a description; selection and status through the row's description, not colour.
- Headings are marked as such (`accessibilityHeading`), `supportsRtl` is on. Back gesture and key use the classic path;
  predictive back is deliberately off (`enableOnBackInvokedCallback=false`, reasons in the manifest and in `CLAUDE.md`).
- Counted texts use singular and plural (“1 file”, “2 files”).
- Language: English (default) and German, selectable in Settings or per app in Android.

## Open / deliberately different

- The Impeccable CLI and the finish-reviewer agents were not installed on the development machine; the rules were applied by
  hand and the final check was a fresh in-thread review.
- Emulator evidence is not hardware evidence: neither type rendering nor motion has been seen on a real device.
