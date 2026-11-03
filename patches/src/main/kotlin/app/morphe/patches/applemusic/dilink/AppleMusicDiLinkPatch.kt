package app.morphe.patches.applemusic.dilink

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.applemusic.shared.Constants.APPLE_MUSIC_DILINK
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

private const val EXTENSION_PACKAGE = "Lapp/morphe/extension/"
private const val OBJECTS_COMPAT = "${EXTENSION_PACKAGE}applemusic/ObjectsCompat;"
private const val LIST_COMPAT = "${EXTENSION_PACKAGE}applemusic/ListCompat;"
private const val INSETS_COMPAT = "${EXTENSION_PACKAGE}applemusic/WindowInsetsControllerCompat;"
private const val WIFI_COMPAT = "${EXTENSION_PACKAGE}applemusic/WifiInfoCompat;"

/**
 * x2/W and x2/W$a are androidx.core.view.WindowInsetsControllerCompat and its Impl30 after R8.
 * Descriptors confirmed with dexdump on classes.dex of 7.0.0 (see analysis/api-levels.md §1.4).
 */
private const val W = "Lx2/W;"
private const val W_IMPL = "Lx2/W\$a;"
private const val SDK_INT = "Landroid/os/Build\$VERSION;->SDK_INT:I"

/**
 * m1.x() is the only method in the dex that contains "android.hardware.sensor.hinge_angle"
 * and the only one returning K; (m1.java:505-515). Forcing K.TABLET reaches every consumer,
 * including MainActivity's setRequestedOrientation(-1) decision (MainActivity.java:937-945).
 */
internal object TabletFingerprint : Fingerprint(
    returnType = "Lcom/apple/android/music/utils/K;",
    strings = listOf("android.hardware.sensor.hinge_angle"),
)

/**
 * EmojiSearchFragment$a.onGlobalLayout() calls API 30 WindowInsets/Window methods without
 * any SDK_INT check (EmojiSearchFragment.java:82,85,92,104).
 */
internal object EmojiGlobalLayoutFingerprint : Fingerprint(
    definingClass = "Lcom/apple/android/music/common/views/EmojiSearchFragment\$a;",
    name = "onGlobalLayout",
    returnType = "V",
)

/**
 * The implementing class is obfuscated (Le7/i; in 7.0.0), so it is identified by the
 * interface it implements. getAppIntegrityToken is the only method with this name in a
 * class implementing IExternalAppIntegrityProvider.
 */
internal object AppIntegrityTokenFingerprint : Fingerprint(
    name = "getAppIntegrityToken",
    custom = { _, classDef ->
        "Lcom/apple/mediaservices/amskit/fairplay/IExternalAppIntegrityProvider;" in classDef.interfaces
    },
)

/**
 * openKeyboardForTitle() builds the androidx WindowInsetsControllerCompat wrapper
 * (p468x2.W / x2.W$a), whose constructor calls Window.getInsetsController() (API 30)
 * unconditionally. The app was built with minSdk 30, so R8 already removed the
 * SDK_INT >= 30 fallback that the original compat class had; on API < 30 this path
 * throws NoSuchMethodError before the dialog's keyboard is shown.
 */
internal object NewPlaylistKeyboardFingerprint : Fingerprint(
    definingClass = "Lcom/apple/android/music/collection/mediaapi/fragment/NewPlaylistFragment;",
    name = "openKeyboardForTitle",
    returnType = "V",
)

/**
 * x2/W$a.<init>(Window, x2/v) is the Impl30 constructor: it calls Window.getInsetsController()
 * (API 30) with no guard (dexdump: 6ab29c, invoke-virtual @0000). This is the constructor used
 * by every API < 35 caller (dexdump: x2/W.<init> @0x0018 and every new-instance x2/W$a).
 */
internal object InsetsImplWindowInitFingerprint : Fingerprint(
    definingClass = W_IMPL,
    name = "<init>",
    parameters = listOf("Landroid/view/Window;", "Lx2/v;"),
    returnType = "V",
)

/** x2/W.a(Z) — setAppearanceLightStatusBars, dereferences the controller (dexdump: 6ab4c0). */
internal object InsetsLightStatusBarsFingerprint : Fingerprint(
    definingClass = W,
    name = "a",
    parameters = listOf("Z"),
    returnType = "V",
)

