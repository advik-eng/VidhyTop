package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.FocusSession
import com.example.data.model.Friendship
import com.example.data.model.PublicStudyProfile
import com.example.data.model.SavedVideo
import com.example.data.model.StudyNote
import com.example.data.model.StudyTask
import com.example.data.model.TutorMessage
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun Query.safeSnapshots(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
        } else if (snapshot != null) {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

private fun DocumentReference.safeSnapshots(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
        } else if (snapshot != null) {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

class VidhyaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        FirebaseAuth.getInstance()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun generateReferralCode(uid: String, displayName: String): String {
        val cleanName = displayName.filter { it.isLetterOrDigit() }.uppercase(Locale.US).take(4).padEnd(4, 'V')
        val cleanUid = uid.filter { it.isLetterOrDigit() }.uppercase(Locale.US).take(4).padEnd(4, '9')
        return "VID$cleanName$cleanUid"
    }

    // --- 1. User Profile & Public Study Profile ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .safeSnapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                    throw error
                }
        )
    }

    suspend fun getUserProfile(userId: String): Result<UserProfile?> = runCatching {
        val path = "users/$userId"
        try {
            val snap = db.collection("users").document(userId).get().await()
            if (snap.exists()) {
                snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            throw e
        }
    }

    suspend fun saveOnboardingAndProfile(profile: UserProfile): Result<Unit> = runCatching {
        val uid = requireUserId()
        val userPath = "users/$uid"
        val publicPath = "public_profiles/$uid"
        val referralCode = profile.referralCode.ifBlank { generateReferralCode(uid, profile.displayName) }

        val existingSnap = try {
            db.collection("users").document(uid).get().await()
        } catch (e: Exception) {
            null
        }

        val existingCreatedAt = existingSnap?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val userPayload = mutableMapOf<String, Any>(
            "userId" to uid,
            "displayName" to profile.displayName.trim().take(60).ifBlank { "Student" },
            "gradeLevel" to profile.gradeLevel.trim().take(40).ifBlank { "Grade 10" },
            "subjects" to profile.subjects.map { it.trim().take(60) }.filter { it.isNotBlank() }.take(15),
            "dailyStudyTargetMinutes" to profile.dailyStudyTargetMinutes.coerceIn(15L, 720L),
            "pomodoroFocusMinutes" to profile.pomodoroFocusMinutes.coerceIn(5L, 120L),
            "pomodoroBreakMinutes" to profile.pomodoroBreakMinutes.coerceIn(1L, 60L),
            "onboardingCompleted" to profile.onboardingCompleted,
            "referralCode" to referralCode.take(20),
            "shareStatsWithFriends" to profile.shareStatsWithFriends,
            "allowedApps" to profile.allowedApps.take(30),
            "blockedApps" to profile.blockedApps.take(30),
            "themeMode" to profile.themeMode.takeIf { it in listOf("system", "light", "dark") }.orEmpty().ifBlank { "system" },
            "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        profile.schoolName?.takeIf { it.isNotBlank() }?.let { userPayload["schoolName"] = it.trim().take(100) }
        profile.examType?.takeIf { it.isNotBlank() }?.let { userPayload["examType"] = it.trim().take(80) }
        profile.nextExamDate?.takeIf { it.isNotBlank() }?.let { userPayload["nextExamDate"] = it.trim().take(30) }
        profile.prepStartDate?.takeIf { it.isNotBlank() }?.let { userPayload["prepStartDate"] = it.trim().take(30) }
        profile.preferredSchedule?.takeIf { it.isNotBlank() }?.let { userPayload["preferredSchedule"] = it.trim().take(60) }
        profile.referredByCode?.takeIf { it.isNotBlank() }?.let { userPayload["referredByCode"] = it.trim().take(20) }
        profile.ultraFocusPasscodeHash?.takeIf { it.isNotBlank() }?.let { userPayload["ultraFocusPasscodeHash"] = it.take(128) }

        try {
            db.collection("users").document(uid).set(userPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existingCreatedAt == null) OperationType.CREATE else OperationType.UPDATE, userPath)
            throw e
        }

        // Also ensure PublicStudyProfile exists and stays synced
        val pubSnap = try {
            db.collection("public_profiles").document(uid).get().await()
        } catch (e: Exception) {
            null
        }
        val existingPublic = pubSnap?.toObject(PublicStudyProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val badges = (existingPublic?.earnedBadges ?: emptyList()).toMutableSet()
        if (profile.nextExamDate?.isNotBlank() == true) {
            badges.add("exam_planner")
        }
        val publicPayload = mutableMapOf<String, Any>(
            "userId" to uid,
            "displayName" to (userPayload["displayName"] as String),
            "gradeLevel" to (userPayload["gradeLevel"] as String),
            "referralCode" to referralCode.take(20),
            "totalVerifiedStudyMinutes" to (existingPublic?.totalVerifiedStudyMinutes ?: 0L),
            "totalVerifiedFocusMinutes" to (existingPublic?.totalVerifiedFocusMinutes ?: 0L),
            "completedPomodoroSessions" to (existingPublic?.completedPomodoroSessions ?: 0L),
            "currentStreakDays" to (existingPublic?.currentStreakDays ?: 0L),
            "longestStreakDays" to (existingPublic?.longestStreakDays ?: 0L),
            "earnedBadges" to badges.take(20),
            "shareStatsWithFriends" to profile.shareStatsWithFriends,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        existingPublic?.lastQualifyingSessionDate?.takeIf { it.isNotBlank() }?.let {
            publicPayload["lastQualifyingSessionDate"] = it
        }

        try {
            db.collection("public_profiles").document(uid).set(publicPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existingPublic == null) OperationType.CREATE else OperationType.UPDATE, publicPath)
            throw e
        }
    }

    fun observePublicProfile(userId: String): Flow<PublicStudyProfile?> = flow {
        val path = "public_profiles/$userId"
        emitAll(
            db.collection("public_profiles").document(userId)
                .safeSnapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(PublicStudyProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                    throw error
                }
        )
    }

    // --- 2. Interactive Kalam Study Notes ---

    fun observeNotes(userId: String): Flow<List<StudyNote>> = flow {
        val path = "users/$userId/notes"
        emitAll(
            db.collection("users").document(userId).collection("notes")
                .safeSnapshots()
                .map { snapshot ->
                    snapshot.toObjects(StudyNote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createNote(
        title: String,
        subject: String,
        sourceType: String,
        sourceSummary: String?,
        kalamNoteContent: String,
        keyTermsJson: String?,
        formulasSummary: String?,
        revisionSummary: String?
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val notesRef = db.collection("users").document(uid).collection("notes")
        val docRef = notesRef.document()
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "title" to title.trim().take(150).ifBlank { "Study Note" },
            "subject" to subject.trim().take(60).ifBlank { "General" },
            "sourceType" to sourceType.takeIf { it in listOf("topic", "photo", "pdf") }.orEmpty().ifBlank { "topic" },
            "kalamNoteContent" to kalamNoteContent.take(20000).ifBlank { "Notes content" },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        sourceSummary?.takeIf { it.isNotBlank() }?.let { payload["sourceSummary"] = it.take(5000) }
        keyTermsJson?.takeIf { it.isNotBlank() }?.let { payload["keyTermsJson"] = it.take(10000) }
        formulasSummary?.takeIf { it.isNotBlank() }?.let { payload["formulasSummary"] = it.take(5000) }
        revisionSummary?.takeIf { it.isNotBlank() }?.let { payload["revisionSummary"] = it.take(5000) }

        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun getNoteById(ownerUid: String, noteId: String): Result<StudyNote?> = runCatching {
        val path = "users/$ownerUid/notes/$noteId"
        try {
            val snap = db.collection("users").document(ownerUid).collection("notes").document(noteId).get().await()
            if (snap.exists()) {
                snap.toObject(StudyNote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            throw e
        }
    }

    suspend fun deleteNote(noteId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 3. Exam Prep Study Tasks ---

    fun observeStudyTasks(userId: String): Flow<List<StudyTask>> = flow {
        val path = "users/$userId/study_tasks"
        emitAll(
            db.collection("users").document(userId).collection("study_tasks")
                .safeSnapshots()
                .map { snapshot ->
                    snapshot.toObjects(StudyTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareBy<StudyTask> { it.scheduledDate }.thenBy { it.completed })
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createStudyTask(
        subject: String,
        topicTitle: String,
        taskType: String,
        scheduledDate: String,
        plannedDurationMinutes: Long,
        examName: String?,
        examDate: String?,
        difficulty: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val tasksRef = db.collection("users").document(uid).collection("study_tasks")
        val docRef = tasksRef.document()
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "subject" to subject.trim().take(60).ifBlank { "Subject" },
            "topicTitle" to topicTitle.trim().take(150).ifBlank { "Study Topic" },
            "taskType" to taskType.takeIf { it in listOf("study", "revision", "practice") }.orEmpty().ifBlank { "study" },
            "scheduledDate" to scheduledDate.trim().take(20),
            "plannedDurationMinutes" to plannedDurationMinutes.coerceIn(5L, 360L),
            "completed" to false,
            "difficulty" to difficulty.takeIf { it in listOf("easy", "medium", "hard") }.orEmpty().ifBlank { "medium" },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        examName?.takeIf { it.isNotBlank() }?.let { payload["examName"] = it.trim().take(80) }
        examDate?.takeIf { it.isNotBlank() }?.let { payload["examDate"] = it.trim().take(20) }

        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateStudyTask(task: StudyTask): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(task.id.isNotBlank()) { "Task ID must not be blank" }
        requireNotNull(task.createdAt) { "Task createdAt must not be null for update" }
        val docRef = db.collection("users").document(uid).collection("study_tasks").document(task.id)
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "subject" to task.subject.trim().take(60),
            "topicTitle" to task.topicTitle.trim().take(150),
            "taskType" to task.taskType,
            "scheduledDate" to task.scheduledDate.trim().take(20),
            "plannedDurationMinutes" to task.plannedDurationMinutes.coerceIn(5L, 360L),
            "completed" to task.completed,
            "difficulty" to task.difficulty,
            "createdAt" to task.createdAt,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        task.examName?.takeIf { it.isNotBlank() }?.let { payload["examName"] = it.trim().take(80) }
        task.examDate?.takeIf { it.isNotBlank() }?.let { payload["examDate"] = it.trim().take(20) }

        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteStudyTask(taskId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("study_tasks").document(taskId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 4. Focus & Pomodoro Sessions + Streak/Badge Calculation ---

    fun observeFocusSessions(userId: String): Flow<List<FocusSession>> = flow {
        val path = "users/$userId/focus_sessions"
        emitAll(
            db.collection("users").document(userId).collection("focus_sessions")
                .safeSnapshots()
                .map { snapshot ->
                    snapshot.toObjects(FocusSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun recordCompletedFocusSession(
        mode: String,
        durationMinutes: Long,
        pomodoroIntervalsCompleted: Long,
        subjectTag: String?
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val sessionsRef = db.collection("users").document(uid).collection("focus_sessions")
        val docRef = sessionsRef.document()

        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "mode" to mode.takeIf { it in listOf("normal", "pomodoro", "ultra") }.orEmpty().ifBlank { "normal" },
            "durationMinutes" to durationMinutes.coerceIn(1L, 480L),
            "pomodoroIntervalsCompleted" to pomodoroIntervalsCompleted.coerceIn(0L, 24L),
            "sessionDate" to todayStr,
            "completed" to true,
            "createdAt" to FieldValue.serverTimestamp()
        )
        subjectTag?.takeIf { it.isNotBlank() }?.let { payload["subjectTag"] = it.trim().take(60) }

        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }

        // Update validated PublicStudyProfile streak, badges, and focus totals
        updateVerifiedStatsAfterSession(
            uid = uid,
            addedFocusMinutes = durationMinutes.coerceIn(1L, 480L),
            addedStudyMinutes = durationMinutes.coerceIn(1L, 480L),
            addedPomodoros = pomodoroIntervalsCompleted.coerceIn(0L, 24L),
            sessionDate = todayStr
        )
        docRef.id
    }

    private suspend fun updateVerifiedStatsAfterSession(
        uid: String,
        addedFocusMinutes: Long,
        addedStudyMinutes: Long,
        addedPomodoros: Long,
        sessionDate: String
    ) {
        val pubRef = db.collection("public_profiles").document(uid)
        val snap = try {
            pubRef.get().await()
        } catch (e: Exception) {
            return
        }
        val current = snap.toObject(PublicStudyProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(cal.time)

        val lastDate = current?.lastQualifyingSessionDate
        val newStreak = when (lastDate) {
            sessionDate -> (current.currentStreakDays).coerceAtLeast(1L)
            yesterdayStr -> (current.currentStreakDays + 1L)
            else -> 1L
        }
        val newLongest = maxOf(current?.longestStreakDays ?: 0L, newStreak)

        val milestones = listOf(1L to "streak_1", 3L to "streak_3", 7L to "streak_7", 14L to "streak_14", 30L to "streak_30", 60L to "streak_60", 100L to "streak_100")
        val earned = (current?.earnedBadges ?: emptyList()).toMutableSet()
        for ((days, badgeId) in milestones) {
            if (newLongest >= days) earned.add(badgeId)
        }

        val updatedPayload = mutableMapOf<String, Any>(
            "userId" to uid,
            "displayName" to (current?.displayName?.ifBlank { "Student" } ?: "Student"),
            "referralCode" to (current?.referralCode?.ifBlank { generateReferralCode(uid, "Student") } ?: generateReferralCode(uid, "Student")),
            "totalVerifiedStudyMinutes" to ((current?.totalVerifiedStudyMinutes ?: 0L) + addedStudyMinutes).coerceAtMost(1000000L),
            "totalVerifiedFocusMinutes" to ((current?.totalVerifiedFocusMinutes ?: 0L) + addedFocusMinutes).coerceAtMost(1000000L),
            "completedPomodoroSessions" to ((current?.completedPomodoroSessions ?: 0L) + addedPomodoros).coerceAtMost(100000L),
            "currentStreakDays" to newStreak,
            "longestStreakDays" to newLongest,
            "lastQualifyingSessionDate" to sessionDate,
            "earnedBadges" to earned.take(20),
            "shareStatsWithFriends" to (current?.shareStatsWithFriends ?: true),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        current?.gradeLevel?.takeIf { it.isNotBlank() }?.let { updatedPayload["gradeLevel"] = it }

        try {
            pubRef.set(updatedPayload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, pubRef.path)
        }
    }

    // --- 5. AI Tutor Conversation History ---

    fun observeTutorMessages(userId: String): Flow<List<TutorMessage>> = flow {
        val path = "users/$userId/tutor_messages"
        emitAll(
            db.collection("users").document(userId).collection("tutor_messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .safeSnapshots()
                .map { snapshot ->
                    snapshot.toObjects(TutorMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun saveTutorMessage(role: String, content: String, subjectContext: String?): Result<String> = runCatching {
        val uid = requireUserId()
        val msgRef = db.collection("users").document(uid).collection("tutor_messages").document()
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "role" to role.takeIf { it in listOf("user", "assistant") }.orEmpty().ifBlank { "user" },
            "content" to content.take(15000).ifBlank { "..." },
            "createdAt" to FieldValue.serverTimestamp()
        )
        subjectContext?.takeIf { it.isNotBlank() }?.let { payload["subjectContext"] = it.take(60) }

        try {
            msgRef.set(payload).await()
            msgRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            throw e
        }
    }

    // --- 6. EduTube Saved & Recently Watched Videos ---

    fun observeSavedVideos(userId: String): Flow<List<SavedVideo>> = flow {
        val path = "users/$userId/saved_videos"
        emitAll(
            db.collection("users").document(userId).collection("saved_videos")
                .safeSnapshots()
                .map { snapshot ->
                    snapshot.toObjects(SavedVideo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun saveOrRecordVideo(
        youtubeVideoId: String,
        title: String,
        channelTitle: String,
        subjectCategory: String,
        thumbnailUrl: String?,
        durationText: String?,
        isSaved: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeDocId = youtubeVideoId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(64).ifBlank { "vid_1" }
        val docRef = db.collection("users").document(uid).collection("saved_videos").document(safeDocId)

        val existingSnap = try {
            docRef.get().await()
        } catch (e: Exception) {
            null
        }
        val existingCreatedAt = existingSnap?.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "youtubeVideoId" to youtubeVideoId.take(40),
            "title" to title.trim().take(200).ifBlank { "Educational Video" },
            "channelTitle" to channelTitle.trim().take(120).ifBlank { "Academic Channel" },
            "subjectCategory" to subjectCategory.trim().take(60).ifBlank { "General Learning" },
            "isSaved" to isSaved,
            "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        thumbnailUrl?.takeIf { it.isNotBlank() }?.let { payload["thumbnailUrl"] = it.take(500) }
        durationText?.takeIf { it.isNotBlank() }?.let { payload["durationText"] = it.take(30) }

        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (existingCreatedAt == null) OperationType.CREATE else OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- 7. Friends, Referrals & Validated Leaderboard ---

    fun observeFriendships(userId: String): Flow<List<Friendship>> = flow {
        val path = "friendships"
        emitAll(
            db.collection("friendships")
                .whereArrayContains("participantUids", userId)
                .safeSnapshots()
                .map { snapshot ->
                    snapshot.toObjects(Friendship::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun findStudentByReferralCode(referralCode: String): Result<PublicStudyProfile?> = runCatching {
        val cleanCode = referralCode.trim().uppercase(Locale.US)
        val path = "public_profiles"
        try {
            val querySnap = db.collection("public_profiles")
                .whereEqualTo("shareStatsWithFriends", true)
                .whereEqualTo("referralCode", cleanCode)
                .get()
                .await()
            querySnap.documents.firstOrNull()?.toObject(PublicStudyProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun sendFriendRequest(
        requesterName: String,
        targetProfile: PublicStudyProfile
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(targetProfile.userId != uid) { "You cannot send a friend request to yourself." }
        val pairSorted = listOf(uid, targetProfile.userId).sorted()
        val friendshipId = "${pairSorted[0]}_${pairSorted[1]}".filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(120)
        val docRef = db.collection("friendships").document(friendshipId)

        val payload = mutableMapOf<String, Any>(
            "requesterId" to uid,
            "receiverId" to targetProfile.userId,
            "requesterName" to requesterName.trim().take(60).ifBlank { "Student" },
            "receiverName" to targetProfile.displayName.trim().take(60).ifBlank { "Friend" },
            "status" to "pending",
            "participantUids" to listOf(uid, targetProfile.userId),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        targetProfile.referralCode.takeIf { it.isNotBlank() }?.let {
            payload["requesterReferralCode"] = it.take(20)
        }

        try {
            docRef.set(payload).await()
            friendshipId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun respondToFriendRequest(friendshipId: String, accept: Boolean): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("friendships").document(friendshipId)
        val newStatus = if (accept) "accepted" else "declined"
        try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun removeFriendship(friendshipId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("friendships").document(friendshipId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun fetchAcceptedFriendsLeaderboard(friendUids: List<String>): Result<List<PublicStudyProfile>> = runCatching {
        val uid = requireUserId()
        val allUids = (friendUids + uid).distinct()
        val results = mutableListOf<PublicStudyProfile>()
        for (targetUid in allUids) {
            val path = "public_profiles/$targetUid"
            try {
                val snap = db.collection("public_profiles").document(targetUid).get().await()
                if (snap.exists()) {
                    val profile = snap.toObject(PublicStudyProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    if (profile != null && (profile.userId == uid || profile.shareStatsWithFriends)) {
                        results.add(profile)
                    }
                }
            } catch (e: Exception) {
                // Skip if friend disabled shareStatsWithFriends
            }
        }
        results.sortedByDescending { it.totalVerifiedFocusMinutes + it.totalVerifiedStudyMinutes }
    }
}
