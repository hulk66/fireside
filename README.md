# Immich Fire TV Slideshow

A native Android TV / Fire OS app that streams albums from a self-hosted
[Immich](https://immich.app) server and plays them back as ambient slideshows in
ten different styles, in the spirit of the classic Apple TV screensavers.

Built with Kotlin, Jetpack Compose for TV and a hand-written `DrawScope`
renderer, so every theme is a pure function of time — the same code paints the
full-screen show and the animated thumbnails in the theme picker.

---

## Features

- **Album browser** — covers, photo counts and dates straight from Immich.
- **Ten themes** — Classic, Ken Burns, Slide, Flip, Fold, Shifting Tiles,
  Reflections, Scrapbook, Origami, Photo Grid.
- **Fill or fit** — photos can cover the screen (crop, no bars) or be shown
  whole in their original aspect ratio on a dark backdrop.
- **Flickr-style Photo Grid** — photos keep their aspect ratio, rows are
  justified edge to edge, and the wall turns over one calm page at a time.
- **Live theme picker** — every tile is an animated preview rendered by the real
  slideshow renderer, not a static image.
- **Downsized previews** — photos are fetched from Immich's `preview` rendition
  (`/api/assets/{id}/thumbnail?size=preview`), never the original, so a show
  costs a few hundred KB per photo even for 50 MP raws.
- **Demo mode** — no server needed; 14 procedurally painted landscapes (multiple
  palettes, landform styles and foreground silhouettes) so the app is fully
  usable out of the box.
- **Remote-first UI** — D-pad navigation, no touch targets, no IME popping up on
  focus, auto-hiding playback chrome.
- **Runtime configuration** — server URL and API key are entered on-screen (or
  passed as intent extras); nothing is hard-coded.
- **Resilient playback** — LRU bitmap caches, two-way parallel decoding with
  retry, and prefetching of the next photos while the current one holds.

## Requirements

| | |
|---|---|
| Android | 6.0 (API 23) and up — Fire OS 6/7/8, Android TV |
| Build | JDK 17+ (tested on 21), Android SDK 35, Gradle wrapper 8.11.1 |
| Immich | any recent version; an API key with `album.read` + `asset.view` |

## Build & install

```bash
./gradlew :app:assembleDebug
adb connect <fire-tv-ip>:5555          # if not already paired
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On the TV the app appears in **Your Apps → All Apps** (leanback launcher icon
and banner are included). To launch it from a shell:

```bash
adb shell am start -n com.firetv.immichslideshow/.MainActivity
```

### Running on a TV emulator

```bash
emulator -avd FireTV_TV_API36 -no-snapshot -no-boot-anim -gpu host -no-audio \
         -dns-server 192.168.0.2
```

Pass `-dns-server` with your LAN resolver. Without it the AVD advertises an
IPv6 resolver (`fec0::3`) that it cannot reach, and after a while *every* lookup
fails — the app then reports "the TV couldn't look up that name", which looks
like a server problem but is the emulator's resolver. A Fire TV on the real LAN
does not have this issue.

### Provisioning without typing on a remote

```bash
adb shell am start -n com.firetv.immichslideshow/.MainActivity \
  -e immich_url  "http://192.168.0.10:2283" \
  -e immich_api_key "XXXXXXXXXXXXXXXXXXXXXXXX"
```

Add `-e demo true` to jump straight into the generated demo library. The
`immich_base_url` / `immich_url` keys are both accepted; the URL may be given
with or without a scheme or a trailing `/api`.

## Using it

| Key | Action |
|---|---|
| ▲ ▼ ◀ ▶ | move focus / previous-next photo while playing |
| OK / Enter / Space | select, apply, pause & resume |
| MENU | open the theme picker while playing |
| 1 – 0 | jump straight to theme 1…10 |
| BACK | close the picker / leave the album / exit |

Settings live in `immich_slideshow.xml` (`slideshow_effect`, `slideshow_seconds`,
`slideshow_shuffle`, `slideshow_caption`, `slideshow_vignette`, `slideshow_pan`,
`slideshow_fit`) and are re-applied live while a show is running.

`Fit` in the theme picker is the aspect-ratio switch: off (the default) crops
each photo to fill the screen, on shows the whole frame with bars. The Photo
Grid keeps filling regardless — it is a mosaic of crops, and letterboxing every
tile would fill the wall with bars.

## Architecture

```
data/          Immich REST client (Retrofit + kotlinx.serialization), config,
               repository, prefs, demo artwork generator, bitmap source
slideshow/     pure playback core: effects, timing, motion, easing, RNG,
               frame model, controller (clock + caches), renderer
ui/            theme, shared components, screens (setup, albums, player,
               theme picker) and the four-route navigator
```

The interesting split is between **what** is on screen and **how** it is painted:

- `SlideshowFrame` is an immutable snapshot — effect, index, transition/hold
  progress, photos, prefs, bitmaps.
- `SlideshowRenderer.renderSlideshow(frame, textMeasurer)` is a stateless
  `DrawScope` extension that turns that snapshot into pixels. No composition
  state, no coroutines: the player and the picker thumbnails call the very same
  function with a different canvas size and time.
- `SlideshowController` owns the clock and the bitmaps: it advances the timeline,
  keeps a small LRU of decoded frames, and prefetches ahead of the playhead.

Motions are driven by a splitmix64 hash of the photo id, so a given photo always
pans and zooms the same way — previews do not jitter between recompositions.

Captions are sized relative to the canvas (`height * 0.052`), which is why a
40 dp thumbnail and a 1080p frame both read correctly from one code path.

The Photo Grid is laid out by `JustifiedGrid`: a dynamic program splits the page
into rows so that every row is filled edge to edge at a similar height. A row's
height is fixed by its photos' aspects, so a stack rarely adds up to the frame
exactly; the finished mosaic is scaled uniformly to cover the frame and centred,
which crops the surplus at the edges. Cells are never stretched — squeezing the
rows to fit is what used to bend portrait photos into portrait-shaped holes. `GridPlan` owns the
page clock — twelve photos stay on the wall for 16 s, the tiles flip over once at
staggered moments, and the prefetch window is exactly two pages.

The Scrapbook desk is a pile of up to twenty polaroids. Each card keeps the place
it was given when it arrived — the spot is derived from the photo id, so a card
never wanders while it is on screen — and the newest card lands on top, partly or
wholly covering older ones. When the pile is full the oldest card slides off the
bottom. `ScrapbookDesk` owns the pile: which photos are on the desk, which one
leaves, and each card's scattered placement.

Cards are big (22–40 % of the frame width), vary in size, and are allowed to run
past the screen edges, so the pile covers the table instead of sitting on it.
Only the newest three carry a caption — with twenty cards the rest are mostly
buried. Scrapbook cards are the one place that never needs a full-frame decode,
so that theme keeps only the tile cache warm.

`GridLayers` turns a page into paint layers so the wall on screen keeps its own
layout until a tile turns over: a photo on its way out is still drawn in the cell
the *old* layout gave it, and only the arriving photo takes the new layout's
cell. Otherwise the incoming layout would re-flow photos that have not changed
yet, and the whole wall would visibly jump before a single photo swapped.

## Layout notes

A 1080p Fire TV reports roughly **960 × 540 dp** at 320 dpi, so vertical space is
the scarce resource. Screens keep their own content scrollable, use compact type
scales and avoid fixed-height hero blocks; the picker fits a 5 × 2 grid of 16:9
tiles above a single row of preference chips.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

`SlideshowCoreTest` covers the timeline (index/transition/hold/progress
arithmetic, wraparound), the deterministic RNG, easing curves, motion viewports
and effect metadata — 47 assertions that pin down the playback contract the
renderer relies on.
