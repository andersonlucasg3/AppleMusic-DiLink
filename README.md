# Apple Music DiLink — patches Morphe

Bundle de patches [Morphe](https://github.com/MorpheApp) para o **Apple Music 7.0.0**
(`com.apple.android.music`, versionCode 1614) rodar no head unit **BYD DiLink 3.0** (Android 9/10).

Objetivo final do bundle: destravar o landscape, rebaixar o `minSdk` de 30 para 28/29 e rodar sem
GMS/Play Integrity.

> **Estado atual:** bundle funcional com **2 patches reais** — landscape/tablet, `minSdk` 29,
> backport das chamadas API 30 e bypass do Play Integrity. Build, aplicação no APK, verificação de
> bytecode e **carregamento no patcher do Morphe Manager (1.15.0)** estão validados localmente;
> **runtime no DiLink ainda não testado**.

## Patches

| Patch | Default | O que faz |
|---|---|---|
| `Apple Music DiLink support` | sim | `m1.x()` passa a devolver `K.TABLET` (destrava a rotação: `MainActivity` deixa de fixar `SENSOR_PORTRAIT`); `EmojiSearchFragment$a.onGlobalLayout()` retorna cedo em API < 30 (evita `NoSuchMethodError` em `WindowInsets.Type`/`setDecorFitsSystemWindows`); `getAppIntegrityToken()` devolve `null`, caminho que o app já trata. |
| `Apple Music DiLink manifest` | não (dependência) | `minSdkVersion` 30 → 28 e remove `screenOrientation="portrait"` da única activity que o declarava (`MLIChromeTabActivity`), eliminando a feature implícita `android.hardware.screen.portrait`. |

Evidências da análise (com `arquivo:linha`) em [`analysis/apple-music-7.0.0.md`](analysis/apple-music-7.0.0.md).

### Limitações conhecidas

- **Android 10 (API 29) ainda não funciona.** A varredura de APIs ([`analysis/api-levels.md`](analysis/api-levels.md))
  mostrou que o R8 removeu os guards `SDK_INT >= 30` das próprias bibliotecas: o compat de insets
  (`p468x2.W`) e `Objects.requireNonNullElse*` chamam API 30 sem checagem e derrubam a tela principal.
  Para API 28 há ainda `m1.<clinit>` (`FontStyle`). O patch atual só cobre as guardas de UI já mapeadas
  (emoji e nova playlist); o backport completo é trabalho pendente.
- O bypass de integrity só cobre o lado Java. Se o servidor da Apple **exigir** o token, o próximo
  alvo é `libAMSKit.so` (nativo, FairPlay/FPDI) — não resolvido.
- O APK final é um **APK único merged** (a CLI funde os splits) e é assinado com `CN=Morphe`:
  é preciso desinstalar o Apple Music oficial antes de instalar o patcheado.

## Estrutura

```
settings.gradle.kts                  # plugin app.morphe.patches 1.3.3 (mavenLocal primeiro)
gradle/libs.versions.toml            # morphe-patcher 1.6.0, smali, gson
patches/
  build.gradle.kts                   # about/group do bundle
  src/main/kotlin/
    app/morphe/patches/applemusic/shared/Constants.kt      # Compatibility + AppTarget("7.0.0")
    app/morphe/patches/applemusic/dilink/AppleMusicDiLinkPatch.kt  # landscape + emoji + integrity
    app/morphe/patches/applemusic/dilink/DilinkManifestPatch.kt    # minSdk + portrait lock
    util/PatchListGenerator.kt       # gera patches-list.json
extensions/extension/                # módulo de extensão (.mpe), namespace app.morphe.extension.applemusic
patches-list.json                    # gerado por :patches:generatePatchesList
tools/                               # jars locais (não versionados): morphe-desktop, APKEditor
```

## Requisitos

- JDK 21 (o JDK 25 quebra o Kotlin 2.2).
- Android SDK com `compileSdk 36` (o módulo `extensions/extension` usa AGP). `ANDROID_HOME` ou
  `local.properties` com `sdk.dir`.
- Token GitHub com `read:packages` para `app.morphe:morphe-patcher` (GitHub Packages).

## Build

Testado neste repo:

```powershell
$env:JAVA_HOME   = "C:\Users\anderson\.jdks\jdk-21.0.11+10"
$env:GRADLE_USER_HOME = "$PWD\.gradle-user"   # sandbox bloqueia escrita em ~/.gradle
$env:GITHUB_ACTOR = "andersonlucasg3"
$env:GITHUB_TOKEN = $(gh auth token)

.\gradlew.bat :patches:build
.\gradlew.bat :patches:generatePatchesList
```

Para **publicar/instalar no Morphe mobile**, o `.mpp` precisa conter `classes.dex` (o Manager
carrega via `loadPatchesFromDex`). O plugin 1.3.3 só funde o dex no `:patches:buildAndroid`:

```powershell
.\gradlew.bat :patches:buildAndroid
```

O `:patches:build` sozinho gera um bundle só com classes — suficiente para a CLI desktop, mas
rejeitado pelo Manager com "Patch bundle is missing dex entries".

`GRADLE_USER_HOME` fica dentro do repo (`.gradle-user/`, no `.gitignore`); o token nunca é escrito
em arquivo versionado — só passado por variável de ambiente.

Artefatos gerados em `patches/build/libs/`:

| Arquivo | Conteúdo |
|---|---|
| `patches-1.0.0.mpp` | bundle aplicável (classes + `extensions/extension.mpe`) |
| `patches-1.0.0-sources.mpp` | sources |
| `patches-1.0.0-javadoc.mpp` | javadoc |

`generatePatchesList` reescreve `patches-list.json` (não editar à mão) e depende de `build`.

> Sob o sandbox deste repo o Kotlin daemon não consegue abrir `~/.kotlin` e o build cai
> automaticamente para compilação in-process (o build continua verde). Para evitar o erro no log:
> `.\gradlew.bat :patches:build "-Pkotlin.compiler.execution.strategy=in-process"`.

## Aplicar o bundle localmente (sem celular)

Jars necessários (não versionados, baixar em `tools/`):

- `morphe-desktop-1.20.0-all.jar` — <https://github.com/MorpheApp/morphe-desktop/releases/download/v1.20.0/morphe-desktop-1.20.0-all.jar>
- `APKEditor-1.4.9.jar` — <https://github.com/REAndroid/APKEditor/releases/download/V1.4.9/APKEditor-1.4.9.jar>

### 1. Merge dos splits (base + arm64_v8a + mdpi)

```powershell
java -jar tools\APKEditor-1.4.9.jar m -i .pipeline\splits -o .pipeline\merged.apk
```

### 2. Aplicar o patch

```powershell
java -jar tools\morphe-desktop-1.20.0-all.jar patch `
  -p patches\build\libs\patches-1.0.0.mpp `
  -o .pipeline\applemusic-7.0.0-dilink-patched.apk `
  .pipeline\merged.apk
```

A CLI assina o APK de saída com o keystore padrão que ela gera em `tools/morphe-data/`
(`CN=Morphe`). Conferir:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs .pipeline\applemusic-7.0.0-dilink-patched.apk
```

### Alternativa: aplicar direto no XAPK

O Morphe Desktop também aceita bundle de splits e faz o merge sozinho:

```powershell
java -jar tools\morphe-desktop-1.20.0-all.jar patch `
  -p patches\build\libs\patches-1.0.0.mpp `
  -o .pipeline\applemusic-7.0.0-from-xapk.apk `
  apks\apple-music-7.0.0\AppleMusic_7.0.0_apkpure.xapk
```

Outros comandos úteis da CLI (`java -jar tools\morphe-desktop-1.20.0-all.jar --help`):

- `list-patches --patches <mpp>` — lista os patches do bundle e se estão habilitados.
- `list-versions --patches <mpp>` — versões de app declaradas na compatibilidade.
- flags de `patch`: `-e/--enable`, `-d/--disable`, `--force`, `--striplibs`, `--unsigned`, `--keystore`.

## Usar no app Morphe (Android)

O bundle é publicado em [releases](https://github.com/andersonlucasg3/AppleMusic-DiLink/releases)
e o repositório serve como *patch source*:

- **Remote**: no Morphe → **Sources** → **+** → Remote →
  `github.com/andersonlucasg3/AppleMusic-DiLink`.
- **Local**: baixe `patches-1.0.0.mpp` do release e abra no Morphe, ou
  `adb push patches-1.0.0.mpp /sdcard/Download/` e abra o arquivo no aparelho.

O Apple Music não é distribuído por este repositório, então o Morphe não oferece link de
download: forneça o **XAPK 7.0.0 (vc 1614)** na tela de seleção de APK. O Manager funde os
splits e aplica os mesmos patches da CLI.

> O bundle foi compilado contra `morphe-patcher` 1.6.0; o Morphe Manager usa a linha 1.15.x.
> Se a source aparecer com **Update Morphe** ou falhar ao carregar, o scaffold precisa subir para
> o template atual (plugin 2.1.0) — ainda não testado em aparelho.

## Instalar no DiLink

Pré-requisito: ADB habilitado no head unit (Wi-Fi ADB ou USB host).

```powershell
adb connect <ip-do-dilink>:5555
adb uninstall com.apple.android.music        # assinatura diferente do app original
adb install -r .pipeline\out.apk
adb logcat -s AndroidRuntime:E ActivityManager:I
```

Checklist de teste no carro:

1. O app abre sem `NoSuchMethodError`/`ClassNotFoundException`.
2. A UI principal fica em landscape (sem `SENSOR_PORTRAIT`).
3. Seletor de emoji abre sem crash (em API < 30 o teclado não abre automaticamente).
4. Login e reprodução funcionam sem GMS; se o servidor recusar por falta de integrity, ver limitações.

## Licença

MIT — ver [LICENSE](LICENSE).
