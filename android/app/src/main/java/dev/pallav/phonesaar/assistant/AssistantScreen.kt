package dev.pallav.phonesaar.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.pallav.phonesaar.R
import kotlinx.coroutines.launch

private val BrandGreen = Color(0xFF1F8A70)

private val LightScheme = lightColorScheme(
    primary = BrandGreen, onPrimary = Color.White, primaryContainer = Color(0xFFD3F0E7), onPrimaryContainer = Color(0xFF00352A),
    background = Color(0xFFF8FCFA), onBackground = Color(0xFF0B1512), surface = Color(0xFFF8FCFA), onSurface = Color(0xFF0B1512),
    surfaceVariant = Color(0xFFE2EEE9), onSurfaceVariant = Color(0xFF1B2C26), outline = Color(0xFF4A6159),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5FD4B3), onPrimary = Color(0xFF00382B), primaryContainer = Color(0xFF1C4A3D), onPrimaryContainer = Color(0xFFD3F0E7),
    background = Color(0xFF0E1513), onBackground = Color(0xFFEAF5F1), surface = Color(0xFF0E1513), onSurface = Color(0xFFEAF5F1),
    surfaceVariant = Color(0xFF22332E), onSurfaceVariant = Color(0xFFDCEAE5), outline = Color(0xFF9DB5AC),
)

private val AssistantTypography = Typography(
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodySmall = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 18.sp),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
)

@Composable
internal fun AssistantTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, typography = AssistantTypography, content = content)
}

/** Entry point: the opt-in screen first, then the Ask and My apps views. */
@Composable
fun AssistantScreen(host: AssistantHost, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { AssistantPrefs(context) }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    AssistantTheme {
        Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!enabled) IntroView { prefs.enabled = true; enabled = true }
            else AssistantMain(host, prefs) { prefs.enabled = false; enabled = false }
        }
    }
}

@Composable
private fun IntroView(onTurnOn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🤖", fontSize = 64.sp, modifier = Modifier.semantics { contentDescription = "" })
        Text(stringResource(R.string.asst_intro_title), style = MaterialTheme.typography.titleLarge)
        listOf(R.string.asst_intro_line1, R.string.asst_intro_line2, R.string.asst_intro_line3).forEach {
            Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
        }
        Button(onClick = onTurnOn, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            Text(stringResource(R.string.asst_turn_on), fontSize = 20.sp)
        }
    }
}

@Composable
private fun AssistantMain(host: AssistantHost, prefs: AssistantPrefs, onTurnOff: () -> Unit) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    LaunchedEffect(tab) { apps = AppCatalog.load(context) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.asst_tab_ask, R.string.asst_tab_apps).forEachIndexed { i, label ->
                val selected = tab == i
                val mod = Modifier.weight(1f).heightIn(min = 56.dp)
                if (selected) Button(onClick = { tab = i }, modifier = mod) { Text(stringResource(label)) }
                else OutlinedButton(onClick = { tab = i }, modifier = mod) { Text(stringResource(label)) }
            }
        }
        Spacer(Modifier.size(8.dp))
        if (tab == 0) AskView(host, prefs, apps, onTurnOff) else AppsView(prefs, apps)
    }
}

