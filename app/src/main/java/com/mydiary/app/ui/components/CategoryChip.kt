package com.mydiary.app.ui.components

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mydiary.shared.model.CategoryInfo

@Composable
fun CategoryChip(
    category: CategoryInfo,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = Color(category.colorHex.toLong(16))

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(category.label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.3f)
        ),
        modifier = modifier
    )
}
