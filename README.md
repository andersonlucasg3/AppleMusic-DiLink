# Apple Music — patches Morphe

Bundle de patches [Morphe](https://github.com/MorpheApp) para o **Apple Music 7.0.0**
(`com.apple.android.music`, versionCode 1614) rodar no head unit **BYD DiLink 3.0** (Android 9/10).

Objetivo final do bundle: destravar o landscape, rebaixar o `minSdk` de 30 para 28/29 e rodar sem
GMS/Play Integrity. O bundle gera o **engine** (backend); a UI é dos companions `tv-app`
(Android TV) e `car-app` (DiLink) — ver [Engine e companions](#engine-e-companions).

> **Estado atual (1.4.0):** o app **instala, abre, loga e reproduz no DiLink** (API 29, landscape).
> O login passa por e-mail, senha, 2FA e pelo `AMSIDMSAuthenticationCompleteAction`; o player toca
> normalmente. Os assets JS da Apple são transpilados para Chrome 74 no build e servidos pelo APK;
> o Anisette/ADI nativo é substituído por OTP local (GrandSlam V3 e Store AMD V1.5) com uma
> identidade Android provisionada via `apple-auth` embutida no bundle.
>
> A 1.2.0 corrige duas falhas de UI no API 29: o tema do login no WebView (o JS servido passa a
> reportar `prefers-color-scheme: dark`) e a faixa preta da tela de letras (o modo imersivo só é
> aplicado em API 30+). O smoke da 1.2.0 foi feito no Galaxy S24; a validação no DiLink (carro)
> ainda não foi feita.
>
> A 1.3.0 acrescentou patches de **Android TV** (leanback + foco de D-pad); eles foram **removidos**
> — a UI de TV agora é o companion `tv-app` e o engine roda como backend oculto.
>
> Validado: build, aplicação no APK, bytecode, carregamento no patcher do Manager, fluxo completo
> no Morphe mobile, startup sem GMS, runtime no DiLink (1.1.0), smoke no S24 (1.2.0) e TV box
> Xiaomi (1.3.0).

## Patches

| Patch | Default | O que faz |
|---|---|---|
| `DiLink: support` | sim | `m1.x()` passa a devolver `K.TABLET` (destrava a rotação: `MainActivity` deixa de fixar `SENSOR_PORTRAIT`); `EmojiSearchFragment$a.onGlobalLayout()` retorna cedo em API < 30 (evita `NoSuchMethodError` em `WindowInsets.Type`/`setDecorFitsSystemWindows`); `getAppIntegrityToken()` devolve `null`, caminho que o app já trata; backport de `Objects.requireNonNullElse*`, `List.of`, `WifiInfo.getWifiStandard`, das chamadas de `WindowInsetsController` e dos acessores de *state description* (`View`/`AccessibilityNodeInfo`, API 30). |
| `DiLink: manifest` | não (dependência) | `minSdkVersion` 30 → 29 e remove `screenOrientation="portrait"` da única activity que o declarava (`MLIChromeTabActivity`), eliminando a feature implícita `android.hardware.screen.portrait`. |
| `DiLink: TLS trust` | não (dependência) | Substitui `res/xml/network_security_config.xml` para confiar em `@raw/apple_ca` (Apple Root CA - G3, já embutida no APK) **apenas** em `gsd.apple.com`; o resto continua usando o store do sistema. Nada é instalado no head unit. |
| `DiLink: web assets` | sim | Embute os assets JS da Apple (page + chunks `appleauth`) **transpilados para Chrome 74** dentro de `res/raw/js_ams_adaptor.js`; `PlatformCompat.interceptWebResource` serve esses assets em `shouldInterceptRequest`. Sem isso o WebView 74 do DiLink não parseia o ES2020 da Apple. |
| `DiLink: hide engine launcher` | não (engine) | Remove a categoria `LAUNCHER` da `MainActivity`: o engine não aparece no launcher e o companion é a única entrada. A action `MAIN` continua, então a intent explícita do companion (e `am start -n`) resolve. |

Evidências da análise (com `arquivo:linha`) em [`analysis/apple-music-7.0.0.md`](analysis/apple-music-7.0.0.md);
resultados dos testes em aparelho em [`analysis/validation.md`](analysis/validation.md). As análises de
TV ([`analysis/android-tv-viability.md`](analysis/android-tv-viability.md) e
[`analysis/tv-dpad-plan.md`](analysis/tv-dpad-plan.md)) são históricas: descrevem os patches `TV:`
que foram removidos.

### Limitações conhecidas

- **API 29 validada no DiLink**: o app abre, renderiza no landscape e navega. O backport cobre os
  pontos mapeados em [`analysis/api-levels.md`](analysis/api-levels.md) (insets/`WindowInsetsController`,
  `Objects.requireNonNullElse*`, `List.of`, `WifiInfo.getWifiStandard`, acessibilidade) e agora também
  `IBinder.getSuggestedMaxIpcSizeBytes()` (API 30, usado por `p507z3.C7059k`). Para API 28 ainda há
  `m1.<clinit>` (`FontStyle`), fora do escopo.
- **Login resolvido:** WebView 74 (assets JS transpilados), 2FA e commerce auth (OTP local
  GrandSlam/Store) funcionam. O `libCoreADI` nativo não é mais chamado.
- **UI (API 29):** o tema do login no WebView e a faixa preta da tela de letras estão corrigidos
  na 1.2.0 (smoke no S24 OK; validação no carro pendente). Seguem em aberto as linhas de toggle do
  Ajustes que não são pintadas e a decisão sobre o `peek` dos carrosséis (geometria está correta;
  os 36 dp à direita são o card seguinte).
- **TV:** a UI é o `tv-app`; o engine não tem entrada no launcher nem patches de foco. O bundle
  arm32 do engine precisa do split `armeabi-v7a` para instalar em TV box 32-bit — ver
  [Engine e companions](#engine-e-companions).
- **Entrada para patch: XAPK.** O bundle declara `apkFileType = XAPK`; arquivos `.apkm` são
  rejeitados pelo Manager. Use o XAPK do Apple Music 7.0.0 (APKPure).
- A identidade ADI embutida é sintética (sem conta); o APK final é merged e assinado com
  `CN=Morphe`, então é preciso desinstalar o Apple Music oficial antes de instalar o patcheado.

## Estrutura

```
settings.gradle.kts                  # plugin app.morphe.patches 1.3.3 (mavenLocal primeiro)
gradle/libs.versions.toml            # morphe-patcher 1.6.0, smali, gson
patches/
  build.gradle.kts                   # about/group do bundle
  src/main/kotlin/
    app/morphe/patches/applemusic/shared/Constants.kt      # Compatibility + AppTarget("7.0.0")
    app/morphe/patches/applemusic/dilink/AppleMusicDiLinkPatch.kt  # landscape + API 29 backport
    app/morphe/patches/applemusic/dilink/DilinkManifestPatch.kt    # minSdk + portrait lock
    app/morphe/patches/applemusic/dilink/DilinkTlsPatch.kt         # Apple Root CA G3 p/ gsd.apple.com
    app/morphe/patches/applemusic/dilink/EngineLauncherPatch.kt    # engine sem entrada no launcher
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
| `patches-1.3.0.mpp` | bundle aplicável (classes + `extensions/extension.mpe`) |
| `patches-1.3.0-sources.mpp` | sources |
| `patches-1.3.0-javadoc.mpp` | javadoc |

`generatePatchesList` reescreve `patches-list.json` (não editar à mão) e depende de `build`.

> O gerador lê o **primeiro** `.mpp` de `build/libs/`. Se houver bundles antigos de versões
> anteriores, rode `:patches:clean` (ou apague-os) antes de gerar, senão a lista sai com a versão
> errada — foi o que aconteceu aqui com 1.0.0/1.0.1.

> Sob o sandbox deste repo o Kotlin daemon não consegue abrir `~/.kotlin` e o build cai
> automaticamente para compilação in-process (o build continua verde). Para evitar o erro no log:
> `.\gradlew.bat :patches:build "-Pkotlin.compiler.execution.strategy=in-process"`.

### Build via GitHub Actions

Quando não há JVM utilizável no local (por exemplo, no próprio Android), o workflow
[`.github/workflows/build.yml`](.github/workflows/build.yml) roda:

```sh
./gradlew clean :patches:generatePatchesList :patches:buildAndroid --no-daemon
```

e publica `patches/build/libs/*.mpp` + `patches-list.json` como artefato (`if-no-files-found:
error`). Roda em `workflow_dispatch` (artifact) e em push de tag `v*`; no caso da tag ele também
cria a release — ver [Publicação (CI)](#publicação-ci).

Requer o secret de repositório **`GPR_KEY`** (token com `read:packages`, para o registry
`maven.pkg.github.com/MorpheApp/registry`) e, opcionalmente, **`GPR_USER`**. O `GITHUB_TOKEN`
padrão do repositório não tem acesso aos pacotes de outra organização. O JDK 21 e o SDK Android
(`platforms;android-36`, `build-tools;36.0.0`) são instalados no runner.

## Publicação (CI)

Um push de tag `v*` (ex.: `v1.3.0`) builda e publica a release `v1.3.0` em **uma única execução**
do workflow, anexando `patches/build/libs/*.mpp` e `patches-list.json`. Se houver fonte de APK, o
mesmo job também gera e anexa `AppleMusic-<versão>-AndroidTV-arm32.apk` — o **engine** 32-bit
(base + `armeabi_v7a` + `xhdpi` mergeados pelo APKEditor 1.4.9 e patcheados pela CLI do
morphe-desktop 2.1.1 com o launcher oculto), validado com `aapt2 dump badging` (sem
`launchable-activity`, `native-code: 'armeabi-v7a'`) e `apksigner verify --print-certs`
(`CN=Morphe`). Sem fonte, a release sai só com o bundle.

As notas da release são a seção `description` do [`patches-bundle.json`](patches-bundle.json),
copiada tal e qual — atualize-a antes de taguear. O job falha se um asset esperado não existir; a
publicação não é silenciada.

O `workflow_dispatch` tem os inputs opcionais `version` (sem o `v`) e `source_url`. Com `version`,
ele também publica a release (o `gh` cria a tag); sem `version`, só gera o artifact.

Secrets:

- **`GPR_KEY`** (obrigatório) — token com `read:packages` para `app.morphe:morphe-patcher` no
  GitHub Packages; **`GPR_USER`** (opcional) — conta do token. O `GITHUB_TOKEN` padrão do
  repositório não lê pacotes de outra organização.
- **`TV_SOURCE_URL`** (opcional) — URL do APKM/XAPK 4-ABI usado no APK do engine 32-bit; o input
  `source_url` do dispatch tem precedência. Sem ela, a release publica só `.mpp` +
  `patches-list.json`.

A release é criada pelo `gh` com o `github.token` do próprio workflow (o job declara
`permissions: contents: write`); não é preciso PAT extra.

## Aplicar o bundle localmente (sem celular)

Jars necessários (não versionados, baixar em `tools/`):

- `morphe-desktop-2.1.1-all.jar` — <https://github.com/MorpheApp/morphe-desktop/releases/download/v2.1.1/morphe-desktop-2.1.1-all.jar>
- `APKEditor-1.4.9.jar` — <https://github.com/REAndroid/APKEditor/releases/download/V1.4.9/APKEditor-1.4.9.jar>

### 1. Merge dos splits (base + arm64_v8a + mdpi)

```powershell
java -jar tools\APKEditor-1.4.9.jar m -i .pipeline\splits -o .pipeline\merged.apk
```

### 2. Aplicar o patch

```powershell
java -jar tools\morphe-desktop-2.1.1-all.jar patch `
  -p patches\build\libs\patches-1.3.0.mpp `
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
java -jar tools\morphe-desktop-2.1.1-all.jar patch `
  -p patches\build\libs\patches-1.3.0.mpp `
  -o .pipeline\applemusic-7.0.0-from-xapk.apk `
  apks\apple-music-7.0.0\AppleMusic_7.0.0_apkpure.xapk
```

Outros comandos úteis da CLI (`java -jar tools\morphe-desktop-2.1.1-all.jar --help`):

- `list-patches --patches <mpp>` — lista os patches do bundle e se estão habilitados.
- `list-versions --patches <mpp>` — versões de app declaradas na compatibilidade.
- flags de `patch`: `-e/--enable`, `-d/--disable`, `--force`, `--striplibs`, `--unsigned`, `--keystore`.

## Usar no app Morphe (Android)

O bundle é publicado em [releases](https://github.com/andersonlucasg3/apple-music-android-freedom/releases)
e o repositório serve como *patch source*:

- **Remote**: no Morphe → **Sources** → **+** → Remote →
  `github.com/andersonlucasg3/apple-music-android-freedom`.
- **Local**: baixe `patches-1.3.0.mpp` do release e abra no Morphe, ou
  `adb push patches-1.3.0.mpp /sdcard/Download/` e abra o arquivo no aparelho.

O Apple Music não é distribuído por este repositório, então o Morphe não oferece link de
download: forneça o **XAPK 7.0.0 (vc 1614)** na tela de seleção de APK. O Manager funde os
splits e aplica os mesmos patches da CLI.

> **Testado no Morphe Manager 2.3.0 (Galaxy S24, Android 16):** source carregada, Apple Music 7.0.0
> reconhecido, patch aplicado (1 patch) e APK instalado/aberto pelo próprio Manager. O bundle usa
> `Patcher-Version: 1.6.0`; o Manager aceita versões maiores e não exige atualização.

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

## Engine e companions

O engine é só backend: o bundle carrega os patches DiLink (support/web assets e as dependências
de manifest/TLS) e, no APK do engine, `DiLink: hide engine launcher` — a `MainActivity` não
aparece no launcher. A UI é dos companions:

- `tv-app/` — Android TV, navegação por D-pad;
- `car-app/` — head unit BYD DiLink, toque/D-pad.

Engine e companion são assinados com o mesmo cert e usam o UID compartilhado; o companion conecta
no `MediaBrowserService` do engine e o login abre a `MainActivity` do engine por intent explícita.
O plano de distribuição está no [ROADMAP](ROADMAP.md).

### Engine 32-bit (TV) precisa do split `armeabi-v7a`

O Apple Music 7.0.0 é distribuído por ABI **e** densidade. TV boxes (Mi Box/TV Stick, inclusive o
Xiaomi validado) são 32-bit: o APK só instala com `lib/armeabi-v7a`, e um bundle montado só com
`base + config.arm64_v8a` é rejeitado com `INSTALL_FAILED_NO_MATCHING_ABIS`. Monte com base +
`config.armeabi_v7a` + uma densidade (`config.xhdpi` serve para 1080p):

```powershell
java -jar tools\APKEditor-1.4.9.jar m -i .pipeline\tv\splits32 -o .pipeline\tv\merged32.apk
java -jar tools\morphe-desktop-2.1.1-all.jar patch `
  -p patches\build\libs\patches-1.4.0.mpp `
  -o .pipeline\release\AppleMusic-1.4.0-AndroidTV-arm32.apk `
  .pipeline\tv\merged32.apk `
  -e "DiLink: hide engine launcher"
```

Conferir o APK de saída (build-tools 36.0.0):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\aapt2.exe" dump badging `
  .pipeline\release\AppleMusic-1.4.0-AndroidTV-arm32.apk | Select-String `
  "launchable-activity|native-code|sdkVersion"
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs `
  .pipeline\release\AppleMusic-1.4.0-AndroidTV-arm32.apk
```

Esperado: nenhum `launchable-activity` (o engine não aparece no launcher),
`native-code: 'armeabi-v7a'`, `sdkVersion:'29'` e certificado `CN=Morphe`.

### Instalação

O engine sai no asset do release (`AppleMusic-<versão>-AndroidTV-arm32.apk`) ou é gerado
localmente. Instale o engine e depois o companion (`AppleMusic-TV.apk` / `AppleMusic-Car.apk`) por
ADB ou por um app de transferência de arquivos:

```powershell
adb connect <ip-da-tv>:5555
adb install -r .pipeline\release\AppleMusic-1.4.0-AndroidTV-arm32.apk
adb install -r AppleMusic-TV.apk
```

`am start -n com.apple.android.music/com.apple.android.music.common.MainActivity` continua
funcionando para abrir a tela de login do engine: a action `MAIN` permanece, só a categoria
`LAUNCHER` é removida.

## Licença

MIT — ver [LICENSE](LICENSE).
