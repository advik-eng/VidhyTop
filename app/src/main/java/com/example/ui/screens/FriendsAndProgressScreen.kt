package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FocusSession
import com.example.data.model.Friendship
import com.example.data.model.PublicStudyProfile
import com.example.data.model.StudyNote
import com.example.data.model.StudyTask
import com.example.data.model.UserProfile
import androidx.compose.ui.graphics.Color
import com.example.ui.components.VidhyaIcons
import com.example.ui.theme.LocalVidhyaColors
import com.example.viewmodel.InstalledAppInfo
import com.example.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Friends, Referrals & Study Leaderboard Screen.
 * Designed with clean rankings, readable study metrics, referral sharing, and strict privacy controls.
 */
@Composable
fun FriendsAndLeaderboardScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    publicProfile: PublicStudyProfile?,
    friendshipsState: UiState<List<Friendship>>,
    leaderboardProfiles: List<PublicStudyProfile>,
    onSendFriendRequestByCode: (String) -> Unit,
    onRespondToRequest: (friendshipId: String, accept: Boolean) -> Unit,
    onRemoveFriend: (friendshipId: String) -> Unit,
    onToggleShareStats: (Boolean) -> Unit,
    onShowBanner: (String) -> Unit
) {
    val context = LocalContext.current
    var referralInput by rememberSaveable { mutableStateOf("") }
    var rankingSortBy by rememberSaveable { mutableStateOf("STREAK") } // STREAK, MINUTES, SESSIONS

    val friendships = (friendshipsState as? UiState.Success)?.data.orEmpty()
    val incomingRequests = friendships.filter { it.status == "pending" && it.receiverId == currentUserId }
    val outgoingRequests = friendships.filter { it.status == "pending" && it.requesterId == currentUserId }
    val acceptedFriendships = friendships.filter { it.status == "accepted" }

    val myReferralCode = userProfile?.referralCode?.ifBlank { publicProfile?.referralCode.orEmpty() }.orEmpty()
    val shareEnabled = userProfile?.shareStatsWithFriends ?: true

    // Combine current student's profile with accepted friends who share stats
    val combinedLeaderboard = remember(leaderboardProfiles, publicProfile, rankingSortBy) {
        val baseList = leaderboardProfiles.toMutableList()
        if (publicProfile != null && baseList.none { it.userId == publicProfile.userId }) {
            baseList.add(publicProfile)
        }
        when (rankingSortBy) {
            "MINUTES" -> baseList.sortedByDescending { it.totalVerifiedFocusMinutes }
            "SESSIONS" -> baseList.sortedByDescending { it.completedPomodoroSessions }
            else -> baseList.sortedWith(
                compareByDescending<PublicStudyProfile> { it.currentStreakDays }
                    .thenByDescending { it.totalVerifiedFocusMinutes }
            )
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("friends_leaderboard_screen")
    ) {
        // 1. Referral Invite & Add Friend Header Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "STUDY CIRCLE & REFERRAL CODE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Study with Friends",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (myReferralCode.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Vidhya Referral Code", myReferralCode))
                                        onShowBanner("Copied referral code $myReferralCode")
                                    }
                                    .testTag("copy_referral_code_chip")
                            ) {
                                Text(
                                    text = myReferralCode,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Invite classmates with your personal referral code or enter a friend's code to compare study streaks and focus hours.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = referralInput,
                            onValueChange = { referralInput = it.uppercase(Locale.US) },
                            placeholder = { Text("Enter friend code (e.g. VID-AB12)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("referral_code_input")
                        )
                        Button(
                            onClick = {
                                onSendFriendRequestByCode(referralInput)
                                referralInput = ""
                            },
                            enabled = referralInput.trim().length >= 4,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("send_friend_request_button")
                        ) {
                            Icon(
                                imageVector = VidhyaIcons.AddFriend,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Join my study circle on Vidhya! Enter my referral code $myReferralCode in Friends & Leaderboard to compare streaks and exam prep progress."
                                    )
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Vidhya Referral Code"))
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("share_referral_button")
                        ) {
                            Icon(
                                imageVector = VidhyaIcons.ShareReferral,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Invite Link")
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Share my stats",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = shareEnabled,
                                onCheckedChange = onToggleShareStats,
                                modifier = Modifier.testTag("share_stats_switch")
                            )
                        }
                    }
                }
            }
        }

        // 2. Incoming & Outgoing Friend Requests
        if (incomingRequests.isNotEmpty() || outgoingRequests.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Friend Requests",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        incomingRequests.forEach { req ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = req.requesterName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Wants to join your study leaderboard",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { onRespondToRequest(req.id, false) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("decline_friend_${req.id}")
                                    ) {
                                        Text("Decline")
                                    }
                                    Button(
                                        onClick = { onRespondToRequest(req.id, true) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("accept_friend_${req.id}")
                                    ) {
                                        Text("Accept")
                                    }
                                }
                            }
                        }

                        outgoingRequests.forEach { req ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = req.receiverName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Invitation pending",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { onRemoveFriend(req.id) }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Leaderboard Header & Sort Controls
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Study Leaderboard",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = rankingSortBy == "STREAK",
                        onClick = { rankingSortBy = "STREAK" },
                        label = { Text("Streak") },
                        modifier = Modifier.testTag("sort_leaderboard_streak")
                    )
                    FilterChip(
                        selected = rankingSortBy == "MINUTES",
                        onClick = { rankingSortBy = "MINUTES" },
                        label = { Text("Focus Time") },
                        modifier = Modifier.testTag("sort_leaderboard_minutes")
                    )
                    FilterChip(
                        selected = rankingSortBy == "SESSIONS",
                        onClick = { rankingSortBy = "SESSIONS" },
                        label = { Text("Sessions") },
                        modifier = Modifier.testTag("sort_leaderboard_sessions")
                    )
                }
            }
        }

        // 4. Ranked Student Rows
        itemsIndexed(combinedLeaderboard, key = { _, item -> item.userId.ifBlank { item.id } }) { index, entry ->
            val rank = index + 1
            val isMe = entry.userId == currentUserId
            val vidhyaColors = LocalVidhyaColors.current
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isMe) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = when {
                        rank == 1 -> vidhyaColors.goldHighlight.copy(alpha = 0.65f)
                        isMe -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("leaderboard_row_$rank")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (rank == 1) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(brush = vidhyaColors.podiumGradient)
                        ) {
                            Text(
                                text = "#1",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = when (rank) {
                                2, 3 -> MaterialTheme.colorScheme.secondaryContainer
                                else -> vidhyaColors.interactiveSurface
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "#$rank",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (rank <= 3) vidhyaColors.goldAccent else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isMe) "${entry.displayName} (You)" else entry.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${entry.gradeLevel ?: "Student"} • ${entry.completedPomodoroSessions} sessions • ${entry.earnedBadges.size} milestones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = VidhyaIcons.StreakFlame,
                                contentDescription = null,
                                tint = vidhyaColors.goldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${entry.currentStreakDays}d streak",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${entry.totalVerifiedFocusMinutes} min focused",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (acceptedFriendships.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Your study circle currently shows your personal progress. Share your referral code ($myReferralCode) with classmates so you can encourage each other's daily study streaks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Progress, Streaks, Badges & Study Analytics Screen.
 * Replaces decorative charts with clear, authentic study telemetry derived from real Firestore sessions, notes, and tasks.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressAnalyticsScreen(
    userProfile: UserProfile?,
    publicProfile: PublicStudyProfile?,
    focusSessionsState: UiState<List<FocusSession>>,
    studyTasksState: UiState<List<StudyTask>>,
    notesState: UiState<List<StudyNote>>
) {
    val sessions = (focusSessionsState as? UiState.Success)?.data.orEmpty()
    val tasks = (studyTasksState as? UiState.Success)?.data.orEmpty()
    val notes = (notesState as? UiState.Success)?.data.orEmpty()

    val currentStreak = publicProfile?.currentStreakDays ?: 0L
    val longestStreak = publicProfile?.longestStreakDays ?: 0L
    val totalFocusMinutes = publicProfile?.totalVerifiedFocusMinutes ?: sessions.sumOf { it.durationMinutes }
    val completedSessions = publicProfile?.completedPomodoroSessions ?: sessions.size.toLong()
    val completedTasks = tasks.count { it.completed }
    val earnedBadges = publicProfile?.earnedBadges.orEmpty()

    // Last 7 days daily focus distribution (real dates, no fabricated telemetry)
    val last7DaysStats = remember(sessions) {
        val sdfIso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfLabel = SimpleDateFormat("EEE", Locale.US)
        (6 downTo 0).map { offset ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -offset)
            val iso = sdfIso.format(cal.time)
            val dayLabel = sdfLabel.format(cal.time)
            val mins = sessions
                .filter { s ->
                    val sDate = s.sessionDate.ifBlank {
                        s.createdAt?.toDate()?.let { sdfIso.format(it) }.orEmpty()
                    }
                    sDate == iso
                }
                .sumOf { it.durationMinutes }
            Triple(dayLabel, iso, mins)
        }
    }

    // Subject distribution from real focus sessions + study tasks
    val subjectMinutesMap = remember(sessions, tasks) {
        val map = mutableMapOf<String, Long>()
        sessions.forEach { s ->
            val subj = s.subjectTag?.takeIf { it.isNotBlank() } ?: "General Study"
            map[subj] = (map[subj] ?: 0L) + s.durationMinutes
        }
        tasks.filter { it.completed }.forEach { t ->
            map[t.subject] = (map[t.subject] ?: 0L) + t.plannedDurationMinutes
        }
        map.entries.sortedByDescending { it.value }
    }

    val allMilestoneCatalog = listOf(
        Triple("First Focus Session", "Complete your first distraction-free focus session", completedSessions >= 1),
        Triple("3-Day Study Streak", "Study consistently for 3 consecutive days", longestStreak >= 3),
        Triple("7-Day Consistency", "Maintain a full week of daily study activity", longestStreak >= 7),
        Triple("100+ Focus Minutes", "Accumulate 100 minutes of timed focus work", totalFocusMinutes >= 100),
        Triple("500+ Focus Minutes", "Complete over 8 hours of deep study sessions", totalFocusMinutes >= 500),
        Triple("Kalam Note Author", "Create at least 1 interactive Kalam revision note", notes.isNotEmpty())
    )

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("progress_analytics_screen")
    ) {
        // 1. Key Study Metrics Summary Row
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricSummaryBox(
                    label = "CURRENT STREAK",
                    value = "$currentStreak days",
                    subtext = "Best: $longestStreak days",
                    modifier = Modifier.weight(1f)
                )
                MetricSummaryBox(
                    label = "TOTAL FOCUSED",
                    value = "$totalFocusMinutes min",
                    subtext = "$completedSessions sessions",
                    modifier = Modifier.weight(1f)
                )
                MetricSummaryBox(
                    label = "STUDY OUTPUT",
                    value = "${notes.size} notes",
                    subtext = "$completedTasks tasks done",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. 7-Day Daily Target vs Actual Focus Bar Breakdown
        item {
            val dailyTarget = (userProfile?.dailyStudyTargetMinutes ?: 120L).coerceAtLeast(30L)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "LAST 7 DAYS CONSISTENCY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Daily Focus vs ${dailyTarget}m Goal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(128.dp)
                    ) {
                        last7DaysStats.forEach { (dayLabel, _, mins) ->
                            val ratio = (mins.toFloat() / dailyTarget.toFloat()).coerceIn(0.06f, 1f)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Text(
                                    text = if (mins > 0) "${mins}m" else "—",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    contentAlignment = Alignment.BottomCenter,
                                    modifier = Modifier
                                        .width(22.dp)
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(ratio)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (mins >= dailyTarget) MaterialTheme.colorScheme.secondary
                                                else if (mins > 0) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = dayLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Subject Allocation Breakdown
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Time by Subject",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (subjectMinutesMap.isEmpty()) {
                        Text(
                            text = "Complete focus sessions or study plan tasks to see your time distribution across subjects.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val totalSubjMins = subjectMinutesMap.sumOf { it.value }.coerceAtLeast(1L)
                        subjectMinutesMap.forEach { (subject, mins) ->
                            val fraction = (mins.toFloat() / totalSubjMins.toFloat()).coerceIn(0f, 1f)
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = subject,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "$mins min (${(fraction * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Meaningful Study Milestones & Badges
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Study Milestones",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Earned through verified focus sessions, daily streaks, and revision notes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val vidhyaColors = LocalVidhyaColors.current
                    allMilestoneCatalog.forEach { (title, description, unlocked) ->
                        val isEarned = unlocked || earnedBadges.contains(title)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            if (isEarned) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(brush = vidhyaColors.podiumGradient)
                                ) {
                                    Icon(
                                        imageVector = VidhyaIcons.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = vidhyaColors.interactiveSurface,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = VidhyaIcons.BadgeMilestone,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isEarned) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (isEarned) "Earned" else "Locked",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isEarned) {
                                    vidhyaColors.goldAccent
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricSummaryBox(
    label: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Comprehensive Profile & Settings Screen covering all 9 required sections:
 * 1. Student Identity & Academic Profile Card (Monogram, Grade, Institution, Subjects)
 * 2. Appearance (Light / Dark / System & Selective Liquid Glass toggle)
 * 3. Study Schedule, Exam Target & Pomodoro Defaults
 * 4. Allowed Apps, Ultra Focus Passcode & Home Launcher Role
 * 5. Leaderboard Privacy & Student Guide / FAQ
 * 6. Account Session & Sign Out (Positioned at the very bottom when scrolled)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VidhyaSettingsScreen(
    userProfile: UserProfile?,
    userEmail: String,
    liquidGlassEnabled: Boolean,
    installedApps: List<InstalledAppInfo>,
    isDefaultLauncher: Boolean,
    onToggleLiquidGlass: (Boolean) -> Unit,
    onUpdateThemeMode: (String) -> Unit,
    onUpdateProfileBasics: (displayName: String, schoolName: String, gradeLevel: String, subjects: List<String>) -> Unit,
    onUpdateExamSettings: (examType: String, nextExamDate: String, dailyTargetMinutes: Long) -> Unit,
    onUpdatePomodoroDefaults: (focusMin: Int, breakMin: Int) -> Unit,
    onUpdateAllowedBlockedApps: (allowed: List<String>, blocked: List<String>) -> Unit,
    onSetPasscode: (String) -> Unit,
    onResetPasscodeViaAccount: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onUpdateShareStats: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var showAllowedAppsDialog by remember { mutableStateOf(false) }
    var showFaqDialog by remember { mutableStateOf(false) }

    val themeMode = userProfile?.themeMode ?: "system"
    val hasPasscode = !userProfile?.ultraFocusPasscodeHash.isNullOrBlank()
    val studentName = userProfile?.displayName?.takeIf { it.isNotBlank() } ?: "Student"
    val initials = remember(studentName) {
        studentName.trim().split("\\s+".toRegex())
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
            .ifBlank { "VT" }
    }
    val subjectsList = userProfile?.subjects?.takeIf { it.isNotEmpty() }
        ?: listOf("Mathematics", "Science")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .testTag("vidhya_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Student Identity & Academic Profile Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "STUDENT PROFILE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = studentName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = userEmail.ifBlank { "Verified Google Student Account" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

                // Structured Academic Metadata Grid
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ProfileMetadataTile(
                        label = "CLASS / GRADE",
                        value = userProfile?.gradeLevel?.ifBlank { "Grade 11" } ?: "Grade 11",
                        modifier = Modifier.weight(1f)
                    )
                    ProfileMetadataTile(
                        label = "INSTITUTION",
                        value = userProfile?.schoolName?.takeIf { it.isNotBlank() } ?: "Independent Study",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Enrolled Subjects Pills
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ACTIVE SUBJECTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subjectsList.forEach { subject ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = subject,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showEditProfileDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("edit_profile_button")
                ) {
                    Icon(
                        imageVector = VidhyaIcons.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Edit Academic Profile & Subjects",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 2. Appearance & Selective Liquid Glass Material
        SettingsSectionSurface(title = "Appearance & Surface Material") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Choose between warm academic Light mode, deep-charcoal Dark mode, or System Default.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "light" to "Light",
                        "dark" to "Dark",
                        "system" to "System Default"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = themeMode == key,
                            onClick = { onUpdateThemeMode(key) },
                            label = {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            },
                            modifier = Modifier.testTag("theme_chip_$key")
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Liquid Glass Navigation & Overlays",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Applies optical specular rim and diffused translucency to floating navigation and contextual note toolbars only.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = liquidGlassEnabled,
                    onCheckedChange = onToggleLiquidGlass,
                    modifier = Modifier.testTag("liquid_glass_toggle")
                )
            }
        }

        // 3. Exam Prep & Pomodoro Defaults
        SettingsSectionSurface(title = "Study Schedule & Pomodoro Defaults") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Target Exam: ${userProfile?.examType?.takeIf { it.isNotBlank() } ?: "Board / Competitive Exams"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Exam Date: ${userProfile?.nextExamDate?.takeIf { it.isNotBlank() } ?: "Not scheduled"} • Daily Study Goal: ${userProfile?.dailyStudyTargetMinutes ?: 120} minutes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Default Pomodoro Interval (Focus / Break)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(25 to 5, 45 to 10, 50 to 10).forEach { (f, b) ->
                        FilterChip(
                            selected = (userProfile?.pomodoroFocusMinutes?.toInt() == f),
                            onClick = { onUpdatePomodoroDefaults(f, b) },
                            label = {
                                Text(
                                    text = "${f}m Focus / ${b}m Break",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        // 4. Allowed Apps, Ultra Focus Passcode & Home Launcher Role
        SettingsSectionSurface(title = "Ultra Focus Launcher & Security") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isDefaultLauncher) "Home Launcher Role: VidhyTop is Default Home" else "Home Launcher Role: Standard App Mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Allowed third-party study apps: ${userProfile?.allowedApps?.size ?: 0} configured • Exit Passcode: ${if (hasPasscode) "Enabled (SHA-256 protected)" else "Not set"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { showAllowedAppsDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("manage_allowed_apps_button")
                ) {
                    Icon(imageVector = VidhyaIcons.AllowedApps, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage Allowed Study Apps (${userProfile?.allowedApps?.size ?: 0})")
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showPasscodeDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("configure_passcode_button")
                    ) {
                        Icon(imageVector = VidhyaIcons.SecurityPasscode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasPasscode) "Passcode" else "Set Passcode",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenLauncherSettings,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("system_launcher_settings_button")
                    ) {
                        Icon(imageVector = VidhyaIcons.UltraFocusLauncher, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Home Settings",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 5. Privacy & Help / FAQ
        SettingsSectionSurface(title = "Leaderboard Privacy & Student Guide") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Share Study Streak & Focus Hours with Friends",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "When off, your profile is hidden from friend leaderboard queries.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = userProfile?.shareStatsWithFriends ?: true,
                    onCheckedChange = onUpdateShareStats
                )
            }

            OutlinedButton(
                onClick = { showFaqDialog = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_help_faq_button")
            ) {
                Icon(imageVector = VidhyaIcons.HelpFaq, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Student Guide & FAQ")
            }
        }

        // 6. Account Session & Sign Out (Last section at the very bottom of the scroll view)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ACCOUNT SESSION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Your Kalam notes, exam schedule, and focus streaks are synced with your cloud account (${userEmail.ifBlank { studentName }}).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = VidhyaIcons.SignOut,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sign Out of VidhyTop",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    // Edit Profile & Subjects Dialog
    if (showEditProfileDialog) {
        var nameInput by remember { mutableStateOf(userProfile?.displayName ?: "") }
        var schoolInput by remember { mutableStateOf(userProfile?.schoolName ?: "") }
        var gradeInput by remember { mutableStateOf(userProfile?.gradeLevel ?: "Grade 11") }
        var subjectsCsv by remember {
            mutableStateOf(userProfile?.subjects?.joinToString(", ") ?: "Mathematics, Physics, Chemistry")
        }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Academic Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Student Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = schoolInput,
                        onValueChange = { schoolInput = it },
                        label = { Text("School / Institution") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = gradeInput,
                        onValueChange = { gradeInput = it },
                        label = { Text("Class / Grade Level") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subjectsCsv,
                        onValueChange = { subjectsCsv = it },
                        label = { Text("Subjects (comma-separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedSubjects = subjectsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        onUpdateProfileBasics(nameInput, schoolInput, gradeInput, parsedSubjects)
                        showEditProfileDialog = false
                    }
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Passcode Setup & Account Recovery Dialog
    if (showPasscodeDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasscodeDialog = false },
            title = { Text("Ultra Focus Exit Passcode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Set a 4 to 8 digit passcode required when exiting Ultra Focus Launcher mode. If you ever forget it, you can recover and clear it via your authenticated Google account.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it.filter { ch -> ch.isDigit() }.take(8) },
                        label = { Text("4–8 Digit Passcode (leave blank to remove)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetPasscode(newPin)
                        showPasscodeDialog = false
                    }
                ) {
                    Text(if (newPin.isBlank()) "Remove Passcode" else "Save Passcode")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onResetPasscodeViaAccount()
                        showPasscodeDialog = false
                    }
                ) {
                    Text("Reset via Account")
                }
            }
        )
    }

    // Allowed Apps Picker Dialog
    if (showAllowedAppsDialog) {
        val currentAllowed = remember(userProfile?.allowedApps) {
            userProfile?.allowedApps.orEmpty().toMutableSet()
        }
        var selectedPkgs by remember { mutableStateOf(currentAllowed.toSet()) }

        AlertDialog(
            onDismissRequest = { showAllowedAppsDialog = false },
            title = { Text("Allowed Study Apps") },
            text = {
                if (installedApps.isEmpty()) {
                    Text(
                        text = "No external launchable packages detected in this container.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(installedApps, key = { it.packageName }) { app ->
                            val checked = selectedPkgs.contains(app.packageName)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPkgs = if (checked) {
                                            selectedPkgs - app.packageName
                                        } else {
                                            selectedPkgs + app.packageName
                                        }
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedPkgs = if (isChecked) {
                                            selectedPkgs + app.packageName
                                        } else {
                                            selectedPkgs - app.packageName
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(text = app.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateAllowedBlockedApps(
                            selectedPkgs.toList(),
                            userProfile?.blockedApps.orEmpty()
                        )
                        showAllowedAppsDialog = false
                    }
                ) {
                    Text("Save Allowed Apps")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAllowedAppsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Help & FAQ Dialog
    if (showFaqDialog) {
        AlertDialog(
            onDismissRequest = { showFaqDialog = false },
            title = { Text("VidhyTop Student Guide & FAQ") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    FaqItem(
                        q = "How do Kalam Handwritten Notes & PDF Export work?",
                        a = "Open Notes & PDF, choose a topic, textbook photo, or imported PDF page, and generate structured notes rendered in the bundled Kalam handwriting font. Use the Full Screen button to read unobstructed or tap 'Export PDF' to create a multi-page handwritten PDF document."
                    )
                    FaqItem(
                        q = "How do contextual AI note actions work?",
                        a = "Tap any paragraph inside a Kalam note (or tap a key term) to open the floating Liquid Glass action bar for Define, Explain, Summarize, Ask AI, Read Aloud, Flashcards, or Quiz."
                    )
                    FaqItem(
                        q = "How does Ultra Focus Launcher work on Android?",
                        a = "Ultra Focus provides a dedicated academic home screen with the 6 core VidhyTop study tools and your Allowed Apps list. You can optionally set VidhyTop as your default Android Home app in System Home Settings and protect exit with a passcode."
                    )
                    FaqItem(
                        q = "What if I forget my Ultra Focus passcode?",
                        a = "Tap 'Forgot Passcode? Reset via Verified Google Account' on the passcode dialog to verify your signed-in account and clear the lock."
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showFaqDialog = false }) {
                    Text("Got It")
                }
            }
        )
    }
}

@Composable
private fun ProfileMetadataTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsSectionSurface(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = title.uppercase(Locale.US),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            content()
        }
    }
}

@Composable
private fun FaqItem(q: String, a: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = q,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = a,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
