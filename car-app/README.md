# car-app — touch-first Apple Music client (BYD DiLink)

Standalone Android client for the head unit. It drives the patched Apple Music engine
through the framework `MediaBrowser` / `MediaController` API, reusing the shared
`:core` client (`com.anderson.applemusic.core`) — no protobuf, no private APIs, no
duplicated models.

The UI is designed for touch (1920x1080, API 29): targets >= 48 dp, large type and at
most **two navigation levels** (tab root -> one detail list). The mini player is fixed at
the bottom; `screenOrientation="sensorLandscape"`.

## Layout

- `:core` — the shared `MediaBrowserClient`, `BrowseItem`, `Section`,
  `PlaybackSnapshot`, `CustomAction` (see [../core/README.md](../core/README.md)).
- `:car-app` — this module: `CarApp.kt` (Compose UI), `CarViewModel.kt` (screen state),
  `Theme.kt` (Apple-like tokens), resources. Depends on `project(":core")`.

## Screens

- **Início / Rádio** — `LazyColumn` of shelves, each a `LazyRow` of large artwork cards
  plus a "Ver tudo" card. Tapping a playable card starts it (`playFromMediaId`);
  browsable-only cards (radio genres) open the detail level.
- **Biblioteca** — the engine's category list (Biblioteca (Aleatória), Adições Recentes,
  Playlists, Artistas, ...). Tapping a category opens its items; playable rows play.
- **Detalhe** — one level only, with breadcrumb (e.g. `Biblioteca / Playlists`), back
  button and item count. Playable rows play; a browsable-only row at this level shows
  "Use o app do Apple Music para abrir mais níveis" instead of stacking a third level.
- **Tocando agora** — fixed bottom bar: cover, title/artist, touch seek bar, previous /
  play-pause / next and a "Criar estação" button (enabled only while the session
  publishes `CREATE_STATION`; sends it through `sendCustomAction`).
- **Entrar na conta** — explicit intent to
  `com.apple.android.music/.common.MainActivity` (the engine is hidden from the
  launcher). Shown in the top bar and in the failure pane when the engine is installed.
- Error states: "Apple Music não instalado neste dispositivo" and the core's
  "serviço indisponível" messages, each with "Tentar de novo".

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
  landscape (780x360 dp, compact cards).
- **TV** MiTV-AFMU0 (`100.74.105.35:5555`), API 34, 1920x1080 override @ 320 dpi —
  navigated only with `adb shell input tap` (no remote).

Evidence in `.pipeline/`:

| flow | file |
|---|---|
| Início, shelves + artwork | `car-app-02-home.png`, `car-app-13-tv-home2.png` |
| Biblioteca (categories) | `car-app-03-library.png`, `car-app-14-tv-library.png` |
| Detalhe Playlists + breadcrumb | `car-app-05-library-playlists.png`, `car-app-15b-tv-playlists.png` |
| Rádio / Rádio por Gênero | `car-app-09-radio.png`, `car-app-20-tv-radio.png` |
| Rádio -> gênero (nível 1) | `car-app-10-radio-genre.png` |
| Ver tudo (Início) | `car-app-29-phone-seeall.png`, `car-app-30-phone-back-home.png` |
| Play a partir de card/lista | `car-app-06-play-playlist.png`, `car-app-16-tv-play.png` |
| Criar estação (`CREATE_STATION`) | `car-app-07-create-station.png` |
| Seek por toque | `car-app-08-seek.png` (5:16/10:56), `car-app-22-tv-paused.png` (5:28/10:56) |
| Play/pause, progresso, next/prev | `car-app-25-phone-progress.png`, `car-app-26-phone-paused.png`, `car-app-27-phone-next.png`, `car-app-28-phone-prev.png` |
| Entrar na conta | `car-app-11-account.png` (`…/.common.MainActivity` focused) |
| Sessão final tocando | `car-app-32-phone-final.png` |

`logcat` was clean for `com.anderson.applemusic.car` (no `AndroidRuntime`/`FATAL`), and
the engine session was left playing.

## What is missing on the real DiLink

The head unit is not reachable from this machine, so **everything was validated on the
phone and the TV only; the DiLink is UNVERIFIED**. Still to check there:

- Real screen: 1920x1080 @ ? dpi, status/navigation insets (the phone adds a cutout
  inset; the TV has none).
- Touch latency / tap size with the car's touch panel (the UI uses >= 48 dp targets,
  but only ADB taps were exercised).
- Wi-Fi/engine connectivity in the car, steering-wheel media keys (they reach the media
  session; the app itself adds no key handling).
- Cold start on the head unit SoC.

## Known limitations

- Inter is not bundled in the repo yet, so the UI uses `FontFamily.Default`; the colour
  tokens are already the ROADMAP ones (`#0A0A0B` background, `#FA243C` accent).
- No search: the Android Auto browse tree exposes no search surface (same as `:tv-app`).
- Two levels by design; deeper nodes are not reachable from this UI.
- Some engine sessions publish `PLAYING` without `position`/`duration` (or stay
  `BUFFERING` with `speed=0.0`); the seek bar then has nothing to seek and
  `togglePlayPause` can only resume. Validated on the phone after the engine reported
  `PLAYING` with a duration.
