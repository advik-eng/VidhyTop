package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.StudyNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Handles real PDF page rendering (`PdfRenderer`) and real Kalam handwritten PDF export (`PdfDocument`).
 */
object PdfDocumentHelper {

    data class RenderedPdfInfo(
        val pageCount: Int,
        val fileName: String
    )

    suspend fun openPdfInfo(context: Context, uri: Uri): Result<RenderedPdfInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalArgumentException("Could not open the selected PDF file.")
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val count = renderer.pageCount
                    if (count <= 0) throw IllegalStateException("The selected PDF has no readable pages.")
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported_Document.pdf"
                    RenderedPdfInfo(pageCount = count, fileName = name)
                }
            }
        }
    }

    suspend fun renderPdfPage(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        scaleFactor: Float = 1.5f
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalArgumentException("Unable to read PDF file descriptor.")
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val safeIndex = pageIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
                    renderer.openPage(safeIndex).use { page ->
                        val width = (page.width * scaleFactor).toInt().coerceIn(400, 2048)
                        val height = (page.height * scaleFactor).toInt().coerceIn(500, 2800)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }
    }

    /**
     * Exports a StudyNote into a real, multi-page A4 PDF using the bundled Kalam handwriting typeface.
     */
    suspend fun exportNoteToKalamPdf(
        context: Context,
        note: StudyNote
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val kalamTypeface: Typeface = try {
                ResourcesCompat.getFont(context, R.font.kalam) ?: Typeface.SERIF
            } catch (e: Exception) {
                Typeface.SERIF
            }
            val kalamBoldTypeface: Typeface = Typeface.create(kalamTypeface, Typeface.BOLD)

            val pdfDocument = PdfDocument()
            val pageWidth = 595 // Standard A4 width in postscript points
            val pageHeight = 842 // Standard A4 height in postscript points
            val margin = 44f
            val contentWidth = (pageWidth - margin * 2).toInt()

            val bgPaint = Paint().apply {
                color = Color.rgb(252, 251, 247) // Warm notebook paper
                style = Paint.Style.FILL
            }
            val ruleLinePaint = Paint().apply {
                color = Color.rgb(229, 224, 213)
                strokeWidth = 0.8f
            }
            val marginLinePaint = Paint().apply {
                color = Color.rgb(224, 165, 165)
                strokeWidth = 1f
            }

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = kalamBoldTypeface
                textSize = 22f
                color = Color.rgb(24, 43, 89)
            }
            val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = kalamTypeface
                textSize = 11.5f
                color = Color.rgb(82, 88, 102)
            }
            val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = kalamBoldTypeface
                textSize = 16.5f
                color = Color.rgb(30, 58, 138)
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = kalamTypeface
                textSize = 13.5f
                color = Color.rgb(27, 37, 56)
            }

            val fullTextBuilder = StringBuilder()
            fullTextBuilder.append(note.kalamNoteContent.trim())
            if (!note.formulasSummary.isNullOrBlank()) {
                fullTextBuilder.append("\n\n# Important Formulas & Rules\n")
                fullTextBuilder.append(note.formulasSummary.trim())
            }
            if (!note.revisionSummary.isNullOrBlank()) {
                fullTextBuilder.append("\n\n# Quick Revision Summary\n")
                fullTextBuilder.append(note.revisionSummary.trim())
            }

            val paragraphs = fullTextBuilder.toString().split("\n")
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var currentPage = pdfDocument.startPage(pageInfo)
            var canvas = currentPage.canvas

            fun drawNotebookBackground(c: Canvas, pageNum: Int) {
                c.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)
                var yRule = 76f
                while (yRule < pageHeight - 40f) {
                    c.drawLine(margin - 8f, yRule, pageWidth - margin + 8f, yRule, ruleLinePaint)
                    yRule += 22f
                }
                c.drawLine(margin - 6f, 0f, margin - 6f, pageHeight.toFloat(), marginLinePaint)
                c.drawText("Vidhya • Kalam Handwritten Revision Notes • Page $pageNum", margin, pageHeight - 20f, metaPaint)
            }

            drawNotebookBackground(canvas, pageNumber)
            var currentY = margin

            // Draw Title Header on Page 1
            val titleLayout = StaticLayout.Builder
                .obtain(note.title, 0, note.title.length, titlePaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.05f)
                .build()
            canvas.save()
            canvas.translate(margin, currentY)
            titleLayout.draw(canvas)
            canvas.restore()
            currentY += titleLayout.height + 6f

            canvas.drawText("Subject: ${note.subject}   •   Source: ${note.sourceType.uppercase()}", margin, currentY + 12f, metaPaint)
            currentY += 28f

            for (rawLine in paragraphs) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) {
                    currentY += 10f
                    continue
                }
                val isHeading = trimmed.startsWith("#")
                val cleanLine = trimmed.trimStart('#', ' ')
                val activePaint = if (isHeading) headingPaint else bodyPaint

                val staticLayout = StaticLayout.Builder
                    .obtain(cleanLine, 0, cleanLine.length, activePaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(3f, 1.15f)
                    .build()

                if (currentY + staticLayout.height > pageHeight - 52f) {
                    pdfDocument.finishPage(currentPage)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas
                    drawNotebookBackground(canvas, pageNumber)
                    currentY = margin
                }

                if (isHeading) currentY += 6f
                canvas.save()
                canvas.translate(margin, currentY)
                staticLayout.draw(canvas)
                canvas.restore()
                currentY += staticLayout.height + 6f
            }

            pdfDocument.finishPage(currentPage)

            val pdfDir = File(context.cacheDir, "pdfs").apply { mkdirs() }
            val safeFileName = note.title
                .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
                .trim()
                .replace("\\s+".toRegex(), "_")
                .take(40)
                .ifBlank { "Vidhya_Kalam_Notes" }
            val outFile = File(pdfDir, "${safeFileName}_Kalam.pdf")
            FileOutputStream(outFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            if (!outFile.exists() || outFile.length() == 0L) {
                throw IllegalStateException("PDF export produced an empty file.")
            }
            outFile
        }
    }

    fun sharePdfFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Kalam Notes PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

