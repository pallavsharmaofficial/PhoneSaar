package dev.pallav.phonesaar.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pallav.phonesaar.launcher.NotifStore
import dev.pallav.phonesaar.launcher.Prefs
import dev.pallav.phonesaar.launcher.data.AppRepo
import dev.pallav.phonesaar.launcher.design.DesignState
import dev.pallav.phonesaar.launcher.design.DesignStore

class SettingsActions(
    val close: () -> Unit, val openCustomize: () -> Unit, val setHome: () -> Unit, val homeSettings: () -> Unit,
    val usage: () -> Unit, val notifAccess: () -> Unit, val share: (String, String) -> Unit, val setLanguage: (String) -> Unit,
    val onChanged: () -> Unit,
)

@Composable
private fun Section(text: String) = Text(text, Modifier.padding(top = 18.dp, bottom = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface); Switch(value, onChange) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(st: DesignState, prefs: Prefs, lang: String, acts: SettingsActions) {
    val ctx = LocalContext.current
    var designMode by remember { mutableStateOf(prefs.designMode) }
    var ongoing by remember { mutableStateOf(prefs.showOngoing) }
    var badges by remember { mutableStateOf(prefs.badges) }
    val apps by AppRepo.apps.collectAsState()
    var hidden by remember { mutableStateOf(prefs.hidden) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.systemBars)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { acts.close() }, Alignment.Center) { Glyph(G.CLOSE, onSurface, 22.dp) }
            Text(tr("Launcher settings", "लॉन्चर सेटिंग"), Modifier.padding(start = 6.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = onSurface)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp)) {
            Section(tr("Look", "रूप"))
            Button({ acts.openCustomize() }, Modifier.fillMaxWidth()) { Text(tr("Themes and customize", "थीम और बदलाव")) }
            Text(tr("Active: ", "चालू: ") + st.design.name, Modifier.padding(top = 6.dp), color = onSurface.copy(alpha = 0.7f))
            Section(tr("Design warnings", "डिज़ाइन चेतावनी"))
            if (st.warnings.isEmpty()) Text(tr("None. Every field was understood.", "कोई नहीं। सब कुछ समझ आ गया।"), color = onSurface.copy(alpha = 0.7f))
            else for (w in st.warnings.take(30)) Text("• $w", fontSize = 12.sp, color = onSurface.copy(alpha = 0.8f))
            OutlinedButton({ acts.share(st.design.name, DesignStore.exportText(st.active).orEmpty()) }, Modifier.padding(top = 8.dp)) { Text(tr("Export current design JSON", "मौजूदा डिज़ाइन JSON शेयर करें")) }
            Section(tr("Features", "सुविधाएँ"))
            ToggleRow(tr("Design mode (show block outlines)", "डिज़ाइन मोड (ब्लॉक की रेखाएँ)"), designMode) { designMode = it; prefs.designMode = it; acts.onChanged() }
            ToggleRow(tr("Show ongoing notifications", "चालू नोटिफिकेशन दिखाएँ"), ongoing) { ongoing = it; prefs.showOngoing = it; acts.onChanged() }
            ToggleRow(tr("Notification badges on icons", "आइकन पर बैज"), badges) { badges = it; prefs.badges = it; acts.onChanged() }
            Section(tr("Access", "अनुमतियाँ"))
            val usage = AppRepo.hasUsageAccess(ctx)
            Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("Usage access: ", "Usage access: ") + if (usage) tr("on", "चालू") else tr("off (apps ranked by launcher taps)", "बंद"), Modifier.weight(1f), color = onSurface); TextButton({ acts.usage() }) { Text(tr("Open", "खोलें")) } }
            val notif = NotifStore.enabled(ctx)
            Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("Notification access: ", "Notification access: ") + if (notif) tr("on", "चालू") else tr("off", "बंद"), Modifier.weight(1f), color = onSurface); TextButton({ acts.notifAccess() }) { Text(tr("Open", "खोलें")) } }
            Section(tr("Language", "भाषा"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(lang == "en", { acts.setLanguage("en") }, { Text("English") })
                FilterChip(lang == "hi", { acts.setLanguage("hi") }, { Text("हिन्दी") })
            }
            Section(tr("Home app", "होम ऐप"))
            Button({ acts.setHome() }, Modifier.fillMaxWidth()) { Text(tr("Make PhoneSaar Launcher my Home app", "PhoneSaar Launcher को होम ऐप बनाएँ")) }
            OutlinedButton({ acts.homeSettings() }, Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(tr("Go back to my old launcher", "पुराने लॉन्चर पर वापस जाएँ")) }
            Text(tr("Xiaomi / HyperOS: if the button does nothing, open Settings > Apps > Default apps > Home screen and pick PhoneSaar Launcher (or your old one to go back). On some MIUI and HyperOS builds, full-screen gestures fall back to the three buttons with a third-party launcher; that is the system's choice, not something the launcher can change.",
                "Xiaomi / HyperOS: बटन से कुछ न हो तो Settings > Apps > Default apps > Home screen में जाकर चुनें। कुछ MIUI/HyperOS में थर्ड-पार्टी लॉन्चर के साथ फुल-स्क्रीन जेस्चर की जगह तीन बटन आ सकते हैं; यह सिस्टम तय करता है।"),
                Modifier.padding(top = 8.dp), fontSize = 12.sp, color = onSurface.copy(alpha = 0.7f))
            Section(tr("Hidden apps", "छिपे ऐप"))
            val hiddenApps = apps.filter { it.pkg in hidden }
            if (hiddenApps.isEmpty()) Text(tr("None", "कोई नहीं"), color = onSurface.copy(alpha = 0.7f))
            for (a in hiddenApps) Row(verticalAlignment = Alignment.CenterVertically) { Text(a.label, Modifier.weight(1f), color = onSurface); TextButton({ hidden = hidden - a.pkg; prefs.hidden = hidden; acts.onChanged() }) { Text(tr("Unhide", "दिखाएँ")) } }
            Spacer(Modifier.height(20.dp))
            Text("PhoneSaar Launcher " + (runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""), fontSize = 12.sp, color = onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun FirstRunScreen(lang: String, onLang: (String) -> Unit, onSetHome: () -> Unit, onSkip: () -> Unit, onDone: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val onS = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).windowInsetsPadding(WindowInsets.systemBars).padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Text("PhoneSaar Launcher", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = onS)
        Spacer(Modifier.height(16.dp))
        if (step == 0) {
            Text("भाषा चुनें / Choose your language", fontSize = 16.sp, color = onS)
            Spacer(Modifier.height(16.dp))
            Button({ onLang("hi"); step = 1 }, Modifier.fillMaxWidth()) { Text("हिन्दी") }
            OutlinedButton({ onLang("en"); step = 1 }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("English") }
        } else {
            Text(tr("Make it your Home app", "इसे अपना होम ऐप बनाएँ"), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = onS)
            Text(tr("The Home button and the Home screen will then show this launcher. You can go back to your old launcher any time from Launcher settings or Android's Default apps.", "फिर होम बटन दबाने पर यही लॉन्चर खुलेगा। आप कभी भी लॉन्चर सेटिंग या Android के Default apps से पुराने लॉन्चर पर लौट सकते हैं।"), Modifier.padding(top = 8.dp), color = onS)
            Text(tr("Xiaomi / HyperOS: you may need Settings > Apps > Default apps > Home screen, and full-screen gestures can fall back to buttons with a third-party launcher on some builds.", "Xiaomi / HyperOS: Settings > Apps > Default apps > Home screen में चुनना पड़ सकता है, और कुछ बिल्ड में फुल-स्क्रीन जेस्चर की जगह बटन आ सकते हैं।"), Modifier.padding(top = 8.dp), fontSize = 13.sp, color = onS.copy(alpha = 0.75f))
            Spacer(Modifier.height(20.dp))
            Button({ onSetHome(); onDone() }, Modifier.fillMaxWidth()) { Text(tr("Set as Home app", "होम ऐप बनाएँ")) }
            TextButton({ onSkip(); onDone() }, Modifier.fillMaxWidth()) { Text(tr("Not now, just look around", "अभी नहीं, पहले देख लूँ")) }
        }
    }
}
