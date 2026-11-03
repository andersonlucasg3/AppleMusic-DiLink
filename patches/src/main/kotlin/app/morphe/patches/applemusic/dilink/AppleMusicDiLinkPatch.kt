package app.morphe.patches.applemusic.dilink

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.applemusic.shared.Constants.APPLE_MUSIC_DILINK

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

@Suppress("unused")
val appleMusicDiLinkPatch = bytecodePatch(
    name = "Apple Music DiLink support",
    description = "Unlocks landscape rotation, lowers the API floor to Android 9/10 and bypasses Play Integrity.",
    default = true,
) {
    compatibleWith(APPLE_MUSIC_DILINK)
    dependsOn(dilinkManifestPatch)

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
                    sget v0, Landroid/os/Build${'$'}VERSION;->SDK_INT:I
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
    }
}
