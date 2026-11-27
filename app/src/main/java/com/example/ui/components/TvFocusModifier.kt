package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adds visible Xiaomi Google TV / Android TV D-Pad focus ring and subtle scale feedback
 * without breaking standard mobile touch behavior.
 */
fun Modifier.tvFocusHighlight(
    shape: Shape = RoundedCornerShape(16.dp),
    focusedBorderColor: Color? = null,
    borderWidth: Dp = 2.5.dp,
    makeFocusable: Boolean = false
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val targetColor = focusedBorderColor ?: MaterialTheme.colorScheme.primary
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused) targetColor else Color.Transparent,
        label = "tvFocusBorder"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.035f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tvFocusScale"
    )

    this
        .scale(animatedScale)
        .border(width = borderWidth, color = animatedBorderColor, shape = shape)
        .onFocusChanged { isFocused = it.isFocused }
        .then(if (makeFocusable) Modifier.focusable() else Modifier)
}
