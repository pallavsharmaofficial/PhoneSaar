package dev.pallav.phonesaar.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import dev.pallav.phonesaar.assistant.Command
import dev.pallav.phonesaar.assistant.Phonetics
import dev.pallav.phonesaar.assistant.SettingsPage
import dev.pallav.phonesaar.launcher.data.AppRepo
import dev.pallav.phonesaar.launcher.design.DesignStore
import dev.pallav.phonesaar.launcher.voice.LCommand
import dev.pallav.phonesaar.launcher.voice.LauncherCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** What a command wants the screen to do beyond speaking its answer. */
data class Outcome(val reply: String, val filter: String? = null, val askReplyText: NotifItem? = null)

/** Carries out typed or spoken text: launcher commands first, then the shared assistant commands. */
class Runner(private val context: Context, private val prefs: Prefs, private val lang: () -> String) {
    private fun t(en: String, hi: String) = if (lang() == "hi") hi else en
    private val locale get() = Locale.forLanguageTag(if (lang() == "hi") "hi-IN" else "en-IN")

    suspend fun handle(text: String): Outcome {
        val apps = AppRepo.visibleApps(prefs)
        val cmd = LauncherCommands.parse(text, apps.map { it.label }, DesignStore.presets)
        return when (cmd) {
            is LCommand.Filter -> Outcome("", filter = cmd.query)
            is LCommand.SetTheme -> { DesignStore.setPreset(cmd.preset); Outcome(t("Theme ${cmd.preset.replace('-', ' ')} applied", "${cmd.preset.replace('-', ' ')} थीम लगा दी")) }
            LCommand.ReadNotifications -> readNotifications()
            is LCommand.Reply -> reply(cmd)
            is LCommand.Base -> base(cmd.command)
        }
    }

    private fun readNotifications(): Outcome {
        if (!NotifStore.enabled(context)) return Outcome(t("Allow notification access first", "पहले नोटिफिकेशन एक्सेस दें"))
        val items = NotifStore.items.value.filter { !it.ongoing }.take(5)
        if (items.isEmpty()) return Outcome(t("No notifications", "कोई नोटिफिकेशन नहीं है"))
        return Outcome(items.joinToString(". ") { "${it.appLabel}. ${it.title}. ${it.text}" })
    }

    private fun reply(cmd: LCommand.Reply): Outcome {
        val q = cmd.appQuery
        val item = NotifStore.items.value.firstOrNull { q.isBlank() || Phonetics.matches(it.appLabel, q) || it.pkg.contains(q) }
            ?: return Outcome(t("No notification from $q", "$q का कोई नोटिफिकेशन नहीं है"))
        val action = NotifStore.replyAction(item) ?: return Outcome(t("${item.appLabel} cannot be replied to here", "${item.appLabel} में यहाँ जवाब नहीं दे सकते"))
        val msg = cmd.text ?: return Outcome(t("What should I say to ${item.appLabel}?", "${item.appLabel} को क्या जवाब दूँ?"), askReplyText = item)
        return Outcome(if (NotifStore.reply(context, action, msg)) t("Replied on ${item.appLabel}", "${item.appLabel} पर जवाब भेज दिया") else t("Could not send the reply", "जवाब नहीं भेज पाया"))
    }

