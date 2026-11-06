# tv-app — Apple Music TV client

Standalone Android TV app that drives the patched Apple Music engine installed on the
box through the framework `MediaBrowser` / `MediaController` API (no protobuf, no
private APIs). It browses the Android Auto tree exposed under `__AUTO_ROOT__`
(`Início` / `Rádio` / `Biblioteca` + shelves) and plays items with
`playFromMediaId`. The current track is shown as a compact chip on the navigation row;
`enter` on it opens a full-screen Now Playing screen with D-pad controls.

The app declares `android:sharedUserId="com.apple.android"` and must be signed with
the Morphe certificate, otherwise the media service refuses the connection.

## Build

```powershell
$env:JAVA_HOME="C:\Users\anderson\.jdks\jdk-21.0.11+10"
$env:GRADLE_USER_HOME="$PWD\.gradle-user"
$env:GITHUB_ACTOR="andersonlucasg3"; $env:GITHUB_TOKEN=$(gh auth token)
.\gradlew.bat :tv-app:assembleDebug :patches:buildAndroid --no-daemon `
  "-Pkotlin.compiler.execution.strategy=in-process"
```

The module does not use the default debug key (`signingConfig = null`); the APK that
AGP writes is `tv-app/build/outputs/apk/debug/tv-app-debug-unsigned.apk`.

For the box, prefer the release variant (`:tv-app:assembleRelease` →
`tv-app/build/outputs/apk/release/tv-app-release-unsigned.apk`, same signing steps): a
debuggable APK renders ~4x slower here, see Performance.

## Sign with the Morphe certificate

```powershell
java -cp ".companion\tools;<bcprov-jdk18on.jar>" BksToP12 `
  tools\morphe-data\morphe.keystore Morphe tools\tv-app-signing.p12 Morphe

$bt="$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0"
& "$bt\zipalign.exe" -p -f 4 tv-app\build\outputs\apk\debug\tv-app-debug-unsigned.apk .pipeline\tv-app\tv-app-aligned.apk
& "$bt\apksigner.bat" sign --ks tools\tv-app-signing.p12 --ks-key-alias Morphe `
  --ks-pass pass:Morphe --key-pass pass:Morphe `
  --out .pipeline\tv-app\tv-app-debug.apk .pipeline\tv-app\tv-app-aligned.apk
& "$bt\apksigner.bat" verify --print-certs .pipeline\tv-app\tv-app-debug.apk
# SHA-256 must be 1e058623722ed37eb59bb55411d01d6b7b212c6a8353aacecd60d515cee60463
adb install -r .pipeline\tv-app\tv-app-debug.apk
```

## Now Playing (full screen)

The player chip sits at the top right, after the tabs and the search icon. It shows
the session artwork plus title/artist, is focusable only while the engine has a loaded
track, and `enter` opens the full-screen `NowPlayingScreen`. `back` closes it and
returns the D-pad focus to the chip. The old bottom bar was removed so the content
area never competes with it for focus.

The full screen shows large artwork, title, artist, album, elapsed/total time and a
progress bar. Values come from `MediaController.getPlaybackState()` /
`getMetadata()` in a dedicated `TvViewModel.playback` `StateFlow`, collected with
`collectAsStateWithLifecycle` inside `NowPlayingScreen` / `Header` (not in `TvApp`,
so a playback update never invalidates the browse tree). `MediaController.Callback`
(`onPlaybackStateChanged` / `onMetadataChanged`) delivers track changes; the 500 ms
`viewModelScope` tick only runs while the full player is open, and it re-reads the
position only — `getMetadata()` (which marshals the album art bitmap over binder) is
called exclusively from the callbacks.

D-pad order: initial focus is play/pause, `LEFT` = previous, `RIGHT` = next, `DOWN` =
seek bar. On the seek bar `LEFT`/`RIGHT` call `TransportControls.seekTo` for −10 s /
+10 s and show "▶ +10s • 1:56" feedback for 1.5 s. `back` returns to the previous
screen (tab root or drill-down grid) with focus on the chip.

