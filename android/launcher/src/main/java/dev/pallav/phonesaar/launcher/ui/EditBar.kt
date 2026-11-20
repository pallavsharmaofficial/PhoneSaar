package dev.pallav.phonesaar.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pallav.phonesaar.launcher.design.BlockSpecs
import dev.pallav.phonesaar.launcher.design.DesignState
import dev.pallav.phonesaar.launcher.design.DesignStore
import dev.pallav.phonesaar.launcher.design.LayoutMode
import dev.pallav.phonesaar.launcher.design.Reorder

@Composable
private fun Pill(text: String, enabled: Boolean = true, onClick: () -> Unit) =
    Text(text, Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF1F1F23)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp), color = Color.White.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 13.sp)

/** The toolbar under the home screen while editing: resize, reorder, hide, settings, add, style, undo/redo. */
@Composable
fun EditBar(st: DesignState, selected: Int, onSelect: (Int) -> Unit, onSettings: () -> Unit, onStyle: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val l = st.design.layout
    val b = l.blocks.getOrNull(selected)
    var adding by remember { mutableStateOf(false) }
    fun upd(f: (dev.pallav.phonesaar.launcher.design.Block) -> dev.pallav.phonesaar.launcher.design.Block) =
        DesignStore.edit { d -> d.copy(layout = d.layout.copy(blocks = d.layout.blocks.mapIndexed { i, x -> if (i == selected) f(x) else x })) }
    Column(modifier.fillMaxWidth().background(Color(0xE6101012)).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (b != null) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(b.type.uppercase(), Modifier.padding(vertical = 9.dp), color = Color(0xFF8AB4FF), fontSize = 12.sp)
            if (l.mode == LayoutMode.GRID) {
                Pill(tr("Wider/narrower", "चौड़ा/पतला")) { upd { it.copy(colSpan = if (it.colSpan >= l.columns) 1 else it.colSpan + 1, col = if (it.colSpan + 1 >= l.columns) -1 else it.col) } }
                Pill(tr("Taller", "ऊँचा")) { upd { it.copy(rowSpan = (it.rowSpan + 1).coerceAtMost(8)) } }
                Pill(tr("Shorter", "छोटा")) { upd { it.copy(rowSpan = (it.rowSpan - 1).coerceAtLeast(1)) } }
            } else {
                Pill(tr("Bigger", "बड़ा")) { upd { it.copy(weight = it.weight + 0.5f) } }
                Pill(tr("Smaller", "छोटा")) { upd { it.copy(weight = (it.weight - 0.5f).coerceAtLeast(0f)) } }
            }
            Pill(tr("Earlier", "पहले")) { DesignStore.edit { d -> d.copy(layout = d.layout.copy(blocks = Reorder.swap(d.layout.blocks, selected, -1))) }; onSelect((selected - 1).coerceAtLeast(0)) }
            Pill(tr("Later", "बाद में")) { DesignStore.edit { d -> d.copy(layout = d.layout.copy(blocks = Reorder.swap(d.layout.blocks, selected, 1))) }; onSelect((selected + 1).coerceAtMost(l.blocks.size - 1)) }
            Pill(if (b.hidden) tr("Show", "दिखाएँ") else tr("Hide", "छिपाएँ")) { upd { it.copy(hidden = !it.hidden) } }
            Pill(tr("Settings", "सेटिंग"), onClick = onSettings)
            Pill(tr("Delete", "हटाएँ")) { DesignStore.edit { d -> d.copy(layout = d.layout.copy(blocks = d.layout.blocks.filterIndexed { i, _ -> i != selected })) }; onSelect(-1) }
        } else Text(tr("Tap a block to select it, drag it to move it.", "ब्लॉक चुनने को टैप करें, खिसकाने को ड्रैग करें।"), Modifier.padding(horizontal = 14.dp), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(tr("Add block", "ब्लॉक जोड़ें")) { adding = true }
            Pill(tr("Style", "स्टाइल"), onClick = onStyle)
            Pill(tr("Undo", "पीछे"), st.canUndo) { DesignStore.undo() }
            Pill(tr("Redo", "आगे"), st.canRedo) { DesignStore.redo() }
            Pill(tr("Done", "हो गया"), onClick = onDone)
        }
    }
    if (adding) AlertDialog(onDismissRequest = { adding = false }, confirmButton = { TextButton({ adding = false }) { Text(tr("Cancel", "रद्द")) } }, title = { Text(tr("Add a block", "ब्लॉक जोड़ें")) }, text = {
        Column {
            for (t in BlockSpecs.types) Text(t, Modifier.fillMaxWidth().clickable {
                DesignStore.edit { d -> d.copy(layout = d.layout.copy(blocks = d.layout.blocks + BlockSpecs.make(t))) }
                onSelect(l.blocks.size); adding = false
            }.padding(vertical = 8.dp))
        }
    })
}
