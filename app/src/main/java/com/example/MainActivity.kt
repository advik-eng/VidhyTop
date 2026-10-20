package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.VidhyaRepository
import com.example.ui.components.VidhyaIcons
import com.example.ui.liquidglass.LiquidGlassBar
import com.example.ui.screens.AiTutorHomeScreen
import com.example.ui.screens.EduTubeScreen
import com.example.ui.screens.ExamPrepScreen
import com.example.ui.screens.FirstTimeOnboardingScreen
import com.example.ui.screens.FriendsAndLeaderboardScreen
import com.example.ui.screens.InteractiveNotesScreen
import com.example.ui.screens.LiveVoiceTutorModal
import com.example.ui.screens.NormalFocusModeScreen
import com.example.ui.screens.ProgressAnalyticsScreen
import com.example.ui.screens.UltraFocusLauncherScreen
import com.example.ui.screens.VidhyaAuthScreen
import com.example.ui.screens.VidhyaSettingsScreen
import com.example.ui.screens.attemptAutoSignIn
import com.example.ui.screens.signOutVidhya
import com.example.ui.theme.VidhyaTheme
import com.example.viewmodel.TimerPhase
import com.example.viewmodel.UiState
import com.example.viewmodel.VidhyaDestination
import com.example.viewmodel.VidhyaViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VidhyaRootApp()
        }
    }
}

