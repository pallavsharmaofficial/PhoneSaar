package dev.pallav.phonesaar

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.pallav.phonesaar.core.BoostPath
import kotlin.concurrent.thread

class BoostTileService : TileService() {
    override fun onStartListening() {
        render(getString(R.string.tile_free, BoostRunner.freeRamPercent(this)), Tile.STATE_INACTIVE)
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        if (BoostRunner.plannedPath(this) == BoostPath.OpenPowerTools) {
            val intent = BoostWidget.openPowerTools(this)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            } else {
                startActivityAndCollapse(intent)
            }
            return
        }
        render(Loc.wrap(this).getString(R.string.widget_boosting), Tile.STATE_ACTIVE)
        thread {
            BoostRunner.run(this)
            BoostWidget.updateAll(this)
            render(Loc.wrap(this).getString(R.string.tile_free, BoostRunner.freeRamPercent(this)), Tile.STATE_INACTIVE)
        }
    }

    private fun render(subtitle: String, state: Int) {
        val tile = qsTile ?: return
        tile.label = Loc.wrap(this).getString(R.string.tile_label)
        tile.state = state
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = subtitle
        tile.updateTile()
    }
}
