package io.sovware.niobium

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private data class HistoryPosition(val index: Int, val offset: Int, val scrolling: Boolean, val forward: Boolean, val owned: Boolean, val items: Int)

/** One scroll policy for DMs and rooms; content stays keyed by its server identity. */
@Composable internal fun ConversationHistory(tail: Any?, count: Int, modifier: Modifier = Modifier,
                                            content: LazyListScope.(pauseFollowing: () -> Unit) -> Unit) {
    val list = rememberLazyListState()
    var following by rememberSaveable { mutableStateOf(true) }
    var owned by remember { mutableStateOf(false) }
    val measured by remember { derivedStateOf {
        list.layoutInfo.let { info -> Triple(info.totalItemsCount, info.visibleItemsInfo.sumOf { it.size }, info.viewportEndOffset) }
    } }
    LaunchedEffect(list) {
        var previous: HistoryPosition? = null
        snapshotFlow { HistoryPosition(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, list.isScrollInProgress, list.canScrollForward, owned, list.layoutInfo.totalItemsCount) }.collect { current ->
            if (current.items == 0) return@collect
            val old = previous
            val backwards = old != null && (current.index < old.index || current.index == old.index && current.offset < old.offset)
            if (!current.owned) when {
                !current.forward && !current.scrolling -> following = true
                backwards || current.scrolling && current.forward -> following = false
                !current.forward -> following = true
            }
            previous = current
        }
    }
    LaunchedEffect(tail, count, following, measured) {
        val last = list.layoutInfo.totalItemsCount - 1
        if (following && last >= 0) {
            owned = true
            try {
                list.scrollToItem(last, list.layoutInfo.visibleItemsInfo.lastOrNull { it.index == last }?.size ?: 0)
                if (list.canScrollForward) list.scrollToItem(last, (list.layoutInfo.visibleItemsInfo.lastOrNull { it.index == last }?.size ?: 0) + list.layoutInfo.afterContentPadding)
            } finally { owned = false }
        }
    }
    Box(modifier) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("conversation.history"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (following) 16.dp else 80.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)) { content { following = false } }
        if (!following) PixelButton("Latest messages", PixelGlyph.ACTIVITY,
            Modifier.align(Alignment.BottomCenter).padding(8.dp).testTag("conversation.latest"), secondary = true) { following = true }
    }
}
