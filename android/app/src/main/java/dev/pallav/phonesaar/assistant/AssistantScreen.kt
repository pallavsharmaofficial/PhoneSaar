package dev.pallav.phonesaar.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Stub replaced at merge by the real assistant.
@Composable
fun AssistantScreen(host: AssistantHost, modifier: Modifier = Modifier) {
    var line by remember { mutableStateOf(host.healthSummary()) }
    val scope = rememberCoroutineScope()
    Column(modifier.padding(16.dp)) {
        Text(line)
        Button({ scope.launch { line = host.boost() } }) { Text("Boost") }
    }
}
