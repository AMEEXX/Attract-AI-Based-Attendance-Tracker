package com.attract.attendance.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val MaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object AttractShapes {
    val extraSmall: Shape = RoundedCornerShape(8.dp)
    val small: Shape = RoundedCornerShape(12.dp)
    val medium: Shape = RoundedCornerShape(16.dp)
    val large: Shape = RoundedCornerShape(24.dp)
    val extraLarge: Shape = RoundedCornerShape(28.dp)

    val interactive: Shape = large
    val card: Shape = medium
    val pill: Shape = RoundedCornerShape(percent = 50)
    val cameraViewport: Shape = small
}

object Shapes {
    val extraSmall: Shape = AttractShapes.extraSmall
    val small: Shape = AttractShapes.small
    val medium: Shape = AttractShapes.medium
    val large: Shape = AttractShapes.large
    val extraLarge: Shape = AttractShapes.extraLarge

    val interactive: Shape = AttractShapes.interactive
    val card: Shape = AttractShapes.card
    val pill: Shape = AttractShapes.pill
    val cameraViewport: Shape = AttractShapes.cameraViewport
}

val PillShape = RoundedCornerShape(percent = 50)
