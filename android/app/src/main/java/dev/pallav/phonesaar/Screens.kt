package dev.pallav.phonesaar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pallav.phonesaar.core.CatalogEntry
import dev.pallav.phonesaar.core.Parsers
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
private fun NeedsConnection(vm: AppViewModel, content: @Composable () -> Unit) {
    if (vm.connected) content() else Page {
        Title(stringResource(R.string.need_connect_title))
        Hint(stringResource(R.string.need_connect_body))
    }
}

@Composable
private fun Busy(vm: AppViewModel) {
    if (vm.busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.padding(2.dp))
        Text(stringResource(R.string.working))
    }
}

@Composable
private fun Choice(selected: Boolean, label: String, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onPick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onPick)
        Text(label)
    }
}

// Shows exactly what the device answered; a block never looks like success.
@Composable
private fun Results(vm: AppViewModel) {
    val done = stringResource(R.string.done_dot)
    vm.results.forEach { r ->
        Panel {
            Text(
                when {
                    r.blocked != null -> r.blocked.message
                    r.refused != null -> stringResource(R.string.res_not_done)
                    r.ok -> stringResource(R.string.res_done)
                    else -> stringResource(R.string.res_said_no)
                },
                style = MaterialTheme.typography.titleSmall,
                color = if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(r.message(done))
        }
    }
}

@Composable
private fun Confirm(title: String, body: String, yes: String, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onYes, Modifier.heightIn(min = 56.dp)) { Text(yes) } },
        dismissButton = { TextButton(onNo, Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String) =
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())

// Typed code and ports for this phone, the fallback when the notification is not available.
@Composable
fun ManualPairing(vm: AppViewModel) {
    Field(vm.code, { vm.code = it }, stringResource(R.string.f_code))
    Field(vm.pairPort, { vm.pairPort = it }, stringResource(R.string.f_pair_port))
    Field(vm.connectPort, { vm.connectPort = it }, stringResource(R.string.f_connect_port))
    BigButton(stringResource(R.string.connect), { vm.target = TargetKind.Self; vm.connect() }, enabled = !vm.busy)
    Busy(vm)
    if (vm.status.isNotBlank()) Text(vm.status)
}

@Composable
fun OtherDeviceScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { if (vm.target == TargetKind.Self) vm.target = TargetKind.Other }
    Page {
        Title(stringResource(R.string.other_title))
        Hint(stringResource(R.string.other_hint))
        Choice(vm.target == TargetKind.Other, stringResource(R.string.other_phone)) { vm.target = TargetKind.Other }
        Choice(vm.target == TargetKind.Legacy, stringResource(R.string.other_legacy)) { vm.target = TargetKind.Legacy }
        Field(vm.host_, { vm.host_ = it }, stringResource(R.string.f_ip))
        if (vm.target != TargetKind.Legacy) {
            Hint(stringResource(R.string.other_pair_hint))
            Field(vm.code, { vm.code = it }, stringResource(R.string.f_code))
            Field(vm.pairPort, { vm.pairPort = it }, stringResource(R.string.f_pair_port))
        }
        Field(vm.connectPort, { vm.connectPort = it }, stringResource(if (vm.target == TargetKind.Legacy) R.string.f_port_legacy else R.string.f_connect_port))
        BigButton(stringResource(if (vm.connected) R.string.connect_again else R.string.connect), { vm.connect() }, enabled = !vm.busy)
        Busy(vm)
        if (vm.status.isNotBlank()) Text(vm.status)
        if (vm.devices.isNotEmpty()) {
            Title(stringResource(R.string.your_devices))
            vm.devices.forEach { d ->
                BigOutlined("${d.name.ifBlank { stringResource(R.string.device) }}  ${d.host}:${d.port}", { vm.useDevice(d) })
            }
        }
    }
}

@Composable
fun ReportScreen(vm: AppViewModel) = NeedsConnection(vm) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { if (vm.before == null) vm.refreshReport() }
    Page {
        Title(stringResource(R.string.report_title))
        Hint(stringResource(R.string.report_hint))
        val before = vm.before
        val after = vm.after
        if (before == null) Text(stringResource(R.string.report_none)) else {
            val afterRows = after?.rows(ctx)?.toMap()
            before.rows(ctx).forEach { (k, v) ->
                Panel {
                    Text(k, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.report_before, v))
                    if (afterRows != null) Text(stringResource(R.string.report_after, afterRows[k] ?: "-"))
                }
            }
        }
        BigOutlined(stringResource(R.string.report_refresh), { vm.refreshReport() }, enabled = !vm.busy)
        Busy(vm)
    }
}

