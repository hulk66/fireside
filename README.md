# Fireside

A native Android TV / Fire OS app that streams albums from a self-hosted
[Immich](https://immich.app) server and plays them back as ambient slideshows in
ten different styles, in the spirit of the classic Apple TV screensavers.

Built with Kotlin, Jetpack Compose for TV and a hand-written `DrawScope`
renderer, so every theme is a pure function of time — the same code paints the
full-screen show and the animated thumbnails in the theme picker.

---

## Features

- **Album browser** — covers, photo counts and dates straight from Immich.
- **Twelve themes** — Classic, Ken Burns, Slide, Flip, Fold, Shifting Tiles,
  Sliding Panels, Reflections, Scrapbook, Vintage Prints, Origami, Photo Grid.
- **Fill or fit** — photos can cover the screen (crop, no bars) or be shown
  whole in their original aspect ratio on a dark backdrop.
- **Reflections** — a bright stage where most steps are a collage (a tall column
  beside two stacked photos, sharing one baseline and one reflection smear) and
  roughly one step in three is a single big photo.
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
adb shell am start -n org.himstedt.fireside/.MainActivity
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
adb shell am start -n org.himstedt.fireside/.MainActivity \
  -e immich_url  "http://192.168.0.10:2283" \
  -e immich_api_key "XXXXXXXXXXXXXXXXXXXXXXXX"
```

Add `-e demo true` (or `--ez demo true`) to jump straight into the generated
demo library — both spellings are accepted. The `immich_base_url` / `immich_url`
keys are both accepted; the URL may be given with or without a scheme or a
trailing `/api`.

## Using it

| Key | Action |
|---|---|
| ▲ ▼ ◀ ▶ | move focus / previous-next photo while playing |
| OK / Enter / Space | select, apply, pause & resume |
| MENU | open the theme picker while playing |
| 1 – 0 | jump straight to themes 1…10 (the last two are in the picker) |
| BACK | close the picker / leave the album / exit |

Settings live in `immich_slideshow.xml` (`slideshow_effect`, `slideshow_seconds`,
`slideshow_shuffle`, `slideshow_caption`, `slideshow_vignette`, `slideshow_pan`,
`slideshow_fit`) and are re-applied live while a show is running.

`Fit` in the theme picker is the aspect-ratio switch: off (the default) crops
each photo to fill the screen, on shows the whole frame with bars. The two walls
— Photo Grid and Origami — keep filling regardless: they are mosaics of crops,
and letterboxing every tile would fill the wall with bars.

### Sliding Panels

A wall of four to six photos whose panels slide their photo out and the next one
in. A cycle is one wall: each panel changes **once**, one at a time, 2.5 s apart —
so a four-panel wall has every panel changed once in ten seconds — and then the
whole wall is exchanged at once for the next one, with a new cut and new photos.
Both rhythms the reference uses, in one cycle.

The change is a **chase, not a blink**: the arriving photo pushes the leaving one
out of the panel, so the two are adjacent at every instant and no black shows. The
reference lets its panels go black between photos; this does not, which is what
makes the wall read as panels sliding rather than as pictures flickering.

Photos are consumed in album order and never repeated inside a cycle — the wall
opens with the first *n* photos, each panel's turn brings in the photo *n* places
further on, and the exchange brings in the next cycle's opening photos. So every
photo is shown exactly once per pass through the album.

Photos are **cropped to fill** their panel, and the crop is **centred**: a portrait
in a landscape cell has to lose something, but it loses the same amount from both
sides, so the subject stays in the panel instead of drifting out of it. `CropMath`
holds that rule as pure arithmetic and is unit tested — centred by default, never
running past the photo's edge, always the slot's shape, and clamped (not
extrapolated) when a focus point is asked for.

The layout is cut **once per cycle**, from that cycle's opening photos — never from
the current contents. That distinction is the whole point: cutting from the current
photos re-arranged the entire wall every time a single panel slid, which made a
one-panel change look like a new wall. Panels keep their cells for the cycle, and
photos are cropped to fill them.

Each turn also takes the photo whose **shape** is closest to the one it replaces,
measured on the logarithm of the aspect ratio so 4:3 is as far from 16:9 as 3:4 is
from 9:16. A panel cut for a landscape should not suddenly receive a portrait, or
the photo is cropped hard to fill a cell cut for something else. The choice is
greedy in slot order over the cycle's pool, which is exactly one photo per panel —
so the last panel sometimes has to take what is left; a wider pool would give every
panel a match, but it would also break the arithmetic that lets any moment be
computed on its own.

The travel is randomised per step (deterministically, from the cycle and step) so a
wall does not always slide the same way — but the exchange continues the way the
cycle's last turn went, because randomising it separately let a wall finish sliding
one way and then set off the other, which reads as the wall bouncing.

**All four sides are used**, not just left and right: a panel may slide its photo
out to the left, the right, downwards or upwards, and the incoming photo follows
from the opposite edge. `Travel` is a unit vector on one axis only — never
diagonal — so the leaving and arriving photos stay adjacent along that axis and a
panel never shows a gap; the span travelled is the cell's width when the wall
slides sideways and its height when it slides up or down.

The exchange slides the two walls past each other, so they are adjacent at every
instant and the arriving wall's leading edge is visible from the first pixel of the
slide. That is inherent to a chase: the incoming wall has to be somewhere. It is
drawn only once the wall has actually begun to move, so nothing of the next wall
can show while the composition is at rest.

It is a `mosaic` effect, so it takes its timing from pages rather than slides — but
its page is a *panel*, 2.5 s rather than the sixteen a dense wall needs, and the
chrome says "2 s per panel" accordingly. `SlidingPanels` holds all of it as pure
arithmetic: panel counts, the photo each panel holds at any step, the exchange,
and the slide progress, all derived from the clock rather than accumulated.

### Vintage Prints

One large print lying on a pile of others, under glass.

The pile is the album's own photos, desaturated and sunk into shadow, laid on a
jittered grid so it covers the stage — a bare patch of stage behind the front
print is the one thing this theme cannot have, because the pile *is* the
background. It re-deals itself on every step, so the scatter is never the same
twice.

The front print takes its photo's own shape, so the border is even on all four
sides and the whole photo shows — `Fit` and `Fill` are the same rect here, which
is why the theme ignores that preference. Its size, tilt and position are
randomised per photo, and it is allowed to run **off the edge of the frame**, the
way the reference lets a print leave the bottom of the screen: that is what makes
it read as a physical print larger than the window rather than as a picture fitted
into one. Its centre stays near the middle of the screen, though — between 38 %
and 62 % of the frame in each direction — because a print pinned to a corner reads
as a layout, while one sitting a little off centre reads as a print put down by
hand. The size carries the variation.

It still has to stay the subject, which is measured as a *span*, not an area: a
1:3 portrait print on a 16:9 screen is narrow however tall it is, so demanding a
share of the area would force it to be enormous. Spanning at least three quarters
of the frame in one direction is what keeps it the hero, and that works for every
shape. An arrangement that fails it is re-rolled from the next salt, deterministically.

The cross-fade is not a plain dissolve. The arriving print leads — it is fully in
at 72 % of the transition — while the one it replaces keeps its full strength and
*bleaches*, holding on until the last third and only then clearing. A print that
fades as fast as it washes reads as a grey ghost of itself rather than as a
photograph going white.

Grain, vignette and two soft bands of light lie over everything, fixed to the
lens rather than to the prints: the composition moves underneath them, which is
what makes them read as reflection rather than as paint.

That movement is a camera, not three separate animations: the whole composition
**turns** (2.5° across a hold), **pushes** (3.0 %) and **creeps** (1.1 % of the
frame), all about the frame centre.

It is a **one-way ramp, not an oscillation**, and there is no return at all.

A sine wave reverses every minute or so, which reads as the picture pumping in and
out — the first version did exactly that, reversing 5–6 times per three minutes.
Returning to the start during the cross-fade was the second attempt, and it was
still wrong: the swing back was visible at the start of the fade, and again as the
next hold set off the other way.

What works is **one camera per photo**. A photo creeps from one end of its run to
the other across its hold and then *stops*, holding that framing while it fades
out. The photo arriving is a different print with a camera of its own, starting at
the beginning of its own run. Nothing reverses at any point, in the hold or in the
fade, and nothing jumps either. `VintagePrints.phase(hold, transition)` is the
ramp, frozen through the fade, and tests walk a whole cycle asserting the camera
never turns round.

The **direction alternates from photo to photo** (`directionFor`, by index): one
print turns clockwise and pushes in, the next turns anticlockwise and pulls out.
Every photo turning the same way made the theme feel like a turntable. Because the
travel runs end to end and the direction alternates, the framing a photo finishes
on is *exactly* the framing the next one starts on, so the cross-fade between them
is a pure dissolve with no shift in framing at all — a test asserts that handover
to within a thousandth of a degree.

Turning a full-frame composition uncovers its corners, and creeping sideways
uncovers an edge, so the zoom pays for both — and it pays for the *steepest* turn
the drift ever reaches, not for the angle right now, because paying only for the
current angle would make the scale dip as the turn passed through zero and climb
again: a small pump in the zoom channel, the very thing the ramp avoids. That is
also why the pile is drawn larger than the frame, with four big prints centred
just outside the corners underneath the grid — the corners of a turned sheet of
photos would otherwise show bare stage. Tests assert the frame stays covered at
every point of the travel, on 16:9 and portrait frames alike.

### Origami

Origami is a wall, not a photo: a justified mosaic of four to seven photos,
edge to edge, that turns over a page at a time. It is a `mosaic` effect, so it
takes its timing from pages (16 s) rather than from slides, and the chrome says
so — quoting the per-photo setting there would name a duration nothing obeys.

The wall is cut **once**, from the album's own shapes, and keeps that shape all
session. That is what makes the fold work: a cell can only reveal the next photo
in the hole the old one leaves if it is the same hole. Re-cutting the wall every
page was the first attempt and it folded flaps away from cells that no longer
existed, opening black wedges onto the stage instead of onto a photograph.

Most pages turn the whole wall, fanned across the cells in slot order or — every
fourth — as one sheet; each page picks its hinge, top, bottom, left or right, from
the page's generation and the album's first photo.

**Every third page turns a single cell instead.** Replacing all six photographs
every sixteen seconds is more motion than one photograph's worth of news can
justify: one photo leaves, the next arrives in its place, and the rest of the wall
stays exactly where it is. `OrigamiPlan` decides that, and it derives each cell's
photo rather than accumulating it — page `p` sits `fullsBefore(p)` windows into
the album — so any page can be computed on its own and the renderer stays
stateless. The cell that turns on a single page is the one whose photo the *next*
whole-wall turn would have brought in anyway, so a single turn neither repeats a
photo nor skips one; it previews one cell of the wall that is coming.
`OrigamiFold` holds that geometry as pure arithmetic: nine floats of a
homography, plus the edge and sheet decisions. The free edge turns *away* from
the viewer, so it is divided down by a perspective term and its corners converge
on the viewer's line of sight — that trapezoid is the whole difference between
paper and a squash, and it is why the transform cannot be a Compose
`withTransform`: those are affine. The homography goes onto the native canvas,
which applies perspective to everything drawn inside it.

## Architecture

```
data/          Immich REST client (Retrofit + kotlinx.serialization), config,
               repository, prefs, demo artwork generator, bitmap source
slideshow/     pure playback core: effects, timing, motion, easing, RNG,
               frame model, fold geometry, controller (clock + caches), renderer
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
into rows so that every row is filled edge to edge at a similar height. Cells are
never stretched — each one keeps its photo's aspect ratio, so a portrait photo is
never squeezed into a landscape-shaped hole (that was an early bug: rows used to
be squashed vertically to meet the frame, which bent every photo by 9 % or more).

How much has to be cropped depends on how many photos are on the wall, and that
is derived from the album itself: three rows fill a 16:9 frame when the page holds
`16 / average aspect` photos — twelve for a 4:3 album, twenty for a 3:4 one, about
fifteen for a mix. `GridPlan.slotsFor` computes it once per album, and the page
size never changes between pages. Sizing the wall this way is what keeps the crop
at a fraction of a percent instead of a third of the screen.

Whatever surplus remains is cropped horizontally rather than vertically, because
the frame is wider than the photos are tall: cropping width takes a slice off a
landscape photo's long side, while cropping height would eat the short side of the
photos in the outer rows. Tiles decode square (600 × 600, `Scale.FIT`), so a
portrait photo keeps its full width at a usable resolution.

The prefetch window is exactly what the wall draws — the current page plus the
previous one early in the page (tiles are still fading out of it) or the next one
late in the page. Both the player and the renderer must use the same page size:
handing one of them a different size makes them request and draw different photos,
and every cell comes up empty. A unit test pins that invariant. `GridPlan` owns the
page clock — twelve photos stay on the wall for 16 s, the tiles flip over once at
staggered moments, and the prefetch window is exactly two pages.

The Reflections wall (`ReflectionsWall`) shows three photos on 70 % of the steps
and one big photo on the other 30 % — steps 3, 7 and 9 of each ten, so the single
photo arrives here and there rather than in runs. Collage steps put the photo that
best fills the width in the tall column (a landscape fills a 16:9 frame far better
than a portrait does), stack the other two beside it with their width solved so
the stack is exactly as tall as the column, then scale the wall up to meet the
frame edges. Every cell keeps its own aspect ratio, all cells share one baseline,
and one short smear mirrors the whole wall underneath. Inside a mode the cells are
a ring like the scrapbook desk, so exactly one photo changes per step; a step that
switches between one and three photos cross-fades the two arrangements whole.

The stage is white, because a reflection only reads as a reflection when there is
something for it to sit on: a dark smear of a dark photo on a dark background
disappears. The reflection is 18 % of the frame tall — big enough to reach down to
the caption, which sits at the foot of the glass. The photos cast no drop shadow
here: a shadow and a reflection answer the same question — where the photo sits in
space — and together they read as a grey halo around every edge. The reflection is
foreshortened to 0.7,
not squashed to 0.42, which read as the photo smeared out of shape. There is no
wash and no hairline under the wall either: a uniform grey band with four hard
edges is not glass, it is a grey rectangle sitting on the stage. The smear simply
fades into the stage colour, all the way to opaque, so it has no bottom edge - and
the clip stops a pixel inside that opaque part, because on the boundary row the
fade's own edge is only half covered and lets a few percent of the smear through as
a one-pixel rule across the whole frame. The photo shadow is dialled down
to 30 % here (three black layers read as grey boxes around every photo on white),
and a step that switches between one and three photos cross-fades without drawing
the outgoing wall's shadow at all.

A light stage also flips the playback chrome: the title, the counter and the key
hints go dark and the scrims go white, because white type on a white stage is
invisible. Scrapbook, whose desk is paper, gets the same treatment. The vignette is
off on both — a dark vignette on white reads as grime rather than as depth.

The Scrapbook desk is a pile of up to twenty polaroids. Each card keeps the place
it was given when it arrived — the spot is derived from the photo id, so a card
never wanders while it is on screen — and the newest card lands on top, partly or
wholly covering older ones. When the pile is full the oldest card slides off the
bottom. `ScrapbookDesk` owns the pile: which photos are on the desk, which one
leaves, and each card's scattered placement.

Cards are big (22–40 % of the frame width), vary in size, and are allowed to run
past the screen edges, so the pile covers the table instead of sitting on it. A
new card falls in from above the frame (`Easing.dropIn`: gravity acceleration, a
small bounce on impact, the tilt straightening as it lands), and the card it
replaces fades out in place.
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

Two things about focus rings, both learned from a ring with no top edge. The ring
is drawn *over* the content, not under it: a cover image or a theme preview that
fills the surface otherwise paints the border away. And a focused card scales up
4.5 % about its centre, so the first row grows past the grid's own edge and is
clipped there — the grids need top padding to give the ring somewhere to grow.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

`SlideshowCoreTest` covers the timeline (index/transition/hold/progress
arithmetic, wraparound), the deterministic RNG, easing curves, motion viewports
and effect metadata — 47 assertions that pin down the playback contract the
renderer relies on.
