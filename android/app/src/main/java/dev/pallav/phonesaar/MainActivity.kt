package dev.pallav.phonesaar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val tabs = listOf("Connect", "Report", "Boost", "Battery", "Debloat", "Fix-ups", "Undo")

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1F8A70))) {
                Surface(Modifier.fillMaxSize()) {
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    Scaffold { pad ->
                        Column(Modifier.padding(pad)) {
                            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                                tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
                            }
                            when (tab) {
                                0 -> ConnectScreen(vm)
                                1 -> ReportScreen(vm)
                                2 -> BoostScreen(vm)
                                3 -> BatteryScreen(vm)
                                4 -> DebloatScreen(vm)
                                5 -> FixupsScreen(vm)
                                else -> UndoScreen(vm)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.startDiscovery()
    }

    override fun onStop() {
        super.onStop()
        vm.stopDiscovery()
    }
}
