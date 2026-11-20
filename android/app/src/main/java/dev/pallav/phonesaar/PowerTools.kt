package dev.pallav.phonesaar

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.pallav.phonesaar.core.Oem
import dev.pallav.phonesaar.core.OemGuide
import kotlinx.coroutines.delay

@Composable
fun PowerToolsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var tool by rememberSaveable { mutableStateOf<String?>(null) }
    var setup by remember { mutableStateOf(Env.setup(ctx)) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        vm.tryReconnect()
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                setup = Env.setup(ctx)
                vm.syncState()
                delay(2000)
            }
        }
    }
    BackHandler(tool != null) { tool = null }
    val current = tool
    when {
        current != null -> ToolPage(vm, current) { tool = null }
        vm.connected -> ToolList(vm) { tool = it }
        else -> Wizard(vm, setup) { tool = it }
    }
}

@Composable
private fun ToolPage(vm: AppViewModel, tool: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onBack, Modifier.heightIn(min = 56.dp).padding(horizontal = 8.dp)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            Text(stringResource(R.string.back), Modifier.padding(start = 8.dp))
        }
        Box(Modifier.weight(1f)) {
            when (tool) {
                "boost" -> BoostScreen(vm)
                "drain" -> BatteryScreen(vm)
                "debloat" -> DebloatScreen(vm)
                "fix" -> FixupsScreen(vm)
                "report" -> ReportScreen(vm)
                "undo" -> UndoScreen(vm)
                else -> OtherDeviceScreen(vm)
            }
        }
    }
}

@Composable
private fun ToolList(vm: AppViewModel, open: (String) -> Unit) {
    Page {
        Panel(container = MaterialTheme.colorScheme.primaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.connected), tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                Text(stringResource(R.string.tools_connected), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        ActionCard(Icons.Filled.PlayArrow, stringResource(R.string.tool_boost), stringResource(R.string.tool_boost_sub)) { open("boost") }
        ActionCard(Icons.Filled.Favorite, stringResource(R.string.tool_drain), stringResource(R.string.tool_drain_sub)) { open("drain") }
        ActionCard(Icons.Filled.Delete, stringResource(R.string.tool_debloat), stringResource(R.string.tool_debloat_sub)) { open("debloat") }
        ActionCard(Icons.Filled.Build, stringResource(R.string.tool_fix), stringResource(R.string.tool_fix_sub)) { open("fix") }
        ActionCard(Icons.Filled.List, stringResource(R.string.tool_report), stringResource(R.string.tool_report_sub)) { open("report") }
        ActionCard(Icons.Filled.Refresh, stringResource(R.string.tool_undo), stringResource(R.string.tool_undo_sub)) { open("undo") }
        ActionCard(Icons.Filled.Phone, stringResource(R.string.tool_other), stringResource(R.string.tool_other_sub)) { open("other") }
        if (Build.VERSION.SDK_INT >= 30) AutoReconnectCard(vm)
        BigOutlined(stringResource(R.string.disconnect), { vm.disconnect() }, enabled = !vm.busy)
    }
}

@Composable
private fun AutoReconnectCard(vm: AppViewModel) {
    Panel {
        Text(stringResource(R.string.auto_title), style = MaterialTheme.typography.titleSmall)
        if (vm.secureGranted) Hint(stringResource(R.string.auto_on))
        else {
            Hint(stringResource(R.string.auto_off))
            if (vm.secureNote.isNotBlank()) Text(vm.secureNote, color = MaterialTheme.colorScheme.error)
            BigOutlined(stringResource(R.string.auto_try), { vm.grantSecure() }, enabled = !vm.busy)
        }
    }
}

@Composable
private fun StepCard(number: Int, title: String, done: Boolean, active: Boolean, content: @Composable () -> Unit = {}) {
    Panel(container = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(36.dp).background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.step_done), tint = MaterialTheme.colorScheme.onPrimary)
                else Text(number.toString(), color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.titleSmall)
            }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        }
        if (active) content()
    }
}

@Composable
private fun Wizard(vm: AppViewModel, s: SetupState, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val modern = Build.VERSION.SDK_INT >= 30
    val step = when {
        !s.devOptions -> 1
        !s.wifi -> 2
        else -> 3
    }
    var manual by rememberSaveable { mutableStateOf(false) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        PairingService.start(ctx)
        SystemIntents.open(ctx, SystemIntents.developerOptions())
    }
    Page {
        Title(stringResource(R.string.wiz_title))
        Hint(stringResource(R.string.wiz_intro))
        StepCard(1, stringResource(R.string.step1_title), s.devOptions, step == 1) {
            Text(stringResource(R.string.step1_body))
            Text(stringResource(oemText(OemGuide.of(Build.MANUFACTURER, Build.BRAND))), style = MaterialTheme.typography.titleSmall)
            BigButton(stringResource(R.string.step1_button), { SystemIntents.open(ctx, SystemIntents.aboutPhone()) })
        }
        StepCard(2, stringResource(R.string.step2_title), s.wifi, step == 2) {
            Text(stringResource(R.string.step2_body))
            BigButton(stringResource(R.string.step2_button), { SystemIntents.open(ctx, SystemIntents.wifi()) })
        }
        if (modern) {
            StepCard(3, stringResource(R.string.step3_title), false, step == 3) {
                Text(stringResource(R.string.step3_body))
                Hint(stringResource(if (s.wirelessOn) R.string.wireless_on else R.string.wireless_off))
                BigButton(stringResource(R.string.step3_button), {
                    if (Build.VERSION.SDK_INT >= 33 && !s.notifications) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else {
                        PairingService.start(ctx)
                        SystemIntents.open(ctx, SystemIntents.developerOptions())
                    }
                })
                if (Build.VERSION.SDK_INT >= 33 && !s.notifications) Hint(stringResource(R.string.step3_notif))
                TextButton({ manual = !manual }, Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.step3_manual)) }
                if (manual) ManualPairing(vm)
            }
            StepCard(4, stringResource(R.string.step4_title), s.secureGranted, false)
        } else {
            StepCard(3, stringResource(R.string.old_title), false, step == 3) {
                Text(stringResource(R.string.old_body))
                Text("adb tcpip 5555", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleSmall)
                BigButton(stringResource(R.string.old_connect), {
                    vm.target = TargetKind.Legacy
                    vm.host_ = "127.0.0.1"
                    vm.connectPort = "5555"
                    open("other")
                })
            }
        }
        BigOutlined(stringResource(R.string.other_device_button), { open("other") })
    }
}

private fun oemText(oem: Oem): Int = when (oem) {
    Oem.Xiaomi -> R.string.dev_xiaomi
    Oem.Samsung -> R.string.dev_samsung
    Oem.Oppo -> R.string.dev_oppo
    Oem.Vivo -> R.string.dev_vivo
    Oem.Other -> R.string.dev_default
}
