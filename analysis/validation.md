# Validação em aparelho — Apple Music DiLink patches

> Dispositivo de teste: **Samsung SM-S921B (Galaxy S24), Android 16 (API 36), arm64-v8a, 1080×2340 @480dpi**.
> Head unit alvo (**DiLink 3.0, Android 10/API 29**) ainda não testado — é o único alvo que exercita
> o backport de API 30. Data: 2026-11-03.

## 0. Versão 1.0.4 (2026-11-05)

A 1.0.4 leva para o bundle o backport de acessibilidade e o trust TLS descobertos na depuração
do DiLink. Ela foi **compilada** (GitHub Actions, `patches-1.0.4.mpp`) e o workflow de CI rodou
verde, mas **não foi aplicada nem testada no carro** nesta data. Os testes de aparelho abaixo
referem-se às versões anteriores (1.0.0–1.0.3); não valem como validação da 1.0.4.

## 1. APK patcheado (CLI desktop)

| Passo | Resultado |
|---|---|
| `adb install .pipeline/out.apk` | `Success` |
| Cold start `MainActivity` | `Status: ok`, `TotalTime: 614 ms`, sem crash no logcat |
| UI | Onboarding/login renderizados (tela "Iniciar Sessão") |

## 2. Landscape

Com a rotação forçada (`user_rotation 1`):

- `requestedOrientation=SCREEN_ORIENTATION_UNSPECIFIED`
- `overrideConfig` com `w780dp h360dp ... land`, `mBounds=Rect(0,0-2340,1080)`
- UI renderizada em tela larga (login centralizado, layout `w600dp`).

Sem o patch, `m1.x()` classificaria 1080/480dpi = 360dp como `PHONE` e `MainActivity` chamaria
`setRequestedOrientation(7)` (`SENSOR_PORTRAIT`).

## 3. Morphe mobile (Manager 2.3.0)

| Passo | Resultado |
|---|---|
| Source `github.com/andersonlucasg3/AppleMusic-DiLink` | adicionada; "Apple Music" aparece com `v7.0.0` |
| Pacote do app | XAPK 7.0.0 selecionado do armazenamento |
| Patch | `Apple Music DiLink Patches 1.0.0`, 1 patch, status **Patchado** |
| Instalação | feita pelo Manager (Play Protect barrou a 1ª tentativa; verificador desativado temporariamente e restaurado) |
| App pós-instalação | cold start `497 ms`, `MainActivity` em foco, sem `FATAL` |

Isso prova compatibilidade **apply-time** entre o bundle (`Patcher-Version: 1.6.0`) e o patcher do
Manager (1.15.x).

### Metadata (1.0.2)

O bundle declara `apkFileType = XAPK` e os SHA-256 das duas assinaturas do APK original (Apple Inc.
para API < 33 e Google Inc. para API 33+, rotação de certificado). Com isso:

- o XAPK original passou na verificação de assinatura e o repatch com a source 1.0.2 rodou sem aviso;
- um app **já patcheado** instalado aparece como **“Não verificado”** no Manager (assinatura do
  Manager ≠ original) — é o comportamento esperado, não um defeito do bundle.

## 4. Startup sem Google Play Services

`pm disable-user com.google.android.gms` + `pm clear com.apple.android.music` + cold start:

- App abriu, mostrou o onboarding/login, processo vivo (`pid`), **zero** `FATAL`/`NoSuchMethodError`/
  `NoClassDefFoundError`.
- Nenhum diálogo de Play age-signals (degrada sem GMS).
- GMS reativado (`enabled=1`) e Play Protect restaurado (`verifier_verify_adb_installs=1`).

**Login sem GMS**: ainda com o Play Services desativado, tocar em **"Iniciar sessão"** abriu o
formulário "Continue com o e-mail" da Apple (tela nativa/web do fluxo de auth), sem crash e sem
erro de integrity. Ou seja, o caminho de autenticação não depende do GMS. O login completo não foi
feito (sem credenciais).

## 5. Caminho legado API < 30 (build de debug)

`isAtLeast30()` forçado a `false` (build temporário, não commitado) para executar em ART real o
bytecode que só roda em API 29:

- `isAtLeast30(36) -> forced false` **9 vezes** (startup + 2 rotações) — comprova que o ramo
  `const/4 v0, 0` (controller `null`) do construtor `x2.W$a` foi o executado.
- Fluxo: login → tap no campo de e-mail → teclado abriu (`mInputShown=true`) → landscape → portrait,
  **PID único**, sem crash; `logcat -b crash` vazio.
- Exercitados: `W$a` ctor legado, `W.a/b` null + `d()/e()`, `W$a.a/b`, shims do `e.p.b` com controller null,
  relayout nas rotações.
- Não exercitados: `x2/v$b` (só via teclado Compose/SearchView/diálogo de nova playlist — exige login),
  guarda do `FullScreenVideoActivity` (exige vídeo fullscreen) e `W$a.c()`.
- Fonte revertida, `git status` limpo, bundle `1.0.1` restaurado (700.292 B) e app normal reinstalado.

## 6. Risco residual

1. **API 29 real**: ausência de `WindowInsetsController`/`Objects.requireNonNullElse`/`List.of` não é
   simulável em API 36; o teste de caminho legado valida a lógica injetada, não a ausência de classe.
2. `x2/v$b` e `FullScreenVideoActivity` não foram executados.
3. Sem GMS o fluxo testado foi só o onboarding; login/reprodução reais no DiLink continuam pendentes.
