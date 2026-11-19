package dev.pallav.phonesaar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.pallav.phonesaar.core.CatalogEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleLarge)

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF5C6B6B))

@Composable
private fun NeedsConnection(vm: AppViewModel, content: @Composable () -> Unit) {
    if (vm.connected) content() else Page {
        Title("Connect first")
        Hint("Go to the Connect tab and link to a phone or TV. Then come back here.")
    }
}

@Composable
private fun Busy(vm: AppViewModel) {
    if (vm.busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.padding(2.dp))
        Text("Working...")
    }
}

// Shows exactly what the device answered; a block never looks like success.
@Composable
private fun Results(vm: AppViewModel) {
    vm.results.forEach { r ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when {
                        r.blocked != null -> r.blocked.message
                        r.refused != null -> "Not done"
                        r.ok -> "Done"
                        else -> "The device said no"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (r.ok) Color(0xFF1F8A70) else Color(0xFFB3261E),
                )
                Text(r.message(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Confirm(title: String, body: String, yes: String, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onYes) { Text(yes) } },
        dismissButton = { TextButton(onNo) { Text("Cancel") } },
    )
}

@Composable
fun ConnectScreen(vm: AppViewModel) = Page {
    Title("Connect")
    Hint("Pick what you want to speed up. Wireless debugging must be on in Developer options.")
    listOf(
        TargetKind.Self to "This phone",
        TargetKind.Other to "Another phone (IP address)",
        TargetKind.Legacy to "Older phone or TV (adb tcpip 5555)",
    ).forEach { (kind, label) ->
        Row(Modifier.fillMaxWidth().clickable { vm.target = kind }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(vm.target == kind, { vm.target = kind })
            Text(label)
        }
    }
    if (vm.target != TargetKind.Self) {
        OutlinedTextField(vm.host_, { vm.host_ = it }, label = { Text("IP address, like 192.168.1.20") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    if (vm.target != TargetKind.Legacy) {
        Hint("First time only: in Wireless debugging tap Pair device with pairing code, then enter the code and port it shows.")
        OutlinedTextField(vm.code, { vm.code = it }, label = { Text("6-digit pairing code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vm.pairPort, { vm.pairPort = it }, label = { Text("Pairing port") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    OutlinedTextField(
        vm.connectPort, { vm.connectPort = it },
        label = { Text(if (vm.target == TargetKind.Legacy) "Port (usually 5555)" else "Connection port") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    Button({ vm.connect() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(if (vm.connected) "Connect again" else "Connect") }
    if (vm.connected) OutlinedButton({ vm.disconnect() }, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
    Busy(vm)
    if (vm.status.isNotBlank()) Text(vm.status)
    if (vm.devices.isNotEmpty()) {
        Title("Your devices")
        vm.devices.forEach { d ->
            OutlinedButton({ vm.useDevice(d) }, modifier = Modifier.fillMaxWidth()) { Text("${d.name.ifBlank { "Device" }}  ${d.host}:${d.port}") }
        }
    }
}

@Composable
fun ReportScreen(vm: AppViewModel) = NeedsConnection(vm) {
    Page {
        Title("Report")
        Hint("Before is taken when you connect. After updates every time you change something.")
        val before = vm.before
        val after = vm.after
        if (before == null) Text("No report yet.") else {
            val afterRows = after?.rows()?.toMap()
            before.rows().forEach { (k, v) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(k, style = MaterialTheme.typography.titleSmall)
                        Text("Before: $v")
                        if (afterRows != null) Text("After: ${afterRows[k] ?: "-"}")
                    }
                }
            }
        }
        Button({ vm.refreshReport() }, enabled = !vm.busy) { Text("Refresh report") }
        Busy(vm)
    }
}

@Composable
fun BoostScreen(vm: AppViewModel) = NeedsConnection(vm) {
    Page {
        Title("Boost")
        Hint("Safe and quick. Frees memory and tidies storage. Nothing is deleted and nobody is logged out.")
        Button({ vm.boost() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Boost now") }
        Text("Animation speed")
        listOf("0.5" to "Faster (0.5x)", "1.0" to "Normal (1x)", "off" to "No animations").forEach { (v, label) ->
            Row(Modifier.fillMaxWidth().clickable { vm.setAnim(v) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(vm.animation == v, { vm.setAnim(v) })
                Text(label)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(vm.trimCaches, { vm.setTrim(it) })
            Column {
                Text("Also clear temporary app files")
                Hint("Clears temporary app files; never logs out.")
            }
        }
        Busy(vm)
        Results(vm)
    }
}

@Composable
fun BatteryScreen(vm: AppViewModel) = NeedsConnection(vm) {
    var ask by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (vm.drainers == null) vm.loadDrainers() }
    Page {
        Title("Battery")
        Hint("Apps that used the most battery since the last full charge.")
        val rep = vm.after ?: vm.before
        rep?.top?.filter { it.name != "top" }?.take(3)?.forEach { p ->
            val advice = dev.pallav.phonesaar.core.Parsers.heatAdvice(p.name)
            if (advice != null) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${p.name} is busy (${p.cpu}% CPU)", style = MaterialTheme.typography.titleSmall)
                    Text(advice)
                }
            }
        }
        vm.drainers?.forEach { d ->
            val pkg = d.packages.firstOrNull { !vm.repo.gate.isProtected(it) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(d.label, style = MaterialTheme.typography.titleSmall)
                    Text("${d.mah.toInt()} mAh used")
                    if (pkg == null) Hint("Protected: PhoneSaar never limits this one.")
                    else OutlinedButton({ ask = pkg }, enabled = !vm.busy) { Text("Limit in the background") }
                }
            }
        }
        if (vm.drainers == null) Busy(vm)
        Results(vm)
    }
    ask?.let { pkg ->
        Confirm("Limit $pkg?", "It will stop running in the background. You can still open it, and Undo puts it back.", "Limit it", { vm.restrict(pkg); ask = null }, { ask = null })
    }
}

@Composable
fun DebloatScreen(vm: AppViewModel) = NeedsConnection(vm) {
    var ask by remember { mutableStateOf<CatalogEntry?>(null) }
    LaunchedEffect(Unit) { if (vm.debloat == null) vm.loadDebloat() }
    Page {
        Title("Debloat")
        Hint("Pre-installed extras you can turn off. Nothing is uninstalled, and Undo turns it back on.")
        val items = vm.debloat
        if (items == null) Busy(vm) else if (items.isEmpty()) Text("Nothing to turn off here. This device looks clean.")
        items?.forEach { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.entry.pkg, style = MaterialTheme.typography.titleSmall)
                    Text(item.entry.description)
                    Hint("${item.entry.vendor}. ${if (item.entry.risk == "safe") "Safe to turn off" else "Be careful"}.")
                    Hint("Last used: ${item.lastUsed?.let { ago(it.toLocalDate()) } ?: "never"}. Memory: ${if (item.ramKb > 0) "${item.ramKb / 1024} MB" else "not running"}.")
                    if (item.disabled) Text("Already turned off", color = Color(0xFF1F8A70))
                    else OutlinedButton({ ask = item.entry }, enabled = !vm.busy) { Text("Turn off") }
                }
            }
        }
        Results(vm)
    }
    ask?.let { e ->
        Confirm("Turn off ${e.pkg}?", "${e.description} You can turn it back on from Undo.", "Turn off", { vm.disable(e); ask = null }, { ask = null })
    }
}

private fun ago(d: LocalDate): String {
    val days = ChronoUnit.DAYS.between(d, LocalDate.now())
    return when {
        days <= 0 -> "today"
        days < 31 -> "$days days ago"
        days < 365 -> "${days / 30} months ago"
        else -> "over a year ago"
    }
}

private val permissions = listOf(
    "Camera" to "android.permission.CAMERA",
    "Microphone" to "android.permission.RECORD_AUDIO",
    "Location" to "android.permission.ACCESS_FINE_LOCATION",
    "Contacts" to "android.permission.READ_CONTACTS",
    "Notifications" to "android.permission.POST_NOTIFICATIONS",
)

@Composable
fun FixupsScreen(vm: AppViewModel) = NeedsConnection(vm) {
    var ask by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var app by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var permission by remember { mutableStateOf(permissions.first()) }
    Page {
        Title("Fix-ups")
        Hint("Each one asks first and can be undone.")
        OutlinedButton({ ask = "Turn location on?" to { vm.fixLocation() } }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Turn location on") }
        OutlinedButton({ ask = "Set animation speed to ${vm.animation}?" to { vm.fixAnimations() } }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Set animation speed") }
        OutlinedButton({ ask = "Make touch and hold quicker?" to { vm.fixTouch() } }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Quicker touch and hold") }
        Title("For one app")
        OutlinedButton({ vm.loadPackages(); picking = true }, modifier = Modifier.fillMaxWidth()) { Text(if (app.isBlank()) "Choose an app" else app) }
        permissions.forEach { p ->
            Row(Modifier.fillMaxWidth().clickable { permission = p }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(permission == p, { permission = p })
                Text(p.first)
            }
        }
        Button({ ask = "Allow ${permission.first} for $app?" to { vm.grant(app, permission.second) } }, enabled = !vm.busy && app.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Allow ${permission.first}") }
        OutlinedButton({ ask = "Stop Android removing permissions from $app?" to { vm.stopRevoke(app) } }, enabled = !vm.busy && app.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Keep this app's permissions") }
        Busy(vm)
        Results(vm)
    }
    ask?.let { (q, go) ->
        Confirm(q, "You can undo it later.", "Yes, do it", { go(); ask = null }, { ask = null })
    }
    if (picking) AlertDialog(
        onDismissRequest = { picking = false },
        confirmButton = { TextButton({ picking = false }) { Text("Close") } },
        title = { Text("Choose an app") },
        text = {
            LazyColumn {
                items(vm.packages) { p -> Text(p, Modifier.fillMaxWidth().clickable { app = p; picking = false }.padding(vertical = 10.dp)) }
            }
        },
    )
}

@Composable
fun UndoScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.refreshUndo() }
    var askAll by remember { mutableStateOf(false) }
    Page {
        Title("Undo")
        Hint("Everything PhoneSaar changed on this device. Tap Undo to put one thing back.")
        if (vm.undo.isEmpty()) Text("Nothing to undo.")
        vm.undo.reversed().forEach { e ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(e.label, style = MaterialTheme.typography.titleSmall)
                    Hint(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(e.time)))
                    OutlinedButton({ vm.undoOne(e) }, enabled = vm.connected && !vm.busy) { Text("Undo") }
                }
            }
        }
        if (vm.undo.isNotEmpty()) Button({ askAll = true }, enabled = vm.connected && !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Undo everything") }
        if (!vm.connected && vm.undo.isNotEmpty()) Hint("Connect to the device to undo.")
        Busy(vm)
        Results(vm)
    }
    if (askAll) Confirm("Undo everything?", "Every change in this list will be put back.", "Undo all", { vm.undoAll(); askAll = false }, { askAll = false })
}
