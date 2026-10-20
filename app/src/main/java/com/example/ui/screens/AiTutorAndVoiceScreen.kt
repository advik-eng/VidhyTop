package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.StudyNote
import com.example.data.model.TutorMessage
import com.example.data.model.UserProfile
import com.example.ui.theme.LocalVidhyaColors
import com.example.data.remote.GeminiAiService
import com.example.ui.components.VidhyaIcons
import com.example.viewmodel.UiState
import com.example.viewmodel.VidhyaDestination
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AiTutorHomeScreen(
    userProfile: UserProfile?,
    tutorMessagesState: UiState<List<TutorMessage>>,
    notesState: UiState<List<StudyNote>>,
    activeSubject: String,
    isTutorThinking: Boolean,
    tutorError: String?,
    onSelectSubject: (String) -> Unit,
    onSendQuestion: (String, String?) -> Unit,
    onCancelRequest: () -> Unit,
    onOpenLiveVoice: () -> Unit,
    onNavigate: (VidhyaDestination) -> Unit
) {
    var messageInput by rememberSaveable { mutableStateOf("") }
    var attachedNote by remember { mutableStateOf<StudyNote?>(null) }
    var showNotePicker by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val messages = (tutorMessagesState as? UiState.Success)?.data ?: emptyList()
    val subjects = remember(userProfile) {
        val base = userProfile?.subjects.orEmpty()
        (listOf("General") + base).distinct()
    }

    LaunchedEffect(messages.size, isTutorThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Calm, human-designed Top Header with Subject Selector & Study Context
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Vidhya AI Tutor",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${userProfile?.gradeLevel ?: "Student"} • Step-by-step academic guidance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { onNavigate(VidhyaDestination.FRIENDS_LEADERBOARD) },
                            modifier = Modifier.testTag("header_friends_button")
                        ) {
                            Icon(
                                imageVector = VidhyaIcons.FriendsLeaderboard,
                                contentDescription = "Friends & Leaderboard",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onNavigate(VidhyaDestination.PROGRESS_ANALYTICS) },
                            modifier = Modifier.testTag("header_progress_button")
                        ) {
                            Icon(
                                imageVector = VidhyaIcons.ProgressStats,
                                contentDescription = "Study Progress",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onNavigate(VidhyaDestination.SETTINGS) },
                            modifier = Modifier.testTag("header_settings_button")
                        ) {
                            Icon(
                                imageVector = VidhyaIcons.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Subject Context Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subjects.forEach { subj ->
                        FilterChip(
                            selected = activeSubject == subj,
                            onClick = { onSelectSubject(subj) },
                            label = { Text(subj, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Conversation Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && !isTutorThinking) {
                TutorEmptyState(
                    gradeLevel = userProfile?.gradeLevel ?: "Grade 10",
                    activeSubject = activeSubject,
                    onSuggestionClick = { prompt ->
                        onSendQuestion(prompt, attachedNote?.kalamNoteContent)
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id.ifBlank { it.content.hashCode().toString() } }) { msg ->
                        TutorMessageBubble(message = msg)
                    }

                    if (isTutorThinking) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Reasoning step by step...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    TextButton(onClick = onCancelRequest) {
                                        Text("Cancel", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Error Recovery Banner
        if (tutorError != null) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = tutorError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    if (messages.lastOrNull()?.role == "user") {
                        TextButton(
                            onClick = {
                                val lastQ = messages.last().content
                                onSendQuestion(lastQ, attachedNote?.kalamNoteContent)
                            }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }

        // Attached Study Note Indicator
        if (attachedNote != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = VidhyaIcons.Document,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Context: ${attachedNote?.title}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { attachedNote = null }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = VidhyaIcons.Close,
                            contentDescription = "Remove attached note",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Clean Message Composer with Attach, Text Input, Send, and Compact Live Voice Control
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                IconButton(
                    onClick = { showNotePicker = !showNotePicker },
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("attach_note_context_button")
                ) {
                    Icon(
                        imageVector = VidhyaIcons.InteractiveNotes,
                        contentDescription = "Attach study note context",
                        tint = if (attachedNote != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = {
                        Text(
                            text = "Ask a $activeSubject question...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tutor_message_input")
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send Action
                IconButton(
                    onClick = {
                        if (messageInput.isNotBlank()) {
                            onSendQuestion(messageInput, attachedNote?.kalamNoteContent)
                            messageInput = ""
                        }
                    },
                    enabled = messageInput.isNotBlank() && !isTutorThinking,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (messageInput.isNotBlank() && !isTutorThinking)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("tutor_send_button")
                ) {
                    Icon(
                        imageVector = VidhyaIcons.Send,
                        contentDescription = "Send question",
                        tint = if (messageInput.isNotBlank() && !isTutorThinking)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Small, clearly identifiable Live Voice control beside the input
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenLiveVoice)
                        .testTag("tutor_live_voice_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = VidhyaIcons.LiveVoice,
                            contentDescription = "Open Live Voice Tutor",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Live",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // Study Note Attachment Picker Sheet
        if (showNotePicker) {
            val savedNotes = (notesState as? UiState.Success)?.data.orEmpty()
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Attach Study Note for Context",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (savedNotes.isEmpty()) {
                        Text(
                            text = "No notes saved yet. Create a note in the Notes tab to attach it here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        savedNotes.take(4).forEach { note ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        attachedNote = note
                                        showNotePicker = false
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = VidhyaIcons.Document,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${note.title} (${note.subject})",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorEmptyState(
    gradeLevel: String,
    activeSubject: String,
    onSuggestionClick: (String) -> Unit
) {
    val suggestions = remember(activeSubject, gradeLevel) {
        when (activeSubject) {
            "Mathematics" -> listOf(
                "Explain derivatives using a real-world speed analogy for $gradeLevel",
                "Walk me through solving quadratic equations step by step",
                "How do trigonometric identities connect to the unit circle?"
            )
            "Physics", "Science" -> listOf(
                "Explain Faraday's Law of Electromagnetic Induction simply",
                "Why does a satellite stay in orbit without falling to Earth?",
                "Compare ionic and covalent bonding with examples"
            )
            else -> listOf(
                "Explain a difficult concept in $activeSubject at a $gradeLevel level",
                "Give me 3 step-by-step practice problems in $activeSubject",
                "Help me build a 30-minute revision plan for today's topic"
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "What are we studying today?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Ask any question in $activeSubject. Vidhya explains concepts clearly with step-by-step reasoning and examples for $gradeLevel.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            suggestions.forEach { prompt ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSuggestionClick(prompt) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = VidhyaIcons.ExplainConcept,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorMessageBubble(message: TutorMessage) {
    val isUser = message.role == "user"
    Column(
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (!isUser && !message.subjectContext.isNullOrBlank()) {
                    Text(
                        text = "Vidhya Tutor • ${message.subjectContext}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Calm, real-time Live Voice Tutor sheet using Android SpeechRecognizer + Gemini AI + TextToSpeech.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVoiceTutorModal(
    gradeLevel: String,
    activeSubject: String,
    onDismiss: () -> Unit,
    onSaveTranscriptToHistory: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    var voiceState by remember { mutableStateOf("IDLE") } // IDLE, LISTENING, THINKING, SPEAKING, ERROR
    var recognizedTranscript by remember { mutableStateOf("") }
    var spokenResponse by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isMuted by remember { mutableStateOf(false) }

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale.US
            }
        }
        ttsEngine = tts

        val recognizer = if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
        speechRecognizer = recognizer

        onDispose {
            try {
                tts.stop()
                tts.shutdown()
            } catch (e: Exception) {
                // ignore
            }
            try {
                recognizer?.destroy()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun processVoiceQuestion(spokenText: String) {
        if (spokenText.isBlank()) {
            voiceState = "ERROR"
            errorMessage = "Could not hear a clear question. Tap Listen to try again."
            return
        }
        recognizedTranscript = spokenText
        voiceState = "THINKING"
        errorMessage = null

        scope.launch {
            val res = GeminiAiService.askTutor(
                question = "$spokenText (Keep response concise and conversational for voice playback)",
                gradeLevel = gradeLevel,
                subject = activeSubject
            )
            res.fold(
                onSuccess = { reply ->
                    spokenResponse = reply
                    onSaveTranscriptToHistory(spokenText, reply)
                    voiceState = "SPEAKING"
                    if (!isMuted) {
                        val cleanSpeech = reply.replace("#", "").replace("*", "")
                        ttsEngine?.speak(cleanSpeech.take(900), TextToSpeech.QUEUE_FLUSH, null, "vidhya_voice")
                    }
                },
                onFailure = { err ->
                    voiceState = "ERROR"
                    errorMessage = err.localizedMessage ?: "Voice AI service unavailable."
                }
            )
        }
    }

    fun startListening() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            voiceState = "ERROR"
            errorMessage = "Speech recognition service is not available on this device/emulator. You can type a prompt below to test spoken TTS output."
            return
        }
        ttsEngine?.stop()
        errorMessage = null
        voiceState = "LISTENING"

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                voiceState = "LISTENING"
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                voiceState = "ERROR"
                errorMessage = "Microphone listening ended without speech (code $error). Tap Listen to retry."
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                processVoiceQuestion(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    recognizedTranscript = partial
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    ModalBottomSheet(
        onDismissRequest = {
            ttsEngine?.stop()
            speechRecognizer?.cancel()
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = "Vidhya Live Voice Tutor",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$activeSubject • $gradeLevel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!hasMicPermission) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Icon(
                            imageVector = VidhyaIcons.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Microphone Access Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Vidhya uses your microphone only while you actively speak to the Live Voice Tutor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            modifier = Modifier.testTag("grant_mic_permission_button")
                        ) {
                            Text("Grant Microphone Permission")
                        }
                    }
                }
            } else {
                // Calm Status Indicator Circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(
                            when (voiceState) {
                                "LISTENING" -> MaterialTheme.colorScheme.primaryContainer
                                "THINKING" -> MaterialTheme.colorScheme.secondaryContainer
                                "SPEAKING" -> LocalVidhyaColors.current.successContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                ) {
                    if (voiceState == "THINKING") {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = if (voiceState == "SPEAKING") VidhyaIcons.ReadAloud else VidhyaIcons.Mic,
                            contentDescription = voiceState,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = when (voiceState) {
                        "LISTENING" -> "Listening... Speak your question clearly"
                        "THINKING" -> "Thinking through the explanation..."
                        "SPEAKING" -> if (isMuted) "Response ready (Audio Muted)" else "Speaking explanation aloud..."
                        "ERROR" -> "Session Paused"
                        else -> "Tap 'Start Listening' to speak with Vidhya"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (recognizedTranscript.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "“$recognizedTranscript”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                if (spokenResponse.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = spokenResponse,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { startListening() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("live_voice_listen_button")
                    ) {
                        Icon(imageVector = VidhyaIcons.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (voiceState == "LISTENING") "Listening..." else "Start Listening")
                    }

                    OutlinedButton(
                        onClick = {
                            isMuted = !isMuted
                            if (isMuted) ttsEngine?.stop()
                        }
                    ) {
                        Text(if (isMuted) "Unmute" else "Mute TTS")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {
                    ttsEngine?.stop()
                    speechRecognizer?.cancel()
                    onDismiss()
                },
                modifier = Modifier.testTag("end_live_voice_button")
            ) {
                Text("End Voice Session")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
