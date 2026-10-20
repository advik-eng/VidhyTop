package com.example.data.remote

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class YouTubeSearchResponse(
    val items: List<YouTubeSearchItem> = emptyList()
)

@Serializable
data class YouTubeSearchItem(
    val id: YouTubeVideoId? = null,
    val snippet: YouTubeSnippet? = null
)

@Serializable
data class YouTubeVideoId(
    val videoId: String? = null
)

@Serializable
data class YouTubeSnippet(
    val title: String = "",
    val description: String = "",
    val channelTitle: String = "",
    val publishedAt: String = "",
    val thumbnails: YouTubeThumbnails? = null
)

@Serializable
data class YouTubeThumbnails(
    val high: YouTubeThumbnail? = null,
    val medium: YouTubeThumbnail? = null
)

@Serializable
data class YouTubeThumbnail(
    val url: String = ""
)

interface YouTubeDataApi {
    @GET("youtube/v3/search")
    suspend fun searchEducationalVideos(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("videoCategoryId") videoCategoryId: String = "27", // 27 = Education
        @Query("safeSearch") safeSearch: String = "strict",
        @Query("maxResults") maxResults: Int = 15,
        @Query("key") apiKey: String
    ): YouTubeSearchResponse
}

data class EduVideoItem(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val description: String,
    val subjectCategory: String,
    val durationText: String,
    val thumbnailUrl: String,
    val educationalReason: String,
    val isSampleCatalog: Boolean = false
)

sealed interface EduTubeSearchResult {
    data class Success(val videos: List<EduVideoItem>, val fromLiveApi: Boolean) : EduTubeSearchResult
    data class BlockedEntertainment(val reasonMessage: String, val matchedTerm: String) : EduTubeSearchResult
    data class Error(val message: String) : EduTubeSearchResult
}

object EduTubeService {
    const val BLOCKED_CONTENT_MESSAGE =
        "This content isn’t available in EduTube. EduTube is designed for educational learning."

    val SUBJECT_CATEGORIES = listOf(
        "All Subjects",
        "Mathematics",
        "Science",
        "Social Science",
        "English",
        "Languages",
        "Computer Science",
        "Exam Preparation",
        "General Learning"
    )

    // Layered entertainment blocklist for query analysis & metadata filtering
    private val ENTERTAINMENT_BLOCK_TERMS = listOf(
        "prank", "pranks", "vlog", "vlogs", "gossip", "celebrity", "tiktok", "reels",
        "funny compilation", "memes", "meme", "roast", "diss track", "music video",
        "movie trailer", "gameplay", "walkthrough", "fortnite", "gta", "valorant",
        "pubg", "free fire", "minecraft survival", "roblox", "unboxing", "haul",
        "asmr eating", "mukbang", "reality show", "drama alert", "reaction video",
        "skit", "comedy club", "standup comedy", "horoscope", "astrology"
    )

    private val EDUCATIONAL_POSITIVE_SIGNALS = listOf(
        "lecture", "tutorial", "explained", "concept", "derivation", "theorem", "formula",
        "exam", "revision", "chapter", "syllabus", "ncert", "cbse", "icse", "jee", "neet",
        "calculus", "algebra", "geometry", "trigonometry", "physics", "chemistry", "biology",
        "history", "geography", "civics", "economics", "grammar", "literature", "essay",
        "programming", "algorithm", "data structure", "python", "kotlin", "science", "math",
        "lesson", "course", "study", "academy", "education", "how to solve", "problem"
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val api: YouTubeDataApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(YouTubeDataApi::class.java)
    }

    fun isYouTubeKeyConfigured(): Boolean {
        val key = BuildConfig.YOUTUBE_API_KEY
        return key.isNotBlank() && key != "MY_YOUTUBE_API_KEY" && !key.startsWith("YOUR_")
    }

    /**
     * Layered Educational Classifier:
     * 1. Evaluates query against entertainment blocklist.
     * 2. Evaluates video metadata (title, channel, description).
     */
    fun classifyQuery(query: String): Pair<Boolean, String?> {
        val normalized = query.trim().lowercase(Locale.US)
        if (normalized.isEmpty()) return true to null

        for (blocked in ENTERTAINMENT_BLOCK_TERMS) {
            if (normalized.contains(blocked)) {
                return false to blocked
            }
        }
        return true to null
    }

    private fun isVideoMetadataEducational(title: String, description: String, channel: String): Boolean {
        val combined = "$title $description $channel".lowercase(Locale.US)
        for (blocked in ENTERTAINMENT_BLOCK_TERMS) {
            if (combined.contains(blocked)) return false
        }
        return true
    }

