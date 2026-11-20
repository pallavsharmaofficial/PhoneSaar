package dev.pallav.phonesaar.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.StringRes
import dev.pallav.phonesaar.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

enum class ReplyAction { POWER_TOOLS }

data class Reply(val text: String, val action: ReplyAction? = null)

/** Carries out a parsed Command on the device and words the answer in the active language. */
class CommandRunner(
    private val context: Context,
    private val host: AssistantHost,
    private val apps: () -> List<AppEntry>,
) {
    private val locale: Locale get() = context.resources.configuration.locales[0]

    private fun s(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    suspend fun run(cmd: Command): Reply = when (cmd) {
        is Command.OpenApp -> openApp(cmd)
        is Command.Call -> call(cmd)
        is Command.Torch -> torch(cmd.on)
        is Command.Alarm -> alarm(cmd)
        is Command.Timer -> timer(cmd.seconds)
        is Command.Settings -> settings(cmd.page)
        Command.Health -> Reply(host.healthSummary())
        Command.Boost -> boost()
        Command.Camera -> camera()
        Command.Time -> Reply(s(R.string.asst_reply_time, DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date())))
        Command.Date -> Reply(s(R.string.asst_reply_date, DateFormat.getDateInstance(DateFormat.FULL, locale).format(Date())))
        Command.Help -> Reply(s(R.string.asst_help_body))
        is Command.Unknown -> Reply(
            s(R.string.asst_reply_unknown) + listOf(R.string.asst_chip_1, R.string.asst_chip_2, R.string.asst_chip_5)
                .joinToString("") { "\n• " + s(it) },
        )
    }

    private fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    private fun openApp(cmd: Command.OpenApp): Reply {
        val label = cmd.label ?: return Reply(s(R.string.asst_reply_app_missing, cmd.query))
        val pkg = apps().firstOrNull { it.label == label }?.packageName
        val intent = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
        return if (intent != null && launch(intent)) Reply(s(R.string.asst_reply_opening, label))
        else Reply(s(R.string.asst_reply_app_failed, label))
    }

    private fun dial(number: String?): Boolean =
        launch(Intent(Intent.ACTION_DIAL, number?.let { Uri.fromParts("tel", it, null) }))

    private suspend fun call(cmd: Command.Call): Reply {
        if (cmd.number != null) {
            dial(cmd.number)
            return Reply(s(R.string.asst_reply_dialer_number, cmd.number))
        }
        val name = cmd.name
        if (name == null) {
            dial(null)
            return Reply(s(R.string.asst_reply_call_who))
        }
        val hit = findContact(name) ?: return Reply(s(R.string.asst_reply_contact_missing, name))
        dial(hit.second)
        return Reply(s(R.string.asst_reply_dialer_name, hit.first, hit.second))
    }

    /** Finds (display name, number) for a spoken name; needs READ_CONTACTS to be granted. */
    private suspend fun findContact(name: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val rows = mutableListOf<Pair<String, String>>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext() && rows.size < 5000) rows += (c.getString(0) ?: "") to (c.getString(1) ?: "")
            }
            val q = name.lowercase(Locale.ROOT)
            rows.firstOrNull { it.first.lowercase(Locale.ROOT) == q && it.second.isNotBlank() }
                ?: rows.firstOrNull { it.first.lowercase(Locale.ROOT).contains(q) && it.second.isNotBlank() }
                ?: rows.firstOrNull { Phonetics.matches(it.first, name) && it.second.isNotBlank() }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun torch(on: Boolean): Reply = try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        if (id == null) Reply(s(R.string.asst_reply_torch_fail)) else {
            cm.setTorchMode(id, on)
            Reply(s(if (on) R.string.asst_reply_torch_on else R.string.asst_reply_torch_off))
        }
    } catch (e: Exception) {
        Reply(s(R.string.asst_reply_torch_fail))
    }

    private fun alarm(cmd: Command.Alarm): Reply {
        val h = cmd.hour
        if (h == null) {
            return if (launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))) Reply(s(R.string.asst_reply_alarm_open))
            else Reply(s(R.string.asst_reply_alarm_fail))
        }
        val m = cmd.minute ?: 0
        fun intent(skipUi: Boolean) = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m).putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
        val ok = launch(intent(true)) || launch(intent(false))
        val shown = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(
            java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, h); set(java.util.Calendar.MINUTE, m) }.time,
        )
        return Reply(if (ok) s(R.string.asst_reply_alarm_set, shown) else s(R.string.asst_reply_alarm_fail))
    }

    private fun timer(seconds: Int): Reply {
        fun intent(skipUi: Boolean) = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
        val ok = launch(intent(true)) || launch(intent(false))
        return Reply(if (ok) s(R.string.asst_reply_timer_set, durationText(seconds)) else s(R.string.asst_reply_timer_fail))
    }

    private fun durationText(total: Int): String {
        val parts = mutableListOf<String>()
        if (total / 3600 > 0) parts += s(R.string.asst_dur_h, total / 3600)
        if (total % 3600 / 60 > 0) parts += s(R.string.asst_dur_m, total % 3600 / 60)
        if (total % 60 > 0) parts += s(R.string.asst_dur_s, total % 60)
        return parts.joinToString(" ")
    }

    private fun settings(page: SettingsPage): Reply {
        val (action, nameRes) = when (page) {
            SettingsPage.MAIN -> Settings.ACTION_SETTINGS to R.string.asst_set_main
            SettingsPage.WIFI -> Settings.ACTION_WIFI_SETTINGS to R.string.asst_set_wifi
            SettingsPage.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS to R.string.asst_set_bluetooth
            SettingsPage.DATA -> Settings.ACTION_DATA_ROAMING_SETTINGS to R.string.asst_set_data
            SettingsPage.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS to R.string.asst_set_display
            SettingsPage.SOUND -> Settings.ACTION_SOUND_SETTINGS to R.string.asst_set_sound
            SettingsPage.LOCATION -> Settings.ACTION_LOCATION_SOURCE_SETTINGS to R.string.asst_set_location
            SettingsPage.BATTERY -> Settings.ACTION_BATTERY_SAVER_SETTINGS to R.string.asst_set_battery
            SettingsPage.STORAGE -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS to R.string.asst_set_storage
        }
        val ok = launch(Intent(action)) || launch(Intent(Settings.ACTION_SETTINGS))
        return Reply(if (ok) s(R.string.asst_reply_settings, s(nameRes)) else s(R.string.asst_reply_settings_fail))
    }

    private fun camera(): Reply =
        if (launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) Reply(s(R.string.asst_reply_camera))
        else Reply(s(R.string.asst_reply_camera_fail))

    private suspend fun boost(): Reply {
        val text = host.boost()
        val needsSetup = text.contains("setup", true) || text.contains("set up", true) || text.contains("सेटअप")
        return Reply(text, if (needsSetup) ReplyAction.POWER_TOOLS else null)
    }
}
