package dev.pallav.phonesaar

import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.pallav.phonesaar.core.BoostPath
import dev.pallav.phonesaar.core.Health
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(vm: AppViewModel, onSetup: () -> Unit) {
    val ctx = LocalContext.current
    var snap by remember { mutableStateOf(Env.health(ctx)) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, vm.boosting) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                snap = withContext(Dispatchers.IO) { Env.health(ctx) }
                vm.syncState()
                delay(5000)
            }
        }
    }
    val path = if (vm.needsSetup) BoostPath.OpenPowerTools else BoostRunner.plannedPath(ctx)
    Page {
        VerdictCard(snap)
        BoostSection(vm, path, onSetup)
        if (snap.input.tempC > Health.HOT_C) Panel(container = MaterialTheme.colorScheme.errorContainer) {
            Text(stringResource(R.string.heat_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(stringResource(R.string.heat_body), color = MaterialTheme.colorScheme.onErrorContainer)
        }
        HealthRows(snap)
        Title(stringResource(R.string.more_title))
        ActionCard(Icons.Filled.Delete, stringResource(R.string.act_storage), stringResource(R.string.act_storage_sub)) { SystemIntents.open(ctx, SystemIntents.storage()) }
        ActionCard(Icons.Filled.Favorite, stringResource(R.string.act_saver), stringResource(R.string.act_saver_sub)) { SystemIntents.open(ctx, SystemIntents.batterySaver()) }
        ActionCard(Icons.Filled.Search, stringResource(R.string.act_usage), stringResource(R.string.act_usage_sub)) { SystemIntents.open(ctx, SystemIntents.batteryUsage()) }
        ActionCard(Icons.Filled.Add, stringResource(R.string.act_widget), stringResource(R.string.act_widget_sub)) { pinWidget(ctx) }
        if (Build.VERSION.SDK_INT >= 33) {
            ActionCard(Icons.Filled.Add, stringResource(R.string.act_tile), stringResource(R.string.act_tile_sub)) { addTile(ctx) }
        }
    }
}

@Composable
private fun VerdictCard(snap: HealthSnapshot) {
    val fine = snap.verdict.fine
    val color = if (fine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val on = if (fine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    val head = stringResource(if (fine) R.string.verdict_fine else R.string.verdict_attention)
    Panel(container = color) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (fine) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = head, tint = on, modifier = Modifier.size(40.dp))
            Text(head, style = MaterialTheme.typography.headlineSmall, color = on)
        }
        if (fine) Text(stringResource(R.string.verdict_fine_sub), color = on)
        snap.verdict.issues.forEach { Text("• " + stringResource(PhoneSaarAssistantHost.issueText(it)), color = on) }
    }
}

@Composable
private fun BoostSection(vm: AppViewModel, path: BoostPath, onSetup: () -> Unit) {
    if (path == BoostPath.OpenPowerTools) {
        Panel(container = MaterialTheme.colorScheme.secondaryContainer) {
            Text(stringResource(R.string.honest_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(stringResource(R.string.honest_body), color = MaterialTheme.colorScheme.onSecondaryContainer)
            BigButton(stringResource(R.string.setup_button), onSetup)
        }
        return
    }
    BigButton(stringResource(if (vm.boosting) R.string.boosting else R.string.boost_button), { vm.homeBoost() }, enabled = !vm.boosting, icon = Icons.Filled.PlayArrow)
    if (vm.boosting) CircularProgressIndicator(Modifier.size(32.dp))
    vm.boostMessage?.let { Panel { Text(it, style = MaterialTheme.typography.titleSmall) } }
    if (path == BoostPath.Quick) Hint(stringResource(R.string.quick_note))
}

@Composable
private fun Meter(label: String, value: String, fraction: Float, bad: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(value, color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun HealthRows(s: HealthSnapshot) {
    val ctx = LocalContext.current
    val i = s.input
    Panel {
        Meter(
            stringResource(R.string.row_memory),
            stringResource(R.string.row_memory_val, i.freeRamPercent, Formatter.formatFileSize(ctx, s.ramFreeBytes), Formatter.formatFileSize(ctx, s.ramTotalBytes)),
            i.freeRamPercent / 100f, bad = i.freeRamPercent in 0..9,
        )
        Meter(
            stringResource(R.string.row_storage),
            stringResource(R.string.row_storage_val, Formatter.formatFileSize(ctx, s.storageFreeBytes), Formatter.formatFileSize(ctx, s.storageTotalBytes)),
            if (s.storageTotalBytes == 0L) 0f else s.storageFreeBytes.toFloat() / s.storageTotalBytes,
            bad = s.storageTotalBytes > 0 && s.storageFreeBytes * 100 / s.storageTotalBytes < 10,
        )
        Meter(
            stringResource(R.string.row_battery),
            stringResource(R.string.row_battery_val, i.batteryPercent) + " · " + stringResource(if (i.charging) R.string.charging else R.string.not_charging),
            i.batteryPercent / 100f, bad = i.batteryPercent in 0..15 && !i.charging,
        )
        Meter(
            stringResource(R.string.row_temp),
            stringResource(R.string.row_temp_val, i.tempC),
            (i.tempC / 60f), bad = i.tempC > Health.HOT_C,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.row_battery_health), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(if (i.batteryHealthGood) R.string.health_good else R.string.health_weak))
        }
        if (Build.VERSION.SDK_INT >= 29) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.row_thermal), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(if (i.thermalStatus <= 0) R.string.thermal_ok else if (i.thermalStatus == 1) R.string.thermal_warm else R.string.thermal_hot))
        }
    }
}

private fun pinWidget(ctx: android.content.Context) {
    val c = Loc.wrap(ctx)
    val awm = AppWidgetManager.getInstance(ctx)
    if (awm.isRequestPinAppWidgetSupported) awm.requestPinAppWidget(ComponentName(ctx, BoostWidget::class.java), null, null)
    else Toast.makeText(ctx, c.getString(R.string.widget_manual), Toast.LENGTH_LONG).show()
}

private fun addTile(ctx: android.content.Context) {
    if (Build.VERSION.SDK_INT < 33) return
    val c = Loc.wrap(ctx)
    runCatching {
        ctx.getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(ctx, BoostTileService::class.java), c.getString(R.string.tile_label),
            Icon.createWithResource(ctx, R.drawable.ic_tile), ctx.mainExecutor,
        ) { }
    }.onFailure { Toast.makeText(ctx, c.getString(R.string.tile_manual), Toast.LENGTH_LONG).show() }
}
