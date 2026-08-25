package com.hienthai.fastowin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hienthai.fastowin.resources.Res
import com.hienthai.fastowin.resources.arcade_screen_background
import com.hienthai.fastowin.resources.fast_to_win_logo_banner
import com.hienthai.fastowin.ui.theme.ArcadePalette
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Static arcade backdrop: decorative only, so it remains comfortable with reduced motion enabled. */
@Composable
fun ArcadeBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    val isDarkBackground = background.luminance() < 0.5f
    Box(
        modifier = modifier.background(background)
    ) {
        Image(
            painter = painterResource(Res.drawable.arcade_screen_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                if (isDarkBackground) {
                    Color.Black.copy(alpha = 0.18f)
                } else {
                    background.copy(alpha = 0.86f)
                }
            )
        )
        content()
    }
}

@Composable
fun ArcadePanel(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f)),
        shadowElevation = 2.dp,
        content = content
    )
}

@Composable
fun ArcadeBrandLockup(modifier: Modifier = Modifier, compact: Boolean = false) {
    val shape = RoundedCornerShape(if (compact) 14.dp else 22.dp)
    Image(
        painter = painterResource(Res.drawable.fast_to_win_logo_banner),
        contentDescription = "Fast To Win",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .widthIn(max = if (compact) 240.dp else 420.dp)
            .fillMaxWidth()
            .aspectRatio(3f)
            .clip(shape)
            .border(2.dp, ArcadePalette.Blue300.copy(alpha = 0.7f), shape)
    )
}

@Composable
fun ArcadeHeaderLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.fast_to_win_logo_banner),
        contentDescription = "Fast To Win",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .width(116.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
    )
}

@Composable
fun ArcadeFeatureHero(
    illustration: DrawableResource,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onIllustrationClick: (() -> Unit)? = null,
    illustrationContentDescription: String? = null
) {
    ArcadePanel(modifier = modifier.fillMaxWidth(), accent = accent) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val stackVertically = maxWidth < 340.dp || (maxWidth < 520.dp && fontScale >= 1.3f)

            if (stackVertically) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ArcadeFeatureHeroText(
                        title = title,
                        subtitle = subtitle,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ArcadeFeatureHeroIllustration(
                        illustration = illustration,
                        onClick = onIllustrationClick,
                        contentDescription = illustrationContentDescription,
                        modifier = Modifier.size(118.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp)
                        .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArcadeFeatureHeroText(
                        title = title,
                        subtitle = subtitle,
                        modifier = Modifier.weight(1f)
                    )
                    ArcadeFeatureHeroIllustration(
                        illustration = illustration,
                        onClick = onIllustrationClick,
                        contentDescription = illustrationContentDescription,
                        modifier = Modifier.size(142.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ArcadeFeatureHeroText(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArcadeFeatureHeroIllustration(
    illustration: DrawableResource,
    onClick: (() -> Unit)?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val interactionModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Image(
        painter = painterResource(illustration),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.then(interactionModifier)
    )
}

@Composable
fun ArcadeEmptyState(
    illustration: DrawableResource,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Image(
            painter = painterResource(illustration),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(148.dp)
        )
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ArcadeRankBadge(rank: Int, modifier: Modifier = Modifier) {
    val color = when (rank) {
        1 -> ArcadePalette.Gold400
        2 -> Color(0xFFC8D4EA)
        3 -> Color(0xFFE59655)
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (rank in 1..3) ArcadePalette.Navy950 else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = color,
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.48f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                if (rank in 1..3) rank.toString() else "#$rank",
                color = contentColor,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