private data class ChatLine(val fromUser: Boolean, val text: String, val action: ReplyAction? = null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskView(host: AssistantHost, prefs: AssistantPrefs, apps: List<AppEntry>, onTurnOff: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val langTag = if (LocalConfiguration.current.locales[0].language == "hi") "hi-IN" else "en-IN"
    val langName = stringResource(if (langTag == "hi-IN") R.string.asst_lang_hi else R.string.asst_lang_en)
    val greeting = stringResource(R.string.asst_greeting)
    val lines = remember { mutableStateListOf(ChatLine(false, greeting)) }
    var input by rememberSaveable { mutableStateOf("") }
    var speak by remember { mutableStateOf(prefs.speakReplies) }
    var listening by remember { mutableStateOf(false) }
    var voiceMissing by remember { mutableStateOf(false) }
    var cardOpen by remember { mutableStateOf(!prefs.ttsCardShown) }
    var pendingCall by remember { mutableStateOf<Command.Call?>(null) }
    val runner = remember(host, apps) { CommandRunner(context, host) { apps } }
    val listState = rememberLazyListState()

    val speaker = remember(langTag) { Speaker(context, langTag) { voiceMissing = true } }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }

    fun reply(r: Reply) {
        lines += ChatLine(false, r.text, r.action)
        if (speak) speaker.speak(r.text)
    }

    fun execute(cmd: Command) {
        scope.launch {
            if (cmd == Command.Boost) lines += ChatLine(false, context.getString(R.string.asst_reply_boosting))
            reply(try { runner.run(cmd) } catch (e: Exception) { Reply(context.getString(R.string.asst_reply_error)) })
        }
    }

    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val cmd = pendingCall
        pendingCall = null
        if (cmd == null) return@rememberLauncherForActivityResult
        if (granted) execute(cmd) else {
            reply(Reply(context.getString(R.string.asst_reply_contacts_denied)))
        }
    }

    fun submit(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        lines += ChatLine(true, t)
        input = ""
        val cmd = CommandParser.parse(t, apps.map { it.label })
        if (cmd is Command.Call && cmd.name != null &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingCall = cmd
            contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
        } else execute(cmd)
    }

    val voice = remember(langTag) {
        VoiceInput(
            context,
            onListening = { listening = it },
            onPartial = { input = it },
            onResult = { submit(it) },
            onFail = {
                reply(
                    Reply(
                        context.getString(
                            when (it) {
                                VoiceError.NO_PACK -> R.string.asst_voice_no_pack
                                VoiceError.NO_SPEECH -> R.string.asst_voice_no_speech
                                VoiceError.DENIED -> R.string.asst_voice_denied
                                VoiceError.OTHER -> R.string.asst_voice_error
                            },
                            langName,
                        ),
                    ),
                )
            },
        )
    }
    DisposableEffect(voice) { onDispose { voice.destroy() } }
    val micAvailable = remember { VoiceInput.isAvailable(context) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { input = ""; voice.start(langTag) } else reply(Reply(context.getString(R.string.asst_voice_denied)))
    }

    LaunchedEffect(lines.size) { listState.animateScrollToItem(lines.lastIndex) }
    // While typing, the mic and turn-off button step aside so the conversation stays visible.
    val typing = WindowInsets.isImeVisible

    Column(Modifier.fillMaxSize().imePadding()) {
        if (!typing) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.asst_speak_replies), Modifier.weight(1f))
            Switch(speak, { speak = it; prefs.speakReplies = it })
        }
        if (voiceMissing && cardOpen && speak) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.asst_tts_card_title, langName), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.asst_tts_card_body))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            prefs.ttsCardShown = true; cardOpen = false
                            runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }) { Text(stringResource(R.string.asst_tts_install)) }
                        TextButton(onClick = { prefs.ttsCardShown = true; cardOpen = false }) { Text(stringResource(R.string.asst_tts_dismiss)) }
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(lines) { _, line -> Bubble(line, host) }
        }
        val chips = listOf(R.string.asst_chip_1, R.string.asst_chip_2, R.string.asst_chip_3, R.string.asst_chip_4, R.string.asst_chip_5, R.string.asst_chip_6)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            itemsIndexed(chips) { _, id ->
                val text = stringResource(id)
                AssistChip(onClick = { submit(text) }, label = { Text(text) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        if (micAvailable && !typing) {
            val micCd = stringResource(if (listening) R.string.asst_mic_stop_cd else R.string.asst_mic_cd)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(80.dp).background(if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable(role = Role.Button) {
                            if (listening) voice.stop()
                            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { input = ""; voice.start(langTag) }
                            else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        .semantics { contentDescription = micCd },
                    contentAlignment = Alignment.Center,
                ) { Text(if (listening) "⏹" else "🎤", fontSize = 36.sp) }
                Text(stringResource(if (listening) R.string.asst_listening else R.string.asst_mic_caption), Modifier.padding(top = 4.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.asst_input_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit(input) }),
            )
            val sendCd = stringResource(R.string.asst_send_cd)
            Button(onClick = { submit(input) }, modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = sendCd }) {
                Text(stringResource(R.string.asst_send))
            }
        }
        if (!typing) TextButton(onClick = onTurnOff, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.asst_turn_off)) }
    }
}

@Composable
private fun Bubble(line: ChatLine, host: AssistantHost) {
    val who = stringResource(if (line.fromUser) R.string.asst_you else R.string.asst_assistant)
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (line.fromUser) colors.primaryContainer else colors.surfaceVariant,
            contentColor = if (line.fromUser) colors.onPrimaryContainer else colors.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp).semantics(mergeDescendants = true) { contentDescription = "$who: ${line.text}" },
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(line.text)
                if (line.action == ReplyAction.POWER_TOOLS) {
                    Button(onClick = host::openPowerTools, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.asst_open_power_tools)) }
                }
            }
        }
    }
}
