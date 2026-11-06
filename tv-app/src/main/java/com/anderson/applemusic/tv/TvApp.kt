@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.anderson.applemusic.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

private val Background = Color(0xFF0A0A0B)
private val BarBackground = Color(0xFF161618)
private val CardColor = Color(0xFF1C1C1E)
private val CardColorFocused = Color(0xFF34343A)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9A9A9E)
private val Accent = Color(0xFFFA233B)

private const val CARDS_PER_ROW = 4

@Composable
fun TvApp(viewModel: TvViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstTabFocus = remember { FocusRequester() }

    // TV apps start with nothing focused; put the D-pad on the tab bar and put it
    // back there whenever the drill-down stack returns to a tab root.
    LaunchedEffect(state.tabs.size, state.stack.size) {
        if (state.tabs.isNotEmpty() && state.stack.isEmpty()) {
            withFrameNanos { }
            runCatching { firstTabFocus.requestFocus() }
        }
    }

    BackHandler(enabled = state.stack.isNotEmpty()) { viewModel.back() }

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Background)) {
            Column(Modifier.fillMaxSize()) {
                Header(
                    tabs = state.tabs,
                    selected = state.selectedTab,
                    onSelect = viewModel::selectTab,
                    firstTabFocus = firstTabFocus,
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (state.phase) {
                        ConnectionPhase.CONNECTING -> CenteredMessage("Conectando ao Apple Music…")
                        ConnectionPhase.FAILED -> ErrorPane(state.error, viewModel::retry)
                        ConnectionPhase.CONNECTED -> {
                            val top = state.stack.lastOrNull()
                            if (top == null) {
                                TabRoot(state, viewModel)
                            } else {
                                LevelGrid(
                                    title = state.stack.joinToString(" › ") { it.title },
                                    items = state.levelItems[top.id],
                                    failed = top.id in state.levelFailed,
                                    onItem = viewModel::enter,
                                )
                            }
                        }
                    }
                }
                NowPlayingBar(state.playback, viewModel)
            }
        }
    }
}

@Composable
private fun Header(
    tabs: List<TvTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    firstTabFocus: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Apple Music", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(18.dp))
        tabs.forEachIndexed { index, tab ->
            TabPill(
                title = tab.title,
                selected = index == selected,
                modifier = if (index == 0) Modifier.focusRequester(firstTabFocus) else Modifier,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun TabPill(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "tabScale")
    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    selected -> Accent
                    focused -> CardColorFocused
                    else -> Color.Transparent
                },
            )
            .border(
                BorderStroke(2.dp, if (focused) Color.White else Color.Transparent),
                RoundedCornerShape(50),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun TabRoot(state: TvUiState, viewModel: TvViewModel) {
    val visibleSections = state.sections.filter { it.loading || it.items.isNotEmpty() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
    ) {
        items(visibleSections, key = { it.id }) { section ->
            SectionRow(
                section = section,
                onOpenSection = { viewModel.openSection(section) },
                onItem = viewModel::enter,
            )
        }
    }
}

@Composable
private fun SectionRow(
    section: Section,
    onOpenSection: () -> Unit,
    onItem: (BrowseItem) -> Unit,
) {
    val rowState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = section.title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 3.dp),
            )
            Spacer(Modifier.width(10.dp))
            if (!section.loading) {
                Text("${section.items.size}", color = TextSecondary, fontSize = 12.sp)
            }
        }
        when {
            section.loading -> Text("Carregando…", color = TextSecondary, fontSize = 13.sp)
            else -> LazyRow(
                state = rowState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val offset = if (section.direct) 0 else 1
                if (!section.direct) {
                    item(key = "open-${section.id}") {
                        SeeAllCard(
                            count = section.items.size,
                            onClick = onOpenSection,
                        )
                    }
                }
                itemsIndexed(section.items, key = { _, item -> item.id }) { index, item ->
                    TvCard(
                        item = item,
                        onFocused = { scope.launch { rowState.animateScrollToItem(index + offset) } },
                        onClick = { onItem(item) },
                    )
                }
            }
        }
    }
}

