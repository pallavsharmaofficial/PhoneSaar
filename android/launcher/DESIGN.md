# design.json reference

Everything the launcher draws comes from one JSON file. Unknown or invalid keys never crash it: each field falls back to its default and shows up in the orange "design warnings" chip (and in Settings).

## Where the design comes from

1. **Pushed from your computer**: `tools/launcher-design.sh push my.json` (or `watch my.json` to push on every save). The launcher reloads in about a second, without restarting.
2. **A theme you made in the app** (Customize, then Duplicate or edit anything).
3. **A built-in preset**: `mono-cards` (default), `default` (Sphere), `minimal`, `neon`, `paper`.

The newest source wins: pushing a file makes "Pushed from computer" the active theme; choosing a theme in the app switches back. Editing in the app while "Pushed from computer" is active writes back into the same design.json, so the file and the editor stay interchangeable.

### How the push works

Android 11+ blocks `adb push` into `Android/data` (even for the shell), so the script writes through the launcher's own content provider (`content://dev.pallav.phonesaar.launcher.design/design.json`), which only accepts the adb shell. It falls back to `adb push` on older phones. Then it sends `adb shell am broadcast -a dev.pallav.phonesaar.launcher.RELOAD -p dev.pallav.phonesaar.launcher` (a `--es preset neon` extra switches preset). The file lives at `/sdcard/Android/data/dev.pallav.phonesaar.launcher/files/design.json` and is also watched with a FileObserver and re-checked on resume.

After **reinstalling** the APK Android forgets the default Home app: run `tools/launcher-design.sh home` again.

## Top level

```json
{ "name": "My theme", "theme": {}, "layout": [], "gestures": {}, "sphere": {}, "features": {} }
```

| Key | Type | Meaning |
|---|---|---|
| `name` | text | shown in the themes gallery |
| `theme` | object | colours, fonts, shape |
| `layout` | list or object | vertical block list, or a grid object (below) |
| `gestures` | object | swipe, double tap, long press |
| `sphere` | object | the app sphere |
| `features` | object | `badges`, `designChip` (warnings chip), `haptics` |

Colours are `#RRGGBB` or `#AARRGGBB` (alpha first, like Android), or `#RGB`.

## theme

| Key | Values | Default |
|---|---|---|
| `background` | `wallpaper` \| `solid` \| `gradient` | `wallpaper` |
| `backgroundColor` | colour (solid) | `#101418` |
| `gradient` | 2 to 4 colours | dark blue to black |
| `gradientAngle` | 0 to 360 | 90 |
| `scrim` | colour, dims the wallpaper | `#66000000` |
| `accent`, `text`, `textDim` | colours | light on dark |
| `surface` | card colour with alpha | `#33FFFFFF` |
| `fonts` | `{clock, display, body, signature}`, each `default` \| `serif` \| `mono` \| `rounded` \| `anton` \| `signature` \| `inter` \| `baskerville` | default; signature = `signature` |
| `font` | body font (older shorthand) | |
| `fontScale` | 0.7 to 1.6 | 1 |
| `cornerRadius` | 0 to 48 dp | 20 |
| `blur` | glass: wallpaper blur on Android 12+, plain alpha below | true |
| `shadow` | 0 to 1 | 0 |
| `neumorphic` | light top-left plus soft grey bottom-right shadow (Android 9+) | false |
| `spacing` | 0.5 to 2, multiplies gaps | 1 |
| `iconShape` | `system` \| `circle` \| `squircle` \| `none` | `system` |
| `iconStyle` | `color` \| `mono-circle` (monochrome layer on Android 13+, else a white silhouette on a black circle) | `color` |
| `lightStatusIcons` | white status bar icons | false |

## layout

A list of blocks stacks them top to bottom. `weight` shares the free height (the sphere defaults to 1).

```json
"layout": [ { "type": "clock", "size": 64 }, { "type": "sphere", "weight": 1 }, { "type": "dock" } ]
```

A **grid** object flows blocks like a masonry wall. Heights are counted in rows of `rowUnit` dp; `col` pins a block to a column, otherwise it goes in the shortest one. The `dock` block always sits fixed at the bottom.

