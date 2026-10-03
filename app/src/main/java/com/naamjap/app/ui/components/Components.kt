package com.naamjap.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naamjap.app.navigation.Destination
import com.naamjap.app.R
import com.naamjap.app.ui.theme.JapSpacing

@Composable
fun PremiumScaffold(bottomBar: @Composable () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = bottomBar, content = content)
}

@Composable
fun LotusMark(modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(
        painter = painterResource(R.drawable.ic_golden_lotus),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
fun LotusOutlineMark(modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(painterResource(R.drawable.ic_lotus_outline), contentDescription, modifier, contentScale = ContentScale.Fit)
}

@Composable
fun NaamJapLogo(modifier: Modifier = Modifier, contentDescription: String? = "Naam Jap") {
    Image(painterResource(R.drawable.ic_naam_jap_logo), contentDescription, modifier, contentScale = ContentScale.Fit)
}

@Composable
fun OmSymbol(modifier: Modifier = Modifier, contentDescription: String? = "Om") {
    Image(painterResource(R.drawable.ic_om_symbol), contentDescription, modifier, contentScale = ContentScale.Fit)
}

@Composable
fun OrnamentalDivider(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_ornamental_divider),
        contentDescription = null,
        modifier = modifier.alpha(.72f),
        contentScale = ContentScale.Fit
    )
}

@Composable
fun GlassSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f)),
        shadowElevation = 3.dp
    ) { Column(content = content) }
}

@Composable
fun PremiumCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = GlassSurface(modifier, content)

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (action != null) Text(action, modifier = Modifier.heightIn(min = 48.dp).clickable(enabled = onAction != null, onClick = { onAction?.invoke() }).padding(horizontal = 8.dp).wrapContentHeight(Alignment.CenterVertically), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun DailyCountDisplay(count: String, supportingLabel: String, preview: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(supportingLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (preview) Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) { Text("SAMPLE PREVIEW", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        }
        Text(count, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun JapCircularProgressIndicator(progress: Float, modifier: Modifier = Modifier, strokeWidth: androidx.compose.ui.unit.Dp = 8.dp, content: @Composable BoxScope.() -> Unit) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "circular progress")
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    Box(modifier.semantics { contentDescription = "${(animated * 100).toInt()} percent complete" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(trackColor, -90f, 360f, false, style = stroke)
            drawArc(progressColor, -90f, 360f * animated, false, style = stroke)
        }
        content()
    }
}

@Composable
fun GoalProgressCard(goal: String, percent: Int, remaining: String, progress: Float, modifier: Modifier = Modifier, showPercent: Boolean = true) {
    PremiumCard(modifier) {
        Column(Modifier.padding(JapSpacing.md), verticalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Daily goal", style = MaterialTheme.typography.titleMedium)
                if (showPercent) Text("$percent%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(goal, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            Text(remaining, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, icon: @Composable (() -> Unit)? = null) {
    PremiumCard(modifier) {
        Column(Modifier.padding(JapSpacing.md), verticalArrangement = Arrangement.spacedBy(JapSpacing.xs)) {
            if (icon != null) icon()
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun QuickActionItem(label: String, description: String, onClick: () -> Unit, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 84.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f))) {
        Row(Modifier.padding(horizontal = JapSpacing.md, vertical = JapSpacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JapSpacing.sm)) {
            icon()
            Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelLarge); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SessionRow(title: String, count: String, time: String, recordType: String) {
    Row(Modifier.fillMaxWidth().padding(JapSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JapSpacing.md)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Text("ॐ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium) }
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.labelLarge); Text("$time · $recordType", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(count, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PremiumTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, supportingText: String? = null, keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default, singleLine: Boolean = true, isError: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(), supportingText = supportingText?.let { { Text(it) } }, keyboardOptions = keyboardOptions, singleLine = singleLine, isError = isError, shape = MaterialTheme.shapes.medium)
}

@Composable
fun PrimaryActionButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun PremiumButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) = PrimaryActionButton(text, modifier, enabled, onClick)

@Composable
fun SecondaryActionButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = CircleShape) { Text(text) }
}

@Composable
fun PremiumIconButton(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = .92f)).semantics { contentDescription = description }) { content() }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = JapSpacing.xl, horizontal = JapSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        LotusOutlineMark(Modifier.size(48.dp), null)
        Spacer(Modifier.height(JapSpacing.sm))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(JapSpacing.xs))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun LoadingIndicator(label: String = "Loading") = CircularProgressIndicator(Modifier.semantics { contentDescription = label })

@Composable
fun ThemePreview(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val borderColor by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "theme preview border")
    Surface(onClick = onClick, modifier = modifier.semantics { this.selected = selected }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { icon(); Text(label, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
fun GlassBottomBar(selectedRoute: String?, onSelect: (Destination) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth(.94f).height(70.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .20f), MaterialTheme.colorScheme.secondary.copy(alpha = .16f), MaterialTheme.colorScheme.primary.copy(alpha = .18f))))
                .blur(18.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .82f)),
            shadowElevation = 14.dp,
            tonalElevation = 4.dp
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.onSurface.copy(alpha = .035f), MaterialTheme.colorScheme.surface.copy(alpha = 0f)))).padding(horizontal = 5.dp, vertical = 6.dp)) {
                val itemWidth = maxWidth / Destination.primary.size
                val selectedIndex = Destination.primary.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
                val indicatorX by animateDpAsState(itemWidth * selectedIndex.toFloat(), label = "nav indicator position")
                Box(Modifier.align(Alignment.CenterStart).offset(x = indicatorX).width(itemWidth).height(46.dp).padding(horizontal = 2.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .20f), MaterialTheme.colorScheme.secondary.copy(alpha = .16f), MaterialTheme.colorScheme.primary.copy(alpha = .20f)))))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Destination.primary.forEach { destination ->
                        val selected = selectedRoute == destination.route
                        val tint by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "nav icon tint")
                        val scale by animateFloatAsState(if (selected) 1.06f else 1f, label = "nav icon scale")
                        Column(
                            Modifier.weight(1f).clip(CircleShape).clickable(role = Role.Tab, onClickLabel = "Open ${destination.label}") { if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(destination) }.semantics { this.selected = selected }.padding(horizontal = 2.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 30.dp).scale(scale).padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                                Icon(destination.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                            }
                            Text(destination.label, color = tint, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlassNavigationBar(selectedRoute: String?, onSelect: (Destination) -> Unit) = GlassBottomBar(selectedRoute, onSelect)