    suspend fun searchVideos(
        query: String,
        selectedCategory: String
    ): EduTubeSearchResult = withContext(Dispatchers.IO) {
        val (isAllowed, matchedBlockedTerm) = classifyQuery(query)
        if (!isAllowed) {
            return@withContext EduTubeSearchResult.BlockedEntertainment(
                reasonMessage = BLOCKED_CONTENT_MESSAGE,
                matchedTerm = matchedBlockedTerm ?: query
            )
        }

        if (isYouTubeKeyConfigured()) {
            try {
                val effectiveQuery = buildString {
                    if (query.isNotBlank()) append(query.trim())
                    if (selectedCategory != "All Subjects") {
                        append(" $selectedCategory")
                    }
                    append(" educational lecture lesson")
                }.trim()

                val response = api.searchEducationalVideos(
                    query = effectiveQuery,
                    apiKey = BuildConfig.YOUTUBE_API_KEY
                )

                val filtered = response.items.mapNotNull { item ->
                    val vidId = item.id?.videoId ?: return@mapNotNull null
                    val snippet = item.snippet ?: return@mapNotNull null
                    if (!isVideoMetadataEducational(snippet.title, snippet.description, snippet.channelTitle)) {
                        return@mapNotNull null
                    }
                    EduVideoItem(
                        videoId = vidId,
                        title = decodeHtmlEntities(snippet.title),
                        channelTitle = snippet.channelTitle,
                        description = snippet.description,
                        subjectCategory = if (selectedCategory == "All Subjects") inferSubject(snippet.title) else selectedCategory,
                        durationText = "Educational Lesson",
                        thumbnailUrl = snippet.thumbnails?.high?.url
                            ?: snippet.thumbnails?.medium?.url
                            ?: "https://i.ytimg.com/vi/$vidId/hqdefault.jpg",
                        educationalReason = "Verified Category 27 (Education) & Metadata Classifier",
                        isSampleCatalog = false
                    )
                }

                return@withContext EduTubeSearchResult.Success(
                    videos = filtered,
                    fromLiveApi = true
                )
            } catch (e: Exception) {
                return@withContext EduTubeSearchResult.Error(
                    "YouTube Data API error: ${e.localizedMessage ?: "Check quota or YOUTUBE_API_KEY in AI Studio Secrets."}"
                )
            }
        } else {
            // Curated Verified Educational Catalog (explicitly labeled as sample educational catalog when YOUTUBE_API_KEY is not configured)
            val catalog = getCuratedEducationalCatalog()
            val filtered = catalog.filter { item ->
                val matchesCategory = selectedCategory == "All Subjects" ||
                    item.subjectCategory.equals(selectedCategory, ignoreCase = true)
                val matchesQuery = query.isBlank() ||
                    item.title.contains(query, ignoreCase = true) ||
                    item.description.contains(query, ignoreCase = true) ||
                    item.subjectCategory.contains(query, ignoreCase = true) ||
                    item.channelTitle.contains(query, ignoreCase = true)
                matchesCategory && matchesQuery
            }
            return@withContext EduTubeSearchResult.Success(
                videos = filtered,
                fromLiveApi = false
            )
        }
    }