```json
"layout": { "mode": "grid", "columns": 2, "gap": 12, "rowUnit": 64, "blocks": [
  { "type": "clock", "col": 0, "rowSpan": 3, "style": "card", "name": "Your Name", "title": "CREATIVE DESIGNER" },
  { "type": "weather", "col": 1, "rowSpan": 2, "city": "Pune" },
  { "type": "dock", "colSpan": 2, "indicator": true }
] }
```

Every block accepts `type`, `hidden`, `padding` (dp), and in a vertical list `weight`, in a grid `colSpan`, `rowSpan`, `col`.

### Block types and their options

| type | options |
|---|---|
| `clock` | `style` simple\|card, `format` (e.g. `HH:mm`, `hh:mm`), `size`, `align` start\|center\|end, `date`, `dateFormat` (SimpleDateFormat), `name` and `title` (card style signature) |
| `greeting` | `size`, `align`, `name`; Hindi or English by language and time of day |
| `sphere` | none, see `sphere` |
| `notifications` | `maxItems`, `showOngoing`, `title`; grouped feed, swipe to dismiss, tap to open, inline reply, action buttons, read aloud |
| `dock` | `items`, `labels`, `drawerButton`, `indicator` (the thin pill above) |
| `search` | `mic`, `placeholder`; typed text runs commands, anything else opens the drawer filtered |
| `favorites` | `rows`, `cols`, `items`, `labels` |
| `spacer` | `weight` |
| `weather` | `city`, `unit` c\|f, `useLocation` (coarse location, asks permission); Open-Meteo, cached, shows the last value offline |
| `photo` | `mask` brush\|rounded\|circle, `grayscale`; tap to choose a photo (Photo Picker, no storage permission) |
| `music` | `heart`; follows the active media session (needs notification access) |
| `status` | `moods` (list of texts; tap cycles) with battery |
| `applist` | `items`, `maxItems`, `labels` |
| `calendar` | `events` (dots; asks for calendar permission), `weekStart` sun\|mon |
| `google` | `text`, `action` search\|voice |

`items` entries are package names (`com.whatsapp`), roles (`role:phone`, `role:browser`, `role:gallery`, `role:calendar`, `role:messages`, `role:music`, `role:email`, `role:maps`, `role:camera`) or `auto:topN` (your N most used). A missing app is replaced by the next most used one.

## gestures

Each of `swipeUp`, `swipeDown`, `doubleTap`, `longPress` takes `drawer`, `shade`, `lock`, `settings` (opens Customize) or `none`.

* `shade` calls `StatusBarManager.expandNotificationsPanel` by reflection; if the system blocks it, the in-launcher notification feed opens instead.
* `lock` is a documented no-op: locking the screen without device-admin or accessibility access is not possible, so the launcher shows a short message instead of asking for that power.
* `voiceOnLongPressEmpty`: true makes a long press on empty space start voice instead.
* In a scrolling grid, swipe up on the dock, or use the apps button, to open the drawer.

## sphere

| Key | Range | Meaning |
|---|---|---|
| `radius` | 0.2 to 0.6 | fraction of the width (never more than fits the height) |
| `apps` | 6 to 80 | how many apps |
| `iconMin`, `iconMax` | dp | smallest and biggest icon; most used is biggest and starts at the front |
| `labels` | bool | names on the front icons |
| `autoRotate` | deg/s, negative reverses | idle spin |
| `friction` | 0.2 to 8 | how fast a fling slows |
| `rank` | `usage` \| `launches` \| `alphabetical` | usage uses the last 30 days when Usage access is granted, else the launcher's own tap counter, else A to Z |
| `highlightTop` | 0 to 20 | glow behind the top N |
| `glow` | bool | the glow itself |
| `perspective` | 1.5 to 6 | camera distance in radii |

## Presets as examples

See `src/main/assets/design/*.json`: `mono-cards.json` (grid, white cards), `default.json` (sphere on the wallpaper), `minimal.json`, `neon.json`, `paper.json`.

## Developer loop

```
tools/launcher-design.sh -s <serial> home          # make it the Home app
tools/launcher-design.sh -s <serial> grant         # usage access, notification access, runtime permissions
tools/launcher-design.sh -s <serial> watch my.json # push on every save
tools/launcher-design.sh -s <serial> preset neon
tools/launcher-design.sh -s <serial> photo me.jpg  # set the photo card
tools/launcher-design.sh -s <serial> screenshot out.png
```
