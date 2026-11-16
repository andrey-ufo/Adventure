package com.myapp.adventure.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import com.myapp.adventure.data.theme.ThemeShapes

/**
 * Maps the data-layer [ThemeShapes] (dp values) to Compose [Shapes].
 * Material 3 has five named slots; we map small→extraSmall/small,
 * medium→medium, large→large/extraLarge so a single theme knob
 * (cornerSmall/Medium/Large) covers them.
 */
fun ThemeShapes.toShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(cornerSmall.dp),
    small = RoundedCornerShape(cornerSmall.dp),
    medium = RoundedCornerShape(cornerMedium.dp),
    large = RoundedCornerShape(cornerLarge.dp),
    extraLarge = RoundedCornerShape(cornerLarge.dp),
)