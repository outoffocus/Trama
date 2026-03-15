package com.mydiary.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mydiary.app.ui.theme.HighlightColor
import com.mydiary.app.ui.theme.NoteColor
import com.mydiary.app.ui.theme.ReminderColor
import com.mydiary.app.ui.theme.TodoColor
import com.mydiary.shared.model.Category
import com.mydiary.shared.model.DiaryEntry
import com.mydiary.shared.model.Source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EntryCard(
    entry: DiaryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = when (entry.category) {
        Category.TODO -> TodoColor
        Category.REMINDER -> ReminderColor
        Category.HIGHLIGHT -> HighlightColor
        Category.NOTE -> NoteColor
    }

    val categoryLabel = when (entry.category) {
        Category.TODO -> "Por hacer"
        Category.REMINDER -> "Recordatorio"
        Category.HIGHLIGHT -> "Destacado"
        Category.NOTE -> "Nota"
    }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = categoryColor.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = categoryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = categoryColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (entry.source == Source.WATCH) Icons.Default.Watch else Icons.Default.Mic,
                    contentDescription = if (entry.source == Source.WATCH) "Reloj" else "Teléfono",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = timeFormat.format(Date(entry.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
