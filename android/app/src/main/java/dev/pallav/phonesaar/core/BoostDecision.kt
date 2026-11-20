package dev.pallav.phonesaar.core

enum class BoostPath { Deep, Quick, OpenPowerTools }

object BoostDecision {
    // Since Android 14 an app can only kill its own processes, so a quick boost is real only up to 13.
    const val QUICK_MAX_SDK = 33

    fun decide(adbReachable: Boolean, canReenable: Boolean, sdk: Int): BoostPath = when {
        adbReachable || canReenable -> BoostPath.Deep
        sdk <= QUICK_MAX_SDK -> BoostPath.Quick
        else -> BoostPath.OpenPowerTools
    }
}
