package dev.pallav.phonesaar

import androidx.compose.foundation.layout.consumeWindowInsets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pallav.phonesaar.assistant.AssistantHost
import dev.pallav.phonesaar.assistant.AssistantScreen

@Composable
fun WelcomeScreen(onPick: (String) -> Unit) {
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, androidx.compose.ui.Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.app_name), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.welcome_line))
            BigButton("हिन्दी", { onPick("hi") })
            BigButton("English", { onPick("en") })
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var lang by remember { mutableStateOf(Prefs(ctx).language) }
    BackHandler(onBack = onBack)
    Scaffold { pad ->
        Column(Modifier.padding(pad)) {
            Page {
                Title(stringResource(R.string.settings_title))
                Text(stringResource(R.string.language), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                BigOutlined("हिन्दी" + if (lang == "hi") "  ✓" else "", { lang = "hi"; Loc.set(ctx, "hi") })
                BigOutlined("English" + if (lang == "en") "  ✓" else "", { lang = "en"; Loc.set(ctx, "en") })
                Text(stringResource(R.string.about), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
                Hint(stringResource(R.string.about_body, version))
                BigOutlined(stringResource(R.string.privacy), { SystemIntents.open(ctx, SystemIntents.url(PRIVACY_URL)) })
                BigButton(stringResource(R.string.back), onBack)
            }
        }
    }
}

private const val PRIVACY_URL = "https://pallavsharmaofficial.github.io/PhoneSaar/privacy.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(vm: AppViewModel, host: AssistantHost, tab: Int, onTab: (Int) -> Unit) {
    var settings by rememberSaveable { mutableStateOf(false) }
    if (settings) {
        SettingsScreen { settings = false }
        return
    }
    val titles = listOf(R.string.nav_home, R.string.nav_assistant, R.string.nav_power)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titles[tab])) },
                actions = {
                    IconButton({ settings = true }, Modifier.padding(4.dp)) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant) {
                listOf(Icons.Filled.Home to R.string.nav_home, Icons.Filled.Face to R.string.nav_assistant, Icons.Filled.Build to R.string.nav_power)
                    .forEachIndexed { i, (icon, label) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { onTab(i) },
                            icon = { Icon(icon, contentDescription = stringResource(label)) },
                            label = { Text(stringResource(label)) },
                        )
                    }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).consumeWindowInsets(pad)) {
            when (tab) {
                0 -> HomeScreen(vm) { onTab(2) }
                1 -> AssistantScreen(host, Modifier.fillMaxSize())
                else -> PowerToolsScreen(vm)
            }
        }
    }
}