/** x2/W.b(Z) — setAppearanceLightNavigationBars, dereferences the controller (dexdump: 6ab51c). */
internal object InsetsLightNavigationBarsFingerprint : Fingerprint(
    definingClass = W,
    name = "b",
    parameters = listOf("Z"),
    returnType = "V",
)

/** x2/W$a.a()Z — isAppearanceLightStatusBars (dexdump: 6ab234). */
internal object InsetsIsLightStatusBarsFingerprint : Fingerprint(
    definingClass = W_IMPL,
    name = "a",
    parameters = emptyList(),
    returnType = "Z",
)

/** x2/W$a.b()Z — isAppearanceLightNavigationBars (dexdump: 6ab268). */
internal object InsetsIsLightNavigationBarsFingerprint : Fingerprint(
    definingClass = W_IMPL,
    name = "b",
    parameters = emptyList(),
    returnType = "Z",
)

/** x2/W$a.c()V — setSystemBarsBehavior (dexdump: 6ab2fc). */
internal object InsetsSetBehaviorFingerprint : Fingerprint(
    definingClass = W_IMPL,
    name = "c",
    parameters = emptyList(),
    returnType = "V",
)

/**
 * FullScreenVideoActivity.onCreate ends with the API 30 insets block
 * (FullScreenVideoActivity.java:691-695):
 *
 *   Window window = getWindow();
 *   C6812v impl = new C6812v(getWindow().getDecorView());
 *   W.a v = SDK_INT >= 35 ? new W.c(window, impl) : new W.a(window, impl);
 *   v.c();
 *   v.f82859a.hide(519);
 *
 * On API 29 v.f82859a is null, so the block has to be skipped as a whole.
 */
internal object FullScreenVideoOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/apple/android/music/player2/FullScreenVideoActivity;",
    name = "onCreate",
    parameters = listOf("Landroid/os/Bundle;"),
    returnType = "V",
)

/** One "redirect these platform calls to an extension helper" rule. */
private class RedirectRule(
    val key: String,
    val targetClass: String,
    val targetNames: Set<String>,
    val helperClass: String,
    val helperParameters: (MethodReference) -> List<String>,
    val expectedCalls: Int,
)

private fun Instruction.methodCalled(definingClass: String, names: Set<String>): MethodReference? {
    val reference = (this as? ReferenceInstruction)?.reference as? MethodReference ?: return null
    return reference.takeIf { it.definingClass == definingClass && it.name in names }
}

/**
 * Rewrites every invoke-* of the given platform methods to a static helper that receives the
 * original receiver as its first argument. Register list and return type are preserved, so the
 * call site keeps working; only the declaring class and the prototype change.
 */
private fun BytecodePatchContext.redirectCalls(rules: List<RedirectRule>): Map<String, Int> {
    val counts = rules.associate { it.key to 0 }.toMutableMap()

    val candidates = mutableListOf<ClassDef>()
    classDefForEach { classDef ->
        // Never rewrite the helpers themselves (they call the platform methods on purpose).
        if (!classDef.type.startsWith(EXTENSION_PACKAGE) &&
            classDef.methods.any { method ->
                method.implementation?.instructions?.any { instruction ->
                    rules.any { rule -> instruction.methodCalled(rule.targetClass, rule.targetNames) != null }
                } == true
            }
        ) {
            candidates += classDef
        }
    }

    for (classDef in candidates) {
        for (method in mutableClassDefBy(classDef).methods) {
            val implementation = method.implementation ?: continue
            val rewrites = implementation.instructions.mapIndexedNotNull { index, instruction ->
                rules.firstOrNull { rule ->
                    instruction.methodCalled(rule.targetClass, rule.targetNames) != null
                }?.let { index to it }
            }
            for ((index, rule) in rewrites) {
                val instruction = implementation.instructions[index] as Instruction35c
                val reference = (instruction as ReferenceInstruction).reference as MethodReference
                implementation.replaceInstruction(
                    index,
                    BuilderInstruction35c(
                        Opcode.INVOKE_STATIC,
                        instruction.registerCount,
                        instruction.registerC,
                        instruction.registerD,
                        instruction.registerE,
                        instruction.registerF,
                        instruction.registerG,
                        ImmutableMethodReference(
                            rule.helperClass,
                            reference.name,
                            rule.helperParameters(reference),
                            reference.returnType,
                        ),
                    ),
                )
                counts[rule.key] = counts.getValue(rule.key) + 1
            }
        }
    }

    counts.forEach { (key, count) ->
        val expected = rules.first { it.key == key }.expectedCalls
        check(count == expected) { "Expected $expected $key call sites, redirected $count" }
    }
    return counts
}

