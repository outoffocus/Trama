package com.trama.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trama.app.chat.DiaryAssistant
import com.trama.app.chat.DiaryContextBuilder
import com.trama.app.ui.components.TramaChip
import com.trama.app.ui.theme.LocalTramaColors
import com.trama.shared.data.DatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val id: Long = System.nanoTime()
)

private val SUGGESTIONS = listOf(
    "¿Dónde estuve el martes?",
    "¿Qué restaurantes me gustaron?",
    "¿Qué tareas completé esta semana?",
    "¿Con quién quedé en abril?",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { DatabaseProvider.getRepository(context) }
    val contextBuilder = remember { DiaryContextBuilder(repository) }
    val assistant = remember { DiaryAssistant(context, contextBuilder, repository) }
    val t = LocalTramaColors.current

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    val entryCount by repository.countAll().collectAsState(initial = 0)

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isThinking) {
        val target = messages.size + (if (isThinking) 1 else 0)
        if (target > 0) listState.animateScrollToItem(target - 1)
    }

    fun send(raw: String) {
        val msg = raw.trim()
        if (msg.isBlank() || isThinking) return
        inputText = ""
        messages = messages + ChatMessage(msg, isUser = true)
        isThinking = true
        scope.launch {
            val reply = try {
                withContext(Dispatchers.IO) { assistant.send(msg) }
            } catch (t: Throwable) {
                "Error inesperado: ${t.javaClass.simpleName}: ${t.message}"
            }
            messages = messages + ChatMessage(reply, isUser = false)
            isThinking = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Asistente",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    actions = {
                        if (messages.isNotEmpty()) {
                            TextButton(onClick = {
                                assistant.clearHistory()
                                messages = emptyList()
                            }) {
                                Text(
                                    "Nuevo",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = t.teal,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                )
                ContextStrip(entryCount = entryCount)
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Pregúntame algo…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        enabled = !isThinking
                    )
                    FilledIconButton(
                        onClick = { send(inputText) },
                        enabled = inputText.isNotBlank() && !isThinking
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                    }
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty() && !isThinking) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onPick = { send(it) }
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(msg)
                }
                if (isThinking) {
                    item("thinking") { ThinkingBubble() }
                }
            }
        }
    }
}

@Composable
private fun ContextStrip(entryCount: Int) {
    val t = LocalTramaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(t.teal)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$entryCount entradas indexadas",
            style = MaterialTheme.typography.labelMedium,
            color = t.mutedText,
        )
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit,
) {
    Box(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Pregúntale a tu diario",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Busca lugares, personas, tareas o recuerdos con tus propias palabras.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalTramaColors.current.mutedText,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            SUGGESTIONS.forEach { q ->
                TramaChip(
                    text = q,
                    onClick = { onPick(q) },
                    color = LocalTramaColors.current.teal,
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val t = LocalTramaColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        val bg = if (msg.isUser) t.amberBg else t.surface
        val fg = if (msg.isUser) t.amber else MaterialTheme.colorScheme.onSurface
        val border = if (msg.isUser) t.amber.copy(alpha = 0.3f) else t.softBorder
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (msg.isUser) 16.dp else 4.dp,
                bottomEnd = if (msg.isUser) 4.dp else 16.dp
            ),
            color = bg,
            border = BorderStroke(1.dp, border),
        ) {
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun ThinkingBubble() {
    val t = LocalTramaColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = t.surface,
            border = BorderStroke(1.dp, t.softBorder),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThinkingDot(delayMillis = 0, color = t.teal)
                ThinkingDot(delayMillis = 200, color = t.teal)
                ThinkingDot(delayMillis = 400, color = t.teal)
            }
        }
    }
}

@Composable
private fun ThinkingDot(delayMillis: Int, color: androidx.compose.ui.graphics.Color) {
    val infinite = rememberInfiniteTransition(label = "dot")
    val a by infinite.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = delayMillis),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot-a"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .alpha(a)
            .clip(CircleShape)
            .background(color)
    )
}
