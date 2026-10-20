package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.sp
import com.example.data.model.FocusSession
import com.example.data.model.PublicStudyProfile
import com.example.data.model.UserProfile
import com.example.ui.components.LauncherShortcutTile
import com.example.ui.components.VidhyaIcons
import com.example.ui.theme.AcademicSuccess
import com.example.ui.theme.AcademicWarning
import com.example.ui.theme.LocalVidhyaColors
import com.example.util.FocusAudioEngine
import com.example.viewmodel.InstalledAppInfo
import com.example.viewmodel.TimerPhase
import com.example.viewmodel.UiState
import com.example.viewmodel.VidhyaDestination
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NormalFocusModeScreen(
    userProfile: UserProfile?,
    publicProfile: PublicStudyProfile?,
    focusSessionsState: UiState<List<FocusSession>>,
    pomodoroEnabled: Boolean,
    focusDurationMinutes: Int,
    breakDurationMinutes: Int,
    timerPhase: TimerPhase,
    remainingSeconds: Long,
    completedIntervals: Int,
    onTogglePomodoro: (Boolean) -> Unit,
    onConfigureDurations: (Int, Int) -> Unit,
    onStartOrResume: () -> Unit,
    onPause: () -> Unit,
    onResetOrEnd: () -> Unit,
    onOpenUltraFocusLauncher: () -> Unit,
    onOpenEduTube: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioEngine = remember { FocusAudioEngine(context) }

    var activeSoundId by remember { mutableStateOf<String?>(null) }
    var soundVolume by remember { mutableFloatStateOf(0.55f) }
    var showEndConfirmDialog by remember { mutableStateOf(false) }
    var showUltraExplainDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            audioEngine.stopSound()
        }
    }

    val sessions = (focusSessionsState as? UiState.Success)?.data.orEmpty()
    val mins = remainingSeconds / 60L
    val secs = remainingSeconds % 60L
    val formattedTime = String.format(Locale.US, "%02d:%02d", mins, secs)

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Access to Ultra Focus Mode — Use Vidhya as my Home Screen
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = VidhyaIcons.UltraFocusLauncher,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ultra Focus Mode — Home Launcher",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Use Vidhya as your minimalist Android home screen with 6 study shortcuts and selected allowed apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { showUltraExplainDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("open_ultra_focus_button")
                    ) {
                        Text("Open")
                    }
                }
            }
        }

        // Calm, Timer-First Pomodoro & Focus Timer Surface
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = if (pomodoroEnabled) "Pomodoro Focus Timer" else "Basic Focus Timer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (pomodoroEnabled) {
                                    "${focusDurationMinutes}m focus • ${breakDurationMinutes}m automatic break"
                                } else {
                                    "${focusDurationMinutes}m continuous focus (no auto-breaks)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pomodoro", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = pomodoroEnabled,
                                onCheckedChange = onTogglePomodoro,
                                enabled = timerPhase == TimerPhase.IDLE || timerPhase == TimerPhase.COMPLETED,
                                modifier = Modifier.testTag("pomodoro_toggle_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Phase Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = when (timerPhase) {
                            TimerPhase.BREAK_RUNNING, TimerPhase.BREAK_PAUSED -> LocalVidhyaColors.current.successContainer
                            TimerPhase.FOCUS_RUNNING -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = when (timerPhase) {
                                TimerPhase.IDLE -> "READY TO FOCUS"
                                TimerPhase.FOCUS_RUNNING -> "DEEP FOCUS IN PROGRESS"
                                TimerPhase.FOCUS_PAUSED -> "FOCUS PAUSED"
                                TimerPhase.BREAK_RUNNING -> "5-MINUTE REST BREAK"
                                TimerPhase.BREAK_PAUSED -> "BREAK PAUSED"
                                TimerPhase.COMPLETED -> "SESSION COMPLETED"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Large, Legible Countdown Display
                    Text(
                        text = formattedTime,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("focus_timer_display")
                    )

                    if (pomodoroEnabled && completedIntervals > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Completed Pomodoro intervals this session: $completedIntervals",
                            style = MaterialTheme.typography.bodySmall,
                            color = AcademicSuccess
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Duration Presets when Idle
                    if (timerPhase == TimerPhase.IDLE || timerPhase == TimerPhase.COMPLETED) {
                        Text(
                            text = "Focus Duration",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 25, 45, 60).forEach { dur ->
                                FilterChip(
                                    selected = focusDurationMinutes == dur,
                                    onClick = { onConfigureDurations(dur, breakDurationMinutes) },
                                    label = { Text("${dur}m") }
                                )
                            }
                        }

                        if (pomodoroEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Break Duration (Default: 5m)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(5, 10, 15).forEach { brk ->
                                    FilterChip(
                                        selected = breakDurationMinutes == brk,
                                        onClick = { onConfigureDurations(focusDurationMinutes, brk) },
                                        label = { Text("${brk}m break") }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Primary Timer Controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val isRunning = timerPhase == TimerPhase.FOCUS_RUNNING || timerPhase == TimerPhase.BREAK_RUNNING
                        Button(
                            onClick = {
                                if (isRunning) onPause() else onStartOrResume()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("focus_start_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isRunning) VidhyaIcons.Pause else VidhyaIcons.Play,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (timerPhase) {
                                    TimerPhase.IDLE, TimerPhase.COMPLETED -> "Start Focus"
                                    TimerPhase.FOCUS_RUNNING, TimerPhase.BREAK_RUNNING -> "Pause"
                                    TimerPhase.FOCUS_PAUSED, TimerPhase.BREAK_PAUSED -> "Resume"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (timerPhase != TimerPhase.IDLE) {
                            OutlinedButton(
                                onClick = { showEndConfirmDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(50.dp)
                                    .testTag("focus_end_button")
                            ) {
                                Text("End")
                            }
                        }
                    }
                }
            }
        }

        // Focus Sounds & White Noise Utility
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = VidhyaIcons.FocusSounds,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Focus Sounds & Acoustic Masking",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (activeSoundId != null) {
                                        "Playing: ${audioEngine.availableTracks.find { it.id == activeSoundId }?.title}"
                                    } else {
                                        "Select a synthesized ambient sound for studying"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (activeSoundId != null) {
                            TextButton(
                                onClick = {
                                    audioEngine.stopSound()
                                    activeSoundId = null
                                }
                            ) {
                                Text("Stop")
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        audioEngine.availableTracks.forEach { track ->
                            val isSelected = activeSoundId == track.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        audioEngine.stopSound()
                                        activeSoundId = null
                                    } else {
                                        activeSoundId = track.id
                                        audioEngine.volume = soundVolume
                                        audioEngine.startSound(track.id, scope) {
                                            activeSoundId = null
                                        }
                                    }
                                },
                                label = { Text(track.title) }
                            )
                        }
                    }

                    if (activeSoundId != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Volume", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.width(12.dp))
                            Slider(
                                value = soundVolume,
                                onValueChange = { v ->
                                    soundVolume = v
                                    audioEngine.volume = v
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Distraction Guidance & EduTube Recommendation
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = VidhyaIcons.BlockedApps,
                            contentDescription = null,
                            tint = AcademicWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Distraction-Free Study Guidance",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "During Focus sessions, we recommend avoiding entertainment feeds like Instagram and standard YouTube. Use Vidhya's EduTube for distraction-filtered educational lectures, or switch to Ultra Focus Launcher to hide distracting apps from your home screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onOpenEduTube) {
                        Icon(imageVector = VidhyaIcons.EduTube, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open EduTube Instead of YouTube")
                    }
                }
            }
        }

        // Recent Focus History & Streak Summary
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Recent Completed Sessions (${sessions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Streak: ${publicProfile?.currentStreakDays ?: 0} days",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (sessions.isEmpty()) {
            item {
                Text(
                    text = "Complete your first focus interval above to start your verified study streak.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(sessions.take(5), key = { it.id }) { session ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "${session.mode.uppercase(Locale.US)} Session • ${session.durationMinutes} mins",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Date: ${session.sessionDate} • Subject: ${session.subjectTag ?: "General"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = VidhyaIcons.Check,
                            contentDescription = "Verified",
                            tint = AcademicSuccess,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showEndConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEndConfirmDialog = false },
            title = { Text("End Focus Session Early?") },
            text = {
                Text("Ending an incomplete interval resets the current countdown without recording unverified focus time.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetOrEnd()
                        showEndConfirmDialog = false
                    }
                ) {
                    Text("End Session")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirmDialog = false }) {
                    Text("Keep Focusing")
                }
            }
        )
    }

    if (showUltraExplainDialog) {
        AlertDialog(
            onDismissRequest = { showUltraExplainDialog = false },
            title = { Text("Ultra Focus Mode — Android Home Launcher") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "How Ultra Focus Mode works:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• Replaces your cluttered home screen with a calm study environment featuring 6 core Vidhya shortcuts (Learn with Vidhya, Exam Prep, Live Voice, Pomodoro, EduTube, Interactive Notes).\n• Shows only the installed apps you explicitly allow.\n• You can optionally set Vidhya as your Android Default Home app in system settings, and exit anytime with your passcode or Google account recovery.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUltraExplainDialog = false
                        onOpenUltraFocusLauncher()
                    },
                    modifier = Modifier.testTag("confirm_activate_ultra_focus")
                ) {
                    Text("Enter Ultra Focus Launcher")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUltraExplainDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Ultra Focus Mode — Dedicated Android Home Launcher Experience.
 * Includes the 6 required primary Vidhya feature icons, separate Allowed Apps section,
 * legitimate Android default home launcher role check, and passcode exit + Google account recovery.
 */
@Composable
fun UltraFocusLauncherScreen(
    userProfile: UserProfile?,
    publicProfile: PublicStudyProfile?,
    installedApps: List<InstalledAppInfo>,
    isDefaultHomeLauncher: Boolean,
    onOpenDestination: (VidhyaDestination) -> Unit,
    onOpenLiveVoice: () -> Unit,
    onUpdateAllowedApps: (List<String>) -> Unit,
    onRequestDefaultLauncherSettings: () -> Unit,
    onVerifyPasscode: (String) -> Boolean,
    onResetPasscodeViaAccount: (() -> Unit) -> Unit,
    onExitUltraFocus: () -> Unit
) {
    val context = LocalContext.current
    var currentTimeStr by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    var currentDateStr by remember {
        mutableStateOf(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()))
    }

    var showAllowedAppsSheet by remember { mutableStateOf(false) }
    var showExitPasscodeDialog by remember { mutableStateOf(false) }
    var passcodeInput by remember { mutableStateOf("") }
    var passcodeError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            currentDateStr = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
            delay(15_000L)
        }
    }

    val hasPasscode = !userProfile?.ultraFocusPasscodeHash.isNullOrBlank()

    fun attemptExit() {
        if (hasPasscode) {
            passcodeInput = ""
            passcodeError = null
            showExitPasscodeDialog = true
        } else {
            onExitUltraFocus()
        }
    }

    BackHandler {
        attemptExit()
    }

    val allowedPackages = userProfile?.allowedApps.orEmpty()
    val allowedInstalledApps = remember(installedApps, allowedPackages) {
        installedApps.filter { allowedPackages.contains(it.packageName) }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Minimalist Clock, Date, Study Greeting & Streak
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = VidhyaIcons.StreakFlame,
                                contentDescription = null,
                                tint = AcademicWarning,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${publicProfile?.currentStreakDays ?: 0}d Streak • Target ${userProfile?.dailyStudyTargetMinutes ?: 120}m",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    TextButton(
                        onClick = { attemptExit() },
                        modifier = Modifier.testTag("exit_ultra_focus_button")
                    ) {
                        Icon(imageVector = VidhyaIcons.SecurityPasscode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exit Launcher")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = currentTimeStr,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = currentDateStr,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Focus deeply, ${userProfile?.displayName ?: "Student"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                if (!userProfile?.nextExamDate.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Upcoming: ${userProfile?.examType ?: "Exam"} on ${userProfile?.nextExamDate}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // THE SIX PRIMARY VIDHYA LAUNCHER ICONS
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "VIDHYA STUDY SHORTCUTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                val vidhyaColors = LocalVidhyaColors.current
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LauncherShortcutTile(
                        label = "Learn with Vidhya",
                        icon = VidhyaIcons.LearnWithVidhyaFilled,
                        accentColor = vidhyaColors.primaryAccent,
                        testTag = "launcher_shortcut_tutor",
                        onClick = { onOpenDestination(VidhyaDestination.AI_TUTOR) },
                        modifier = Modifier.weight(1f)
                    )
                    LauncherShortcutTile(
                        label = "Exam Prep",
                        icon = VidhyaIcons.ExamPrepFilled,
                        accentColor = vidhyaColors.secondaryAccent,
                        testTag = "launcher_shortcut_exam_prep",
                        onClick = { onOpenDestination(VidhyaDestination.EXAM_PREP) },
                        modifier = Modifier.weight(1f)
                    )
                    LauncherShortcutTile(
                        label = "Live Voice",
                        icon = VidhyaIcons.LiveVoice,
                        accentColor = vidhyaColors.success,
                        testTag = "launcher_shortcut_live_voice",
                        onClick = {
                            onOpenDestination(VidhyaDestination.AI_TUTOR)
                            onOpenLiveVoice()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LauncherShortcutTile(
                        label = "Pomodoro",
                        icon = VidhyaIcons.PomodoroFilled,
                        accentColor = vidhyaColors.goldAccent,
                        testTag = "launcher_shortcut_pomodoro",
                        onClick = { onOpenDestination(VidhyaDestination.FOCUS_MODE) },
                        modifier = Modifier.weight(1f)
                    )
                    LauncherShortcutTile(
                        label = "EduTube",
                        icon = VidhyaIcons.EduTubeFilled,
                        accentColor = vidhyaColors.error,
                        testTag = "launcher_shortcut_edutube",
                        onClick = { onOpenDestination(VidhyaDestination.EDUTUBE) },
                        modifier = Modifier.weight(1f)
                    )
                    LauncherShortcutTile(
                        label = "Interactive Notes",
                        icon = VidhyaIcons.InteractiveNotesFilled,
                        accentColor = vidhyaColors.primaryAccent,
                        testTag = "launcher_shortcut_notes",
                        onClick = { onOpenDestination(VidhyaDestination.INTERACTIVE_NOTES) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SEPARATE ALLOWED APPS SECTION
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Selected Allowed Device Apps",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Apps not listed here are hidden from the Ultra Focus home screen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { showAllowedAppsSheet = true },
                            modifier = Modifier.testTag("manage_allowed_apps_button")
                        ) {
                            Text("Manage")
                        }
                    }

                    if (allowedInstalledApps.isEmpty()) {
                        Text(
                            text = "No external device apps selected. Tap 'Manage' to allow essential utilities (e.g., Calculator, Clock).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            allowedInstalledApps.forEach { app ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            }
                                        }
                                        .padding(vertical = 8.dp, horizontal = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = VidhyaIcons.AllowedApps,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = app.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = "Launch",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Android Default Home Launcher Configuration & Settings / FAQ Access
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isDefaultHomeLauncher) {
                                "System Status: Vidhya is active as your Default Android Home App"
                            } else {
                                "System Status: Vidhya is not yet set as Default Android Home App"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "You can change your default home launcher anytime in Android Home Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onRequestDefaultLauncherSettings,
                        modifier = Modifier.testTag("configure_default_home_button")
                    ) {
                        Text(if (isDefaultHomeLauncher) "Home Settings" else "Set as Home")
                    }
                }
            }
        }
    }

    if (showAllowedAppsSheet) {
        val currentSelection = remember(allowedPackages) {
            mutableStateOf(allowedPackages.toSet())
        }
        AlertDialog(
            onDismissRequest = { showAllowedAppsSheet = false },
            title = { Text("Select Allowed Apps") },
            text = {
                Column(modifier = Modifier.height(320.dp)) {
                    Text(
                        text = "Only selected apps will appear in the Allowed Apps section of Ultra Focus Launcher.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn {
                        items(installedApps, key = { it.packageName }) { app ->
                            val checked = currentSelection.value.contains(app.packageName)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentSelection.value = if (checked) {
                                            currentSelection.value - app.packageName
                                        } else {
                                            currentSelection.value + app.packageName
                                        }
                                    }
                                    .padding(vertical = 6.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        currentSelection.value = if (isChecked) {
                                            currentSelection.value + app.packageName
                                        } else {
                                            currentSelection.value - app.packageName
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateAllowedApps(currentSelection.value.toList())
                        showAllowedAppsSheet = false
                    }
                ) {
                    Text("Save Allowed Apps")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAllowedAppsSheet = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExitPasscodeDialog) {
        AlertDialog(
            onDismissRequest = { showExitPasscodeDialog = false },
            title = { Text("Enter Ultra Focus Exit Passcode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your configured Ultra Focus passcode to return to normal mode.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = passcodeInput,
                        onValueChange = { passcodeInput = it },
                        label = { Text("Passcode") },
                        singleLine = true,
                        modifier = Modifier.testTag("ultra_focus_passcode_input")
                    )
                    if (passcodeError != null) {
                        Text(
                            text = passcodeError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    HorizontalDivider()
                    Text(
                        text = "Forgot Passcode? Since you are signed in with your verified Google account, you can securely reset your Ultra Focus passcode without entering a Gmail password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            onResetPasscodeViaAccount {
                                showExitPasscodeDialog = false
                                onExitUltraFocus()
                            }
                        },
                        modifier = Modifier.testTag("recover_passcode_button")
                    ) {
                        Text("Reset Passcode via Verified Vidhya Account")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onVerifyPasscode(passcodeInput)) {
                            showExitPasscodeDialog = false
                            onExitUltraFocus()
                        } else {
                            passcodeError = "Incorrect passcode. Try again or use Verified Account Reset below."
                        }
                    }
                ) {
                    Text("Unlock & Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitPasscodeDialog = false }) {
                    Text("Stay in Ultra Focus")
                }
            }
        )
    }
}
