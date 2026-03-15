package com.mydiary.app.ui.components

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mydiary.app.ui.theme.HighlightColor
import com.mydiary.app.ui.theme.NoteColor
import com.mydiary.app.ui.theme.ReminderColor
import com.mydiary.app.ui.theme.TodoColor
import com.mydiary.shared.model.Category

@Composable
fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = when (category) {
        Category.TODO -> TodoColor
        Category.REMINDER -> ReminderColor
        Category.HIGHLIGHT -> HighlightColor
        Category.NOTE -> NoteColor
    }

    val label = when (category) {
        Category.TODO -> "Por hacer"
        Category.REMINDER -> "Recordatorio"
        Category.HIGHLIGHT -> "Destacado"
        Category.NOTE -> "Nota"
    }

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.3f)
        ),
        modifier = modifier
    )
}