/** Focusable "open this shelf as a grid" entry at the start of every row. */
@Composable
private fun SeeAllCard(count: Int, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (focused) 18.dp else 0.dp, label = "seeAllElevation")
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(132.dp)
            .height(112.dp)
            .onFocusChanged { focused = it.isFocused }
            .shadow(elevation, RoundedCornerShape(10.dp)),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        colors = CardDefaults.colors(
            containerColor = CardColor,
            contentColor = TextPrimary,
            focusedContainerColor = CardColorFocused,
            focusedContentColor = TextPrimary,
        ),
        scale = CardDefaults.scale(1f, 1.06f, 1f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Ver tudo", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("$count itens", color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LevelGrid(
    title: String,
    items: List<BrowseItem>?,
    failed: Boolean,
    onItem: (BrowseItem) -> Unit,
) {
    if (failed) {
        CenteredMessage("Não foi possível carregar “$title”")
        return
    }
    if (items == null) {
        CenteredMessage("Carregando…")
        return
    }
    if (items.isEmpty()) {
        CenteredMessage("Nada por aqui")
        return
    }
    val columnState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val firstCard = remember { FocusRequester() }

    LaunchedEffect(items) {
        withFrameNanos { }
        runCatching { firstCard.requestFocus() }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 32.dp, top = 6.dp, bottom = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LazyColumn(
            state = columnState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(items.chunked(CARDS_PER_ROW)) { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { item ->
                        val isFirst = item === items.first()
                        TvCard(
                            item = item,
                            modifier = if (isFirst) Modifier.focusRequester(firstCard) else Modifier,
                            onFocused = {
                                val rowIndex = items.indexOf(item) / CARDS_PER_ROW
                                if (rowIndex >= 0) {
                                    scope.launch { columnState.animateScrollToItem(rowIndex) }
                                }
                            },
                            onClick = { onItem(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvCard(
    item: BrowseItem,
    modifier: Modifier = Modifier,
    width: Dp = 200.dp,
    height: Dp = 112.dp,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember(item.id) { mutableStateOf(false) }
    val elevation by animateDpAsState(if (focused) 18.dp else 0.dp, label = "cardElevation")

    Card(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(height)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .shadow(elevation, RoundedCornerShape(10.dp)),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        colors = CardDefaults.colors(
            containerColor = CardColor,
            contentColor = TextPrimary,
            focusedContainerColor = CardColorFocused,
            focusedContentColor = TextPrimary,
        ),
        scale = CardDefaults.scale(1f, 1.06f, 1f),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkPlaceholder(52.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle.isNotEmpty()) {
                    Text(
                        text = item.subtitle,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (item.playable) {
                    Text("Tocar", color = Accent, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun NowPlayingBar(playback: PlaybackSnapshot, viewModel: TvViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .background(BarBackground)
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(playback)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (playback.title.isBlank()) "Nada tocando" else playback.title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (playback.artist.isBlank()) "Apple Music" else playback.artist,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(
                    positionMs = playback.positionMs,
                    durationMs = playback.durationMs,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${formatTime(playback.positionMs)} / ${formatTime(playback.durationMs)}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.width(20.dp))
        IconButton(onClick = viewModel::previous, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_prev),
                contentDescription = "Anterior",
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(52.dp)) {
            Icon(
                painter = painterResource(
                    if (playback.playing) R.drawable.ic_pause else R.drawable.ic_play,
                ),
                contentDescription = if (playback.playing) "Pausar" else "Tocar",
                modifier = Modifier.size(28.dp),
            )
        }
        IconButton(onClick = viewModel::next, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_next),
                contentDescription = "Próxima",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun Artwork(playback: PlaybackSnapshot) {
    val bitmap = playback.artwork
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        ArtworkPlaceholder(56.dp)
    }
}

@Composable
private fun ArtworkPlaceholder(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    listOf(Accent.copy(alpha = 0.85f), Color(0xFF5A1018)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("♪", color = Color.White, fontSize = (size.value * 0.4f).sp)
    }
}

@Composable
private fun ProgressBar(positionMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    val fraction = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF3A3A3C)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(Accent),
        )
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = TextSecondary, fontSize = 18.sp)
    }
}

@Composable
private fun ErrorPane(message: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message ?: "Falha ao conectar ao Apple Music",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Verifique se o Apple Music está instalado e se a sessão de mídia está ativa.",
            color = TextSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(20.dp))
        var focused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (focused) Accent else CardColor)
                .border(
                    BorderStroke(2.dp, if (focused) Color.White else Color.Transparent),
                    RoundedCornerShape(10.dp),
                )
                .onFocusChanged { focused = it.isFocused }
                .clickable { onRetry() }
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text("Tentar de novo", color = TextPrimary, fontSize = 16.sp)
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val totalSeconds = millis / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
