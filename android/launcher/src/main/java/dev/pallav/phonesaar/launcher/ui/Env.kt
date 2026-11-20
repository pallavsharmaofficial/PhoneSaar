package dev.pallav.phonesaar.launcher.ui

import dev.pallav.phonesaar.launcher.NotifItem
import dev.pallav.phonesaar.launcher.data.AppInfo
import dev.pallav.phonesaar.launcher.design.GestureAction

/** Everything a block can ask the screen to do. */
class Env(
    val launch: (AppInfo) -> Unit = {},
    val appMenu: (AppInfo) -> Unit = {},
    val openDrawer: (String?) -> Unit = {},
    val run: (String) -> Unit = {},
    val voice: () -> Unit = {},
    val speak: (String) -> Unit = {},
    val pickPhoto: () -> Unit = {},
    val openGoogle: () -> Unit = {},
    val openCalendar: () -> Unit = {},
    val openCustomize: () -> Unit = {},
    val requestPermission: (String) -> Unit = {},
    val openUsageSettings: () -> Unit = {},
    val openNotifSettings: () -> Unit = {},
    val editBlock: (String) -> Unit = {},
    val gesture: (GestureAction) -> Unit = {},
    val sendReply: (NotifItem, String) -> Unit = { _, _ -> },
)
