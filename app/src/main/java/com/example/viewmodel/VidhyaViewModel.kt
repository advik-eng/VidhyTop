package com.example.viewmodel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.FocusSession
import com.example.data.model.Friendship
import com.example.data.model.PublicStudyProfile
import com.example.data.model.SavedVideo
import com.example.data.model.StudyNote
import com.example.data.model.StudyTask
import com.example.data.model.TutorMessage
import com.example.data.model.UserProfile
import com.example.data.remote.EduTubeSearchResult
import com.example.data.remote.EduTubeService
import com.example.data.remote.EduVideoItem
import com.example.data.remote.GeminiAiService
import com.example.data.repository.VidhyaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class VidhyaDestination {
    AI_TUTOR,
    INTERACTIVE_NOTES,
    EXAM_PREP,
    FOCUS_MODE,
    ULTRA_FOCUS_LAUNCHER,
    EDUTUBE,
    FRIENDS_LEADERBOARD,
    PROGRESS_ANALYTICS,
    SETTINGS
}

enum class TimerPhase {
    IDLE,
    FOCUS_RUNNING,
    FOCUS_PAUSED,
    BREAK_RUNNING,
    BREAK_PAUSED,
    COMPLETED
}

data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

class VidhyaViewModel(
    private val repository: VidhyaRepository,
    val currentUserId: String,
    val currentUserDisplayName: String,
    val currentUserEmail: String
) : ViewModel() {

    // Navigation & Global Material Appearance
    private val _currentDestination = MutableStateFlow(VidhyaDestination.AI_TUTOR)
    val currentDestination: StateFlow<VidhyaDestination> = _currentDestination.asStateFlow()

    private val _liquidGlassEnabled = MutableStateFlow(true)
    val liquidGlassEnabled: StateFlow<Boolean> = _liquidGlassEnabled.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    // Two-tier Kotlin Flow error handling on all Firestore streams
    val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing userProfile", e)
            emit(UiState.Error(e.localizedMessage ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val publicProfileState: StateFlow<UiState<PublicStudyProfile?>> = repository.observePublicProfile(currentUserId)
        .map<PublicStudyProfile?, UiState<PublicStudyProfile?>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing publicProfile", e)
            emit(UiState.Error(e.localizedMessage ?: "Failed to load study statistics"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val notesState: StateFlow<UiState<List<StudyNote>>> = repository.observeNotes(currentUserId)
        .map<List<StudyNote>, UiState<List<StudyNote>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing notes", e)
            emit(UiState.Error(e.localizedMessage ?: "Failed to load notes"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val studyTasksState: StateFlow<UiState<List<StudyTask>>> = repository.observeStudyTasks(currentUserId)
        .map<List<StudyTask>, UiState<List<StudyTask>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing study tasks", e)
            emit(UiState.Error(e.localizedMessage ?: "Failed to load exam schedule"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val focusSessionsState: StateFlow<UiState<List<FocusSession>>> = repository.observeFocusSessions(currentUserId)
        .map<List<FocusSession>, UiState<List<FocusSession>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing focus sessions", e)
            emit(UiState.Error(e.localizedMessage ?: "Failed to load focus sessions"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val tutorMessagesState: StateFlow<UiState<List<TutorMessage>>> = repository.observeTutorMessages(currentUserId)
        .map<List<TutorMessage>, UiState<List<TutorMessage>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing tutor messages", e)
            emit(UiState.Error(e.localizedMessage ?: "Failed to load chat history"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val savedVideosState: StateFlow<UiState<List<SavedVideo>>> = repository.observeSavedVideos(currentUserId)
        .map<List<SavedVideo>, UiState<List<SavedVideo>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing saved videos", e)
            emit(UiState.Error(e.localizedMessage ?: "Failed to load saved videos"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val friendshipsState: StateFlow<UiState<List<Friendship>>> = repository.observeFriendships(currentUserId)
        .map<List<Friendship>, UiState<List<Friendship>>> { list ->
            refreshLeaderboardForFriendships(list)
            UiState.Success(list)
        }
        .catch { e ->
            Log.w(TAG, "Error observing friendships", e)
            emit(UiState.Error(e.localizedMessage ?: "Failed to load friends"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _leaderboardProfiles = MutableStateFlow<List<PublicStudyProfile>>(emptyList())
    val leaderboardProfiles: StateFlow<List<PublicStudyProfile>> = _leaderboardProfiles.asStateFlow()

    // AI Tutor State
    private val _isTutorThinking = MutableStateFlow(false)
    val isTutorThinking: StateFlow<Boolean> = _isTutorThinking.asStateFlow()

    private val _tutorError = MutableStateFlow<String?>(null)
    val tutorError: StateFlow<String?> = _tutorError.asStateFlow()

    private val _activeSubjectFilter = MutableStateFlow("General")
    val activeSubjectFilter: StateFlow<String> = _activeSubjectFilter.asStateFlow()

    private val _showLiveVoiceSheet = MutableStateFlow(false)
    val showLiveVoiceSheet: StateFlow<Boolean> = _showLiveVoiceSheet.asStateFlow()

    private var tutorRequestJob: Job? = null

    // Interactive Notes & Contextual AI State
    private val _isGeneratingNote = MutableStateFlow(false)
    val isGeneratingNote: StateFlow<Boolean> = _isGeneratingNote.asStateFlow()

    private val _noteGenerationError = MutableStateFlow<String?>(null)
    val noteGenerationError: StateFlow<String?> = _noteGenerationError.asStateFlow()

    private val _contextualAiLoading = MutableStateFlow(false)
    val contextualAiLoading: StateFlow<Boolean> = _contextualAiLoading.asStateFlow()

    private val _contextualAiResult = MutableStateFlow<Pair<String, String>?>(null) // Title to Output
    val contextualAiResult: StateFlow<Pair<String, String>?> = _contextualAiResult.asStateFlow()

    private val _isNoteFullScreen = MutableStateFlow(false)
    val isNoteFullScreen: StateFlow<Boolean> = _isNoteFullScreen.asStateFlow()

    // Focus & Pomodoro Timer State (Lifecycle-Safe via elapsedRealtime target)
    private val _pomodoroEnabled = MutableStateFlow(true)
    val pomodoroEnabled: StateFlow<Boolean> = _pomodoroEnabled.asStateFlow()

    private val _focusDurationMinutes = MutableStateFlow(25)
    val focusDurationMinutes: StateFlow<Int> = _focusDurationMinutes.asStateFlow()

    private val _breakDurationMinutes = MutableStateFlow(5)
    val breakDurationMinutes: StateFlow<Int> = _breakDurationMinutes.asStateFlow()

    private val _timerPhase = MutableStateFlow(TimerPhase.IDLE)
    val timerPhase: StateFlow<TimerPhase> = _timerPhase.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(25 * 60L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    private val _completedPomodoroIntervalsInSession = MutableStateFlow(0)
    val completedPomodoroIntervalsInSession: StateFlow<Int> = _completedPomodoroIntervalsInSession.asStateFlow()

    private var timerJob: Job? = null
    private var phaseTargetElapsedRealtimeMs: Long = 0L
    private var activeSessionTotalFocusMinutes: Long = 0L

    // EduTube State
    private val _eduTubeQuery = MutableStateFlow("")
    val eduTubeQuery: StateFlow<String> = _eduTubeQuery.asStateFlow()

    private val _eduTubeCategory = MutableStateFlow("All Subjects")
    val eduTubeCategory: StateFlow<String> = _eduTubeCategory.asStateFlow()

    private val _eduTubeResult = MutableStateFlow<EduTubeSearchResult>(
        EduTubeSearchResult.Success(EduTubeService.getCuratedEducationalCatalog(), fromLiveApi = false)
    )
    val eduTubeResult: StateFlow<EduTubeSearchResult> = _eduTubeResult.asStateFlow()

    private val _isEduTubeLoading = MutableStateFlow(false)
    val isEduTubeLoading: StateFlow<Boolean> = _isEduTubeLoading.asStateFlow()

    // Installed Apps for Ultra Focus Launcher
    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    init {
        performEduTubeSearch("", "All Subjects")
    }

    fun navigateTo(destination: VidhyaDestination) {
        if (destination != VidhyaDestination.INTERACTIVE_NOTES) {
            _isNoteFullScreen.value = false
        }
        _currentDestination.value = destination
    }

    fun setNoteFullScreen(fullScreen: Boolean) {
        _isNoteFullScreen.value = fullScreen
    }

    fun setLiquidGlassEnabled(enabled: Boolean) {
        _liquidGlassEnabled.value = enabled
    }

    fun clearBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun showBanner(message: String) {
        _statusBannerMessage.value = message
    }

    fun setActiveSubject(subject: String) {
        _activeSubjectFilter.value = subject
    }

    fun openLiveVoice(open: Boolean) {
        _showLiveVoiceSheet.value = open
    }

    // --- Onboarding & Profile ---

    fun completeOnboarding(
        displayName: String,
        schoolName: String,
        gradeLevel: String,
        subjects: List<String>,
        examType: String,
        nextExamDate: String,
        prepStartDate: String,
        dailyTargetMinutes: Long,
        preferredSchedule: String,
        pomodoroFocusMin: Long,
        pomodoroBreakMin: Long,
        topicsToPrepare: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val currentProfile = (userProfileState.value as? UiState.Success)?.data
            val referralCode = currentProfile?.referralCode?.takeIf { it.isNotBlank() }
                ?: repository.generateReferralCode(currentUserId, displayName)

            val updatedProfile = UserProfile(
                id = currentUserId,
                userId = currentUserId,
                displayName = displayName.ifBlank { currentUserDisplayName.ifBlank { "Student" } },
                schoolName = schoolName.takeIf { it.isNotBlank() },
                gradeLevel = gradeLevel.ifBlank { "Grade 10" },
                subjects = subjects.ifEmpty { listOf("Mathematics", "Science", "English") },
                examType = examType.takeIf { it.isNotBlank() },
                nextExamDate = nextExamDate.takeIf { it.isNotBlank() },
                prepStartDate = prepStartDate.ifBlank { todayIsoDate() },
                dailyStudyTargetMinutes = dailyTargetMinutes.coerceIn(15L, 720L),
                preferredSchedule = preferredSchedule.ifBlank { "Morning & Evening" },
                pomodoroFocusMinutes = pomodoroFocusMin.coerceIn(5L, 120L),
                pomodoroBreakMinutes = pomodoroBreakMin.coerceIn(1L, 60L),
                onboardingCompleted = true,
                referralCode = referralCode,
                shareStatsWithFriends = currentProfile?.shareStatsWithFriends ?: true,
                ultraFocusPasscodeHash = currentProfile?.ultraFocusPasscodeHash,
                allowedApps = currentProfile?.allowedApps ?: emptyList(),
                blockedApps = currentProfile?.blockedApps ?: listOf("com.google.android.youtube", "com.instagram.android"),
                themeMode = currentProfile?.themeMode ?: "system"
            )

            val result = repository.saveOnboardingAndProfile(updatedProfile)
            if (result.isSuccess) {
                _focusDurationMinutes.value = updatedProfile.pomodoroFocusMinutes.toInt()
                _breakDurationMinutes.value = updatedProfile.pomodoroBreakMinutes.toInt()
                _remainingSeconds.value = updatedProfile.pomodoroFocusMinutes * 60L

                // Generate personalized initial Exam Prep schedule if subjects/topics provided
                generatePersonalizedSchedule(
                    subjects = updatedProfile.subjects,
                    rawTopics = topicsToPrepare,
                    dailyMinutes = updatedProfile.dailyStudyTargetMinutes,
                    examName = updatedProfile.examType ?: "Upcoming Exams",
                    examDate = updatedProfile.nextExamDate
                )
                onComplete()
            } else {
                showBanner(result.exceptionOrNull()?.localizedMessage ?: "Failed to save onboarding settings.")
            }
        }
    }

    fun updateThemeMode(themeMode: String) {
        val current = (userProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.saveOnboardingAndProfile(current.copy(themeMode = themeMode))
        }
    }

    fun updateShareStatsPreference(share: Boolean) {
        val current = (userProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.saveOnboardingAndProfile(current.copy(shareStatsWithFriends = share))
            showBanner(if (share) "Study stats visible to accepted friends." else "Study stats hidden from leaderboard.")
        }
    }

    fun updateAllowedAndBlockedApps(allowed: List<String>, blocked: List<String>) {
        val current = (userProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.saveOnboardingAndProfile(
                current.copy(allowedApps = allowed.distinct(), blockedApps = blocked.distinct())
            )
        }
    }

    // --- Passcode Hashing & Account Recovery for Ultra Focus Launcher ---

    fun hashPasscode(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.trim().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setUltraFocusPasscode(newPin: String) {
        val current = (userProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val hash = if (newPin.isBlank()) null else hashPasscode(newPin)
            repository.saveOnboardingAndProfile(current.copy(ultraFocusPasscodeHash = hash))
            showBanner(if (hash != null) "Ultra Focus exit passcode configured." else "Ultra Focus passcode removed.")
        }
    }

    fun verifyUltraFocusPasscode(enteredPin: String): Boolean {
        val current = (userProfileState.value as? UiState.Success)?.data ?: return true
        val savedHash = current.ultraFocusPasscodeHash
        if (savedHash.isNullOrBlank()) return true
        return hashPasscode(enteredPin) == savedHash
    }

    fun resetPasscodeViaVerifiedGoogleAccount(onResetComplete: () -> Unit) {
        val current = (userProfileState.value as? UiState.Success)?.data ?: run {
            onResetComplete()
            return
        }
        viewModelScope.launch {
            repository.saveOnboardingAndProfile(current.copy(ultraFocusPasscodeHash = null))
            showBanner("Passcode reset verified via your authenticated Vidhya Google account.")
            onResetComplete()
        }
    }

    // --- AI Tutor & Live Voice ---

    fun askAiTutor(question: String, studyMaterialContext: String? = null) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return
        _tutorError.value = null
        _isTutorThinking.value = true

        val profile = (userProfileState.value as? UiState.Success)?.data
        val grade = profile?.gradeLevel ?: "Grade 10"
        val subject = _activeSubjectFilter.value
        val existingMessages = (tutorMessagesState.value as? UiState.Success)?.data ?: emptyList()
        val recentHistory = existingMessages.takeLast(6).map { it.role to it.content }

        tutorRequestJob?.cancel()
        tutorRequestJob = viewModelScope.launch {
            repository.saveTutorMessage(role = "user", content = trimmed, subjectContext = subject)

            val responseResult = GeminiAiService.askTutor(
                question = trimmed,
                gradeLevel = grade,
                subject = subject,
                recentHistory = recentHistory,
                studyMaterialContext = studyMaterialContext
            )

            _isTutorThinking.value = false
            responseResult.fold(
                onSuccess = { reply ->
                    repository.saveTutorMessage(role = "assistant", content = reply, subjectContext = subject)
                },
                onFailure = { err ->
                    _tutorError.value = err.localizedMessage ?: "Unable to reach AI Tutor."
                }
            )
        }
    }

    fun cancelTutorRequest() {
        tutorRequestJob?.cancel()
        _isTutorThinking.value = false
    }

    fun saveVoiceTranscriptToHistory(userSpoken: String, aiSpoken: String) {
        val subject = _activeSubjectFilter.value
        viewModelScope.launch {
            if (userSpoken.isNotBlank()) {
                repository.saveTutorMessage(role = "user", content = userSpoken.trim(), subjectContext = subject)
            }
            if (aiSpoken.isNotBlank()) {
                repository.saveTutorMessage(role = "assistant", content = aiSpoken.trim(), subjectContext = subject)
            }
        }
    }

    // --- Interactive Notes (Photo, PDF, Topic -> Kalam Handwritten Notes) ---

    fun generateKalamNotes(
        topicOrExtractedText: String,
        subject: String,
        sourceType: String,
        photoBitmap: Bitmap? = null,
        onNoteCreated: (String) -> Unit = {}
    ) {
        if (topicOrExtractedText.isBlank() && photoBitmap == null) {
            _noteGenerationError.value = "Please enter a topic or provide study material."
            return
        }
        _isGeneratingNote.value = true
        _noteGenerationError.value = null

        val profile = (userProfileState.value as? UiState.Success)?.data
        val grade = profile?.gradeLevel ?: "Grade 10"

        viewModelScope.launch {
            val bundleResult = GeminiAiService.generateStructuredNotes(
                topicOrExtractedText = topicOrExtractedText.ifBlank { "Extract text from this study material photo and organize comprehensive revision notes." },
                subject = subject.ifBlank { "General" },
                gradeLevel = grade,
                sourceType = sourceType,
                photoBitmap = photoBitmap
            )

            bundleResult.fold(
                onSuccess = { bundle ->
                    val saveRes = repository.createNote(
                        title = bundle.title,
                        subject = bundle.subject,
                        sourceType = sourceType,
                        sourceSummary = bundle.sourceSummary,
                        kalamNoteContent = bundle.kalamNoteContent,
                        keyTermsJson = GeminiAiService.serializeKeyTerms(bundle.keyTerms),
                        formulasSummary = bundle.formulasSummary,
                        revisionSummary = bundle.revisionSummary
                    )
                    _isGeneratingNote.value = false
                    saveRes.fold(
                        onSuccess = { newId ->
                            showBanner("Generated Kalam handwritten notes: ${bundle.title}")
                            onNoteCreated(newId)
                        },
                        onFailure = { err ->
                            _noteGenerationError.value = err.localizedMessage ?: "Failed to save generated note."
                        }
                    )
                },
                onFailure = { err ->
                    _isGeneratingNote.value = false
                    _noteGenerationError.value = err.localizedMessage ?: "Failed to generate notes."
                }
            )
        }
    }

    fun runContextualNoteAction(
        action: String,
        selectedText: String,
        fullNoteContent: String,
        customQuestion: String? = null
    ) {
        val profile = (userProfileState.value as? UiState.Success)?.data
        val grade = profile?.gradeLevel ?: "Grade 10"
        _contextualAiLoading.value = true
        _contextualAiResult.value = null

        viewModelScope.launch {
            val res = GeminiAiService.performContextualNoteAction(
                action = action,
                selectedText = selectedText,
                noteContext = fullNoteContent,
                gradeLevel = grade,
                customQuestion = customQuestion
            )
            _contextualAiLoading.value = false
            res.fold(
                onSuccess = { output ->
                    _contextualAiResult.value = "$action: ${selectedText.take(36)}" to output
                },
                onFailure = { err ->
                    _contextualAiResult.value = "Error" to (err.localizedMessage ?: "Action failed.")
                }
            )
        }
    }

    fun clearContextualAiResult() {
        _contextualAiResult.value = null
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
            showBanner("Note deleted.")
        }
    }

    // --- Exam Prep & Adaptive Schedule ---

    fun generatePersonalizedSchedule(
        subjects: List<String>,
        rawTopics: String,
        dailyMinutes: Long,
        examName: String,
        examDate: String?
    ) {
        viewModelScope.launch {
            val cleanSubjects = subjects.ifEmpty { listOf("Mathematics", "Science", "English") }
            val customTopics = rawTopics.split(",", "\n", ";").map { it.trim() }.filter { it.isNotBlank() }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance()

            val sessionDuration = (dailyMinutes / 2L).coerceIn(25L, 90L)
            for (dayOffset in 0..4) {
                val dateStr = sdf.format(cal.time)
                val primarySubj = cleanSubjects[dayOffset % cleanSubjects.size]
                val secondarySubj = cleanSubjects[(dayOffset + 1) % cleanSubjects.size]
                val topic1 = customTopics.getOrNull(dayOffset * 2) ?: "$primarySubj Core Concepts & Examples"
                val topic2 = customTopics.getOrNull(dayOffset * 2 + 1) ?: "$secondarySubj Active Recall & Practice"

                repository.createStudyTask(
                    subject = primarySubj,
                    topicTitle = topic1,
                    taskType = "study",
                    scheduledDate = dateStr,
                    plannedDurationMinutes = sessionDuration,
                    examName = examName,
                    examDate = examDate,
                    difficulty = "medium"
                )
                repository.createStudyTask(
                    subject = secondarySubj,
                    topicTitle = topic2,
                    taskType = if (dayOffset % 2 == 0) "revision" else "practice",
                    scheduledDate = dateStr,
                    plannedDurationMinutes = (dailyMinutes - sessionDuration).coerceIn(20L, 90L),
                    examName = examName,
                    examDate = examDate,
                    difficulty = "medium"
                )
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }

    fun addSingleStudyTask(
        subject: String,
        topicTitle: String,
        taskType: String,
        scheduledDate: String,
        durationMinutes: Long,
        difficulty: String
    ) {
        val profile = (userProfileState.value as? UiState.Success)?.data
        viewModelScope.launch {
            val res = repository.createStudyTask(
                subject = subject,
                topicTitle = topicTitle,
                taskType = taskType,
                scheduledDate = scheduledDate,
                plannedDurationMinutes = durationMinutes,
                examName = profile?.examType,
                examDate = profile?.nextExamDate,
                difficulty = difficulty
            )
            if (res.isSuccess) {
                showBanner("Added study task for $scheduledDate")
            }
        }
    }

    fun toggleStudyTaskCompleted(task: StudyTask) {
        viewModelScope.launch {
            repository.updateStudyTask(task.copy(completed = !task.completed))
        }
    }

    fun rescheduleTaskToTomorrow(task: StudyTask) {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val tomorrowStr = sdf.format(cal.time)
            val res = repository.updateStudyTask(task.copy(scheduledDate = tomorrowStr))
            if (res.isSuccess) {
                showBanner("Adaptive Schedule: Moved '${task.topicTitle}' to $tomorrowStr to keep today's target realistic.")
            }
        }
    }

    fun updateExamPlanSettings(
        examType: String,
        nextExamDate: String,
        dailyTargetMinutes: Long
    ) {
        val current = (userProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.saveOnboardingAndProfile(
                current.copy(
                    examType = examType,
                    nextExamDate = nextExamDate,
                    dailyStudyTargetMinutes = dailyTargetMinutes.coerceIn(15L, 720L)
                )
            )
            showBanner("Exam plan updated. Daily target set to $dailyTargetMinutes mins.")
        }
    }

    // --- Focus Mode & Optional Pomodoro Timer ---

    fun setPomodoroEnabled(enabled: Boolean) {
        _pomodoroEnabled.value = enabled
        if (_timerPhase.value == TimerPhase.IDLE) {
            _remainingSeconds.value = _focusDurationMinutes.value * 60L
        }
    }

    fun configureFocusAndBreakDurations(focusMin: Int, breakMin: Int) {
        val safeFocus = focusMin.coerceIn(5, 120)
        val safeBreak = breakMin.coerceIn(1, 60)
        _focusDurationMinutes.value = safeFocus
        _breakDurationMinutes.value = safeBreak
        if (_timerPhase.value == TimerPhase.IDLE) {
            _remainingSeconds.value = safeFocus * 60L
        }
        val current = (userProfileState.value as? UiState.Success)?.data
        if (current != null) {
            viewModelScope.launch {
                repository.saveOnboardingAndProfile(
                    current.copy(
                        pomodoroFocusMinutes = safeFocus.toLong(),
                        pomodoroBreakMinutes = safeBreak.toLong()
                    )
                )
            }
        }
    }

    fun startOrResumeTimer(context: Context) {
        when (_timerPhase.value) {
            TimerPhase.IDLE, TimerPhase.COMPLETED -> {
                _completedPomodoroIntervalsInSession.value = 0
                activeSessionTotalFocusMinutes = 0L
                val durationSec = _focusDurationMinutes.value * 60L
                _remainingSeconds.value = durationSec
                _timerPhase.value = TimerPhase.FOCUS_RUNNING
                runCountdownLoop(context, durationSec)
            }
            TimerPhase.FOCUS_PAUSED -> {
                _timerPhase.value = TimerPhase.FOCUS_RUNNING
                runCountdownLoop(context, _remainingSeconds.value)
            }
            TimerPhase.BREAK_PAUSED -> {
                _timerPhase.value = TimerPhase.BREAK_RUNNING
                runCountdownLoop(context, _remainingSeconds.value)
            }
            else -> Unit
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        when (_timerPhase.value) {
            TimerPhase.FOCUS_RUNNING -> _timerPhase.value = TimerPhase.FOCUS_PAUSED
            TimerPhase.BREAK_RUNNING -> _timerPhase.value = TimerPhase.BREAK_PAUSED
            else -> Unit
        }
    }

    fun resetOrEndTimer() {
        timerJob?.cancel()
        _timerPhase.value = TimerPhase.IDLE
        _remainingSeconds.value = _focusDurationMinutes.value * 60L
    }

    private fun runCountdownLoop(context: Context, initialRemainingSec: Long) {
        timerJob?.cancel()
        phaseTargetElapsedRealtimeMs = SystemClock.elapsedRealtime() + (initialRemainingSec * 1000L)
        val appContext = context.applicationContext

        timerJob = viewModelScope.launch {
            while (true) {
                val diffMs = phaseTargetElapsedRealtimeMs - SystemClock.elapsedRealtime()
                if (diffMs <= 0L) {
                    _remainingSeconds.value = 0L
                    onTimerPhaseFinished(appContext)
                    break
                } else {
                    _remainingSeconds.value = (diffMs + 999L) / 1000L
                }
                delay(250L)
            }
        }
    }

    private suspend fun onTimerPhaseFinished(context: Context) {
        if (_timerPhase.value == TimerPhase.FOCUS_RUNNING) {
            val completedMins = _focusDurationMinutes.value.toLong()
            activeSessionTotalFocusMinutes += completedMins
            val intervals = _completedPomodoroIntervalsInSession.value + 1
            _completedPomodoroIntervalsInSession.value = intervals

            // Record completed focus session in Firestore (never records incomplete sessions)
            repository.recordCompletedFocusSession(
                mode = if (_pomodoroEnabled.value) "pomodoro" else "normal",
                durationMinutes = completedMins,
                pomodoroIntervalsCompleted = if (_pomodoroEnabled.value) 1L else 0L,
                subjectTag = _activeSubjectFilter.value
            )

            sendTimerNotification(
                context = context,
                title = "Focus Interval Complete",
                body = if (_pomodoroEnabled.value) {
                    "Great focus ($completedMins min)! Starting your ${_breakDurationMinutes.value}-minute break."
                } else {
                    "You completed your $completedMins-minute focus session!"
                }
            )

            if (_pomodoroEnabled.value) {
                // Start configured break automatically (default 5 minutes)
                val breakSec = _breakDurationMinutes.value * 60L
                _remainingSeconds.value = breakSec
                _timerPhase.value = TimerPhase.BREAK_RUNNING
                runCountdownLoop(context, breakSec)
            } else {
                _timerPhase.value = TimerPhase.COMPLETED
                _remainingSeconds.value = _focusDurationMinutes.value * 60L
            }
        } else if (_timerPhase.value == TimerPhase.BREAK_RUNNING) {
            sendTimerNotification(
                context = context,
                title = "Break Ended",
                body = "Your ${_breakDurationMinutes.value}-minute break is over. Ready for your next focus session?"
            )
            _timerPhase.value = TimerPhase.COMPLETED
            _remainingSeconds.value = _focusDurationMinutes.value * 60L
        }
    }

    private fun sendTimerNotification(context: Context, title: String, body: String) {
        try {
            val channelId = "vidhya_focus_timer"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Vidhya Focus & Pomodoro Timer",
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(channel)
            }
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setSound(soundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(1001, notif)
        } catch (e: Exception) {
            // Notification permission may not be granted on API 33+
        }
    }

    // --- EduTube Search & Playback ---

    fun performEduTubeSearch(query: String, category: String) {
        _eduTubeQuery.value = query
        _eduTubeCategory.value = category
        _isEduTubeLoading.value = true

        viewModelScope.launch {
            val result = EduTubeService.searchVideos(query, category)
            _eduTubeResult.value = result
            _isEduTubeLoading.value = false
        }
    }

    fun saveOrWatchEduTubeVideo(video: EduVideoItem, saveBookmark: Boolean) {
        viewModelScope.launch {
            repository.saveOrRecordVideo(
                youtubeVideoId = video.videoId,
                title = video.title,
                channelTitle = video.channelTitle,
                subjectCategory = video.subjectCategory,
                thumbnailUrl = video.thumbnailUrl,
                durationText = video.durationText,
                isSaved = saveBookmark
            )
            if (saveBookmark) {
                showBanner("Saved '${video.title.take(30)}' to your EduTube library.")
            }
        }
    }

    // --- Friends, Referrals & Leaderboard ---

    fun sendFriendRequestByReferralCode(referralCodeInput: String) {
        val clean = referralCodeInput.trim().uppercase(Locale.US)
        if (clean.length < 4) {
            showBanner("Please enter a valid Vidhya referral code.")
            return
        }
        val myProfile = (userProfileState.value as? UiState.Success)?.data
        val myName = myProfile?.displayName?.ifBlank { currentUserDisplayName } ?: currentUserDisplayName

        viewModelScope.launch {
            val lookupRes = repository.findStudentByReferralCode(clean)
            lookupRes.fold(
                onSuccess = { target ->
                    if (target == null) {
                        showBanner("No student found with referral code '$clean' (or their profile is private).")
                    } else if (target.userId == currentUserId) {
                        showBanner("That is your own referral code! Share it with a study friend.")
                    } else {
                        val reqRes = repository.sendFriendRequest(myName, target)
                        reqRes.fold(
                            onSuccess = {
                                showBanner("Friend request sent to ${target.displayName}!")
                            },
                            onFailure = { err ->
                                showBanner(err.localizedMessage ?: "Could not send friend request.")
                            }
                        )
                    }
                },
                onFailure = { err ->
                    showBanner(err.localizedMessage ?: "Referral lookup failed.")
                }
            )
        }
    }

    fun respondToFriendRequest(friendshipId: String, accept: Boolean) {
        viewModelScope.launch {
            val res = repository.respondToFriendRequest(friendshipId, accept)
            if (res.isSuccess) {
                showBanner(if (accept) "Friend request accepted!" else "Friend request declined.")
            }
        }
    }

    fun removeFriend(friendshipId: String) {
        viewModelScope.launch {
            repository.removeFriendship(friendshipId)
            showBanner("Removed from friend group.")
        }
    }

    private fun refreshLeaderboardForFriendships(friendships: List<Friendship>) {
        viewModelScope.launch {
            val acceptedUids = friendships
                .filter { it.status == "accepted" }
                .flatMap { listOf(it.requesterId, it.receiverId) }
                .distinct()
            val res = repository.fetchAcceptedFriendsLeaderboard(acceptedUids)
            if (res.isSuccess) {
                _leaderboardProfiles.value = res.getOrDefault(emptyList())
            }
        }
    }

    // --- Ultra Focus Launcher Package Discovery ---

    fun loadInstalledLaunchableApps(context: Context) {
        viewModelScope.launch {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(mainIntent, 0)
                }
            } catch (e: Exception) {
                emptyList()
            }

            val myPkg = context.packageName
            val apps = resolveInfos.mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == myPkg) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg }
                InstalledAppInfo(packageName = pkg, label = label)
            }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.US) }

            _installedApps.value = apps
        }
    }

    fun isVidhyaDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = try {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (e: Exception) {
            null
        }
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    fun openSystemDefaultLauncherSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    private fun todayIsoDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private companion object {
        const val TAG = "VidhyaVM"
    }
}
