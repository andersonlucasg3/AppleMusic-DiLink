package com.anderson.applemusic.car

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.anderson.applemusic.core.BrowseItem
import com.anderson.applemusic.core.PlaybackSnapshot
import com.anderson.applemusic.core.Section

/**
 * Touch-first client for the BYD DiLink head unit (1920x1080, API 29).
 *
 * Two navigation levels only: the tab root (shelves for Início/Rádio, a category list
 * for Biblioteca) and one detail list. The mini player is fixed at the bottom. The
 * visual language is Apple Music: black canvas, dark surfaces, Inter type and one red
 * accent; state is communicated with colour and a light scale, never with a shadow.
 */
@Composable
fun CarApp(viewModel: CarViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()

    BackHandler(enabled = state.stack.isNotEmpty()) { viewModel.back() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Background)) {
        // Phone in landscape (~360 dp tall) gets smaller cards; the head unit is @1080p.
        val compact = maxHeight < 520.dp
        Column(Modifier.fillMaxSize()) {
            TopBar(
                tabs = state.tabs,
                selected = state.selectedTab,
                onSelect = viewModel::selectTab,
                onAccount = viewModel::openAccount,
            )
            if (state.stack.isNotEmpty()) {
                val node = state.stack.last()
                Breadcrumb(
                    rootTitle = state.tabs.getOrNull(state.selectedTab)?.title.orEmpty(),
                    node = node,
                    count = state.levelItems[node.id]?.size,
                    onBack = viewModel::back,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.phase) {
                    ConnectionPhase.CONNECTING -> CenteredMessage("Conectando ao Apple Music…")
                    ConnectionPhase.FAILED -> ErrorPane(
                        message = state.error,
                        installed = state.appleMusicInstalled,
                        onRetry = viewModel::retry,
                        onAccount = viewModel::openAccount,
                    )

                    ConnectionPhase.CONNECTED -> when {
                        state.stack.isNotEmpty() -> {
                            val node = state.stack.last()
                            LevelScreen(
                                node = node,
                                items = state.levelItems[node.id],
                                failed = node.id in state.levelFailed,
                                onItem = viewModel::openItem,
                            )
                        }

                        state.isLibraryRoot -> LibraryScreen(
                            categories = state.categories,
                            loading = state.rootLoading,
                            onCategory = viewModel::openCategory,
                        )

                        else -> ShelvesScreen(
                            sections = state.sections,
                            loading = state.rootLoading,
                            compact = compact,
                            onOpenSection = viewModel::openSection,
                            onItem = viewModel::openItem,
                        )
                    }
                }
                state.message?.let { message ->
                    MessageChip(
                        text = message,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    )
                }
            }
            MiniPlayer(
                playback = playback,
                compact = compact,
                stationAvailable = playback.customActions.any { it.action.endsWith("CREATE_STATION") },
                onPlayPause = viewModel::togglePlayPause,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onSeek = viewModel::seekToFraction,
                onCreateStation = viewModel::createStation,
            )
        }
    }
}

@Composable
private fun TopBar(
    tabs: List<CarTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = PagePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Apple Music",
            color = Accent,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(24.dp))
        tabs.forEachIndexed { index, tab ->
            TabButton(
                title = tab.title,
                selected = index == selected,
                onClick = { onSelect(index) },
            )
        }
        Spacer(Modifier.weight(1f))
        AccountButton(onClick = onAccount)
    }
}

@Composable
private fun TabButton(title: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = Modifier
            .height(56.dp)
            .clip(RoundedCornerShape(ControlRadius))
            .background(if (pressed) CardColor else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) Accent else Color.Transparent),
        )
    }
}

@Composable
private fun AccountButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(ControlRadius))
            .background(if (pressed) CardColor else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_account),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (pressed) TextPrimary else TextSecondary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Entrar na conta",
            color = if (pressed) TextPrimary else TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun Breadcrumb(
    rootTitle: String,
    node: LevelNode,
    count: Int?,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = PagePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(CardColor)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = "Voltar",
                modifier = Modifier.size(24.dp),
                tint = TextPrimary,
            )
        }
        Spacer(Modifier.width(14.dp))
        if (rootTitle.isNotEmpty()) {
            Text(rootTitle, color = TextSecondary, fontSize = 14.sp, maxLines = 1)
            Text("  /  ", color = TextSecondary, fontSize = 14.sp)
        }
        Text(
            text = node.title,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text("$count itens", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ShelvesScreen(
    sections: List<Section>,
    loading: Boolean,
    compact: Boolean,
    onOpenSection: (Section) -> Unit,
    onItem: (BrowseItem) -> Unit,
) {
    if (loading) {
        CenteredMessage("Carregando…")
        return
    }
    val visible = sections.filter { it.loading || it.items.isNotEmpty() }
    if (visible.isEmpty()) {
        CenteredMessage("Nada por aqui")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = PagePadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else SectionGap),
    ) {
        items(visible, key = { it.id }) { section ->
            Shelf(
                section = section,
                compact = compact,
                onOpenSection = { onOpenSection(section) },
                onItem = onItem,
            )
        }
    }
}

@Composable
private fun Shelf(
    section: Section,
    compact: Boolean,
    onOpenSection: () -> Unit,
    onItem: (BrowseItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = section.title,
                color = TextPrimary,
                fontSize = if (compact) 17.sp else 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!section.loading) {
                Spacer(Modifier.width(8.dp))
                Text("${section.items.size}", color = TextSecondary, fontSize = 12.sp)
            }
        }
        when {
            section.loading -> Text("Carregando…", color = TextSecondary, fontSize = 14.sp)
            section.items.isEmpty() -> Text("Vazio", color = TextSecondary, fontSize = 14.sp)
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)) {
                if (!section.direct) {
                    item(key = "all-${section.id}", contentType = "seeAll") {
                        SeeAllCard(
                            count = section.items.size,
                            compact = compact,
                            onClick = onOpenSection,
                        )
                    }
                }
                items(section.items, key = { it.id }) { item ->
                    MediaCard(
                        item = item,
                        compact = compact,
                        onClick = { onItem(item) },
                    )
                }
            }
        }
    }
}

