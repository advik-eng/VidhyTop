package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AppBlocking
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.liquidglass.liquidGlassSurface

/**
 * Coherent, unified vector icon system for Vidhya.
 * Keeps icon artwork and selective Liquid Glass material treatments cleanly separated.
 */
object VidhyaIcons {
    // Primary Navigation & 6 Ultra Focus Launcher Shortcuts
    val LearnWithVidhya: ImageVector = Icons.Outlined.School
    val LearnWithVidhyaFilled: ImageVector = Icons.Filled.School
    val ExamPrep: ImageVector = Icons.Outlined.EventNote
    val ExamPrepFilled: ImageVector = Icons.Filled.EventNote
    val LiveVoice: ImageVector = Icons.Filled.GraphicEq
    val Pomodoro: ImageVector = Icons.Outlined.Timer
    val PomodoroFilled: ImageVector = Icons.Filled.Timer
    val EduTube: ImageVector = Icons.Outlined.SmartDisplay
    val EduTubeFilled: ImageVector = Icons.Filled.SmartDisplay
    val InteractiveNotes: ImageVector = Icons.Outlined.AutoStories
    val InteractiveNotesFilled: ImageVector = Icons.Filled.AutoStories

    // Secondary Navigation & Features
    val FocusMode: ImageVector = Icons.Outlined.Shield
    val UltraFocusLauncher: ImageVector = Icons.Outlined.Home
    val FriendsLeaderboard: ImageVector = Icons.Outlined.Groups
    val FriendsLeaderboardFilled: ImageVector = Icons.Filled.Groups
    val ProgressStats: ImageVector = Icons.Outlined.Insights
    val Settings: ImageVector = Icons.Outlined.Settings

    // Note Input & Document Intelligence
    val PdfImport: ImageVector = Icons.Outlined.PictureAsPdf
    val CameraCapture: ImageVector = Icons.Outlined.CameraAlt
    val TopicGenerate: ImageVector = Icons.AutoMirrored.Outlined.MenuBook
    val HandwritingExport: ImageVector = Icons.Outlined.Draw
    val Flashcards: ImageVector = Icons.Outlined.Style
    val Quizzes: ImageVector = Icons.Outlined.Quiz
    val DefineTerm: ImageVector = Icons.Outlined.Translate
    val ExplainConcept: ImageVector = Icons.Outlined.Psychology
    val ReadAloud: ImageVector = Icons.AutoMirrored.Outlined.VolumeUp

    // Focus, Audio & App Controls
    val FocusSounds: ImageVector = Icons.Outlined.MusicNote
    val AllowedApps: ImageVector = Icons.Outlined.Apps
    val BlockedApps: ImageVector = Icons.Outlined.AppBlocking
    val StreakFlame: ImageVector = Icons.Outlined.LocalFireDepartment
    val BadgeMilestone: ImageVector = Icons.Outlined.EmojiEvents

    // Social, Account & Actions
    val AddFriend: ImageVector = Icons.Outlined.PersonAdd
    val ShareReferral: ImageVector = Icons.Outlined.Share
    val CalendarSchedule: ImageVector = Icons.Outlined.CalendarMonth
    val Notifications: ImageVector = Icons.Outlined.Notifications
    val Account: ImageVector = Icons.Outlined.AccountCircle
    val HelpFaq: ImageVector = Icons.Outlined.HelpOutline
    val SecurityPasscode: ImageVector = Icons.Outlined.Lock
    val Search: ImageVector = Icons.Outlined.Search
    val Back: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack
    val Close: ImageVector = Icons.Filled.Close
    val Send: ImageVector = Icons.AutoMirrored.Outlined.Send
    val Mic: ImageVector = Icons.Filled.Mic
    val Play: ImageVector = Icons.Filled.PlayArrow
    val Pause: ImageVector = Icons.Filled.Pause
    val BookmarkFilled: ImageVector = Icons.Filled.Bookmark
    val BookmarkOutline: ImageVector = Icons.Outlined.BookmarkBorder
    val Edit: ImageVector = Icons.Outlined.Edit
    val Delete: ImageVector = Icons.Outlined.DeleteOutline
    val Add: ImageVector = Icons.Outlined.Add
    val Check: ImageVector = Icons.Filled.CheckCircle
    val Retry: ImageVector = Icons.Outlined.Refresh
    val Error: ImageVector = Icons.Outlined.ErrorOutline
    val Document: ImageVector = Icons.Outlined.Description
    val Fullscreen: ImageVector = Icons.Outlined.Fullscreen
    val FullscreenExit: ImageVector = Icons.Outlined.FullscreenExit
    val SignOut: ImageVector = Icons.AutoMirrored.Outlined.Logout
}

/**
 * Recognizable shortcut tile for the 6 primary Vidhya actions inside the Ultra Focus Launcher.
 */
@Composable
fun LauncherShortcutTile(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .widthIn(min = 88.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .liquidGlassSurface(
                    shape = RoundedCornerShape(18.dp),
                    cornerRadius = 18.dp,
                    enabled = true,
                    elevated = true
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