@Composable
fun BoostScreen(vm: AppViewModel) = NeedsConnection(vm) {
    Page {
        Title(stringResource(R.string.deep_title))
        Hint(stringResource(R.string.deep_hint))
        BigButton(stringResource(R.string.deep_now), { vm.boost() }, enabled = !vm.busy)
        Text(stringResource(R.string.anim_speed), style = MaterialTheme.typography.titleSmall)
        Choice(vm.animation == "0.5", stringResource(R.string.anim_faster)) { vm.setAnim("0.5") }
        Choice(vm.animation == "1.0", stringResource(R.string.anim_normal)) { vm.setAnim("1.0") }
        Choice(vm.animation == "off", stringResource(R.string.anim_off)) { vm.setAnim("off") }
        Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(vm.trimCaches, { vm.setTrim(it) })
            Text(stringResource(R.string.trim_label))
        }
        Hint(stringResource(R.string.trim_hint))
        Busy(vm)
        Results(vm)
    }
}

@Composable
fun BatteryScreen(vm: AppViewModel) = NeedsConnection(vm) {
    var ask by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (vm.drainers == null) vm.loadDrainers() }
    Page {
        Title(stringResource(R.string.drain_title))
        Hint(stringResource(R.string.drain_hint))
        val rep = vm.after ?: vm.before
        rep?.top?.filter { it.name != "top" }?.take(3)?.forEach { p ->
            val advice = Parsers.heatAdvice(p.name)
            if (advice != null) Panel {
                Text(stringResource(R.string.drain_busy, p.name, p.cpu.toString()), style = MaterialTheme.typography.titleSmall)
                Text(advice)
            }
        }
        vm.drainers?.forEach { d ->
            val pkg = d.packages.firstOrNull { !vm.repo.gate.isProtected(it) }
            Panel {
                Text(d.label, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.drain_mah, d.mah.toInt()))
                if (pkg == null) Hint(stringResource(R.string.drain_protected))
                else BigOutlined(stringResource(R.string.drain_limit), { ask = pkg }, enabled = !vm.busy)
            }
        }
        if (vm.drainers == null) Busy(vm)
        Results(vm)
    }
    ask?.let { pkg ->
        Confirm(stringResource(R.string.drain_ask_title, pkg), stringResource(R.string.drain_ask_body), stringResource(R.string.drain_ask_yes), { vm.restrict(pkg); ask = null }, { ask = null })
    }
}

@Composable
fun DebloatScreen(vm: AppViewModel) = NeedsConnection(vm) {
    var ask by remember { mutableStateOf<CatalogEntry?>(null) }
    LaunchedEffect(Unit) { if (vm.debloat == null) vm.loadDebloat() }
    Page {
        Title(stringResource(R.string.debloat_title))
        Hint(stringResource(R.string.debloat_hint))
        val items = vm.debloat
        if (items == null) Busy(vm) else if (items.isEmpty()) Text(stringResource(R.string.debloat_clean))
        items?.forEach { item ->
            Panel {
                Text(item.entry.pkg, style = MaterialTheme.typography.titleSmall)
                Text(item.entry.description)
                Hint(stringResource(R.string.debloat_vendor, item.entry.vendor, stringResource(if (item.entry.risk == "safe") R.string.debloat_safe else R.string.debloat_careful)))
                Hint(
                    stringResource(
                        R.string.debloat_usage,
                        item.lastUsed?.let { ago(it.toLocalDate()) } ?: stringResource(R.string.never),
                        if (item.ramKb > 0) "${item.ramKb / 1024} MB" else stringResource(R.string.not_running),
                    ),
                )
                if (item.disabled) Text(stringResource(R.string.debloat_already), color = MaterialTheme.colorScheme.primary)
                else BigOutlined(stringResource(R.string.debloat_off), { ask = item.entry }, enabled = !vm.busy)
            }
        }
        Results(vm)
    }
    ask?.let { e ->
        Confirm(stringResource(R.string.debloat_ask_title, e.pkg), stringResource(R.string.debloat_ask_body, e.description), stringResource(R.string.debloat_off), { vm.disable(e); ask = null }, { ask = null })
    }
}