    private fun inferSubject(title: String): String {
        val lower = title.lowercase(Locale.US)
        return when {
            lower.contains("math") || lower.contains("calculus") || lower.contains("algebra") || lower.contains("geometry") -> "Mathematics"
            lower.contains("physics") || lower.contains("chemistry") || lower.contains("biology") || lower.contains("science") -> "Science"
            lower.contains("history") || lower.contains("geography") || lower.contains("economics") -> "Social Science"
            lower.contains("english") || lower.contains("grammar") || lower.contains("essay") -> "English"
            lower.contains("code") || lower.contains("programming") || lower.contains("computer") || lower.contains("algorithm") -> "Computer Science"
            lower.contains("exam") || lower.contains("revision") || lower.contains("strategy") -> "Exam Preparation"
            else -> "General Learning"
        }
    }

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    fun getCuratedEducationalCatalog(): List<EduVideoItem> = listOf(
        EduVideoItem(
            videoId = "WUvTyaaNkzM",
            title = "The Essence of Calculus — Visual Introduction to Derivatives & Integrals",
            channelTitle = "3Blue1Brown",
            description = "A visual, intuitive exploration of fundamental calculus concepts, limits, derivatives, and integration for high school and college students.",
            subjectCategory = "Mathematics",
            durationText = "17:04",
            thumbnailUrl = "https://i.ytimg.com/vi/WUvTyaaNkzM/hqdefault.jpg",
            educationalReason = "Curated Mathematics Visual Lecture (Demonstration Catalog)",
            isSampleCatalog = true
        ),
        EduVideoItem(
            videoId = "kKKM8Y-u7ds",
            title = "Newton's Laws of Motion — Classical Mechanics & Free Body Diagrams",
            channelTitle = "CrashCourse Physics",
            description = "Comprehensive breakdown of Newton's First, Second, and Third Laws, inertia, net force equations, and problem-solving strategies.",
            subjectCategory = "Science",
            durationText = "11:04",
            thumbnailUrl = "https://i.ytimg.com/vi/kKKM8Y-u7ds/hqdefault.jpg",
            educationalReason = "Curated Physics Syllabus Lesson (Demonstration Catalog)",
            isSampleCatalog = true
        ),
        EduVideoItem(
            videoId = "FSyAehMdpyI",
            title = "The Periodic Table & Atomic Structure — Shells, Orbitals & Valency",
            channelTitle = "CrashCourse Chemistry",
            description = "Understand periodic trends, electronegativity, ionization energy, and electron configurations across s, p, d, and f blocks.",
            subjectCategory = "Science",
            durationText = "11:22",
            thumbnailUrl = "https://i.ytimg.com/vi/FSyAehMdpyI/hqdefault.jpg",
            educationalReason = "Curated Chemistry Syllabus Lesson (Demonstration Catalog)",
            isSampleCatalog = true
        ),
        EduVideoItem(
            videoId = "8mAITcNt710",
            title = "CS50 Lecture — Introduction to Computer Science & Algorithmic Thinking",
            channelTitle = "Harvard CS50",
            description = "Foundational computer science concepts: binary representation, algorithms, pseudocode, complexity, and structured problem solving.",
            subjectCategory = "Computer Science",
            durationText = "24:15",
            thumbnailUrl = "https://i.ytimg.com/vi/8mAITcNt710/hqdefault.jpg",
            educationalReason = "Curated Computer Science Lecture (Demonstration Catalog)",
            isSampleCatalog = true
        ),
        EduVideoItem(
            videoId = "Yocja_N5s1I",
            title = "The Agricultural Revolution & Early Civilizations — World History",
            channelTitle = "CrashCourse World History",
            description = "How early human societies transitioned to settled agriculture, river valley civilizations, and structured governance.",
            subjectCategory = "Social Science",
            durationText = "11:10",
            thumbnailUrl = "https://i.ytimg.com/vi/Yocja_N5s1I/hqdefault.jpg",
            educationalReason = "Curated Social Science Lecture (Demonstration Catalog)",
            isSampleCatalog = true
        ),
        EduVideoItem(
            videoId = "IlU-zDU6aQ0",
            title = "Active Recall & Spaced Repetition — Evidence-Based Exam Preparation",
            channelTitle = "Ali Abdaal Study Lab",
            description = "Scientific study techniques based on cognitive psychology: active retrieval practice, spaced repetition schedules, and past-paper mastery.",
            subjectCategory = "Exam Preparation",
            durationText = "14:38",
            thumbnailUrl = "https://i.ytimg.com/vi/IlU-zDU6aQ0/hqdefault.jpg",
            educationalReason = "Curated Exam Strategy Guide (Demonstration Catalog)",
            isSampleCatalog = true
        ),
        EduVideoItem(
            videoId = "Julh11wE2aE",
            title = "Academic Essay Writing — Thesis Statements, Paragraph Structure & Clarity",
            channelTitle = "Oxford Academic Skills",
            description = "Learn how to structure analytical essays, build coherent arguments, and write clear topic sentences for English exams.",
            subjectCategory = "English",
            durationText = "12:20",
            thumbnailUrl = "https://i.ytimg.com/vi/Julh11wE2aE/hqdefault.jpg",
            educationalReason = "Curated English Composition Lesson (Demonstration Catalog)",
            isSampleCatalog = true
        ),
        EduVideoItem(
            videoId = "fNk_zzaMoSs",
            title = "Vectors & Linear Algebra Foundations — Geometric & Algebraic Views",
            channelTitle = "3Blue1Brown",
            description = "Understand vectors, linear combinations, span, and basis vectors with clear geometric intuition.",
            subjectCategory = "Mathematics",
            durationText = "09:52",
            thumbnailUrl = "https://i.ytimg.com/vi/fNk_zzaMoSs/hqdefault.jpg",
            educationalReason = "Curated Mathematics Visual Lecture (Demonstration Catalog)",
            isSampleCatalog = true
        )
    )
}
