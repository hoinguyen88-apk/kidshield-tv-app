package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyUsagePoint
import com.example.ui.theme.LocalKidShieldColors

@Composable
fun WeeklyChartCanvas(
    days: List<DailyUsagePoint>,
    selectedDayIndex: Int,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalKidShieldColors.current
    val gridColor = semantic.chartGrid
    val kidsColor = semantic.kidsSafe
    val familyColor = semantic.family
    val targetLineColor = semantic.warning
    val highlightBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)

    val maxMinutes = (days.maxOfOrNull { maxOf(it.kidsMinutes, it.familyMinutes) } ?: 60)
        .coerceAtLeast(60)
    val chartScaleMax = ((maxMinutes + 29) / 30) * 30 // Round up to nearest 30m

    Column(modifier = modifier.fillMaxWidth()) {
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendChip(color = kidsColor, label = "Trẻ em (phút)")
            LegendChip(color = familyColor, label = "Gia đình (phút)")
            LegendChip(color = targetLineColor, label = "Mức 60p/phiên")
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {
                if (days.isEmpty()) return@Canvas
                val chartHeight = size.height
                val slotWidth = size.width / days.size.toFloat()

                // Horizontal grid lines (0%, 50%, 100%)
                listOf(0f, 0.5f, 1f).forEach { fraction ->
                    val y = chartHeight * (1f - fraction)
                    drawLine(
                        color = gridColor.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // 60-minute Kids guideline dashed line
                val sixtyY = chartHeight * (1f - (60f / chartScaleMax.toFloat()).coerceIn(0f, 1f))
                drawLine(
                    color = targetLineColor.copy(alpha = 0.85f),
                    start = Offset(0f, sixtyY),
                    end = Offset(size.width, sixtyY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )

                // Draw bars for each of the 7 days
                val barWidth = (slotWidth * 0.24f).coerceIn(8.dp.toPx(), 18.dp.toPx())
                val gap = 4.dp.toPx()

                days.forEachIndexed { index, point ->
                    val slotLeft = index * slotWidth
                    val centerX = slotLeft + slotWidth / 2f

                    if (index == selectedDayIndex) {
                        drawRoundRect(
                            color = highlightBg,
                            topLeft = Offset(slotLeft + 4.dp.toPx(), 0f),
                            size = Size(slotWidth - 8.dp.toPx(), chartHeight),
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                        )
                    }

                    val kidsRatio = (point.kidsMinutes.toFloat() / chartScaleMax.toFloat()).coerceIn(0f, 1f)
                    val kidsH = (chartHeight * kidsRatio).coerceAtLeast(if (point.kidsMinutes > 0) 6.dp.toPx() else 2.dp.toPx())
                    drawRoundRect(
                        color = if (point.kidsMinutes > 0) kidsColor else kidsColor.copy(alpha = 0.2f),
                        topLeft = Offset(centerX - barWidth - gap / 2f, chartHeight - kidsH),
                        size = Size(barWidth, kidsH),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )

                    val familyRatio = (point.familyMinutes.toFloat() / chartScaleMax.toFloat()).coerceIn(0f, 1f)
                    val familyH = (chartHeight * familyRatio).coerceAtLeast(if (point.familyMinutes > 0) 6.dp.toPx() else 2.dp.toPx())
                    drawRoundRect(
                        color = if (point.familyMinutes > 0) familyColor else familyColor.copy(alpha = 0.2f),
                        topLeft = Offset(centerX + gap / 2f, chartHeight - familyH),
                        size = Size(barWidth, familyH),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive day labels row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            days.forEachIndexed { index, point ->
                val selected = index == selectedDayIndex
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusHighlight(shape = RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable { onSelectDay(index) }
                        .padding(vertical = 8.dp)
                        .testTag("report_day_$index"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = point.dayLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = point.fullDateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendChip(
    color: androidx.compose.ui.graphics.Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
