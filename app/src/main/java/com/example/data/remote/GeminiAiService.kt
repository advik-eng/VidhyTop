package com.example.data.remote

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.KeyTermItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val responseMimeType: String? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
data class Candidate(
    val content: Content? = null
)

interface GeminiRestApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

data class GeneratedNoteBundle(
    val title: String,
    val subject: String,
    val sourceSummary: String,
    val kalamNoteContent: String,
    val keyTerms: List<KeyTermItem>,
    val formulasSummary: String,
    val revisionSummary: String
)

object GeminiAiService {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val api: GeminiRestApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiRestApi::class.java)
    }

    fun isConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY" && !key.startsWith("YOUR_")
    }

    private fun requireValidKey(): String {
        val key = BuildConfig.GEMINI_API_KEY
        if (!isConfigured()) {
            throw IllegalStateException(
                "Gemini AI key is not configured. Please add your GEMINI_API_KEY in the AI Studio Secrets panel to enable live AI tutoring and note generation."
            )
        }
        return key
    }

    private suspend fun callGeminiWithFallback(
        apiKey: String,
        request: GenerateContentRequest
    ): GenerateContentResponse {
        return try {
            api.generateContent(
                model = "gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )
        } catch (e: Exception) {
            api.generateContent(
                model = "gemini-flash-latest",
                apiKey = apiKey,
                request = request
            )
        }
    }

    suspend fun askTutor(
        question: String,
        gradeLevel: String,
        subject: String,
        recentHistory: List<Pair<String, String>> = emptyList(),
        studyMaterialContext: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = requireValidKey()
            val systemPrompt = buildString {
                append("You are VidhyTop, a calm, clear, and encouraging academic tutor for a student in $gradeLevel studying $subject. ")
                append("Prioritize genuine understanding: use clear headings, step-by-step reasoning, concrete examples, and a brief analogy where helpful. ")
                append("Avoid filler or decorative emojis. Keep explanations well-structured for mobile reading.")
                if (!studyMaterialContext.isNullOrBlank()) {
                    append("\n\nReference Study Material Context:\n$studyMaterialContext")
                }
            }

            val historyContents = recentHistory.takeLast(6).map { (role, text) ->
                Content(
                    role = if (role == "assistant") "model" else "user",
                    parts = listOf(Part(text = text))
                )
            }

            val contents = historyContents + Content(
                role = "user",
                parts = listOf(Part(text = question))
            )

            val response = callGeminiWithFallback(
                apiKey = apiKey,
                request = GenerateContentRequest(
                    contents = contents,
                    generationConfig = GenerationConfig(temperature = 0.4f),
                    systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                )
            )
            val answer = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (answer.isNullOrBlank()) {
                throw IllegalStateException("The AI tutor returned an empty response. Please try again.")
            }
            answer.trim()
        }
    }

    suspend fun generateStructuredNotes(
        topicOrExtractedText: String,
        subject: String,
        gradeLevel: String,
        sourceType: String,
        photoBitmap: Bitmap? = null
    ): Result<GeneratedNoteBundle> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = requireValidKey()
            val prompt = """
                You are generating structured student revision notes for a $gradeLevel student in $subject.
                Source type: $sourceType.
                Input material / topic:
                $topicOrExtractedText

                Return a valid JSON object with the exact following keys:
                - "title": concise topic title (string)
                - "sourceSummary": accurate summary of the source facts (distinguishing extracted facts from explanations)
                - "kalamNoteContent": well-organised revision notes using markdown headings (# Main Topic, ## Subtopic), bullet points (- ), and clear paragraphs. Include definitions, core concepts, step-by-step explanations, and worked examples.
                - "formulasSummary": key formulas, equations, dates, or rules (string)
                - "revisionSummary": 4-5 high-yield exam revision bullet points (string)
                - "keyTerms": array of 4 to 6 important academic terms that appear in kalamNoteContent, each object having:
                    - "term": exact word or short phrase as it appears in the note
                    - "definition": clear textbook definition
                    - "simplifiedExplanation": plain-language explanation for quick understanding
                    - "example": a concrete example or application
            """.trimIndent()

            val parts = mutableListOf<Part>(Part(text = prompt))
            if (photoBitmap != null) {
                val outputStream = ByteArrayOutputStream()
                photoBitmap.compress(Bitmap.CompressFormat.JPEG, 82, outputStream)
                val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image)))
            }

            val response = callGeminiWithFallback(
                apiKey = apiKey,
                request = GenerateContentRequest(
                    contents = listOf(Content(role = "user", parts = parts)),
                    generationConfig = GenerationConfig(
                        temperature = 0.3f,
                        responseMimeType = "application/json"
                    )
                )
            )

            val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw IllegalStateException("Failed to generate notes from AI service.")

            parseGeneratedNoteBundle(rawText, subject, topicOrExtractedText)
        }
    }

    private fun parseGeneratedNoteBundle(
        rawJson: String,
        defaultSubject: String,
        fallbackTopic: String
    ): GeneratedNoteBundle {
        val cleaned = rawJson.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val obj = JSONObject(cleaned)
        val title = obj.optString("title", fallbackTopic.take(60)).ifBlank { fallbackTopic.take(60) }
        val sourceSummary = obj.optString("sourceSummary", "Structured from $fallbackTopic")
        val kalamContent = obj.optString("kalamNoteContent", "")
        val formulas = obj.optString("formulasSummary", "")
        val revision = obj.optString("revisionSummary", "")

        val termsList = mutableListOf<KeyTermItem>()
        val termsArr = obj.optJSONArray("keyTerms") ?: JSONArray()
        for (i in 0 until termsArr.length()) {
            val item = termsArr.optJSONObject(i) ?: continue
            val term = item.optString("term").trim()
            if (term.isNotBlank()) {
                termsList.add(
                    KeyTermItem(
                        term = term,
                        definition = item.optString("definition", "Core academic concept in $defaultSubject."),
                        simplifiedExplanation = item.optString("simplifiedExplanation", ""),
                        example = item.optString("example", "")
                    )
                )
            }
        }

        return GeneratedNoteBundle(
            title = title,
            subject = defaultSubject,
            sourceSummary = sourceSummary,
            kalamNoteContent = kalamContent.ifBlank { "# $title\n\n$sourceSummary" },
            keyTerms = termsList,
            formulasSummary = formulas,
            revisionSummary = revision
        )
    }

    suspend fun performContextualNoteAction(
        action: String, // "Define", "Simplify", "Explain", "Summarize", "Flashcards", "Quiz", "Ask AI"
        selectedText: String,
        noteContext: String,
        gradeLevel: String,
        customQuestion: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = requireValidKey()
            val instruction = when (action) {
                "Define" -> "Provide a precise academic definition of \"$selectedText\" for a $gradeLevel student, followed by its significance in the context of the note."
                "Simplify" -> "Rewrite and explain \"$selectedText\" in simple, everyday language with a relatable analogy for a $gradeLevel student."
                "Explain" -> "Provide a clear step-by-step explanation of \"$selectedText\", breaking down why it works and how it connects to the topic."
                "Summarize" -> "Summarize the following section into 3-4 concise revision bullet points without losing key facts:\n\"$selectedText\""
                "Flashcards" -> "Create 4 high-yield study flashcards (Format each as 'Q: ...' and 'A: ...') based on:\n\"$selectedText\""
                "Quiz" -> "Create 3 multiple-choice practice questions with 4 options (A-D) and brief answer explanations at the end based on:\n\"$selectedText\""
                else -> "Answer the student's question (${customQuestion ?: "Explain this"}) regarding \"$selectedText\"."
            }

            val prompt = "$instruction\n\nSurrounding Note Context:\n${noteContext.take(2500)}"
            val response = callGeminiWithFallback(
                apiKey = apiKey,
                request = GenerateContentRequest(
                    contents = listOf(Content(role = "user", parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(temperature = 0.35f)
                )
            )
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: throw IllegalStateException("No response received from AI service.")
        }
    }

    fun serializeKeyTerms(terms: List<KeyTermItem>): String {
        val arr = JSONArray()
        for (t in terms) {
            arr.put(
                JSONObject().apply {
                    put("term", t.term)
                    put("definition", t.definition)
                    put("simplifiedExplanation", t.simplifiedExplanation)
                    put("example", t.example)
                }
            )
        }
        return arr.toString()
    }

    fun deserializeKeyTerms(jsonStr: String?): List<KeyTermItem> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<KeyTermItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val term = obj.optString("term")
                if (term.isNotBlank()) {
                    list.add(
                        KeyTermItem(
                            term = term,
                            definition = obj.optString("definition"),
                            simplifiedExplanation = obj.optString("simplifiedExplanation"),
                            example = obj.optString("example")
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
