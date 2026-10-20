package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KeyTermItem
import com.example.data.model.StudyNote
import com.example.data.remote.GeminiAiService
import com.example.ui.components.VidhyaIcons
import com.example.ui.liquidglass.LiquidGlassBar
import com.example.ui.theme.KalamFontFamily
import com.example.ui.theme.KalamHighlightTermDark
import com.example.ui.theme.KalamHighlightTermLight
import com.example.ui.theme.KalamInkDark
import com.example.ui.theme.KalamInkLight
import com.example.ui.theme.KalamPaperDark
import com.example.ui.theme.KalamPaperLight
import com.example.ui.theme.KalamRuleLineDark
import com.example.ui.theme.KalamRuleLineLight
import com.example.ui.theme.LocalDarkTheme
import com.example.util.PdfDocumentHelper
import com.example.viewmodel.UiState
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun InteractiveNotesScreen(
    notesState: UiState<List<StudyNote>>,
    userSubjects: List<String>,
    isGeneratingNote: Boolean,
    noteGenerationError: String?,
    contextualAiLoading: Boolean,
    contextualAiResult: Pair<String, String>?,
    liquidGlassEnabled: Boolean,
    isNoteFullScreen: Boolean,
    onSetNoteFullScreen: (Boolean) -> Unit,
    onGenerateNotes: (topicOrText: String, subject: String, sourceType: String, bitmap: Bitmap?, onCreated: (String) -> Unit) -> Unit,
    onRunContextualAction: (action: String, selectedText: String, fullNoteContent: String, customQuestion: String?) -> Unit,
    onClearContextualResult: () -> Unit,
    onDeleteNote: (String) -> Unit,
    onShowBanner: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeInputTab by rememberSaveable { mutableStateOf("TOPIC") } // TOPIC, PHOTO, PDF
    var topicInput by rememberSaveable { mutableStateOf("") }
    var selectedSubject by rememberSaveable { mutableStateOf(userSubjects.firstOrNull() ?: "Physics") }

    // Captured / Selected Photo state
    var capturedPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoTopicHint by rememberSaveable { mutableStateOf("") }

    // Imported PDF Reader state
    var importedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfFileName by rememberSaveable { mutableStateOf("") }
    var pdfPageCount by rememberSaveable { mutableIntStateOf(0) }
    var pdfCurrentPage by rememberSaveable { mutableIntStateOf(0) }
    var pdfZoomScale by rememberSaveable { mutableFloatStateOf(1.5f) }
    var pdfNightMode by rememberSaveable { mutableStateOf(false) }
    var renderedPdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pdfError by remember { mutableStateOf<String?>(null) }

    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            capturedPhotoBitmap = bmp
            activeInputTab = "PHOTO"
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    capturedPhotoBitmap = BitmapFactory.decodeStream(stream)
                    activeInputTab = "PHOTO"
                }
            } catch (e: Exception) {
                onShowBanner("Could not load selected photo.")
            }
        }
    }

    val pdfOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importedPdfUri = uri
            activeInputTab = "PDF"
            pdfError = null
            scope.launch {
                val infoRes = PdfDocumentHelper.openPdfInfo(context, uri)
                infoRes.fold(
                    onSuccess = { info ->
                        pdfPageCount = info.pageCount
                        pdfFileName = info.fileName
                        pdfCurrentPage = 0
                        val pageRes = PdfDocumentHelper.renderPdfPage(context, uri, 0, pdfZoomScale)
                        renderedPdfBitmap = pageRes.getOrNull()
                    },
                    onFailure = { err ->
                        pdfError = err.localizedMessage ?: "Failed to open PDF document."
                    }
                )
            }
        }
    }

    val notesList = (notesState as? UiState.Success)?.data.orEmpty()
    val activeNote = notesList.find { it.id == selectedNoteId }

    BackHandler(enabled = activeNote != null) {
        if (isNoteFullScreen) {
            onSetNoteFullScreen(false)
        } else {
            selectedNoteId = null
            onClearContextualResult()
        }
    }

    if (activeNote != null) {
        val exportPdfAction: () -> Unit = {
            scope.launch {
                onShowBanner("Exporting Kalam handwritten PDF...")
                val exportRes = PdfDocumentHelper.exportNoteToKalamPdf(context, activeNote)
                exportRes.fold(
                    onSuccess = { file ->
                        onShowBanner("Exported ${file.name} (${file.length() / 1024} KB)")
                        PdfDocumentHelper.sharePdfFile(context, file)
                    },
                    onFailure = { err ->
                        onShowBanner("PDF export failed: ${err.localizedMessage}")
                    }
                )
            }
        }

        KalamInteractiveNoteReader(
            note = activeNote,
            isFullScreen = isNoteFullScreen,
            liquidGlassEnabled = liquidGlassEnabled,
            contextualAiLoading = contextualAiLoading,
            contextualAiResult = contextualAiResult,
            onBack = {
                if (isNoteFullScreen) {
                    onSetNoteFullScreen(false)
                } else {
                    selectedNoteId = null
                    onClearContextualResult()
                }
            },
            onToggleFullScreen = { onSetNoteFullScreen(!isNoteFullScreen) },
            onRunContextualAction = { action, text, customQ ->
                onRunContextualAction(action, text, activeNote.kalamNoteContent, customQ)
            },
            onClearContextualResult = onClearContextualResult,
            onExportPdf = exportPdfAction
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Column {
                    Text(
                        text = "Interactive Kalam Notes",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Transform topics, photos, or PDFs into structured Kalam handwritten revision notes with interactive word & paragraph intelligence.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Three Input Entry Points: Topic, Photo OCR, PDF Import
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    InputSourceMethodTab(
                        title = "Topic",
                        icon = VidhyaIcons.TopicGenerate,
                        selected = activeInputTab == "TOPIC",
                        testTag = "notes_tab_topic",
                        onClick = { activeInputTab = "TOPIC" },
                        modifier = Modifier.weight(1f)
                    )
                    InputSourceMethodTab(
                        title = "Photo OCR",
                        icon = VidhyaIcons.CameraCapture,
                        selected = activeInputTab == "PHOTO",
                        testTag = "notes_tab_photo",
                        onClick = { activeInputTab = "PHOTO" },
                        modifier = Modifier.weight(1f)
                    )
                    InputSourceMethodTab(
                        title = "Import PDF",
                        icon = VidhyaIcons.PdfImport,
                        selected = activeInputTab == "PDF",
                        testTag = "notes_tab_pdf",
                        onClick = { activeInputTab = "PDF" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Input & Generation Workspace Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Subject selector
                        val subjects = (userSubjects + listOf("Mathematics", "Physics", "Chemistry", "Biology", "English")).distinct()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            subjects.forEach { subj ->
                                FilterChip(
                                    selected = selectedSubject == subj,
                                    onClick = { selectedSubject = subj },
                                    label = { Text(subj) }
                                )
                            }
                        }

                        when (activeInputTab) {
                            "TOPIC" -> {
                                OutlinedTextField(
                                    value = topicInput,
                                    onValueChange = { topicInput = it },
                                    label = { Text("Enter Syllabus Topic or Chapter") },
                                    placeholder = { Text("e.g., Faraday's Law of Electromagnetic Induction") },
                                    minLines = 2,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("notes_topic_input")
                                )

                                Button(
                                    onClick = {
                                        onGenerateNotes(topicInput, selectedSubject, "topic", null) { newId ->
                                            selectedNoteId = newId
                                        }
                                    },
                                    enabled = !isGeneratingNote && topicInput.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("generate_notes_button")
                                ) {
                                    if (isGeneratingNote) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Organizing Kalam Revision Notes...")
                                    } else {
                                        Icon(imageVector = VidhyaIcons.HandwritingExport, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Generate Notes")
                                    }
                                }
                            }

                            "PHOTO" -> {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { cameraPreviewLauncher.launch(null) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("capture_photo_button")
                                    ) {
                                        Icon(imageVector = VidhyaIcons.CameraCapture, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Camera")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("pick_photo_button")
                                    ) {
                                        Icon(imageVector = VidhyaIcons.Document, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Select Image")
                                    }
                                }

                                if (capturedPhotoBitmap != null) {
                                    Image(
                                        bitmap = capturedPhotoBitmap!!.asImageBitmap(),
                                        contentDescription = "Captured study material",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }

                                OutlinedTextField(
                                    value = photoTopicHint,
                                    onValueChange = { photoTopicHint = it },
                                    label = { Text("Optional Topic Title or Focus Instructions") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = {
                                        onGenerateNotes(
                                            photoTopicHint.ifBlank { "Extract text from photo and structure revision notes" },
                                            selectedSubject,
                                            "photo",
                                            capturedPhotoBitmap
                                        ) { newId ->
                                            selectedNoteId = newId
                                        }
                                    },
                                    enabled = !isGeneratingNote && capturedPhotoBitmap != null,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    if (isGeneratingNote) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Running OCR & Generating Kalam Notes...")
                                    } else {
                                        Text("Generate Notes from Photo")
                                    }
                                }
                            }

                            "PDF" -> {
                                OutlinedButton(
                                    onClick = { pdfOpenLauncher.launch(arrayOf("application/pdf")) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("import_pdf_button")
                                ) {
                                    Icon(imageVector = VidhyaIcons.PdfImport, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (pdfFileName.isBlank()) "Choose PDF Document" else "PDF: $pdfFileName")
                                }

                                if (pdfError != null) {
                                    Text(
                                        text = pdfError ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                // Real PDF Reader Surface (Preserves source document as a clean document surface, NOT a glass card)
                                if (renderedPdfBitmap != null && importedPdfUri != null) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Page ${pdfCurrentPage + 1} of $pdfPageCount",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                TextButton(
                                                    onClick = {
                                                        pdfNightMode = !pdfNightMode
                                                    }
                                                ) {
                                                    Text(if (pdfNightMode) "Light Page" else "Dark Page")
                                                }
                                                TextButton(
                                                    onClick = {
                                                        pdfZoomScale = if (pdfZoomScale < 2.2f) pdfZoomScale + 0.4f else 1.2f
                                                        scope.launch {
                                                            renderedPdfBitmap = PdfDocumentHelper.renderPdfPage(
                                                                context, importedPdfUri!!, pdfCurrentPage, pdfZoomScale
                                                            ).getOrNull()
                                                        }
                                                    }
                                                ) {
                                                    Text("Zoom ${(pdfZoomScale * 100).toInt()}%")
                                                }
                                            }
                                        }

                                        val invertMatrix = remember(pdfNightMode) {
                                            if (pdfNightMode) {
                                                ColorFilter.colorMatrix(
                                                    ColorMatrix(
                                                        floatArrayOf(
                                                            -1f, 0f, 0f, 0f, 255f,
                                                            0f, -1f, 0f, 0f, 255f,
                                                            0f, 0f, -1f, 0f, 255f,
                                                            0f, 0f, 0f, 1f, 0f
                                                        )
                                                    )
                                                )
                                            } else null
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.White,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Image(
                                                bitmap = renderedPdfBitmap!!.asImageBitmap(),
                                                contentDescription = "PDF Page ${pdfCurrentPage + 1}",
                                                colorFilter = invertMatrix,
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    if (pdfCurrentPage > 0) {
                                                        pdfCurrentPage -= 1
                                                        scope.launch {
                                                            renderedPdfBitmap = PdfDocumentHelper.renderPdfPage(
                                                                context, importedPdfUri!!, pdfCurrentPage, pdfZoomScale
                                                            ).getOrNull()
                                                        }
                                                    }
                                                },
                                                enabled = pdfCurrentPage > 0
                                            ) {
                                                Text("Previous Page")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    if (pdfCurrentPage < pdfPageCount - 1) {
                                                        pdfCurrentPage += 1
                                                        scope.launch {
                                                            renderedPdfBitmap = PdfDocumentHelper.renderPdfPage(
                                                                context, importedPdfUri!!, pdfCurrentPage, pdfZoomScale
                                                            ).getOrNull()
                                                        }
                                                    }
                                                },
                                                enabled = pdfCurrentPage < pdfPageCount - 1
                                            ) {
                                                Text("Next Page")
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                onGenerateNotes(
                                                    "Extract and organize study notes from PDF document '$pdfFileName' (Page ${pdfCurrentPage + 1})",
                                                    selectedSubject,
                                                    "pdf",
                                                    renderedPdfBitmap
                                                ) { newId ->
                                                    selectedNoteId = newId
                                                }
                                            },
                                            enabled = !isGeneratingNote,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                        ) {
                                            if (isGeneratingNote) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Generating Kalam Notes from PDF...")
                                            } else {
                                                Text("Generate Kalam Notes from Current PDF Page")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (noteGenerationError != null) {
                            Text(
                                text = noteGenerationError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Saved Kalam Notes Library
            item {
                Text(
                    text = "Your Kalam Revision Notebooks (${notesList.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (notesList.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = VidhyaIcons.InteractiveNotes,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No revision notes yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enter a topic above, snap a photo of your textbook, or import a PDF to generate your first Kalam handwritten note.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(notesList, key = { it.id }) { note ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedNoteId = note.id }
                            .testTag("note_item_${note.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = note.subject.uppercase(Locale.US),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "• ${note.sourceType.uppercase(Locale.US)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = note.title,
                                    fontFamily = KalamFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = note.kalamNoteContent.replace("#", "").trim().take(120),
                                    fontFamily = KalamFontFamily,
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        selectedNoteId = note.id
                                        onSetNoteFullScreen(true)
                                    },
                                    modifier = Modifier.testTag("note_fullscreen_item_${note.id}")
                                ) {
                                    Icon(
                                        imageVector = VidhyaIcons.Fullscreen,
                                        contentDescription = "Open note in full screen",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { onDeleteNote(note.id) }) {
                                    Icon(
                                        imageVector = VidhyaIcons.Delete,
                                        contentDescription = "Delete note",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InputSourceMethodTab(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Interactive Kalam Handwritten Note Reader with:
 * - Authentic Kalam typography on ruled notebook paper (never covered in blur/glass)
 * - Unobtrusive dot indicators on key terms (Define, Simplify, Explain, Ask AI)
 * - Long-press / tap contextual actions on paragraphs & topic headings (Summarize, Explain, Flashcards, Quiz, Read Aloud)
 * - Real multi-page Kalam PDF Export
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun KalamInteractiveNoteReader(
    note: StudyNote,
    isFullScreen: Boolean,
    liquidGlassEnabled: Boolean,
    contextualAiLoading: Boolean,
    contextualAiResult: Pair<String, String>?,
    onBack: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onRunContextualAction: (action: String, selectedText: String, customQuestion: String?) -> Unit,
    onClearContextualResult: () -> Unit,
    onExportPdf: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isDark = LocalDarkTheme.current

    val paperBg = if (isDark) KalamPaperDark else KalamPaperLight
    val ruleLineColor = if (isDark) KalamRuleLineDark else KalamRuleLineLight
    val inkColor = if (isDark) KalamInkDark else KalamInkLight
    val termHighlightBg = if (isDark) KalamHighlightTermDark else KalamHighlightTermLight

    val keyTerms: List<KeyTermItem> = remember(note.keyTermsJson) {
        GeminiAiService.deserializeKeyTerms(note.keyTermsJson)
    }

    var selectedTerm by remember { mutableStateOf<KeyTermItem?>(null) }
    var selectedParagraphOrTopic by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // isHeading to text
    var showAskAiInput by remember { mutableStateOf(false) }
    var customAiQuestion by remember { mutableStateOf("") }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
        tts = engine
        onDispose {
            try {
                engine.stop()
                engine.shutdown()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Reader Top Toolbar with Full Screen Toggle & PDF Export
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("note_reader_back_button")) {
                        Icon(
                            imageVector = if (isFullScreen) VidhyaIcons.Close else VidhyaIcons.Back,
                            contentDescription = if (isFullScreen) "Exit full screen" else "Back to notes"
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${note.subject} • Kalam Handwritten Note",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onToggleFullScreen,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("note_fullscreen_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isFullScreen) VidhyaIcons.FullscreenExit else VidhyaIcons.Fullscreen,
                            contentDescription = if (isFullScreen) "Exit Full Screen" else "Full Screen",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFullScreen) "Exit Full" else "Full Screen",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Button(
                        onClick = onExportPdf,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("export_kalam_pdf_button")
                    ) {
                        Icon(imageVector = VidhyaIcons.PdfImport, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Box(modifier = Modifier.weight(1f)) {
            val blocks = remember(note.kalamNoteContent) {
                note.kalamNoteContent.split("\n\n", "\n").map { it.trim() }.filter { it.isNotBlank() }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(paperBg)
                    .drawBehind {
                        val stepPx = 34.dp.toPx()
                        var y = stepPx
                        while (y < size.height) {
                            drawLine(
                                color = ruleLineColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                            y += stepPx
                        }
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Preserved Source Material Banner (Distinguishes extracted facts from AI notes)
                if (!note.sourceSummary.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Preserved Source Summary (${note.sourceType.uppercase(Locale.US)})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note.sourceSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Interactive Key Terms Bar with Subtle Dot Indicators (•)
                if (keyTerms.isNotEmpty()) {
                    Text(
                        text = "Interactive Key Terms (Tap any • term for instant definition or simplification):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        keyTerms.forEach { termItem ->
                            val isSelected = selectedTerm?.term == termItem.term
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) termHighlightBg else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedTerm = termItem
                                        selectedParagraphOrTopic = null
                                    }
                                    .testTag("key_term_${termItem.term}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = termItem.term,
                                        fontFamily = KalamFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = inkColor
                                    )
                                }
                            }
                        }
                    }
                }

                // Kalam Handwritten Title
                Text(
                    text = note.title,
                    fontFamily = KalamFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // Kalam Handwritten Paragraphs & Topic Headings (Long-press or tap for contextual intelligence)
                blocks.forEach { block ->
                    val isHeading = block.startsWith("#")
                    val cleanText = block.trimStart('#', ' ')
                    val isSelectedBlock = selectedParagraphOrTopic?.second == cleanText

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelectedBlock) termHighlightBg.copy(alpha = 0.45f) else Color.Transparent
                            )
                            .combinedClickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedParagraphOrTopic = isHeading to cleanText
                                    selectedTerm = null
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedParagraphOrTopic = isHeading to cleanText
                                    selectedTerm = null
                                }
                            )
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = cleanText,
                            fontFamily = KalamFontFamily,
                            fontWeight = if (isHeading) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if (isHeading) 22.sp else 19.sp,
                            lineHeight = if (isHeading) 32.sp else 30.sp,
                            color = if (isHeading) MaterialTheme.colorScheme.primary else inkColor
                        )
                    }
                }

                if (!note.formulasSummary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Important Formulas & Rules",
                        fontFamily = KalamFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        lineHeight = 32.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = note.formulasSummary,
                        fontFamily = KalamFontFamily,
                        fontSize = 19.sp,
                        lineHeight = 30.sp,
                        color = inkColor
                    )
                }

                if (!note.revisionSummary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Quick Revision Summary",
                        fontFamily = KalamFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        lineHeight = 32.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = note.revisionSummary,
                        fontFamily = KalamFontFamily,
                        fontSize = 19.sp,
                        lineHeight = 30.sp,
                        color = inkColor
                    )
                }

                Spacer(modifier = Modifier.height(140.dp))
            }

            // Selective Liquid Glass Contextual Toolbar for Selected Term or Paragraph/Heading
            if (selectedTerm != null || selectedParagraphOrTopic != null || contextualAiLoading || contextualAiResult != null) {
                LiquidGlassBar(
                    enabled = liquidGlassEnabled,
                    cornerRadius = 18.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedTerm != null) {
                            val term = selectedTerm!!
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Term • ${term.term}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { selectedTerm = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(imageVector = VidhyaIcons.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(
                                text = term.definition,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (term.simplifiedExplanation.isNotBlank()) {
                                Text(
                                    text = "Simple: ${term.simplifiedExplanation}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                listOf("Define", "Simplify", "Explain", "Ask AI").forEach { action ->
                                    OutlinedButton(
                                        onClick = {
                                            if (action == "Ask AI") {
                                                showAskAiInput = true
                                            } else {
                                                onRunContextualAction(action, term.term, null)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(action, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        } else if (selectedParagraphOrTopic != null) {
                            val (isHeading, text) = selectedParagraphOrTopic!!
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isHeading) "Topic Actions: ${text.take(35)}" else "Paragraph Actions",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = { selectedParagraphOrTopic = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(imageVector = VidhyaIcons.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                                }
                            }
                            val actions = if (isHeading) {
                                listOf("Summarize", "Explain", "Flashcards", "Quiz", "Ask AI")
                            } else {
                                listOf("Simplify", "Summarize", "Explain", "Read Aloud", "Ask AI")
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                actions.forEach { action ->
                                    OutlinedButton(
                                        onClick = {
                                            when (action) {
                                                "Read Aloud" -> {
                                                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "note_tts")
                                                }
                                                "Ask AI" -> {
                                                    showAskAiInput = true
                                                }
                                                else -> {
                                                    onRunContextualAction(action, text, null)
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(action, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        if (showAskAiInput) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = customAiQuestion,
                                    onValueChange = { customAiQuestion = it },
                                    placeholder = { Text("Ask a question about this selection...") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        val target = selectedTerm?.term ?: selectedParagraphOrTopic?.second.orEmpty()
                                        onRunContextualAction("Ask AI", target, customAiQuestion)
                                        showAskAiInput = false
                                        customAiQuestion = ""
                                    }
                                ) {
                                    Text("Ask")
                                }
                            }
                        }

                        if (contextualAiLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generating explanation (original note preserved)...", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        if (contextualAiResult != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = contextualAiResult.first,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(onClick = onClearContextualResult) {
                                    Text("Dismiss")
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = contextualAiResult.second,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