    private fun launch(intent: Intent): Boolean = try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }

    private suspend fun base(c: Command): Outcome = when (c) {
        is Command.OpenApp -> {
            val a = AppRepo.visibleApps(prefs).firstOrNull { it.label == c.label }
            if (a != null) { AppRepo.launch(context, a); Outcome(t("Opening ${a.label}", "${a.label} खोल रहा हूँ")) }
            else Outcome("", filter = c.query)
        }
        is Command.Call -> call(c)
        is Command.Torch -> torch(c.on)
        is Command.Alarm -> alarm(c)
        is Command.Timer -> {
            val ok = launch(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, c.seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, true)) ||
                launch(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, c.seconds))
            Outcome(if (ok) t("Timer set for ${c.seconds / 60} min ${c.seconds % 60} sec", "टाइमर लगा दिया") else t("No clock app for timers", "टाइमर वाला ऐप नहीं मिला"))
        }
        is Command.Settings -> settings(c.page)
        Command.Camera -> Outcome(if (launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) t("Opening the camera", "कैमरा खोल रहा हूँ") else t("No camera app found", "कैमरा ऐप नहीं मिला"))
        Command.Time -> Outcome(t("It is ", "अभी ") + DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date()) + t("", " बजे हैं"))
        Command.Date -> Outcome(DateFormat.getDateInstance(DateFormat.FULL, locale).format(Date()))
        else -> Outcome("", filter = "")
    }

    private suspend fun call(c: Command.Call): Outcome {
        val number = c.number
        if (number != null) { launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))); return Outcome(t("Dialer ready for $number", "$number डायल करने को तैयार")) }
        val name = c.name ?: run { launch(Intent(Intent.ACTION_DIAL)); return Outcome(t("Who should I call?", "किसे कॉल करूँ?")) }
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            launch(Intent(Intent.ACTION_DIAL)); return Outcome(t("Allow contacts to call by name", "नाम से कॉल के लिए कॉन्टैक्ट की अनुमति दें"))
        }
        val hit = withContext(Dispatchers.IO) {
            runCatching {
                val rows = mutableListOf<Pair<String, String>>()
                context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cu ->
                    while (cu.moveToNext() && rows.size < 5000) rows += (cu.getString(0) ?: "") to (cu.getString(1) ?: "")
                }
                rows.firstOrNull { it.first.equals(name, true) && it.second.isNotBlank() } ?: rows.firstOrNull { Phonetics.matches(it.first, name) && it.second.isNotBlank() }
            }.getOrNull()
        } ?: return Outcome(t("No contact called $name", "$name नाम का कॉन्टैक्ट नहीं मिला"))
        launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", hit.second, null)))
        return Outcome(t("Calling ${hit.first}", "${hit.first} को कॉल लगा रहा हूँ"))
    }

    private fun torch(on: Boolean): Outcome = try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        if (id == null) Outcome(t("No torch on this phone", "इस फोन में टॉर्च नहीं है")) else { cm.setTorchMode(id, on); Outcome(if (on) t("Torch on", "टॉर्च चालू") else t("Torch off", "टॉर्च बंद")) }
    } catch (e: Exception) { Outcome(t("Could not use the torch", "टॉर्च नहीं चली")) }

    private fun alarm(c: Command.Alarm): Outcome {
        val h = c.hour ?: return Outcome(if (launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))) t("Opening alarms", "अलार्म खोल रहा हूँ") else t("No clock app found", "घड़ी ऐप नहीं मिला"))
        val m = c.minute ?: 0
        fun intent(skip: Boolean) = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m).putExtra(AlarmClock.EXTRA_SKIP_UI, skip)
        val ok = launch(intent(true)) || launch(intent(false))
        val shown = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }.time)
        return Outcome(if (ok) t("Alarm set for $shown", "$shown का अलार्म लगा दिया") else t("Could not set the alarm", "अलार्म नहीं लगा"))
    }

    private fun settings(p: SettingsPage): Outcome {
        val action = when (p) {
            SettingsPage.MAIN -> Settings.ACTION_SETTINGS
            SettingsPage.WIFI -> Settings.ACTION_WIFI_SETTINGS
            SettingsPage.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            SettingsPage.DATA -> Settings.ACTION_DATA_ROAMING_SETTINGS
            SettingsPage.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
            SettingsPage.SOUND -> Settings.ACTION_SOUND_SETTINGS
            SettingsPage.LOCATION -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            SettingsPage.BATTERY -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            SettingsPage.STORAGE -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
        }
        val ok = launch(Intent(action)) || launch(Intent(Settings.ACTION_SETTINGS))
        return Outcome(if (ok) t("Opening settings", "सेटिंग खोल रहा हूँ") else t("Could not open settings", "सेटिंग नहीं खुली"))
    }
}