private fun cardSize(compact: Boolean): Dp = if (compact) 104.dp else 180.dp

@Composable
private fun MediaCard(item: BrowseItem, compact: Boolean, onClick: () -> Unit) {
    val size = cardSize(compact)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Colour and a light press scale only; no elevation/shadow animation.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "mediaCardScale",
    )
    Box(
        modifier = Modifier
            .width(size)
            .height(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(CardRadius))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        CardArtwork(item.artworkUri, size)
        // Subtle scrim so the short title/subtitle stay readable over busy artwork.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.42f to Color.Transparent,
                            0.72f to Color.Black.copy(alpha = 0.45f),
                            1f to Color.Black.copy(alpha = 0.9f),
                        ),
                    ),
                ),
        )
        if (pressed) {
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.06f)))
        }
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.title,
                color = TextPrimary,
                fontSize = if (compact) 13.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = item.subtitle.ifEmpty {
                when {
                    item.playable -> "Tocar"
                    item.browsable -> "Abrir"
                    else -> ""
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = if (compact) 10.sp else 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SeeAllCard(count: Int, compact: Boolean, onClick: () -> Unit) {
    val size = cardSize(compact)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .width(size)
            .height(size)
            .clip(RoundedCornerShape(CardRadius))
            .background(if (pressed) CardColorPressed else CardColor)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ver tudo", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("$count itens", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LibraryScreen(
    categories: List<BrowseItem>,
    loading: Boolean,
    onCategory: (BrowseItem) -> Unit,
) {
    if (loading) {
        CenteredMessage("Carregando…")
        return
    }
    if (categories.isEmpty()) {
        CenteredMessage("Nada por aqui")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = PagePadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(categories, key = { it.id }) { item ->
            CategoryRow(item = item, onClick = { onCategory(item) })
        }
    }
}

@Composable
private fun CategoryRow(item: BrowseItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(CardRadius))
            .background(if (pressed) CardColorPressed else CardColor)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Accent, Color(0xFF5A1018)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.title.firstOrNull()?.uppercaseChar()?.toString() ?: "♪",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    item.browsable -> "Abrir"
                    item.playable -> "Tocar"
                    else -> ""
                },
                color = TextSecondary,
                fontSize = 13.sp,
            )
        }
        Icon(
            painter = painterResource(
                if (item.browsable) R.drawable.ic_chevron else R.drawable.ic_play,
            ),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (item.browsable) TextSecondary else Accent,
        )
    }
}

@Composable
private fun LevelScreen(
    node: LevelNode,
    items: List<BrowseItem>?,
    failed: Boolean,
    onItem: (BrowseItem) -> Unit,
) {
    when {
        failed -> CenteredMessage("Não foi possível carregar “${node.title}”")
        items == null -> CenteredMessage("Carregando…")
        items.isEmpty() -> CenteredMessage("Nada por aqui")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = PagePadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items, key = { it.id }) { item ->
                TrackRow(item = item, onClick = { onItem(item) })
            }
        }
    }
}

@Composable
private fun TrackRow(item: BrowseItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(ControlRadius))
            .background(if (pressed) CardColor else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemArtwork(item.artworkUri, 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = item.subtitle.ifEmpty { if (item.browsable) "Abrir" else "" }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            painter = painterResource(
                if (item.playable) R.drawable.ic_play else R.drawable.ic_chevron,
            ),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (item.playable) Accent else TextSecondary,
        )
    }
}