@Composable
private fun ago(d: LocalDate): String {
    val days = ChronoUnit.DAYS.between(d, LocalDate.now())
    return when {
        days <= 0 -> stringResource(R.string.today)
        days < 31 -> stringResource(R.string.days_ago, days.toInt())
        days < 365 -> stringResource(R.string.months_ago, (days / 30).toInt())
        else -> stringResource(R.string.over_year)
    }
}

private data class Perm(val label: Int, val id: String)

private val permissions = listOf(
    Perm(R.string.perm_camera, "android.permission.CAMERA"),
    Perm(R.string.perm_mic, "android.permission.RECORD_AUDIO"),
    Perm(R.string.perm_location, "android.permission.ACCESS_FINE_LOCATION"),
    Perm(R.string.perm_contacts, "android.permission.READ_CONTACTS"),
    Perm(R.string.perm_notifications, "android.permission.POST_NOTIFICATIONS"),
)

@Composable
fun FixupsScreen(vm: AppViewModel) = NeedsConnection(vm) {
    var ask by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var app by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var permission by remember { mutableStateOf(permissions.first()) }
    val permName = stringResource(permission.label)
    Page {
        Title(stringResource(R.string.fix_title))
        Hint(stringResource(R.string.fix_hint))
        BigOutlined(stringResource(R.string.fix_location), { ask = "loc" to { vm.fixLocation() } }, enabled = !vm.busy)
        BigOutlined(stringResource(R.string.fix_anim), { ask = "anim" to { vm.fixAnimations() } }, enabled = !vm.busy)
        BigOutlined(stringResource(R.string.fix_touch), { ask = "touch" to { vm.fixTouch() } }, enabled = !vm.busy)
        Title(stringResource(R.string.fix_one_app))
        BigOutlined(app.ifBlank { stringResource(R.string.fix_choose_app) }, { vm.loadPackages(); picking = true })
        permissions.forEach { p -> Choice(permission == p, stringResource(p.label)) { permission = p } }
        BigButton(stringResource(R.string.fix_allow, permName), { ask = "allow" to { vm.grant(app, permission.id) } }, enabled = !vm.busy && app.isNotBlank())
        BigOutlined(stringResource(R.string.fix_keep), { ask = "keep" to { vm.stopRevoke(app) } }, enabled = !vm.busy && app.isNotBlank())
        Busy(vm)
        Results(vm)
    }
    ask?.let { (kind, go) ->
        val q = when (kind) {
            "loc" -> stringResource(R.string.ask_location)
            "anim" -> stringResource(R.string.ask_anim, vm.animation)
            "touch" -> stringResource(R.string.ask_touch)
            "allow" -> stringResource(R.string.ask_allow, permName, app)
            else -> stringResource(R.string.ask_keep, app)
        }
        Confirm(q, stringResource(R.string.ask_undo_later), stringResource(R.string.ask_yes), { go(); ask = null }, { ask = null })
    }
    if (picking) AlertDialog(
        onDismissRequest = { picking = false },
        confirmButton = { TextButton({ picking = false }, Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.fix_choose_app)) },
        text = {
            LazyColumn {
                items(vm.packages) { p -> Text(p, Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { app = p; picking = false }.padding(vertical = 14.dp)) }
            }
        },
    )
}

@Composable
fun UndoScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.refreshUndo() }
    var askAll by remember { mutableStateOf(false) }
    Page {
        Title(stringResource(R.string.undo_title))
        Hint(stringResource(R.string.undo_hint))
        if (vm.undo.isEmpty()) Text(stringResource(R.string.undo_none))
        vm.undo.reversed().forEach { e ->
            Panel {
                Text(e.label, style = MaterialTheme.typography.titleSmall)
                Hint(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(e.time)))
                BigOutlined(stringResource(R.string.undo_button), { vm.undoOne(e) }, enabled = vm.connected && !vm.busy)
            }
        }
        if (vm.undo.isNotEmpty()) BigButton(stringResource(R.string.undo_all), { askAll = true }, enabled = vm.connected && !vm.busy)
        if (!vm.connected && vm.undo.isNotEmpty()) Hint(stringResource(R.string.undo_connect))
        Busy(vm)
        Results(vm)
    }
    if (askAll) Confirm(stringResource(R.string.undo_all_title), stringResource(R.string.undo_all_body), stringResource(R.string.undo_all_yes), { vm.undoAll(); askAll = false }, { askAll = false })
}
