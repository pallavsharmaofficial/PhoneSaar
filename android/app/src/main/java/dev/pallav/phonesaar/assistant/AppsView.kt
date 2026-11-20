package dev.pallav.phonesaar.assistant

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pallav.phonesaar.R

internal fun categoryName(c: Category): Int = when (c) {
    Category.CHAT -> R.string.asst_cat_chat
    Category.MONEY -> R.string.asst_cat_money
    Category.TRAVEL -> R.string.asst_cat_travel
    Category.WORK -> R.string.asst_cat_work
    Category.PHOTOS -> R.string.asst_cat_photos
    Category.MEDIA -> R.string.asst_cat_media
    Category.SHOPPING -> R.string.asst_cat_shopping
    Category.GOV_HEALTH -> R.string.asst_cat_gov_health
    Category.LEARNING -> R.string.asst_cat_learning
    Category.TOOLS -> R.string.asst_cat_tools
    Category.GAMES -> R.string.asst_cat_games
    Category.OTHER -> R.string.asst_cat_other
}

internal fun categoryEmoji(c: Category): String = when (c) {
    Category.CHAT -> "💬"
    Category.MONEY -> "💰"
    Category.TRAVEL -> "🗺"
    Category.WORK -> "🚗"
    Category.PHOTOS -> "📷"
    Category.MEDIA -> "🎬"
    Category.SHOPPING -> "🛒"
    Category.GOV_HEALTH -> "🏛"
    Category.LEARNING -> "📚"
    Category.TOOLS -> "🔧"
    Category.GAMES -> "🎮"
    Category.OTHER -> "📦"
}

@Composable
internal fun AppsView(prefs: AssistantPrefs, apps: List<AppEntry>) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(prefs.loadOrganizer()) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var moveApp by remember { mutableStateOf<AppEntry?>(null) }
    var arranging by remember { mutableStateOf(false) }

    fun update(s: OrganizerState) { state = s; prefs.saveOrganizer(s) }

    val sections = remember(apps, state, query) { AppOrganizer.organize(apps, state, query) }
    val searchCd = stringResource(R.string.asst_search_cd)

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().semantics { contentDescription = searchCd },
            placeholder = { Text(stringResource(R.string.asst_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        if (query.isBlank()) {
            OutlinedButton(onClick = { arranging = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 6.dp)) {
                Text(stringResource(R.string.asst_arrange))
            }
        }
        if (sections.isEmpty()) {
            Text(stringResource(R.string.asst_no_apps), Modifier.padding(24.dp))
        }
        LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.weight(1f, fill = true), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (section in sections) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val title = when {
                        section.isPinned -> "📌 " + stringResource(R.string.asst_pinned)
                        section.category == null -> stringResource(R.string.asst_results)
                        else -> categoryEmoji(section.category) + " " + stringResource(categoryName(section.category))
                    }
                    Text(title, Modifier.padding(top = 14.dp, bottom = 4.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                items(section.apps, key = { "${section.isPinned}-${section.category}-${it.packageName}" }) { app ->
                    AppTile(app, onOpen = {
                        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                            runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    }, onMenu = { menuApp = app })
                }
            }
        }
    }

    menuApp?.let { app ->
        val pinned = app.packageName in state.pins
        AlertDialog(
            onDismissRequest = { menuApp = null },
            title = { Text(app.label) },
            text = {
                Column {
                    TextButton(onClick = { update(state.copy(pins = AppOrganizer.togglePin(state.pins, app.packageName))); menuApp = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(if (pinned) R.string.asst_unpin else R.string.asst_pin))
                    }
                    TextButton(onClick = { moveApp = app; menuApp = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.asst_move))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { menuApp = null }) { Text(stringResource(R.string.asst_cancel)) } },
        )
    }

    moveApp?.let { app ->
        AlertDialog(
            onDismissRequest = { moveApp = null },
            title = { Text(stringResource(R.string.asst_move_title, app.label)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { update(state.copy(overrides = state.overrides - app.packageName)); moveApp = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.asst_move_auto))
                    }
                    Category.entries.forEach { c ->
                        TextButton(onClick = { update(state.copy(overrides = state.overrides + (app.packageName to c))); moveApp = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(categoryEmoji(c) + " " + stringResource(categoryName(c)))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moveApp = null }) { Text(stringResource(R.string.asst_cancel)) } },
        )
    }

    if (arranging) {
        AlertDialog(
            onDismissRequest = { arranging = false },
            title = { Text(stringResource(R.string.asst_arrange_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AppOrganizer.fullOrder(state.categoryOrder).forEach { c ->
                        val name = stringResource(categoryName(c))
                        val upCd = stringResource(R.string.asst_up_cd, name)
                        val downCd = stringResource(R.string.asst_down_cd, name)
                        val showCd = stringResource(R.string.asst_show_cd, name)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(categoryEmoji(c) + " " + name, Modifier.weight(1f))
                            TextButton(onClick = { update(state.copy(categoryOrder = AppOrganizer.move(state.categoryOrder, c, -1))) }, modifier = Modifier.size(48.dp).semantics { contentDescription = upCd }) { Text("▲") }
                            TextButton(onClick = { update(state.copy(categoryOrder = AppOrganizer.move(state.categoryOrder, c, 1))) }, modifier = Modifier.size(48.dp).semantics { contentDescription = downCd }) { Text("▼") }
                            Switch(c !in state.hidden, { on -> update(state.copy(hidden = if (on) state.hidden - c else state.hidden + c)) }, Modifier.semantics { contentDescription = showCd })
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { update(state.copy(categoryOrder = Category.entries, hidden = emptySet())) }) { Text(stringResource(R.string.asst_reset)) } },
            confirmButton = { TextButton(onClick = { arranging = false }) { Text(stringResource(R.string.asst_done)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: AppEntry, onOpen: () -> Unit, onMenu: () -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) { value = AppCatalog.icon(context, app.packageName) }
    val cd = stringResource(R.string.asst_open_cd, app.label)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = onMenu)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = cd },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp)) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(64.dp)) }
        }
        Text(app.label, Modifier.padding(top = 6.dp), fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 22.sp)
    }
}
