package com.example.gtturbo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BatteryIcon(
    batteryLevel: Int,
    modifier: Modifier = Modifier
) {
    val batteryColor = when {
        batteryLevel < 20 -> Color.Red
        batteryLevel < 60 -> Color(0xFFFFAA00) // Yellow/Orange
        else -> Color.Green
    }

    Canvas(
        modifier = modifier
            .width(24.dp)
            .height(48.dp)
            .padding(4.dp)
    ) {
        // Battery body - now vertical
        drawRoundRect(
            color = Color.Gray,
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            size = Size(size.width, size.height)
        )
        
        // Battery terminal - now at the top
        drawRoundRect(
            color = Color.Gray,
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            topLeft = Offset(size.width / 3, -4.dp.toPx()),
            size = Size(size.width / 3, 4.dp.toPx())
        )
        
        // Calculate fill height based on battery percentage (starting from bottom)
        val levelHeight = (size.height - 8.dp.toPx()) * batteryLevel / 100
        val yOffset = size.height - 4.dp.toPx() - levelHeight  // Start from bottom
        
        // Filled level - now fills from bottom to top
        drawRoundRect(
            color = batteryColor,
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            topLeft = Offset(4.dp.toPx(), yOffset),
            size = Size(
                size.width - 8.dp.toPx(),
                levelHeight
            )
        )
    }
} 