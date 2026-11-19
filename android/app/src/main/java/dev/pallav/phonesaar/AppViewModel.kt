package dev.pallav.phonesaar

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pallav.phonesaar.core.CatalogEntry
import dev.pallav.phonesaar.core.UndoEntry
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = PhoneSaarApp.repo(app)
    private val nsd = Nsd(app) { pairing, host, port ->
        if (pairing) {
            pairPort = port.toString()
            if (target != TargetKind.Self) host_ = host
        } else {
            connectPort = port.toString()
        }
    }

    var target by mutableStateOf(TargetKind.Self)
    var host_ by mutableStateOf("")
    var pairPort by mutableStateOf("")
    var connectPort by mutableStateOf("")
    var code by mutableStateOf("")
    var connected by mutableStateOf(repo.connected)
    var status by mutableStateOf("")
    var busy by mutableStateOf(false)
    var before by mutableStateOf<Report?>(null)
    var after by mutableStateOf<Report?>(null)
    var results by mutableStateOf<List<ExecResult>>(emptyList())
    var drainers by mutableStateOf<List<Drainer>?>(null)
    var debloat by mutableStateOf<List<DebloatItem>?>(null)
    var packages by mutableStateOf<List<String>>(emptyList())
    var undo by mutableStateOf<List<UndoEntry>>(emptyList())
    var devices by mutableStateOf(repo.prefs.devices)
    var trimCaches by mutableStateOf(repo.prefs.trimCaches)
    var animation by mutableStateOf(repo.prefs.animation)

    fun startDiscovery() = nsd.start()

    fun stopDiscovery() = nsd.stop()

    fun setTrim(v: Boolean) {
        trimCaches = v
        repo.prefs.trimCaches = v
    }

    fun setAnim(v: String) {
        animation = v
        repo.prefs.animation = v
    }

    private fun work(block: () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    fun connect() = work {
        val host = if (target == TargetKind.Self) "127.0.0.1" else host_.trim()
        try {
            if (target != TargetKind.Legacy && code.isNotBlank()) {
                status = "Pairing..."
                val port = pairPort.toIntOrNull() ?: throw IllegalArgumentException("Type the pairing port shown on the phone.")
                if (!repo.pair(host, port, code.trim())) throw IllegalStateException("Pairing did not work. Check the code and try again.")
            }
            status = "Connecting..."
            val port = if (target == TargetKind.Legacy) connectPort.toIntOrNull() ?: 5555 else
                connectPort.toIntOrNull() ?: throw IllegalArgumentException("Type the connection port shown on the phone.")
            connected = repo.connect(host, port)
            if (!connected) throw IllegalStateException("Could not connect.")
            if (target == TargetKind.Self) {
                repo.prefs.selfHost = host
                repo.prefs.selfPort = port
            }
            val device = SavedDevice("${repo.manufacturer} ${repo.model}".trim(), host, port, target)
            repo.prefs.devices = (listOf(device) + repo.prefs.devices.filter { it.name != device.name || it.host != host }).take(8)
            devices = repo.prefs.devices
            status = "Connected to ${device.name}."
            before = repo.report()
            after = null
        } catch (e: AdbPairingRequiredException) {
            status = "This phone needs pairing first. Turn on Pair with code and enter the code."
        } catch (e: Exception) {
            status = e.message ?: "Could not connect."
        }
    }

    fun disconnect() = work {
        repo.disconnect()
        connected = false
        status = "Disconnected."
    }

    fun useDevice(d: SavedDevice) {
        target = d.kind
        host_ = d.host
        connectPort = d.port.toString()
        code = ""
    }

    // Runs actions, shows the real output, and captures "after" for the side-by-side report.
    private fun act(block: () -> List<ExecResult>) = work {
        results = block()
        after = repo.report()
        refreshUndo()
    }

    fun refreshReport() = work { before = repo.report(); after = null }

    fun boost() = act { repo.boost() }

    fun loadDrainers() = work { drainers = repo.drainers() }

    fun restrict(pkg: String) = act { listOf(repo.restrictBackground(pkg)) }

    fun loadDebloat() = work { debloat = repo.debloatItems() }

    fun disable(entry: CatalogEntry) = work {
        results = listOf(repo.disable(entry))
        after = repo.report()
        debloat = repo.debloatItems()
        refreshUndo()
    }

    fun loadPackages() = work { packages = repo.thirdPartyPackages() }

    fun fixLocation() = act { listOf(repo.turnLocationOn()) }

    fun fixAnimations() = act { listOf(repo.setAnimations(animation)) }

    fun fixTouch() = act { listOf(repo.shortenTouchAndHold()) }

    fun grant(pkg: String, permission: String) = act { listOf(repo.grant(pkg, permission)) }

    fun stopRevoke(pkg: String) = act { listOf(repo.stopAutoRevoke(pkg)) }

    fun refreshUndo() {
        undo = repo.undoEntries
    }

    fun undoOne(e: UndoEntry) = work { results = listOf(repo.undo(e)); refreshUndo() }

    fun undoAll() = work { results = repo.undoAll(); refreshUndo() }
}
