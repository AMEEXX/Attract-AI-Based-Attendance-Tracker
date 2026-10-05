package com.attract.attendance.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.attract.attendance.ui.theme.Dimens
import com.attract.attendance.ui.theme.Shapes

@Composable
fun AttractCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val baseModifier = modifier
        .fillMaxWidth()
        .clip(Shapes.card)
        .then(if (onClick != null) Modifier.feedbackClickable(onClick = onClick) else Modifier)

    Card(
        modifier = baseModifier,
        shape = Shapes.card,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Dimens.CardPaddingHorizontal,
                vertical = Dimens.CardPaddingVertical
            ),
            content = content
        )
    }
}
