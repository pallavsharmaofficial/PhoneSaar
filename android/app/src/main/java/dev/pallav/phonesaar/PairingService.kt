package dev.pallav.phonesaar

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat

// Keeps discovery alive while the person is in Settings, and shows the code field in a notification.
class PairingService : Service() {
    private var nsd: Nsd? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Pairing.ID_LIVE, Pairing.searching(this), type)
        if (nsd == null) {
            Pairing.pairPort = 0
            Pairing.connectPort = 0
            nsd = Nsd(this) { pairing, _, port ->
                if (pairing) {
                    Pairing.pairPort = port
                    getSystemService(android.app.NotificationManager::class.java).notify(Pairing.ID_LIVE, Pairing.prompt(this))
                } else {
                    Pairing.connectPort = port
                }
            }.also { it.start() }
            handler.postDelayed({ stopSelf() }, TIMEOUT_MS)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        nsd?.stop()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val TIMEOUT_MS = 10 * 60 * 1000L

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, PairingService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PairingService::class.java))
        }
    }
}