## Artwork

`MediaDescription.iconUri` is populated for the Home shelves, playlists, albums,
recently played and radio stations (`https://is1-ssl.mzstatic.com/...`), so card
artwork is loaded with Coil 3 (`coil-compose` + `coil-network-okhttp`, hence the
`INTERNET` permission). Coil is pinned to **3.5.0**: 3.6.x requires `compileSdk 37`,
which AGP 9.1.0 does not support here; 3.5.0 requires only 36.

Three node families return no icon URI: `library/artists` (93 items),
`radio/radio_by_genres` (21 items) and the `library` category list. Those cards keep
the gradient + ♪ placeholder, which is also shown while an image is loading/fails.
Player artwork comes from the session's `METADATA_KEY_ALBUM_ART` bitmap.

## Search

The magnifier opens a "Busca indisponível via engine" screen. The legacy MediaBrowser
tree only exposes the Android Auto sections and has no search surface. Implementing it
later needs either the Apple Music media API directly or an extension inside the
shared-UID engine (like the existing Morphe patches). Not attempted in this phase.

## Notes

- AGP 9.1.0 has built-in Kotlin support; applying `org.jetbrains.kotlin.android`
  fails with "no longer required since AGP 9.0", so only the Compose compiler plugin
  is declared. The Kotlin compiler itself comes from the build classpath that the
  Morphe settings plugin already sets up.
- Compose BOM is pinned to `2026.06.01` (Compose 1.11.4): the current BOM
  (`2026.09.00`, Compose 1.12.1) requires `compileSdk 37`, while AGP 9.1.0 caps the
  recommended compile SDK at 36 and the project builds with `compileSdk 36`.
- Compose for TV 1.1.0 no longer ships `TvLazyRow` / `TvLazyColumn` (they were
  removed in favour of the regular Compose lazy layouts), so the shelves and the
  grid use `LazyRow` / `LazyColumn` from `androidx.compose.foundation` with
  tv-material `Card` / `Text` / `IconButton`.
- Focus is shown with the tv-material 1.06 card scale plus a lighter card surface; the
  per-card animated elevation (`Modifier.shadow`) was removed — see Performance.
  `enter` on a browsable card (or on a shelf header) pushes a level, `enter` on a
  playable card calls `playFromMediaId`, and `back` pops the level stack.
- The 5th card peeking at the right edge of a shelf is intentional (lazy row with a
  hint of the next item), not a clipping bug.

## Performance

Measured on the Xiaomi MiTV box (`MiTV-AFMU0`, API 34, arm32, 3840x2160 overridden to
1920x1080 @ 320 dpi, ~2 GB RAM) with the Apple Music engine playing. Method:
`.pipeline/tv-app/perf/sweep.ps1` — cold start + 10 s load wait, `dumpsys gfxinfo
com.anderson.applemusic.tv reset`, then 20x RIGHT, 20x LEFT, 10x DOWN, 10x UP at
~120 ms per key, then `dumpsys gfxinfo` (same script before and after; 3 runs each).
Raw: `.pipeline/tv-app/perf/before*.txt`, `after*.txt`, `release*.txt`.

| build | run | frames | janky | p50 | p90 | p99 |
|---|---|---|---|---|---|---|
| before (debug) | 1/2/3 | 93/83/94 | 96.8/94.0/89.4 % | 250/250/200 | 650/650/600 | 750/900/950 |
| after (debug) | 1/2/3 | 87/99/96 | 97.7/87.9/88.5 % | 300/200/250 | 700/600/600 | 800/1000/700 |
| after (**release**) | 1/2 | 166/164 | 68.1/68.3 % | **65/61** | **250/250** | **300/300** |

What the changes did, and what they did not:

- **Debuggable build is the dominant factor.** The same source as a release APK
  (`assembleRelease`, unsigned then signed with the Morphe cert; `isMinifyEnabled =
  false`) drops p50 from ~250 ms to ~61 ms and p99 from ~900 ms to ~300 ms — about 4x
  shorter frames, and the same key sweep renders ~1.8x more frames (166 vs 93). The
  debug APK is the wrong daily driver for this box.
