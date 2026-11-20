package dev.pallav.phonesaar.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.pallav.phonesaar.launcher.design.DesignStore

/** `adb shell am broadcast -a dev.pallav.phonesaar.launcher.RELOAD -p dev.pallav.phonesaar.launcher [--es preset neon]` */
class ReloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DesignStore.init(context)
        val preset = intent.getStringExtra("preset")
        if (preset != null) DesignStore.setPreset(preset) else DesignStore.reload()
    }
}
