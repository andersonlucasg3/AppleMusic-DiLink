# car-app — touch-first Apple Music client (BYD DiLink)

Standalone Android client for the head unit. It drives the patched Apple Music engine
through the framework `MediaBrowser` / `MediaController` API, reusing the shared
`:core` client (`com.anderson.applemusic.core`) — no protobuf, no private APIs, no
duplicated models.

The UI is designed for touch (1920x1080, API 29): targets >= 48 dp, large type and at
most **two navigation levels** (tab root -> one detail list). The mini player is fixed at
the bottom; `screenOrientation="sensorLandscape"`.

## Visual system

- Canvas `#0A0A0B` (player bar `#000000`), surfaces `#1C1C1E` / `#2C2C2E`, primary type
  `#FFFFFF`, secondary `#8E8E93`, single accent `#FA243C`.
- Card and player corners 14–16 dp, page padding 24 dp, section gap 28 dp.
- Type is **Inter 400/500/600/700**, bundled as static TTFs in `res/font` with the SIL
  OFL license in `res/raw/inter_license.txt`. `AppleMusicCarTheme` provides it through
  `LocalTextStyle`, so every `Text` uses it; the platform font is the fallback for
  glyphs Inter does not cover.
- Cards are square, full-bleed artwork with a bottom scrim and a short title/subtitle.
  Press state is a colour wash plus a 0.97 scale — no animated shadow. Coil requests the
  exact target size, never the full CDN image.
- Top tabs use an accent underline; the account entry is a quiet icon + label. Error
  panes show a 76 dp accent icon, a bold headline and a legible explanation.

## Layout

- `:core` — the shared `MediaBrowserClient`, `BrowseItem`, `Section`,
  `PlaybackSnapshot`, `CustomAction` (see [../core/README.md](../core/README.md)).
- `:car-app` — this module: `CarApp.kt` (Compose UI), `CarViewModel.kt` (screen state),
  `Theme.kt` (Apple-like tokens), resources. Depends on `project(":core")`.

## Screens

- **Início / Rádio** — `LazyColumn` of shelves, each a `LazyRow` of large square artwork
  cards (bottom scrim + title/subtitle) plus a "Ver tudo" card. Tapping a playable card
  starts it (`playFromMediaId`); browsable-only cards (radio genres) open the detail
  level.
- **Biblioteca** — the engine's category list (Biblioteca (Aleatória), Adições Recentes,
  Playlists, Artistas, ...). Tapping a category opens its items; playable rows play.
- **Detalhe** — one level only, with breadcrumb (e.g. `Biblioteca / Playlists`), back
  button and item count. Playable rows play; a browsable-only row at this level shows
  "Use o app do Apple Music para abrir mais níveis" instead of stacking a third level.
- **Tocando agora** — fixed bottom bar over black: rounded cover, title/artist, a 4 dp
  progress bar with elapsed/total times and a 48 dp touch strip + thumb, circular
  previous / play-pause (white) / next controls, and "Criar estação" as the accent pill
  (enabled only while the session publishes `CREATE_STATION`; sends it through
  `sendCustomAction`).
- **Entrar na conta** — explicit intent to
  `com.apple.android.music/.common.MainActivity` (the engine is hidden from the
  launcher). Shown in the top bar and in the failure pane when the engine is installed.
- Error states: "Apple Music não instalado neste dispositivo" and the core's
  "serviço indisponível" messages, each with icon, explanation and "Tentar de novo".

## Build

```powershell
$env:JAVA_HOME="C:\Users\anderson\.jdks\jdk-21.0.11+10"
$env:GRADLE_USER_HOME="$PWD\.gradle-user"
$env:GITHUB_ACTOR="andersonlucasg3"; $env:GITHUB_TOKEN=$(gh auth token)
.\gradlew.bat :car-app:assembleDebug :patches:buildAndroid --no-daemon `
  "-Pkotlin.compiler.execution.strategy=in-process" `
  "-Dorg.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m"
```

The heap override is required: with the default 512 MiB daemon heap
`mergeExtDexDebug` fails (`DexArchiveMergerException` / out of JVM heap) once
material3 + Coil are on the classpath. AGP 9.1 has built-in Kotlin support, so only
`org.jetbrains.kotlin.plugin.compose` is applied (same as `:tv-app`). Compose BOM is
pinned to `2026.06.01` (compileSdk 36); Coil stays on 3.5.0.

The module does not use the default debug key (`signingConfig = null`). AGP writes
`car-app/build/outputs/apk/debug/car-app-debug-unsigned.apk`.

## Sign with the Morphe certificate

`android:sharedUserId="com.apple.android"` requires the same certificate as the engine,
otherwise the media service refuses the connection. `tools/tv-app-signing.p12`
(alias/password `Morphe`, SHA-256 `1e058623…60463`) is reused:

```powershell
.\car-app\sign.ps1            # debug
.\car-app\sign.ps1 -Variant release
adb install -r .pipeline\car-app-debug.apk
```

The script runs `zipalign -p -f 4` and `apksigner sign` with build-tools 36.0.0 and
verifies the certificate.

## Validation

Signed with Morphe and installed with `adb install -r` on:

- **Phone** SM-S921B (`RXCX100BZ5J`), API 36, 1080x2340 @ 480 dpi — the car UI rotates to
  landscape (780x360 dp, compact cards). Navigation and seek were driven only with
  `adb shell input tap`.
- **TV** MiTV-AFMU0 (`100.74.105.35:5555`), API 34, 1920x1080 override @ 320 dpi
  (960x540 dp) — used as a stand-in for the 1080p head-unit layout.

The BYD DiLink (`100.74.124.38:5555`) Tailscale peer went offline during this run
(last seen 14 min before the phone pass, retried repeatedly), so **the car install
remains UNVERIFIED**.

Evidence in `.pipeline/car-app/`:

| flow | file |
|---|---|
| Início, scrim cards, playing | `revamp-phone-home.png`, `revamp-tv-home.png` |
| Biblioteca (categories) | `revamp-phone-library.png` |
| Rádio por Gênero | `revamp-phone-radio.png` |
| Detalhe + breadcrumb | `revamp-phone-detail.png` |
| Seek por toque (5:49/10:56) | `revamp-phone-seek.png` |
| Tocando agora (playing) | `revamp-phone-home.png`, `revamp-phone-player.png` |
| Estado de erro com ícone | `revamp-tv-error.png` |

`logcat` is clean for the app: `revamp-logcat.txt` has no `FATAL EXCEPTION` /
`AndroidRuntime` entries. The phone session was left playing.

## What is missing on the real DiLink

The head unit was **unreachable during this run**, so the revamp was proven only on the
phone and on a 1080p TV at the head-unit resolution. Still to check there:

- Real screen: 1920x1080 @ ? dpi, status/navigation insets (the phone adds a cutout
  inset; the TV has none).
- Touch latency / tap size with the car's touch panel (the UI uses >= 48 dp targets,
  but only ADB taps were exercised).
- Wi-Fi/engine connectivity in the car, steering-wheel media keys (they reach the media
  session; the app itself adds no key handling).
- Cold start on the head unit SoC.

## Known limitations

- No search: the Android Auto browse tree exposes no search surface (same as `:tv-app`).
- Two levels by design; deeper nodes are not reachable from this UI.
- Some engine sessions publish `PLAYING` without `position`/`duration` (or stay
  `BUFFERING` with `speed=0.0`); the seek bar then has nothing to seek and
  `togglePlayPause` can only resume. Validated on the phone after the engine reported
  `PLAYING` with a duration.