@Composable
fun VidhyaRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }
    var authChecked by remember { mutableStateOf(Firebase.auth.currentUser != null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    authChecked = true
                },
                onUnauthenticated = {
                    authChecked = true
                },
                scope = scope
            )
        }
    }

    val themePrefs = remember {
        context.getSharedPreferences("vidhytop_theme_prefs", android.content.Context.MODE_PRIVATE)
    }
    var cachedThemeMode by remember {
        mutableStateOf(themePrefs.getString("theme_mode", "system") ?: "system")
    }

    val activeUser = currentUser
    if (!authChecked) {
        VidhyaTheme(themeMode = cachedThemeMode) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    if (activeUser == null) {
        VidhyaTheme(themeMode = cachedThemeMode) {
            VidhyaAuthScreen(
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                }
            )
        }
        return
    }

    val viewModel: VidhyaViewModel = viewModel(
        key = "vidhya_vm_${activeUser.uid}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VidhyaViewModel(
                    repository = VidhyaRepository(context.applicationContext),
                    currentUserId = activeUser.uid,
                    currentUserDisplayName = activeUser.displayName ?: "Student",
                    currentUserEmail = activeUser.email ?: ""
                ) as T
            }
        }
    )

    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val publicProfileState by viewModel.publicProfileState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val studyTasksState by viewModel.studyTasksState.collectAsStateWithLifecycle()
    val focusSessionsState by viewModel.focusSessionsState.collectAsStateWithLifecycle()
    val tutorMessagesState by viewModel.tutorMessagesState.collectAsStateWithLifecycle()
    val savedVideosState by viewModel.savedVideosState.collectAsStateWithLifecycle()
    val friendshipsState by viewModel.friendshipsState.collectAsStateWithLifecycle()
    val leaderboardProfiles by viewModel.leaderboardProfiles.collectAsStateWithLifecycle()

    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val liquidGlassEnabled by viewModel.liquidGlassEnabled.collectAsStateWithLifecycle()
    val statusBannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    val activeSubjectFilter by viewModel.activeSubjectFilter.collectAsStateWithLifecycle()
    val isTutorThinking by viewModel.isTutorThinking.collectAsStateWithLifecycle()
    val tutorError by viewModel.tutorError.collectAsStateWithLifecycle()
    val showLiveVoiceSheet by viewModel.showLiveVoiceSheet.collectAsStateWithLifecycle()

    val isGeneratingNote by viewModel.isGeneratingNote.collectAsStateWithLifecycle()
    val noteGenerationError by viewModel.noteGenerationError.collectAsStateWithLifecycle()
    val contextualAiLoading by viewModel.contextualAiLoading.collectAsStateWithLifecycle()
    val contextualAiResult by viewModel.contextualAiResult.collectAsStateWithLifecycle()
    val isNoteFullScreen by viewModel.isNoteFullScreen.collectAsStateWithLifecycle()

    val pomodoroEnabled by viewModel.pomodoroEnabled.collectAsStateWithLifecycle()
    val focusDurationMinutes by viewModel.focusDurationMinutes.collectAsStateWithLifecycle()
    val breakDurationMinutes by viewModel.breakDurationMinutes.collectAsStateWithLifecycle()
    val timerPhase by viewModel.timerPhase.collectAsStateWithLifecycle()
    val remainingSeconds by viewModel.remainingSeconds.collectAsStateWithLifecycle()
    val completedIntervals by viewModel.completedPomodoroIntervalsInSession.collectAsStateWithLifecycle()

    val eduTubeQuery by viewModel.eduTubeQuery.collectAsStateWithLifecycle()
    val eduTubeCategory by viewModel.eduTubeCategory.collectAsStateWithLifecycle()
    val eduTubeResult by viewModel.eduTubeResult.collectAsStateWithLifecycle()
    val isEduTubeLoading by viewModel.isEduTubeLoading.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()

    LaunchedEffect(activeUser.uid) {
        viewModel.loadInstalledLaunchableApps(context)
    }

    val userProfile = (userProfileState as? UiState.Success)?.data
    val publicProfile = (publicProfileState as? UiState.Success)?.data
    LaunchedEffect(userProfile?.themeMode) {
        val remoteMode = userProfile?.themeMode?.takeIf { it.isNotBlank() }
        if (remoteMode != null && remoteMode != cachedThemeMode) {
            cachedThemeMode = remoteMode
            themePrefs.edit().putString("theme_mode", remoteMode).apply()
        }
    }
    val themeMode = userProfile?.themeMode?.takeIf { it.isNotBlank() } ?: cachedThemeMode

    VidhyaTheme(themeMode = themeMode) {
        when (val profileState = userProfileState) {
            is UiState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = VidhyaIcons.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = profileState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                signOutVidhya(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onSignOutComplete = { currentUser = null },
                                    scope = scope
                                )
                            }
                        ) {
                            Text("Sign Out & Retry")
                        }
                    }
                }
            }

            is UiState.Success -> {
                val existingProfile = profileState.data
                if (existingProfile == null || !existingProfile.onboardingCompleted) {
                    FirstTimeOnboardingScreen(
                        initialDisplayName = activeUser.displayName ?: "Student",
                        onFinishOnboarding = { name, school, grade, subjects, examType, examDate, prepStart, dailyMins, prefSchedule, pomoFocus, pomoBreak, topics ->
                            viewModel.completeOnboarding(
                                displayName = name,
                                schoolName = school,
                                gradeLevel = grade,
                                subjects = subjects,
                                examType = examType,
                                nextExamDate = examDate,
                                prepStartDate = prepStart,
                                dailyTargetMinutes = dailyMins,
                                preferredSchedule = prefSchedule,
                                pomodoroFocusMin = pomoFocus,
                                pomodoroBreakMin = pomoBreak,
                                topicsToPrepare = topics,
                                onComplete = {}
                            )
                        }
                    )
                } else if (currentDestination == VidhyaDestination.ULTRA_FOCUS_LAUNCHER) {
                    // Dedicated full-screen Android Home Launcher Mode
                    UltraFocusLauncherScreen(
                        userProfile = existingProfile,
                        publicProfile = publicProfile,
                        installedApps = installedApps,
                        isDefaultHomeLauncher = viewModel.isVidhyaDefaultLauncher(context),
                        onOpenDestination = { dest -> viewModel.navigateTo(dest) },
                        onOpenLiveVoice = { viewModel.openLiveVoice(true) },
                        onUpdateAllowedApps = { allowed ->
                            viewModel.updateAllowedAndBlockedApps(allowed, existingProfile.blockedApps)
                        },
                        onRequestDefaultLauncherSettings = {
                            viewModel.openSystemDefaultLauncherSettings(context)
                        },
                        onVerifyPasscode = { pin -> viewModel.verifyUltraFocusPasscode(pin) },
                        onResetPasscodeViaAccount = { onDone ->
                            viewModel.resetPasscodeViaVerifiedGoogleAccount(onDone)
                        },
                        onExitUltraFocus = {
                            viewModel.navigateTo(VidhyaDestination.AI_TUTOR)
                        }
                    )
                } else {
                    // Handle Android system back button on secondary destinations
                    if (currentDestination != VidhyaDestination.AI_TUTOR) {
                        BackHandler {
                            viewModel.navigateTo(VidhyaDestination.AI_TUTOR)
                        }
                    }

                    VidhyaAdaptiveWorkspace(
                        currentDestination = currentDestination,
                        isFullScreenNoteMode = currentDestination == VidhyaDestination.INTERACTIVE_NOTES && isNoteFullScreen,
                        liquidGlassEnabled = liquidGlassEnabled,
                        userProfile = existingProfile,
                        publicProfile = publicProfile,
                        timerPhase = timerPhase,
                        remainingSeconds = remainingSeconds,
                        statusBannerMessage = statusBannerMessage,
                        onClearBanner = { viewModel.clearBannerMessage() },
                        onNavigate = { viewModel.navigateTo(it) },
                        onOpenLiveVoice = { viewModel.openLiveVoice(true) }
                    ) {
                        when (currentDestination) {
                            VidhyaDestination.AI_TUTOR -> {
                                AiTutorHomeScreen(
                                    userProfile = existingProfile,
                                    tutorMessagesState = tutorMessagesState,
                                    notesState = notesState,
                                    activeSubject = activeSubjectFilter,
                                    isTutorThinking = isTutorThinking,
                                    tutorError = tutorError,
                                    onSelectSubject = { viewModel.setActiveSubject(it) },
                                    onSendQuestion = { q, ctx -> viewModel.askAiTutor(q, ctx) },
                                    onCancelRequest = { viewModel.cancelTutorRequest() },
                                    onOpenLiveVoice = { viewModel.openLiveVoice(true) },
                                    onNavigate = { viewModel.navigateTo(it) }
                                )
                            }

                            VidhyaDestination.INTERACTIVE_NOTES -> {
                                InteractiveNotesScreen(
                                    notesState = notesState,
                                    userSubjects = existingProfile.subjects,
                                    isGeneratingNote = isGeneratingNote,
                                    noteGenerationError = noteGenerationError,
                                    contextualAiLoading = contextualAiLoading,
                                    contextualAiResult = contextualAiResult,
                                    liquidGlassEnabled = liquidGlassEnabled,
                                    isNoteFullScreen = isNoteFullScreen,
                                    onSetNoteFullScreen = { viewModel.setNoteFullScreen(it) },
                                    onGenerateNotes = { text, subj, src, bmp, onCreated ->
                                        viewModel.generateKalamNotes(text, subj, src, bmp, onCreated)
                                    },
                                    onRunContextualAction = { action, sel, full, customQ ->
                                        viewModel.runContextualNoteAction(action, sel, full, customQ)
                                    },
                                    onClearContextualResult = { viewModel.clearContextualAiResult() },
                                    onDeleteNote = { viewModel.deleteNote(it) },
                                    onShowBanner = { viewModel.showBanner(it) }
                                )
                            }

                            VidhyaDestination.EXAM_PREP -> {
                                ExamPrepScreen(
                                    userProfile = existingProfile,
                                    studyTasksState = studyTasksState,
                                    onToggleTaskCompleted = { viewModel.toggleStudyTaskCompleted(it) },
                                    onRescheduleTaskToTomorrow = { viewModel.rescheduleTaskToTomorrow(it) },
                                    onAddStudyTask = { subj, topic, type, date, dur, diff ->
                                        viewModel.addSingleStudyTask(subj, topic, type, date, dur, diff)
                                    },
                                    onUpdateExamPlan = { examType, examDate, dailyMins ->
                                        viewModel.updateExamPlanSettings(examType, examDate, dailyMins)
                                    },
                                    onRegenerateFullSchedule = { subjects, topics, dailyMins, examName, examDate ->
                                        viewModel.generatePersonalizedSchedule(subjects, topics, dailyMins, examName, examDate)
                                    }
                                )
                            }

                            VidhyaDestination.FOCUS_MODE -> {
                                NormalFocusModeScreen(
                                    userProfile = existingProfile,
                                    publicProfile = publicProfile,
                                    focusSessionsState = focusSessionsState,
                                    pomodoroEnabled = pomodoroEnabled,
                                    focusDurationMinutes = focusDurationMinutes,
                                    breakDurationMinutes = breakDurationMinutes,
                                    timerPhase = timerPhase,
                                    remainingSeconds = remainingSeconds,
                                    completedIntervals = completedIntervals,
                                    onTogglePomodoro = { viewModel.setPomodoroEnabled(it) },
                                    onConfigureDurations = { f, b -> viewModel.configureFocusAndBreakDurations(f, b) },
                                    onStartOrResume = { viewModel.startOrResumeTimer(context) },
                                    onPause = { viewModel.pauseTimer() },
                                    onResetOrEnd = { viewModel.resetOrEndTimer() },
                                    onOpenUltraFocusLauncher = {
                                        viewModel.navigateTo(VidhyaDestination.ULTRA_FOCUS_LAUNCHER)
                                    },
                                    onOpenEduTube = {
                                        viewModel.navigateTo(VidhyaDestination.EDUTUBE)
                                    }
                                )
                            }

                            VidhyaDestination.EDUTUBE -> {
                                EduTubeScreen(
                                    searchQuery = eduTubeQuery,
                                    selectedCategory = eduTubeCategory,
                                    searchResult = eduTubeResult,
                                    isLoading = isEduTubeLoading,
                                    savedVideosState = savedVideosState,
                                    onSearch = { q, cat -> viewModel.performEduTubeSearch(q, cat) },
                                    onSaveOrRecordVideo = { vid, save -> viewModel.saveOrWatchEduTubeVideo(vid, save) }
                                )
                            }

                            VidhyaDestination.FRIENDS_LEADERBOARD -> {
                                FriendsAndLeaderboardScreen(
                                    currentUserId = activeUser.uid,
                                    userProfile = existingProfile,
                                    publicProfile = publicProfile,
                                    friendshipsState = friendshipsState,
                                    leaderboardProfiles = leaderboardProfiles,
                                    onSendFriendRequestByCode = { viewModel.sendFriendRequestByReferralCode(it) },
                                    onRespondToRequest = { id, accept -> viewModel.respondToFriendRequest(id, accept) },
                                    onRemoveFriend = { viewModel.removeFriend(it) },
                                    onToggleShareStats = { viewModel.updateShareStatsPreference(it) },
                                    onShowBanner = { viewModel.showBanner(it) }
                                )
                            }

                            VidhyaDestination.PROGRESS_ANALYTICS -> {
                                ProgressAnalyticsScreen(
                                    userProfile = existingProfile,
                                    publicProfile = publicProfile,
                                    focusSessionsState = focusSessionsState,
                                    studyTasksState = studyTasksState,
                                    notesState = notesState
                                )
                            }

                            VidhyaDestination.SETTINGS -> {
                                VidhyaSettingsScreen(
                                    userProfile = existingProfile,
                                    userEmail = activeUser.email ?: "",
                                    liquidGlassEnabled = liquidGlassEnabled,
                                    installedApps = installedApps,
                                    isDefaultLauncher = viewModel.isVidhyaDefaultLauncher(context),
                                    onToggleLiquidGlass = { viewModel.setLiquidGlassEnabled(it) },
                                    onUpdateThemeMode = { selectedMode ->
                                        cachedThemeMode = selectedMode
                                        themePrefs.edit().putString("theme_mode", selectedMode).apply()
                                        viewModel.updateThemeMode(selectedMode)
                                    },
                                    onUpdateProfileBasics = { name, school, grade, subjs ->
                                        viewModel.completeOnboarding(
                                            displayName = name,
                                            schoolName = school,
                                            gradeLevel = grade,
                                            subjects = subjs,
                                            examType = existingProfile.examType ?: "",
                                            nextExamDate = existingProfile.nextExamDate ?: "",
                                            prepStartDate = existingProfile.prepStartDate ?: "",
                                            dailyTargetMinutes = existingProfile.dailyStudyTargetMinutes,
                                            preferredSchedule = existingProfile.preferredSchedule ?: "Morning & Evening",
                                            pomodoroFocusMin = existingProfile.pomodoroFocusMinutes,
                                            pomodoroBreakMin = existingProfile.pomodoroBreakMinutes,
                                            topicsToPrepare = "",
                                            onComplete = {}
                                        )
                                    },
                                    onUpdateExamSettings = { examType, examDate, dailyMins ->
                                        viewModel.updateExamPlanSettings(examType, examDate, dailyMins)
                                    },
                                    onUpdatePomodoroDefaults = { f, b ->
                                        viewModel.configureFocusAndBreakDurations(f, b)
                                    },
                                    onUpdateAllowedBlockedApps = { allowed, blocked ->
                                        viewModel.updateAllowedAndBlockedApps(allowed, blocked)
                                    },
                                    onSetPasscode = { viewModel.setUltraFocusPasscode(it) },
                                    onResetPasscodeViaAccount = {
                                        viewModel.resetPasscodeViaVerifiedGoogleAccount {}
                                    },
                                    onOpenLauncherSettings = {
                                        viewModel.openSystemDefaultLauncherSettings(context)
                                    },
                                    onUpdateShareStats = { viewModel.updateShareStatsPreference(it) },
                                    onSignOut = {
                                        signOutVidhya(
                                            context = context,
                                            credentialManager = credentialManager,
                                            onSignOutComplete = { currentUser = null },
                                            scope = scope
                                        )
                                    }
                                )
                            }

                            VidhyaDestination.ULTRA_FOCUS_LAUNCHER -> Unit
                        }
                    }
                }

                // Global Live Voice Tutor Modal Sheet
                if (showLiveVoiceSheet) {
                    LiveVoiceTutorModal(
                        gradeLevel = existingProfile?.gradeLevel ?: "Grade 11",
                        activeSubject = activeSubjectFilter,
                        onDismiss = { viewModel.openLiveVoice(false) },
                        onSaveTranscriptToHistory = { spokenUser, spokenAi ->
                            viewModel.saveVoiceTranscriptToHistory(spokenUser, spokenAi)
                        }
                    )
                }
            }
        }
    }
}

