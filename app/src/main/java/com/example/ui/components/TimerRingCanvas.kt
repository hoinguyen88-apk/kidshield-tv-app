package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.GuardState
import com.example.data.model.LockReason
import com.example.data.model.WatchMode
import com.example.ui.theme.LocalKidShieldColors

@Composable
fun TimerRingCanvas(
    guardState: GuardState,
    modifier: Modifier = Modifier
) {
    val semantic = LocalKidShieldColors.current
    val isFamily = guardState.activeMode == WatchMode.FAMILY
    val isLocked = !isFamily && guardState.isKidsLockedOut
    val isWarning = !isFamily && !isLocked && guardState.effectiveRemainingSeconds <= 600

    val targetProgress = when {
        isFamily -> 1f
        isLocked -> 0f
        else -> (guardState.effectiveRemainingSeconds.toFloat() / guardState.sessionLimitSeconds.coerceAtLeast(1))
            .coerceIn(0f, 1f)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "timerRingSweep"
    )

    val primaryRingColor by animateColorAsState(
        targetValue = when {
            isFamily -> semantic.family
            isLocked -> semantic.locked
            isWarning -> semantic.warning
            else -> semantic.kidsSafe
        },
        label = "ringPrimaryColor"
    )

    val secondaryRingColor by animateColorAsState(
        targetValue = when {
            isFamily -> semantic.kidsSafe
            isLocked -> Color(0xFF991B1B)
            isWarning -> Color(0xFFF97316)
            else -> MaterialTheme.colorScheme.tertiary
        },
        label = "ringSecondaryColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier.size(248.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 18.dp.toPx()
            val inset = strokePx / 2f + 12.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            val center = Offset(size.width / 2f, size.height / 2f)

            // Ambient radial glow behind ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryRingColor.copy(alpha = pulseAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.5f
                ),
                radius = size.minDimension * 0.5f,
                center = center
            )

            // Background track
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Active progress arc
            if (animatedProgress > 0.005f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(primaryRingColor, secondaryRingColor, primaryRingColor),
                        center = center
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            val statusIcon = when {
                isFamily -> Icons.Default.AllInclusive
                guardState.lockReason == LockReason.BEDTIME_CURFEW -> Icons.Default.NightlightRound
                isLocked -> Icons.Default.Lock
                guardState.isTimerPaused -> Icons.Default.PauseCircle
                else -> Icons.Default.Shield
            }

            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = primaryRingColor,
                modifier = Modifier.size(26.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            val mainReadout = when {
                isFamily -> "MỞ KHÓA"
                isLocked -> "00:00"
                else -> formatMinutesSeconds(guardState.effectiveRemainingSeconds)
            }

            Text(
                text = mainReadout,
                style = if (isFamily) {
                    MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.displayLarge
                },
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            val pillText = when {
                isFamily -> "GIA ĐÌNH • TỰ DO"
                guardState.lockReason == LockReason.BEDTIME_CURFEW -> "KHÓA SAU 21:00"
                isLocked -> "HẾT 1 GIỜ • NGẮT MẠNG"
                guardState.isTimerPaused -> "TẠM DỪNG • NGẮT MẠNG"
                else -> "GIỚI HẠN 60 PHÚT"
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = primaryRingColor.copy(alpha = 0.16f)
            ) {
                Text(
                    text = pillText,
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryRingColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

fun formatMinutesSeconds(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return "%02d:%02d".format(mins, secs)
}
