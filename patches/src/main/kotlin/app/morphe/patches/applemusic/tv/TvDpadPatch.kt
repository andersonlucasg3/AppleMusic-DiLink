package app.morphe.patches.applemusic.tv

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.applemusic.shared.Constants.APPLE_MUSIC_DILINK
import org.w3c.dom.Element

private const val MATERIAL_CARD_VIEW = "com.google.android.material.card.MaterialCardView"
private const val DEFAULT_FOCUS_HIGHLIGHT = "android:defaultFocusHighlightEnabled"
private const val FOREGROUND = "android:foreground"
private const val RIPPLE = "?attr/selectableItemBackground"
private const val FOCUSABLE = "android:focusable"
private const val FOCUSABLE_IN_TOUCH_MODE = "android:focusableInTouchMode"


/**
 * A layout and the View inside it that takes the D-pad focus. The app already makes the clickable
 * View focusable at runtime, so the highlight has to go on that View: the layout root for the
 * swiping grids, the `card_content` child for the MaterialCardView cards.
 *
 * `forceForeground` adds the ripple even when the View has a background of its own (the player
 * controls use `selectableItemBackgroundBorderless` backgrounds).
 */
private class FocusTarget(
    val path: String,
    val id: String? = null,
    val forceForeground: Boolean = false,
    val focusable: Boolean = false,
)

private val FOCUS_TARGETS = listOf(
    // Listen Now, swiping grids: the click is bound to the ConstraintLayout root.
    FocusTarget("res/layout/swiping_grid_a_standard.xml"),
    FocusTarget("res/layout/swiping_grid_a_catalog_only.xml"),
    FocusTarget("res/layout/swiping_grid_b_standard.xml"),
    // Listen Now, MaterialCardView cards: the click is bound to the `card_content` child.
    FocusTarget("res/layout/power_swoosh_container_a.xml", "card_content"),
    FocusTarget("res/layout/power_swoosh_container_b.xml", "card_content"),
    FocusTarget("res/layout/super_hero_container_a.xml", "card_content"),
    // Search: result row and landing row.
    FocusTarget("res/layout/search_list_a.xml"),
    FocusTarget("res/layout/search_landing_row.xml"),
    FocusTarget("res/layout/small_list_d_item.xml"),
    FocusTarget("res/layout-w600dp/small_list_d_item.xml"),
)

/** Mini player: the whole bar click-expands the Now Playing fragment. */
private val MINI_PLAYER_TARGETS = listOf(
    FocusTarget("res/layout/mini_player.xml", "mini_player_content", true, true),
    FocusTarget("res/layout-w830dp/mini_player.xml", "mini_player_content", true, true),
)

/** Now Playing: the media controls live in a shared include, the rest in the player layout. */
private val NOW_PLAYING_TARGETS = listOf(
    FocusTarget("res/layout/constraint_player_media_controls.xml", "seek_bar_controls"),
    FocusTarget("res/layout/player_progress_seek_bar.xml", "progress", true),
    FocusTarget("res/layout/constraint_player_media_controls.xml", "play_pause", true),
    FocusTarget("res/layout/constraint_player_media_controls.xml", "previous_rewind", true),
    FocusTarget("res/layout/constraint_player_media_controls.xml", "next_fast_forward", true),
    FocusTarget("res/layout/constraint_player_media_controls.xml", "player_lyrics", true),
    FocusTarget("res/layout/constraint_player_media_controls.xml", "player_queue", true),
    FocusTarget("res/layout/constraint_player_media_controls.xml", "media_route_button", true),
    FocusTarget("res/layout/music_player.xml", "enter_full_screen", true),
    FocusTarget("res/layout/music_player.xml", "list_favorite_icon", true),
    FocusTarget("res/layout/music_player.xml", "list_left_icon", true),
    FocusTarget("res/layout-w600dp/music_player.xml", "enter_full_screen", true),
    FocusTarget("res/layout-w600dp/music_player.xml", "list_favorite_icon", true),
    FocusTarget("res/layout-w600dp/music_player.xml", "list_left_icon", true),
)

/**
 * The media-controls container is focusable and swallows LEFT/DOWN inside the sheet; the arrows
 * have to move between its children instead.
 */
private val NON_FOCUSABLE_TARGETS = listOf(
    "res/layout/music_player.xml" to "player_controls",
    "res/layout-w600dp/music_player.xml" to "player_controls",
)

/**
 * The content viewport ends below the top bar and above the mini player, so the FocusFinder can
 * leave the list in both directions without any bytecode hook. The padding goes on the fragment
 * container, not on the individual lists.
 */
private val CONTENT_HOSTS = listOf(
    "res/layout/fragment_music_content.xml",
    "res/layout-w600dp/fragment_music_content.xml",
)
private const val CONTENT_HOST_ID = "tab_navigation_host"

@Suppress("unused")
internal val tvDpadPatch = resourcePatch(
    name = "TV: D-pad focus",
    description = "Adds a visible focus highlight to the Listen Now cards, the Search rows and " +
        "the player, and reserves room for the mini player around the content viewport.",
    default = false,
) {
    compatibleWith(APPLE_MUSIC_DILINK)
    dependsOn(tvManifestPatch)

    finalize {
        for (target in FOCUS_TARGETS + MINI_PLAYER_TARGETS + NOW_PLAYING_TARGETS) {
            document(target.path).use { document ->
                val root: Element = document.documentElement
                val id = target.id
                val view: Element = if (id == null) root else {
                    findById(root, id) ?: error("$id not found in ${target.path}")
                }

                view.setAttribute(DEFAULT_FOCUS_HIGHLIGHT, "true")
                if (target.focusable) {
                    view.setAttribute(FOCUSABLE, "true")
                    view.setAttribute(FOCUSABLE_IN_TOUCH_MODE, "true")
                }
                val hasOwnBackground = view.hasAttribute("android:background") ||
                    view.hasAttribute(FOREGROUND)
                if (target.forceForeground || (!hasOwnBackground && view.tagName != MATERIAL_CARD_VIEW)) {
                    view.setAttribute(FOREGROUND, RIPPLE)
                }
            }
        }

        for ((path, id) in NON_FOCUSABLE_TARGETS) {
            document(path).use { document ->
                val view = findById(document.documentElement, id) ?: return@use
                view.setAttribute(FOCUSABLE, "false")
                view.setAttribute(FOCUSABLE_IN_TOUCH_MODE, "false")
            }
        }

        // Keep the focus on the pill bar instead of dropping it on an extra DOWN.
        for (path in listOf("res/layout/mini_player.xml", "res/layout-w830dp/mini_player.xml")) {
            document(path).use { document ->
                findById(document.documentElement, "mini_player_content")?.let { content ->
                    content.setAttribute("android:nextFocusDown", "@id/mini_player_content")
                }
            }
        }
        for (path in CONTENT_HOSTS) {
            document(path).use { document ->
                val host = findById(document.documentElement, CONTENT_HOST_ID)
                    ?: error("$CONTENT_HOST_ID not found in $path")
                host.setAttribute("android:paddingTop", "?attr/actionBarSize")
                host.setAttribute("android:paddingBottom", "80dp") // miniplayer_height (64dp) + 16dp gap
                host.setAttribute("android:clipToPadding", "false")
            }
        }
    }
}

private fun findById(root: Element, id: String): Element? {
    val elements = root.getElementsByTagName("*")
    return (0 until elements.length)
        .map { index -> elements.item(index) as Element }
        .firstOrNull { element -> element.getAttribute("android:id").endsWith("/$id") }
}