- **Playback poll was costing 2 janky frames/s while idle.** With 10 s of no input the
  old build rendered 20 frames, all janky, p50 89 ms (100 %). After splitting
  `playback` out of `TvUiState` into its own `StateFlow`, collecting it inside
  `Header` / `NowPlayingScreen`, tick-ing only while the full player is open and
  dropping the `getMetadata()` call from `refreshPlayback()`: **0 frames in 10 s** idle.
  This does not show in the sweep table because the sweep is dominated by scrolling.
- **Vertical navigation is ~10x the cost of horizontal** (debug, isolated legs):
  `RIGHT x10` = 16 frames, p50 57 ms; `DOWN x10` = 25 frames, p50 700 ms; `UP x10` over
  already-visited rows = 27 frames, p50 400 ms. `framestats` attributes 360-440 ms of
  the DOWN frames to the *recomposition* phase (layout stays at 0.3 ms, GPU 8 ms), i.e.
  composing a newly revealed row of ~7 cards. That is Compose composition cost on this
  SoC, not shadows, not images (UP over cached artwork is just as slow).
- **Animated shadows removed** (per-card `animateDpAsState` + `Modifier.shadow` in
  `TvCard`, `SeeAllCard` and the player chip). Focus is now only the tv-material 1.06
  card scale + `focusedContainerColor`. No measurable change in the debug sweep — the
  shadow was not the bottleneck — but it removes an allocation and an animation per
  focus change.
- **Row scroll only when needed**: `LazyListState.needsScrollTo(index)` checks
  `visibleItemsInfo` against `viewportStartOffset`/`viewportEndOffset`; a card that is
  already fully visible triggers no `animateScrollToItem` at all (shelf and grid).
- **`contentType` + stable keys** on the shelves (`"section"`, `"seeAll"`, `"card"`) and
  the grid rows (`"gridRow"`, keyed by the row's first item id), and the grid no longer
  re-`chunked`s the list on every recomposition.
- **Coil**: cards now request an explicit 52 dp target size (`.size(px, px)`) instead of
  decoding whatever the CDN serves. Coil 3 has no crossfade by default and its memory
  cache is on, so nothing else was needed.

Still open:

- The debug sweep before/after is **within run-to-run noise** (±7 pp janky, ±100 ms
  p90/p99): the focus-path changes are not provably a win on their own. The provable
  wins are the idle frames (gone) and the release build (4x).
- A release-with-old-code run was **not** measured, so how much of the release gain
  belongs to the code changes is unknown.
- Next steps: ship a release APK (or at least `debuggable=false`), add JankStats /
  Macrobenchmark for a repeatable CI baseline, and consider reducing the per-row card
  count on ~2 GB boxes or a lighter card (no Coil per card, fixed-height text) since
  composing a row is what dominates DOWN navigation.
- Measure the box at rest: with swap nearly full (`633 MB of 648 MB used`) and
  `mediacodec` + `audioserver` + the engine streaming, the same `gfxinfo` run varies
  ~20 % between tries.

## UNVERIFIED

- Play/pause/seek were validated only after the account's other device stopped
  streaming (Apple Music showed "Mais de um dispositivo…" and the session stayed
  `NONE` until then). Concurrent-device behaviour is outside the app's control.
- The chip placeholder when the engine has no loaded track was not exercised on the
  box: the session always had metadata during validation.
- Artwork loading with a cold Coil cache / slow network; only the warm path was seen.
- The `size(px, px)` Coil hint was not isolated in a measurement; the UP-leg test
  (cached artwork, same cost as DOWN) is what rules images out as the bottleneck.
- `dumpsys gfxinfo` percentiles on this device report `4950ms` when zero frames were
  rendered, which is what the idle run shows.
- The release build was measured and spot-checked end-to-end only for browse, focus,
  fullscreen play/pause and the position advancing (0:39 → 0:45 in 6 s).