@Composable
private fun MiniPlayer(
    playback: PlaybackSnapshot,
    compact: Boolean,
    stationAvailable: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onCreateStation: () -> Unit,
) {
    val hasTrack = playback.hasSession && playback.title.isNotBlank()
    Column(Modifier.fillMaxWidth().background(PlayerColor)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        SeekBar(
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            onSeek = onSeek,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 60.dp else 80.dp)
                .padding(horizontal = PagePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerArtwork(playback.artwork, if (compact) 48.dp else 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (hasTrack) playback.title else "Nada tocando",
                    color = if (hasTrack) TextPrimary else TextSecondary,
                    fontSize = if (compact) 16.sp else 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (hasTrack) {
                        playback.artist.ifBlank { playback.album }.ifBlank { "Apple Music" }
                    } else {
                        "Apple Music"
                    },
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(
                    iconRes = R.drawable.ic_prev,
                    description = "Anterior",
                    size = 48.dp,
                    iconSize = 22.dp,
                    onClick = onPrevious,
                )
                CircleButton(
                    iconRes = if (playback.playing) R.drawable.ic_pause else R.drawable.ic_play,
                    description = if (playback.playing) "Pausar" else "Tocar",
                    size = 60.dp,
                    iconSize = 26.dp,
                    filled = true,
                    onClick = onPlayPause,
                )
                CircleButton(
                    iconRes = R.drawable.ic_next,
                    description = "Próxima",
                    size = 48.dp,
                    iconSize = 22.dp,
                    onClick = onNext,
                )
            }
            Spacer(Modifier.width(16.dp))
            StationButton(available = stationAvailable, onClick = onCreateStation)
        }
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Float) -> Unit) {
    val fraction = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 48 dp tall: the whole strip is a touch target, not just the 4 dp track.
            .height(48.dp)
            .padding(start = PagePadding, end = PagePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatTime(positionMs),
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(44.dp),
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0L) {
                            onSeek((offset.x / size.width).coerceIn(0f, 1f))
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TrackColor),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .background(Accent),
                )
            }
            if (durationMs > 0L) {
                val thumbX = (maxWidth * fraction - 5.dp)
                    .coerceIn(0.dp, (maxWidth - 10.dp).coerceAtLeast(0.dp))
                Box(
                    modifier = Modifier
                        .offset(x = thumbX)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Accent),
                )
            }
        }
        Text(
            text = formatTime(durationMs),
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun CircleButton(
    iconRes: Int,
    description: String,
    size: Dp,
    iconSize: Dp,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    !filled -> if (pressed) CardColorPressed else CardColor
                    pressed -> Color.White.copy(alpha = 0.75f)
                    else -> Color.White
                },
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            modifier = Modifier.size(iconSize),
            tint = if (filled) Color.Black else TextPrimary,
        )
    }
}

@Composable
private fun StationButton(available: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    !available -> CardColor
                    pressed -> Accent.copy(alpha = 0.85f)
                    else -> Accent
                },
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_plus),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (available) Color.White else TextSecondary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Criar estação",
            color = if (available) Color.White else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CardArtwork(uri: String?, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(CardRadius))) {
        PlaceholderArt(size, CardRadius)
        if (uri != null) {
            val context = LocalContext.current
            val artPx = with(LocalDensity.current) { size.roundToPx() }
            AsyncImage(
                // Explicit target size: decode a thumbnail instead of the full CDN image.
                model = remember(uri, artPx) {
                    ImageRequest.Builder(context).data(uri).size(artPx, artPx).build()
                },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun ItemArtwork(uri: String?, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp))) {
        PlaceholderArt(size, 10.dp)
        if (uri != null) {
            val context = LocalContext.current
            val artPx = with(LocalDensity.current) { size.roundToPx() }
            AsyncImage(
                model = remember(uri, artPx) {
                    ImageRequest.Builder(context).data(uri).size(artPx, artPx).build()
                },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun PlayerArtwork(bitmap: Bitmap?, size: Dp) {
    val shape = RoundedCornerShape(12.dp)
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size).clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        PlaceholderArt(size, 12.dp)
    }
}

@Composable
private fun PlaceholderArt(size: Dp, corner: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(Accent.copy(alpha = 0.85f), Color(0xFF5A1018)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "♪",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = (size.value * 0.32f).sp,
        )
    }
}

@Composable
private fun MessageChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(CardColorPressed)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text, color = TextPrimary, fontSize = 14.sp)
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = TextSecondary, fontSize = 18.sp)
    }
}

@Composable
private fun ErrorPane(
    message: String?,
    installed: Boolean,
    onRetry: () -> Unit,
    onAccount: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(PagePadding), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 620.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_error),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Accent,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = message ?: "Serviço indisponível",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (installed) {
                    "Verifique se o Apple Music está logado e se a sessão de mídia está ativa."
                } else {
                    "Instale o Apple Music patcheado (certificado Morphe) neste dispositivo."
                },
                color = TextSecondary,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaneButton(text = "Tentar de novo", primary = true, onClick = onRetry)
                if (installed) {
                    PaneButton(text = "Entrar na conta", primary = false, onClick = onAccount)
                }
            }
        }
    }
}

@Composable
private fun PaneButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(ControlRadius))
            .background(
                when {
                    primary && pressed -> Accent.copy(alpha = 0.85f)
                    primary -> Accent
                    pressed -> CardColorPressed
                    else -> CardColor
                },
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (primary) Color.White else TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val totalSeconds = millis / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
