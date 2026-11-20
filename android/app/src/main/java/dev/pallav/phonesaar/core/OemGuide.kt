package dev.pallav.phonesaar.core

enum class Oem { Xiaomi, Samsung, Oppo, Vivo, Other }

object OemGuide {
    // Picks which "turn on Developer options" instructions fit this maker.
    fun of(manufacturer: String, brand: String = ""): Oem {
        val m = "${manufacturer.trim()} ${brand.trim()}".lowercase()
        return when {
            listOf("xiaomi", "redmi", "poco").any { it in m } -> Oem.Xiaomi
            "samsung" in m -> Oem.Samsung
            listOf("oppo", "realme", "oneplus").any { it in m } -> Oem.Oppo
            listOf("vivo", "iqoo").any { it in m } -> Oem.Vivo
            else -> Oem.Other
        }
    }
}