/**
 * Real acoustic Focus Sound & White Noise synthesizer with proper Android AudioFocus management.
 */
data class FocusSoundTrack(
    val id: String,
    val title: String,
    val category: String,
    val description: String
)

class FocusAudioEngine(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null

    @Volatile
    var volume: Float = 0.55f

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    var activeTrackId: String? = null
        private set

    val availableTracks = listOf(
        FocusSoundTrack("brown_noise", "Deep Brown Noise", "Brown Noise", "Warm low-frequency masking for deep reading"),
        FocusSoundTrack("soft_rain", "Steady Study Rain", "Rain", "Consistent gentle rainfall diffusion"),
        FocusSoundTrack("white_noise", "Balanced White Noise", "White Noise", "Uniform acoustic masking to block chatter"),
        FocusSoundTrack("ocean_waves", "Calm Ocean Tide", "Ocean", "Slow rhythmic wave cadence (0.12 Hz)"),
        FocusSoundTrack("forest_wind", "Pine Forest Breeze", "Wind", "Soft filtered breeze through canopy"),
        FocusSoundTrack("study_drone", "Warm Academic Drone", "Instrumental", "Pure harmonic 216 Hz & 324 Hz sine chord")
    )

    fun startSound(
        trackId: String,
        scope: CoroutineScope,
        onAudioFocusLost: () -> Unit = {}
    ) {
        stopSound()
        val granted = requestAudioFocus {
            stopSound()
            onAudioFocusLost()
        }
        if (!granted) return

        activeTrackId = trackId
        isPlaying = true

        audioJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 22050
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            val buffer = ShortArray(1024)
            var lastOut = 0.0
            var phase = 0.0
            while (isActive && isPlaying) {
                val currentVol = volume.coerceIn(0f, 1f).toDouble()
                for (i in buffer.indices) {
                    val sample: Double = when (trackId) {
                        "white_noise" -> (Random.nextDouble(-1.0, 1.0) * 0.28)
                        "brown_noise" -> {
                            val white = Random.nextDouble(-1.0, 1.0)
                            lastOut = (lastOut + (0.02 * white)) / 1.02
                            (lastOut * 1.8).coerceIn(-0.9, 0.9)
                        }
                        "soft_rain" -> {
                            val white = Random.nextDouble(-1.0, 1.0)
                            lastOut = 0.85 * lastOut + 0.15 * white
                            lastOut * 0.45
                        }
                        "ocean_waves" -> {
                            val white = Random.nextDouble(-1.0, 1.0)
                            lastOut = (lastOut + (0.03 * white)) / 1.03
                            val lfo = 0.55 + 0.45 * sin(2.0 * PI * 0.12 * (phase / sampleRate))
                            lastOut * lfo * 1.4
                        }
                        "forest_wind" -> {
                            val white = Random.nextDouble(-1.0, 1.0)
                            lastOut = 0.94 * lastOut + 0.06 * white
                            val lfo = 0.65 + 0.35 * sin(2.0 * PI * 0.08 * (phase / sampleRate))
                            lastOut * lfo * 0.8
                        }
                        else -> {
                            val t = phase / sampleRate
                            val f1 = sin(2.0 * PI * 216.0 * t)
                            val f2 = sin(2.0 * PI * 324.0 * t) * 0.5
                            (f1 + f2) * 0.22
                        }
                    }
                    phase += 1.0
                    buffer[i] = (sample * currentVol * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }
                try {
                    track.write(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    fun stopSound() {
        isPlaying = false
        audioJob?.cancel()
        audioJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore release errors
        }
        audioTrack = null
        abandonAudioFocus()
    }

    private fun requestAudioFocus(onLost: () -> Unit): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        onLost()
                    }
                }
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            true
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        }
        focusRequest = null
    }
}
