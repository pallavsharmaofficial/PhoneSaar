package dev.pallav.phonesaar

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()
    private var tab by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) route(intent) else tab = savedInstanceState.getInt("tab")
        val host = PhoneSaarAssistantHost(this) { tab = 2 }
        enableEdgeToEdge()
        setContent {
            PhoneSaarTheme {
                var welcomed by remember { mutableStateOf(vm.repo.prefs.welcomed) }
                if (!welcomed) {
                    WelcomeScreen { lang ->
                        vm.repo.prefs.welcomed = true
                        welcomed = true
                        Loc.set(this, lang)
                    }
                } else MainShell(vm, host, tab) { tab = it }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route(intent)
    }

    private fun route(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_TAB) == TAB_POWER) tab = 2
    }

    override fun onStart() {
        super.onStart()
        vm.startDiscovery()
    }

    override fun onStop() {
        super.onStop()
        vm.stopDiscovery()
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_POWER = "power"
    }
}
