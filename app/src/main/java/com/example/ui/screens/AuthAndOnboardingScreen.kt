package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.ui.components.VidhyaIcons
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context as Activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type received.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed. Please try again.")
        }
    }
}

fun signOutVidhya(
    context: Context,
    credentialManager: CredentialManager,
    onSignOutComplete: () -> Unit,
    scope: CoroutineScope
) {
    Firebase.auth.signOut()
    scope.launch {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e("Auth", "Failed to clear credential state", e)
        } finally {
            onSignOutComplete()
        }
    }
}

@Composable
fun VidhyaAuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Brand Identity Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = VidhyaIcons.LearnWithVidhyaFilled,
                        contentDescription = "Vidhya Emblem",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "VidhyTop",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Learn better. Prepare smarter. Focus deeply.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Key Student Capabilities Summary
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AuthFeatureRow(
                            title = "AI Tutor & Live Voice",
                            subtitle = "Step-by-step explanations tailored to your grade and syllabus."
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        AuthFeatureRow(
                            title = "Kalam Interactive Notes",
                            subtitle = "Turn photos, PDFs, or topics into handwritten revision notes."
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        AuthFeatureRow(
                            title = "Exam Prep & Ultra Focus Launcher",
                            subtitle = "Realistic daily study targets and distraction-free Android home mode."
                        )
                    }
                }
            }

            // Sign-In Action Area
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (authError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = VidhyaIcons.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = authError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        authError = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                authError = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = VidhyaIcons.Account,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Secured by Google Authentication. Vidhya never asks for your Gmail password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AuthFeatureRow(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirstTimeOnboardingScreen(
    initialDisplayName: String,
    onFinishOnboarding: (
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
        topicsToPrepare: String
    ) -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    var displayName by rememberSaveable { mutableStateOf(initialDisplayName.ifBlank { "Student" }) }
    var schoolName by rememberSaveable { mutableStateOf("") }
    var gradeLevel by rememberSaveable { mutableStateOf("Grade 11") }

    val availableSubjects = listOf(
        "Mathematics", "Physics", "Chemistry", "Biology",
        "Science", "English", "Social Science", "Computer Science",
        "Economics", "History", "Languages"
    )
    val selectedSubjects = remember {
        mutableStateListOf("Mathematics", "Physics", "Chemistry", "English")
    }

    val defaultExamDate = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 45)
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }
    val todayDate = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    }

    var examType by rememberSaveable { mutableStateOf("Board / Semester Finals") }
    var nextExamDate by rememberSaveable { mutableStateOf(defaultExamDate) }
    var prepStartDate by rememberSaveable { mutableStateOf(todayDate) }
    var topicsToPrepare by rememberSaveable {
        mutableStateOf("Calculus Derivatives, Electromagnetic Induction, Chemical Bonding, Analytical Essays")
    }

    var dailyTargetMinutes by rememberSaveable { mutableLongStateOf(120L) }
    var preferredSchedule by rememberSaveable { mutableStateOf("Morning & Evening") }
    var pomodoroFocusMin by rememberSaveable { mutableLongStateOf(25L) }
    var pomodoroBreakMin by rememberSaveable { mutableLongStateOf(5L) }

    var validationError by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = step > 1 && step < 4) {
        step -= 1
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            if (step < 4) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (step > 1) {
                        IconButton(
                            onClick = { step -= 1 },
                            modifier = Modifier.testTag("onboarding_back_button")
                        ) {
                            Icon(
                                imageVector = VidhyaIcons.Back,
                                contentDescription = "Previous step"
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                    Text(
                        text = "Step $step of 3",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.size(48.dp))
                }

                LinearProgressIndicator(
                    progress = { step / 3f },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                )
            }

            AnimatedContent(
                targetState = step,
                label = "onboarding_step",
                modifier = Modifier.weight(1f)
            ) { currentStep ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (currentStep) {
                        1 -> {
                            Text(
                                text = "Tell Vidhya about your studies",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "We use this to tailor explanations and organize your study notes at the right depth.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Preferred Display Name *") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("onboarding_name_input")
                            )

                            OutlinedTextField(
                                value = schoolName,
                                onValueChange = { schoolName = it },
                                label = { Text("School or Institution (Optional)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("onboarding_school_input")
                            )

                            Text(
                                text = "Grade / Class Level",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            val grades = listOf("Grade 8", "Grade 9", "Grade 10", "Grade 11", "Grade 12", "Undergraduate")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                grades.forEach { grade ->
                                    FilterChip(
                                        selected = gradeLevel == grade,
                                        onClick = { gradeLevel = grade },
                                        label = { Text(grade) }
                                    )
                                }
                            }

                            Text(
                                text = "Subjects You Are Studying",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableSubjects.forEach { subj ->
                                    val isSelected = selectedSubjects.contains(subj)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected && selectedSubjects.size > 1) {
                                                selectedSubjects.remove(subj)
                                            } else if (!isSelected) {
                                                selectedSubjects.add(subj)
                                            }
                                        },
                                        label = { Text(subj) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }

                        2 -> {
                            Text(
                                text = "Exam Dates & Syllabus",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Vidhya uses your upcoming exam dates to build a realistic daily preparation schedule.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = examType,
                                onValueChange = { examType = it },
                                label = { Text("Examination Type (e.g., Board Exams, Finals)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = nextExamDate,
                                onValueChange = { nextExamDate = it },
                                label = { Text("Next Major Exam Date (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = prepStartDate,
                                onValueChange = { prepStartDate = it },
                                label = { Text("Preparation Start Date (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = topicsToPrepare,
                                onValueChange = { topicsToPrepare = it },
                                label = { Text("Priority Chapters or Topics (comma-separated)") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        3 -> {
                            Text(
                                text = "Daily Study & Focus Rhythm",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "We never schedule more study time than you can realistically sustain.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "Available Study Time Per Day: $dailyTargetMinutes mins (${dailyTargetMinutes / 60}h ${dailyTargetMinutes % 60}m)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            val targetOptions = listOf(60L, 90L, 120L, 150L, 180L, 240L)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                targetOptions.forEach { mins ->
                                    FilterChip(
                                        selected = dailyTargetMinutes == mins,
                                        onClick = { dailyTargetMinutes = mins },
                                        label = { Text("${mins}m / day") }
                                    )
                                }
                            }

                            Text(
                                text = "Preferred Study Schedule",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            val schedules = listOf("Early Morning", "Afternoon", "Evening", "Morning & Evening")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                schedules.forEach { sched ->
                                    FilterChip(
                                        selected = preferredSchedule == sched,
                                        onClick = { preferredSchedule = sched },
                                        label = { Text(sched) }
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = pomodoroFocusMin.toString(),
                                    onValueChange = { v -> v.toLongOrNull()?.let { pomodoroFocusMin = it } },
                                    label = { Text("Focus Interval (min)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = pomodoroBreakMin.toString(),
                                    onValueChange = { v -> v.toLongOrNull()?.let { pomodoroBreakMin = it } },
                                    label = { Text("Break (min, default 5)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        4 -> {
                            Spacer(modifier = Modifier.height(48.dp))
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Icon(
                                        imageVector = VidhyaIcons.Check,
                                        contentDescription = "Setup complete",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "You’re all set. Let’s make studying easier.",
                                    style = MaterialTheme.typography.headlineMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Your personalized study schedule, Kalam note workspace, and AI Tutor are ready for $gradeLevel.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(
                onClick = {
                    validationError = null
                    when (step) {
                        1 -> {
                            if (displayName.isBlank()) {
                                validationError = "Please enter your name."
                            } else {
                                step = 2
                            }
                        }
                        2 -> step = 3
                        3 -> step = 4
                        4 -> {
                            onFinishOnboarding(
                                displayName,
                                schoolName,
                                gradeLevel,
                                selectedSubjects.toList(),
                                examType,
                                nextExamDate,
                                prepStartDate,
                                dailyTargetMinutes,
                                preferredSchedule,
                                pomodoroFocusMin,
                                pomodoroBreakMin,
                                topicsToPrepare
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("onboarding_next_button")
            ) {
                Text(
                    text = when (step) {
                        1, 2 -> "Continue"
                        3 -> "Complete Setup"
                        else -> "Open AI Tutor"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