@Suppress("unused")
val appleMusicDiLinkPatch = bytecodePatch(
    name = "Apple Music DiLink support",
    description = "Unlocks landscape rotation, lowers the API floor to Android 10 (API 29), " +
        "backports the API 30 java/androidx calls and bypasses Play Integrity.",
    default = true,
) {
    compatibleWith(APPLE_MUSIC_DILINK)
    dependsOn(dilinkManifestPatch)
    extendWith("extensions/extension.mpe")

    execute {
        // x() has 2 registers and no parameters, so v0 is free at method entry.
        TabletFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Lcom/apple/android/music/utils/K;->TABLET:Lcom/apple/android/music/utils/K;
                return-object v0
            """,
        )

        // onGlobalLayout() has 5 registers and 1 parameter (this = v4), so v0/v1 are free.
        // The original body registers API 30 window insets listeners; returning early on
        // API < 30 skips all of them, including setDecorFitsSystemWindows(false).
        EmojiGlobalLayoutFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    sget v0, $SDK_INT
                    const/16 v1, 0x1e
                    if-ge v0, v1, :supported
                    return-void
                """,
                ExternalLabel("supported", getInstruction(0)),
            )
        }

        // getAppIntegrityToken() has 6 registers and 2 parameters, so v0 is free.
        // Its callers already handle a null token (p095e7/i.java:66-68).
        AppIntegrityTokenFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """,
        )

        // openKeyboardForTitle() has 5 registers and 1 parameter (this = v3), so v0/v1 are
        // free. Skipping it on API < 30 avoids constructing the WindowInsetsControllerCompat
        // wrapper, whose constructor calls Window.getInsetsController() (API 30). The only
        // degradation is that the new-playlist dialog does not focus the title field.
        NewPlaylistKeyboardFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    sget v0, $SDK_INT
                    const/16 v1, 0x1e
                    if-ge v0, v1, :supported
                    return-void
                """,
                ExternalLabel("supported", getInstruction(0)),
            )
        }

        // ---------------------------------------------------------------------------------
        // API 29 backport: platform calls that only exist on API 30.
        // ---------------------------------------------------------------------------------

        // java.util.Objects.requireNonNullElse/ElseGet (7 call sites: the 5 playback getters
        // of MediaPlaybackPreferences, StorePlaybackQueueItemProvider.isExplicitAllowed and
        // medialibrary.library.a.<init>), java.util.List.of (7 call sites, one of them
        // BaseAssetRequest.fetchMiniSinfs), WifiInfo.getWifiStandard (metrics.f.b) and the
        // WindowInsetsController appearance/behavior calls, which all receive a null
        // controller on API 29 because of the x2/W$a constructor patch below.
        redirectCalls(
            listOf(
                RedirectRule(
                    key = "Objects.requireNonNullElse*",
                    targetClass = "Ljava/util/Objects;",
                    targetNames = setOf("requireNonNullElse", "requireNonNullElseGet"),
                    helperClass = OBJECTS_COMPAT,
                    helperParameters = { reference ->
                        listOf(
                            "Ljava/lang/Object;",
                            "Ljava/util/function/Supplier;".takeIf {
                                reference.name == "requireNonNullElseGet"
                            } ?: "Ljava/lang/Object;",
                        )
                    },
                    expectedCalls = 7,
                ),
                RedirectRule(
                    key = "List.of",
                    targetClass = "Ljava/util/List;",
                    targetNames = setOf("of"),
                    helperClass = LIST_COMPAT,
                    helperParameters = { reference ->
                        when (reference.parameterTypes.size) {
                            1 -> listOf("Ljava/lang/Object;")
                            2 -> listOf("Ljava/lang/Object;", "Ljava/lang/Object;")
                            else -> error("Unsupported List.of arity: ${reference.parameterTypes.size}")
                        }
                    },
                    expectedCalls = 7,
                ),
                RedirectRule(
                    key = "WifiInfo.getWifiStandard",
                    targetClass = "Landroid/net/wifi/WifiInfo;",
                    targetNames = setOf("getWifiStandard"),
                    helperClass = WIFI_COMPAT,
                    helperParameters = { listOf("Landroid/net/wifi/WifiInfo;") },
                    expectedCalls = 1,
                ),
                RedirectRule(
                    key = "WindowInsetsController.setSystemBarsAppearance",
                    targetClass = "Landroid/view/WindowInsetsController;",
                    targetNames = setOf("setSystemBarsAppearance"),
                    helperClass = INSETS_COMPAT,
                    helperParameters = { listOf("Ljava/lang/Object;", "I", "I") },
                    expectedCalls = 26,
                ),
                RedirectRule(
                    key = "WindowInsetsController.getSystemBarsAppearance",
                    targetClass = "Landroid/view/WindowInsetsController;",
                    targetNames = setOf("getSystemBarsAppearance"),
                    helperClass = INSETS_COMPAT,
                    helperParameters = { listOf("Ljava/lang/Object;") },
                    expectedCalls = 4,
                ),
                RedirectRule(
                    key = "WindowInsetsController.setSystemBarsBehavior",
                    targetClass = "Landroid/view/WindowInsetsController;",
                    targetNames = setOf("setSystemBarsBehavior"),
                    helperClass = INSETS_COMPAT,
                    helperParameters = { listOf("Ljava/lang/Object;", "I") },
                    expectedCalls = 2,
                ),
            ),
        )

        // x2/W$a.<init>(Window, x2/v): 4 registers, ins 3 -> v0 free, v1 = this, v2 = window,
        // v3 = impl. Morphe 2.1.0 has no addFreeRegisters, and there is no second free register
        // for a comparison constant, so the SDK test goes through the pure helper
        // isAtLeast30(int) and touches only v0:
        //   original instruction 0 = Window.getInsetsController() (API 30)
        //   original instruction 1 = its move-result-object
        //   original instruction 2 = invoke-direct <init>(WindowInsetsController, x2/v), the
        //                            delegation that runs Object.<init> and fills f82859a/b
        // The constructor body is untouched: on API < 30 the delegating call receives null (which
        // merges with the WindowInsetsController produced by the guarded branch, so the verifier
        // never needs to resolve that type at the call site).
        InsetsImplWindowInitFingerprint.method.apply {
            val instructions = implementation?.instructions ?: error("${W_IMPL}init has no code")
            check(instructions.size == 5) { "${W_IMPL}init(Window, x2/v): expected 5 instructions" }
            check(instructions[0].opcode == Opcode.INVOKE_VIRTUAL) {
                "${W_IMPL}init: instruction 0 is not the controller call"
            }
            check(instructions[2].opcode == Opcode.INVOKE_DIRECT) {
                "${W_IMPL}init: instruction 2 is not the delegating <init> call"
            }
            addInstructionsWithLabels(
                0,
                """
                    sget v0, $SDK_INT
                    invoke-static {v0}, $INSETS_COMPAT->isAtLeast30(I)Z
                    move-result v0
                    if-eqz v0, :legacy
                    goto :supported
                    :legacy
                    const/4 v0, 0x0
                    goto :call
                """,
                ExternalLabel("supported", getInstruction(0)),
                ExternalLabel("call", getInstruction(2)),
            )
        }

        // x2/W.a(Z): 5 registers, ins 2 -> v3 = this, v4 = value; v0-v2 are free.
        // With a null controller the original setSystemBarsAppearance would NPE, so only the
        // Impl20 systemUiVisibility fallback is executed (16 = APPEARANCE_LIGHT_STATUS_BARS).
        InsetsLightStatusBarsFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    iget-object v0, v3, ${W}->a:${W_IMPL}
                    iget-object v1, v0, ${W_IMPL}->a:Landroid/view/WindowInsetsController;
                    if-eqz v1, :supported
                    iget-object v1, v0, ${W_IMPL}->c:Landroid/view/Window;
                    if-eqz v1, :done
                    const/16 v2, 0x10
                    if-eqz v4, :clear
                    invoke-virtual {v0, v2}, ${W_IMPL}->d(I)V
                    goto :done
                    :clear
                    invoke-virtual {v0, v2}, ${W_IMPL}->e(I)V
                    :done
                    return-void
                """,
                ExternalLabel("supported", getInstruction(0)),
            )
        }

        // x2/W.b(Z): 6 registers, ins 2 -> v4 = this, v5 = value; v0-v3 are free.
        // The fallback flag is 0x2000 (C.ROLE_FLAG_EASY_TO_READ), the value the original
        // body passes to d()/e(); setSystemBarsAppearance uses 8 (LIGHT_NAVIGATION_BARS).
        InsetsLightNavigationBarsFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    iget-object v0, v4, ${W}->a:${W_IMPL}
                    iget-object v1, v0, ${W_IMPL}->a:Landroid/view/WindowInsetsController;
                    if-eqz v1, :supported
                    iget-object v1, v0, ${W_IMPL}->c:Landroid/view/Window;
                    if-eqz v1, :done
                    const/16 v2, 0x2000
                    if-eqz v5, :clear
                    invoke-virtual {v0, v2}, ${W_IMPL}->d(I)V
                    goto :done
                    :clear
                    invoke-virtual {v0, v2}, ${W_IMPL}->e(I)V
                    :done
                    return-void
                """,
                ExternalLabel("supported", getInstruction(0)),
            )
        }

        // x2/W$a.a()Z / b(): 2 registers, ins 1 -> v1 = this, v0 is free.
        // Reading getSystemBarsAppearance() with a null controller would NPE; report "not set".
        for (fingerprint in listOf(InsetsIsLightStatusBarsFingerprint, InsetsIsLightNavigationBarsFingerprint)) {
            fingerprint.method.apply {
                addInstructionsWithLabels(
                    0,
                    """
                        iget-object v0, v1, ${W_IMPL}->a:Landroid/view/WindowInsetsController;
                        if-eqz v0, :supported
                        const/4 v0, 0x0
                        return v0
                    """,
                    ExternalLabel("supported", getInstruction(0)),
                )
            }
        }

        // x2/W$a.c()V: 4 registers, ins 1 -> v3 = this, v0/v1/v2 are free.
        // Instruction 0 is the window load, instruction 3 the start of the tag path
        // (dexdump: 6ab306 invoke-virtual Window.getDecorView). With a null controller the
        // tag/systemUiVisibility path is still valid when the window is known, and it is
        // skipped (return) when it is not.
        InsetsSetBehaviorFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    iget-object v0, v3, ${W_IMPL}->a:Landroid/view/WindowInsetsController;
                    if-eqz v0, :supported
                    iget-object v0, v3, ${W_IMPL}->c:Landroid/view/Window;
                    if-eqz v0, :tag
                    return-void
                    :tag
                    const/4 v1, 0x2
                    goto :tagPath
                """,
                ExternalLabel("supported", getInstruction(0)),
                ExternalLabel("tagPath", getInstruction(3)),
            )
        }

        // FullScreenVideoActivity.onCreate: skip only the trailing insets block on API < 30.
        // The anchor is the SDK_INT read that selects W$c/W$a; the skip target is the
        // instruction right after WindowInsetsController.hide(519), i.e. the return-void.
        // 10 registers, ins 2 -> v9 = this, v10? no: v8 = decorView (dead after the C6812v
        // constructor) and v1 is dead since the last call at 0x10f; both are overwritten by
        // the original code right after the injection point.
        FullScreenVideoOnCreateFingerprint.method.apply {
            val instructions = implementation?.instructions ?: error("onCreate has no implementation")
            val hideIndex = instructions.indexOfFirst { instruction ->
                instruction.methodCalled(
                    "Landroid/view/WindowInsetsController;",
                    setOf("hide"),
                ) != null
            }
            check(hideIndex >= 0) { "FullScreenVideoActivity.onCreate has no WindowInsetsController.hide call" }
            val sdkIndex = (hideIndex downTo 0).first { index ->
                val reference = (instructions[index] as? ReferenceInstruction)?.reference
                reference is FieldReference &&
                    reference.definingClass == "Landroid/os/Build\$VERSION;" &&
                    reference.name == "SDK_INT"
            }
            addInstructionsWithLabels(
                sdkIndex,
                """
                    sget v8, $SDK_INT
                    const/16 v1, 0x1e
                    if-ge v8, v1, :insets
                """,
                ExternalLabel("insets", getInstruction(hideIndex + 1)),
            )
        }
    }
}
