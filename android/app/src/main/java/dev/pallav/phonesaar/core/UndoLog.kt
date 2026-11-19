package dev.pallav.phonesaar.core

import org.json.JSONArray
import org.json.JSONObject

data class UndoEntry(
    val time: Long,
    val serial: String,
    val label: String,
    val doCommand: String,
    val undoCommand: String,
)

class UndoLog(entries: List<UndoEntry> = emptyList()) {
    private val items = entries.toMutableList()

    val entries: List<UndoEntry> get() = items.toList()

    fun add(entry: UndoEntry) {
        items.add(entry)
    }

    fun remove(entry: UndoEntry) {
        items.remove(entry)
    }

    fun clear() = items.clear()

    fun forSerial(serial: String): List<UndoEntry> = items.filter { it.serial == serial }

    fun toJson(): String {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("time", it.time).put("serial", it.serial).put("label", it.label)
                    .put("do", it.doCommand).put("undo", it.undoCommand),
            )
        }
        return arr.toString()
    }

    companion object {
        fun fromJson(json: String): UndoLog {
            if (json.isBlank()) return UndoLog()
            val arr = JSONArray(json)
            return UndoLog(
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    UndoEntry(o.getLong("time"), o.getString("serial"), o.getString("label"), o.getString("do"), o.getString("undo"))
                },
            )
        }
    }
}
