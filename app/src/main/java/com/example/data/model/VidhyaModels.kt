package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val schoolName: String? = null,
    val gradeLevel: String = "Grade 10",
    val subjects: List<String> = emptyList(),
    val examType: String? = null,
    val nextExamDate: String? = null,
    val prepStartDate: String? = null,
    val dailyStudyTargetMinutes: Long = 120L,
    val preferredSchedule: String? = "Morning & Evening",
    val pomodoroFocusMinutes: Long = 25L,
    val pomodoroBreakMinutes: Long = 5L,
    val onboardingCompleted: Boolean = false,
    val referralCode: String = "",
    val referredByCode: String? = null,
    val shareStatsWithFriends: Boolean = true,
    val ultraFocusPasscodeHash: String? = null,
    val allowedApps: List<String> = emptyList(),
    val blockedApps: List<String> = emptyList(),
    val themeMode: String = "system",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PublicStudyProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val gradeLevel: String? = null,
    val referralCode: String = "",
    val totalVerifiedStudyMinutes: Long = 0L,
    val totalVerifiedFocusMinutes: Long = 0L,
    val completedPomodoroSessions: Long = 0L,
    val currentStreakDays: Long = 0L,
    val longestStreakDays: Long = 0L,
    val lastQualifyingSessionDate: String? = null,
    val earnedBadges: List<String> = emptyList(),
    val shareStatsWithFriends: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudyNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subject: String = "",
    val sourceType: String = "topic", // "topic", "photo", "pdf"
    val sourceSummary: String? = null,
    val kalamNoteContent: String = "",
    val keyTermsJson: String? = null,
    val formulasSummary: String? = null,
    val revisionSummary: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudyTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subject: String = "",
    val topicTitle: String = "",
    val taskType: String = "study", // "study", "revision", "practice"
    val scheduledDate: String = "", // YYYY-MM-DD
    val plannedDurationMinutes: Long = 45L,
    val completed: Boolean = false,
    val examName: String? = null,
    val examDate: String? = null,
    val difficulty: String = "medium", // "easy", "medium", "hard"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FocusSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mode: String = "normal", // "normal", "pomodoro", "ultra"
    val durationMinutes: Long = 25L,
    val pomodoroIntervalsCompleted: Long = 1L,
    val subjectTag: String? = null,
    val sessionDate: String = "", // YYYY-MM-DD
    val completed: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TutorMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user", // "user", "assistant"
    val content: String = "",
    val subjectContext: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SavedVideo(
    @DocumentId val id: String = "",
    val userId: String = "",
    val youtubeVideoId: String = "",
    val title: String = "",
    val channelTitle: String = "",
    val subjectCategory: String = "",
    val thumbnailUrl: String? = null,
    val durationText: String? = null,
    val isSaved: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Friendship(
    @DocumentId val id: String = "",
    val requesterId: String = "",
    val receiverId: String = "",
    val requesterName: String = "",
    val receiverName: String = "",
    val requesterReferralCode: String? = null,
    val status: String = "pending", // "pending", "accepted", "declined"
    val participantUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class KeyTermItem(
    val term: String,
    val definition: String,
    val simplifiedExplanation: String,
    val example: String
)
