package dev.pallav.phonesaar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Brand = Color(0xFF1F8A70)
val Warn = Color(0xFFB3261E)

private val light = lightColorScheme(
    primary = Brand, onPrimary = Color.White, primaryContainer = Color(0xFFD3F0E5), onPrimaryContainer = Color(0xFF00382B),
    secondaryContainer = Color(0xFFBFE8D9), onSecondaryContainer = Color(0xFF00382B),
    background = Color(0xFFF6FAF8), onBackground = Color(0xFF101A17), surface = Color(0xFFF6FAF8), onSurface = Color(0xFF101A17),
    surfaceVariant = Color(0xFFE4EEE9), onSurfaceVariant = Color(0xFF34413C), error = Warn, errorContainer = Color(0xFFFCDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF55635E),
)
private val dark = darkColorScheme(
    primary = Color(0xFF5CCBA8), onPrimary = Color(0xFF00382B), primaryContainer = Color(0xFF0F4F3F), onPrimaryContainer = Color(0xFFC4F2E2),
    secondaryContainer = Color(0xFF1F5A49), onSecondaryContainer = Color(0xFFC4F2E2),
    background = Color(0xFF0E1412), onBackground = Color(0xFFE3ECE8), surface = Color(0xFF0E1412), onSurface = Color(0xFFE3ECE8),
    surfaceVariant = Color(0xFF1F2A26), onSurfaceVariant = Color(0xFFCAD6D1), error = Color(0xFFFFB4AB), errorContainer = Color(0xFF5C1511), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9BA8A3),
)

private val body = TextStyle(fontSize = 18.sp, lineHeight = 26.sp)
private val typo = Typography(
    bodyLarge = body, bodyMedium = body, bodySmall = body,
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), labelMedium = body, labelSmall = body,
    titleLarge = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun PhoneSaarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light, typography = typo, content = content)
}

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleLarge)

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled, shape = RoundedCornerShape(16.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = text, modifier = Modifier.size(24.dp))
            Text(text, Modifier.padding(start = 8.dp))
        } else Text(text)
    }
}

@Composable
fun BigOutlined(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick, modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled, shape = RoundedCornerShape(16.dp)) { Text(text) }
}

@Composable
fun Panel(modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceVariant, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

// A big tappable row with an icon, a title and a one-line explanation.
@Composable
fun ActionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle.isNotBlank()) Hint(subtitle)
            }
        }
    }
}
