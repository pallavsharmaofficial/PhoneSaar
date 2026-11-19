package dev.pallav.phonesaar.core

import java.io.File

object Fixtures {
    private val root = File("../../rules")

    fun text(name: String): String = File(root, "fixtures/$name").readText()

    val rules: Rules by lazy { Rules.parse(File(root, "rules.json").readText()) }
}
