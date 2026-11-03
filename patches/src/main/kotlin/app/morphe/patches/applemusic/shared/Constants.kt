package app.morphe.patches.applemusic.shared

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    /**
     * Apple Music 7.0.0 (versionCode 1614), the version that ships/installs on the
     * BYD DiLink 3.0 head unit (Android 9/10).
     */
    val APPLE_MUSIC_DILINK = Compatibility(
        name = "Apple Music (DiLink)",
        packageName = "com.apple.android.music",
        appIconColor = 0xFA243C,
        targets = listOf(
            AppTarget("7.0.0"),
        ),
    )
}
