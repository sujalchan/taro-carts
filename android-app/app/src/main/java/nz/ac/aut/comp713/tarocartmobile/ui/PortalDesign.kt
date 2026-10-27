package nz.ac.aut.comp713.tarocartmobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import nz.ac.aut.comp713.tarocartmobile.ui.theme.PortalGreenDark
import nz.ac.aut.comp713.tarocartmobile.ui.theme.PortalHero as PortalHeroColor
import nz.ac.aut.comp713.tarocartmobile.ui.theme.PortalHeroEyebrow
import nz.ac.aut.comp713.tarocartmobile.ui.theme.PortalHeroMuted
import nz.ac.aut.comp713.tarocartmobile.ui.theme.PortalHeroText
import nz.ac.aut.comp713.tarocartmobile.ui.theme.PortalLine
import nz.ac.aut.comp713.tarocartmobile.ui.theme.PortalSurface

@Composable
fun PortalBrandTitle(subtitle: String) {
    Column {
        Text("Taro Carts", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PortalHero(eyebrow: String, title: String, description: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier.fillMaxWidth()
            .shadow(8.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(PortalHeroColor, PortalGreenDark)))
            .drawWithCache {
                val lightBloom = Brush.radialGradient(
                    colors = listOf(Color(0xFFD0E6AF).copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.08f),
                    radius = size.width * 0.58f
                )
                val depth = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color(0xFF0B2B28).copy(alpha = 0.34f)),
                    start = Offset(size.width * 0.1f, 0f),
                    end = Offset(size.width, size.height)
                )
                onDrawBehind {
                    drawRect(lightBloom)
                    drawRect(depth)
                }
            }
            .border(1.dp, PortalHeroColor.copy(alpha = 0.65f), shape)
    ) {
        Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = PortalHeroEyebrow, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.headlineSmall, color = PortalHeroText)
            if (description.isNotBlank()) {
                Text(description, style = MaterialTheme.typography.bodyMedium,
                    color = PortalHeroMuted)
            }
        }
    }
}

@Composable
fun PortalPanel(modifier: Modifier = Modifier, itemSpacing: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = PortalSurface,
        border = BorderStroke(1.dp, PortalLine),
        shadowElevation = 3.dp
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(itemSpacing),
            content = content)
    }
}
