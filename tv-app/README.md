# tv-app — Apple Music TV client

Standalone Android TV app that drives the patched Apple Music engine installed on the
box through the framework `MediaBrowser` / `MediaController` API (no protobuf, no
private APIs). It browses the Android Auto tree exposed under `__AUTO_ROOT__`
(`Início` / `Rádio` / `Biblioteca` + shelves) and plays items with
`playFromMediaId`.

The app declares `android:sharedUserId="com.apple.android"` and must be signed with
the Morphe certificate, otherwise the media service refuses the connection.

## Build

```powershell
$env:JAVA_HOME="C:\Users\anderson\.jdks\jdk-21.0.11+10"
$env:GRADLE_USER_HOME="$PWD\.gradle-user"
$env:GITHUB_ACTOR="andersonlucasg3"; $env:GITHUB_TOKEN=$(gh auth token)
.\gradlew.bat :tv-app:assembleDebug --no-daemon
```

The module does not use the default debug key (`signingConfig = null`); the APK that
AGP writes is `tv-app/build/outputs/apk/debug/tv-app-debug-unsigned.apk`.

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
- Focus is shown with a 1.06 scale + elevation + lighter card surface; `enter` on a
  browsable card (or on a shelf header) pushes a level, `enter` on a playable card
  calls `playFromMediaId`, and `back` pops the level stack.
