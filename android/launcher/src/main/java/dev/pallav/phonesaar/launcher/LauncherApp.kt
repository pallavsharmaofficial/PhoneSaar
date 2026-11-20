package dev.pallav.phonesaar.launcher

import android.app.Application
import dev.pallav.phonesaar.launcher.data.AppRepo
import dev.pallav.phonesaar.launcher.design.DesignStore

class LauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DesignStore.init(this)
        AppRepo.init(this)
        WeatherStore.load(Prefs(this))
    }
}
