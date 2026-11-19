package dev.pallav.phonesaar

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlin.concurrent.thread

class BoostTileService : TileService() {
    override fun onStartListening() {
        render("Free ${BoostRunner.freeRamPercent(this)}%", Tile.STATE_INACTIVE)
    }

    override fun onClick() {
        render("Boosting...", Tile.STATE_ACTIVE)
        thread {
            BoostRunner.run(this)
            BoostWidget.updateAll(this)
            render("Free ${BoostRunner.freeRamPercent(this)}%", Tile.STATE_INACTIVE)
        }
    }

    private fun render(subtitle: String, state: Int) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label)
        tile.state = state
        if (android.os.Build.VERSION.SDK_INT >= 29) tile.subtitle = subtitle
        tile.updateTile()
    }
}