private data class PrimaryNavItem(
    val destination: VidhyaDestination,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val testTag: String
)

@Composable
private fun VidhyaAdaptiveWorkspace(
    currentDestination: VidhyaDestination,
    isFullScreenNoteMode: Boolean,
    liquidGlassEnabled: Boolean,
    userProfile: com.example.data.model.UserProfile,
    publicProfile: com.example.data.model.PublicStudyProfile?,
    timerPhase: TimerPhase,
    remainingSeconds: Long,
    statusBannerMessage: String?,
    onClearBanner: () -> Unit,
    onNavigate: (VidhyaDestination) -> Unit,
    onOpenLiveVoice: () -> Unit,
    content: @Composable () -> Unit
) {
    val primaryNavItems = remember {
        listOf(
            PrimaryNavItem(
                destination = VidhyaDestination.AI_TUTOR,
                label = "Tutor",
                icon = VidhyaIcons.LearnWithVidhya,
                selectedIcon = VidhyaIcons.LearnWithVidhyaFilled,
                testTag = "nav_ai_tutor"
            ),
            PrimaryNavItem(
                destination = VidhyaDestination.INTERACTIVE_NOTES,
                label = "Notes",
                icon = VidhyaIcons.InteractiveNotes,
                selectedIcon = VidhyaIcons.InteractiveNotesFilled,
                testTag = "nav_interactive_notes"
            ),
            PrimaryNavItem(
                destination = VidhyaDestination.EXAM_PREP,
                label = "Exam Prep",
                icon = VidhyaIcons.ExamPrep,
                selectedIcon = VidhyaIcons.ExamPrepFilled,
                testTag = "nav_exam_prep"
            ),
            PrimaryNavItem(
                destination = VidhyaDestination.FOCUS_MODE,
                label = "Focus",
                icon = VidhyaIcons.Pomodoro,
                selectedIcon = VidhyaIcons.PomodoroFilled,
                testTag = "nav_focus_mode"
            ),
            PrimaryNavItem(
                destination = VidhyaDestination.EDUTUBE,
                label = "EduTube",
                icon = VidhyaIcons.EduTube,
                selectedIcon = VidhyaIcons.EduTubeFilled,
                testTag = "nav_edutube"
            ),
            PrimaryNavItem(
                destination = VidhyaDestination.FRIENDS_LEADERBOARD,
                label = "Friends",
                icon = VidhyaIcons.FriendsLeaderboard,
                selectedIcon = VidhyaIcons.FriendsLeaderboardFilled,
                testTag = "nav_friends"
            )
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (!isFullScreenNoteMode) {
                    VidhyaTopHeaderBar(
                        currentDestination = currentDestination,
                        streakDays = publicProfile?.currentStreakDays ?: 0L,
                        timerPhase = timerPhase,
                        remainingSeconds = remainingSeconds,
                        onNavigate = onNavigate,
                        onOpenLiveVoice = onOpenLiveVoice
                    )
                }
            },
            bottomBar = {
                if (!isWideScreen && !isFullScreenNoteMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        LiquidGlassBar(
                            cornerRadius = 24.dp,
                            enabled = liquidGlassEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bottom_liquid_glass_nav")
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                primaryNavItems.forEach { item ->
                                    val selected = currentDestination == item.destination
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onNavigate(item.destination) }
                                            .padding(vertical = 6.dp)
                                            .testTag(item.testTag)
                                    ) {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.icon,
                                            contentDescription = item.label,
                                            tint = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen && !isFullScreenNoteMode) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        primaryNavItems.forEach { item ->
                            val selected = currentDestination == item.destination
                            NavigationRailItem(
                                selected = selected,
                                onClick = { onNavigate(item.destination) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.icon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (statusBannerMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = statusBannerMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onClearBanner) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun VidhyaTopHeaderBar(
    currentDestination: VidhyaDestination,
    streakDays: Long,
    timerPhase: TimerPhase,
    remainingSeconds: Long,
    onNavigate: (VidhyaDestination) -> Unit,
    onOpenLiveVoice: () -> Unit
) {
    val titleText = when (currentDestination) {
        VidhyaDestination.AI_TUTOR -> "VidhyTop AI Tutor"
        VidhyaDestination.INTERACTIVE_NOTES -> "Notes & PDF Studio"
        VidhyaDestination.EXAM_PREP -> "Exam Prep Planner"
        VidhyaDestination.FOCUS_MODE -> "Focus & Pomodoro"
        VidhyaDestination.ULTRA_FOCUS_LAUNCHER -> "Ultra Focus Launcher"
        VidhyaDestination.EDUTUBE -> "EduTube Discovery"
        VidhyaDestination.FRIENDS_LEADERBOARD -> "Friends & Leaderboard"
        VidhyaDestination.PROGRESS_ANALYTICS -> "Study Progress"
        VidhyaDestination.SETTINGS -> "Settings"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Live Timer Indicator Pill when Pomodoro/Focus is running in background
                if (timerPhase == TimerPhase.FOCUS_RUNNING || timerPhase == TimerPhase.BREAK_RUNNING) {
                    val mins = remainingSeconds / 60
                    val secs = remainingSeconds % 60
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .clickable { onNavigate(VidhyaDestination.FOCUS_MODE) }
                            .testTag("active_timer_header_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = VidhyaIcons.PomodoroFilled,
                                contentDescription = "Active Timer",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format("%02d:%02d", mins, secs),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Streak & Progress Analytics Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (currentDestination == VidhyaDestination.PROGRESS_ANALYTICS) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .clickable { onNavigate(VidhyaDestination.PROGRESS_ANALYTICS) }
                        .testTag("header_progress_streak_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = VidhyaIcons.StreakFlame,
                            contentDescription = "Study Streak",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${streakDays}d",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Quick Live Voice Tutor Trigger
                IconButton(
                    onClick = onOpenLiveVoice,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("header_live_voice_button")
                ) {
                    Icon(
                        imageVector = VidhyaIcons.LiveVoice,
                        contentDescription = "Live Voice AI Tutor",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Ultra Focus Launcher Quick Switch
                IconButton(
                    onClick = { onNavigate(VidhyaDestination.ULTRA_FOCUS_LAUNCHER) },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("header_ultra_focus_button")
                ) {
                    Icon(
                        imageVector = VidhyaIcons.UltraFocusLauncher,
                        contentDescription = "Ultra Focus Home Launcher",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Settings Button
                IconButton(
                    onClick = { onNavigate(VidhyaDestination.SETTINGS) },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("header_settings_button")
                ) {
                    Icon(
                        imageVector = VidhyaIcons.Settings,
                        contentDescription = "Settings",
                        tint = if (currentDestination == VidhyaDestination.SETTINGS) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    }
}
