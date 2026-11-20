package dev.pallav.phonesaar.launcher

import android.Manifest
import android.app.role.RoleManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.pallav.phonesaar.assistant.Speaker
import dev.pallav.phonesaar.assistant.VoiceInput
import dev.pallav.phonesaar.launcher.data.AppInfo
import dev.pallav.phonesaar.launcher.data.AppRepo
import dev.pallav.phonesaar.launcher.design.DesignStore
import dev.pallav.phonesaar.launcher.design.GestureAction
import dev.pallav.phonesaar.launcher.ui.*
import kotlinx.coroutines.launch

enum class Screen { HOME, DRAWER, CUSTOMIZE, SETTINGS, NOTIFS }

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private var homeSignal by mutableIntStateOf(0)
    private var resumeSignal by mutableIntStateOf(0)
    private var lang by mutableStateOf("en")
    private var listening by mutableStateOf<String?>(null)
    private var bubble by mutableStateOf<String?>(null)
    private var voice: VoiceInput? = null
    private var speaker: Speaker? = null
    private var pendingPermission: (() -> Unit)? = null
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) pendingPermission?.invoke(); pendingPermission = null }
    private val photoLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) PhotoStore.save(this, uri) }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importText(runCatching { contentResolver.openInputStream(it)?.bufferedReader()?.readText() }.getOrNull()) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        lang = prefs.language.ifEmpty { "en" }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { Root() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while we are already home: go back to the plain home screen.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) homeSignal++
    }

    override fun onResume() {
        super.onResume()
        DesignStore.onResume()
        resumeSignal++
        MusicStore.refresh()
    }

    override fun onDestroy() { voice?.destroy(); speaker?.shutdown(); super.onDestroy() }

    private fun langTag() = if (lang == "hi") "hi-IN" else "en-IN"

    private fun speak(text: String) {
        if (speaker == null) speaker = Speaker(this, langTag()) { }
        speaker?.speak(text)
    }

    private fun setLanguage(l: String) { lang = l; prefs.language = l; speaker?.shutdown(); speaker = null }

    private fun withPermission(p: String, then: () -> Unit) {
        if (checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) then() else { pendingPermission = then; permLauncher.launch(p) }
    }

    private fun startVoice(run: (String) -> Unit) = withPermission(Manifest.permission.RECORD_AUDIO) {
        if (!VoiceInput.isAvailable(this)) { bubble = if (lang == "hi") "इस फोन में आवाज़ पहचान नहीं है" else "No speech recognizer on this phone"; return@withPermission }
        voice?.destroy()
        voice = VoiceInput(this, { if (it) listening = if (lang == "hi") "सुन रहा हूँ…" else "Listening…" else listening = null }, { listening = it }, { listening = null; run(it) }, { listening = null; bubble = if (lang == "hi") "समझ नहीं आया" else "Did not catch that" })
        voice?.start(langTag())
    }

    private fun setHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) { startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)); return }
        }
        homeSettings()
    }

    private fun homeSettings() { runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }.onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) } }

    private fun share(name: String, text: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, name).putExtra(Intent.EXTRA_TEXT, text), name))
    }

    private fun importText(text: String?) {
        val w = if (text == null) null else DesignStore.import(text)
        Toast.makeText(this, if (w == null) "Not a design JSON" else if (w.isEmpty()) "Theme imported" else "Imported, ${w.size} fields ignored", Toast.LENGTH_SHORT).show()
    }

    private fun expandShade(): Boolean = try {
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(getSystemService("statusbar")); true
    } catch (e: Throwable) { false }

    @Composable
    private fun Root() {
        val st by DesignStore.state.collectAsState()
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var screen by remember { mutableStateOf(Screen.HOME) }
        var drawerQuery by remember { mutableStateOf("") }
        var editOn by remember { mutableStateOf(false) }
        var selected by remember { mutableIntStateOf(-1) }
        var sheet by remember { mutableIntStateOf(-1) }
        var menuApp by remember { mutableStateOf<AppInfo?>(null) }
        var replyFor by remember { mutableStateOf<NotifItem?>(null) }
        var warnDismissed by remember { mutableIntStateOf(0) }
        var warnOpen by remember { mutableStateOf(false) }
        var launchTick by remember { mutableIntStateOf(0) }
        var firstRun by remember { mutableStateOf(!prefs.firstRunDone) }
        var prefTick by remember { mutableIntStateOf(0) }
        val apps by AppRepo.apps.collectAsState()
        val usage by AppRepo.usage.collectAsState()
        val runner = remember { Runner(ctx, prefs) { lang } }
        LaunchedEffect(homeSignal) { if (homeSignal > 0) { screen = Screen.HOME; editOn = false; sheet = -1; menuApp = null } }
        LaunchedEffect(resumeSignal) { AppRepo.refreshUsage(ctx) }
        LaunchedEffect(st.design.theme) {
            val t = st.design.theme
            WindowInsetsControllerCompat(window, window.decorView).apply { isAppearanceLightStatusBars = !t.lightStatusIcons; isAppearanceLightNavigationBars = !t.lightStatusIcons }
            if (Build.VERSION.SDK_INT >= 31) window.setBackgroundBlurRadius(if (t.blur && t.bgMode.name == "WALLPAPER") 50 else 0)
        }
        val sphereApps = remember(apps, usage, st.design.sphere.rank, launchTick, prefTick) { AppRepo.ranked(prefs, st.design.sphere.rank) }

        fun run(text: String) {
            scope.launch {
                val o = runner.handle(text)
                if (o.filter != null) { drawerQuery = o.filter; screen = Screen.DRAWER }
                if (o.reply.isNotBlank()) { bubble = o.reply; speak(o.reply) }
                o.askReplyText?.let { replyFor = it }
            }
        }
        val env = remember(st.design.layout) {
            Env(
                launch = { AppRepo.launch(ctx, it); launchTick++; screen = Screen.HOME },
                appMenu = { menuApp = it },
                openDrawer = { q -> drawerQuery = q.orEmpty(); screen = Screen.DRAWER },
                run = ::run,
                voice = { startVoice(::run) },
                speak = ::speak,
                pickPhoto = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                openGoogle = { if (!AppRepo.launchPackage(ctx, "com.google.android.googlequicksearchbox")) runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                openCalendar = { AppRepo.roleToPackage(ctx, "calendar")?.let { AppRepo.launchPackage(ctx, it) } },
                openCustomize = { screen = Screen.CUSTOMIZE },
                requestPermission = { p -> withPermission(p) { prefTick++ } },
                openUsageSettings = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                openNotifSettings = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                editBlock = { type -> sheet = st.design.layout.blocks.indexOfFirst { it.type == type }.also { selected = it } },
                gesture = { a ->
                    when (a) {
                        GestureAction.DRAWER -> { drawerQuery = ""; screen = Screen.DRAWER }
                        GestureAction.SHADE -> if (!expandShade()) screen = Screen.NOTIFS
                        GestureAction.LOCK -> Toast.makeText(ctx, if (lang == "hi") "लॉक के लिए खास अनुमति चाहिए" else "Locking needs special access, not available", Toast.LENGTH_SHORT).show()
                        GestureAction.SETTINGS -> screen = Screen.CUSTOMIZE
                        GestureAction.NONE -> {}
                    }
                },
                sendReply = { n, text -> NotifStore.replyAction(n)?.let { NotifStore.reply(ctx, it, text) } },
            )
        }
        BackHandler(enabled = screen != Screen.HOME || editOn) {
            when { sheet >= 0 -> sheet = -1; screen != Screen.HOME -> screen = Screen.HOME; editOn -> { editOn = false; selected = -1 } }
        }
        val acts = remember(st.design.layout) {
            CustomizeActions(
                importFile = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                importClipboard = { importText((getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString()) },
                share = ::share, editHome = { screen = Screen.HOME; editOn = true }, openSettings = { screen = Screen.SETTINGS },
                close = { screen = Screen.HOME }, pickPhoto = { env.pickPhoto() }, requestPermission = { p -> withPermission(p) { prefTick++ } },
            )
        }
        LauncherTheme(st.design, lang) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(st, env, prefs, sphereApps, prefs.designMode, EditState(editOn, selected) { selected = it })
                if (st.design.features.designChip && st.warnings.isNotEmpty() && warnDismissed != st.warnings.hashCode() && !editOn) {
                    Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp).clip(RoundedCornerShape(50)).background(Color(0xE6FF8A00)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("${st.warnings.size} design warnings", Modifier.clickable { warnOpen = true }, color = Color.Black, fontSize = 12.sp)
                        Text("  ✕", Modifier.clickable { warnDismissed = st.warnings.hashCode() }, color = Color.Black, fontSize = 12.sp)
                    }
                }
                if (editOn) EditBar(st, selected, { selected = it }, { sheet = selected }, { screen = Screen.CUSTOMIZE }, { editOn = false; selected = -1 }, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars))
                if (screen == Screen.DRAWER) DrawerScreen(st.design, prefs, env, drawerQuery) { screen = Screen.HOME }
                if (screen == Screen.NOTIFS) Box(Modifier.fillMaxSize().background(st.design.theme.bgColor.c().copy(alpha = 0.97f)).statusBarsPadding().padding(16.dp)) {
                    NotificationsBlock(dev.pallav.phonesaar.launcher.design.BlockSpecs.make("notifications").copy(opts = mapOf("maxItems" to 30)), st.design, env, prefs, Modifier.fillMaxWidth())
                }
                if (screen == Screen.CUSTOMIZE) CustomizeScreen(st, prefs, acts)
                if (screen == Screen.SETTINGS) SettingsScreen(st, prefs, lang, SettingsActions(
                    close = { screen = Screen.HOME }, openCustomize = { screen = Screen.CUSTOMIZE }, setHome = ::setHome, homeSettings = ::homeSettings,
                    usage = { env.openUsageSettings() }, notifAccess = { env.openNotifSettings() }, share = ::share, setLanguage = ::setLanguage, onChanged = { prefTick++ },
                ))
                if (sheet >= 0) BlockSheet(sheet, st, acts) { sheet = -1 }
                if (firstRun) FirstRunScreen(lang, ::setLanguage, ::setHome, {}, { prefs.firstRunDone = true; firstRun = false })
                (listening ?: bubble)?.let { msg ->
                    Box(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 90.dp, start = 24.dp, end = 24.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xE6101012)).clickable { bubble = null }.padding(14.dp)) {
                        Text(msg, color = Color.White, fontSize = 14.sp, maxLines = 6)
                    }
                    LaunchedEffect(msg, listening) { if (listening == null) { kotlinx.coroutines.delay(5000); bubble = null } }
                }
                menuApp?.let { a ->
                    val hidden = prefs.hidden; val pinned = prefs.pinned
                    AlertDialog(onDismissRequest = { menuApp = null }, confirmButton = {}, title = { Text(a.label) }, text = {
                        androidx.compose.foundation.layout.Column {
                            TextButton({ AppRepo.appInfo(ctx, a); menuApp = null }) { Text(if (lang == "hi") "ऐप की जानकारी" else "App info") }
                            TextButton({ prefs.hidden = if (a.pkg in hidden) hidden - a.pkg else hidden + a.pkg; prefTick++; menuApp = null }) { Text(if (a.pkg in hidden) (if (lang == "hi") "दिखाएँ" else "Unhide") else (if (lang == "hi") "छिपाएँ" else "Hide")) }
                            TextButton({ prefs.pinned = if (a.pkg in pinned) pinned - a.pkg else pinned + a.pkg; prefTick++; menuApp = null }) { Text(if (a.pkg in pinned) (if (lang == "hi") "डॉक से हटाएँ" else "Unpin from dock") else (if (lang == "hi") "डॉक में लगाएँ" else "Pin to dock")) }
                        }
                    })
                }
                replyFor?.let { n ->
                    var text by remember { mutableStateOf("") }
                    AlertDialog(onDismissRequest = { replyFor = null }, title = { Text(n.appLabel) }, text = { androidx.compose.material3.OutlinedTextField(text, { text = it }) },
                        confirmButton = { TextButton({ if (text.isNotBlank()) env.sendReply(n, text); replyFor = null }) { Text(if (lang == "hi") "भेजें" else "Send") } })
                }
                if (warnOpen) AlertDialog(onDismissRequest = { warnOpen = false }, confirmButton = { TextButton({ warnOpen = false }) { Text("OK") } }, title = { Text("Design warnings") }, text = {
                    androidx.compose.foundation.layout.Column(Modifier.padding(0.dp)) { for (w in st.warnings.take(20)) Text("• $w", fontSize = 12.sp) }
                })
            }
        }
    }
}
