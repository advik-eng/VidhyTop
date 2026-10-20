package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.StudyTask
import com.example.data.model.UserProfile
import com.example.ui.components.VidhyaIcons
import com.example.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ExamPrepScreen(
    userProfile: UserProfile?,
    studyTasksState: UiState<List<StudyTask>>,
    onToggleTaskCompleted: (StudyTask) -> Unit,
    onRescheduleTaskToTomorrow: (StudyTask) -> Unit,
    onAddStudyTask: (subject: String, topic: String, type: String, date: String, duration: Long, difficulty: String) -> Unit,
    onUpdateExamPlan: (examType: String, nextExamDate: String, dailyTargetMinutes: Long) -> Unit,
    onRegenerateFullSchedule: (subjects: List<String>, topics: String, dailyMinutes: Long, examName: String, examDate: String?) -> Unit
) {
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val allTasks = (studyTasksState as? UiState.Success)?.data.orEmpty()
    val todayTasks = allTasks.filter { it.scheduledDate == todayStr }
    val upcomingTasks = allTasks.filter { it.scheduledDate > todayStr }

    val dailyTarget = userProfile?.dailyStudyTargetMinutes ?: 120L
    val todayCompletedMinutes = todayTasks.filter { it.completed }.sumOf { it.plannedDurationMinutes }
    val todayPlannedMinutes = todayTasks.sumOf { it.plannedDurationMinutes }

    var showEditPlanDialog by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }

    val daysUntilExam = remember(userProfile?.nextExamDate) {
        val dateStr = userProfile?.nextExamDate
        if (dateStr.isNullOrBlank()) null
        else {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val target = sdf.parse(dateStr)
                if (target != null) {
                    val diffMs = target.time - System.currentTimeMillis()
                    TimeUnit.MILLISECONDS.toDays(diffMs).coerceAtLeast(0L)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Next Exam & Date + Edit Plan Action
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Exam Preparation",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Personalized daily targets & adaptive syllabus planner",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { showEditPlanDialog = true },
                    modifier = Modifier.testTag("edit_exam_plan_button")
                ) {
                    Icon(imageVector = VidhyaIcons.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Plan")
                }
            }
        }

        // Priority 1 & 2: Next Exam Date + Today's Total Study Target
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "1. WHEN ARE MY EXAMS?",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = userProfile?.examType?.takeIf { it.isNotBlank() } ?: "No Exam Date Configured",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (!userProfile?.nextExamDate.isNullOrBlank()) {
                                    "Exam Date: ${userProfile?.nextExamDate}"
                                } else {
                                    "Tap 'Edit Plan' to set your exam date"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (daysUntilExam != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "$daysUntilExam",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "days left",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "2. TODAY'S STUDY TARGET ($todayStr)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${todayCompletedMinutes}m / ${dailyTarget}m completed",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (todayCompletedMinutes.toFloat() / dailyTarget.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Planned today: ${todayPlannedMinutes}m across ${todayTasks.size} tasks • Preferred: ${userProfile?.preferredSchedule ?: "Flexible"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Priority 3 & 4: What should I study today? + Time planned per subject
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Today's Study & Revision Tasks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = { showAddTaskDialog = true },
                    modifier = Modifier.testTag("add_study_task_button")
                ) {
                    Icon(imageVector = VidhyaIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Task")
                }
            }
        }

        if (todayTasks.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "No tasks scheduled for today",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Generate a personalized 5-day study plan based on your subjects and daily target (${dailyTarget}m/day).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                onRegenerateFullSchedule(
                                    userProfile?.subjects.orEmpty(),
                                    "",
                                    dailyTarget,
                                    userProfile?.examType ?: "Upcoming Exams",
                                    userProfile?.nextExamDate
                                )
                            },
                            modifier = Modifier.testTag("generate_schedule_button")
                        ) {
                            Text("Generate Realistic Daily Schedule")
                        }
                    }
                }
            }
        } else {
            items(todayTasks, key = { it.id }) { task ->
                StudyTaskRowCard(
                    task = task,
                    onToggleCompleted = { onToggleTaskCompleted(task) },
                    onReschedule = { onRescheduleTaskToTomorrow(task) }
                )
            }
        }

        if (upcomingTasks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Upcoming Scheduled Days (${upcomingTasks.size} tasks)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(upcomingTasks.take(8), key = { it.id }) { task ->
                StudyTaskRowCard(
                    task = task,
                    onToggleCompleted = { onToggleTaskCompleted(task) },
                    onReschedule = null
                )
            }
        }
    }

    if (showEditPlanDialog) {
        var examTypeInput by remember { mutableStateOf(userProfile?.examType ?: "Board Finals") }
        var examDateInput by remember { mutableStateOf(userProfile?.nextExamDate ?: todayStr) }
        var dailyMinsInput by remember { mutableStateOf((userProfile?.dailyStudyTargetMinutes ?: 120L).toString()) }

        AlertDialog(
            onDismissRequest = { showEditPlanDialog = false },
            title = { Text("Edit Exam Plan & Availability") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = examTypeInput,
                        onValueChange = { examTypeInput = it },
                        label = { Text("Examination Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = examDateInput,
                        onValueChange = { examDateInput = it },
                        label = { Text("Exam Date (YYYY-MM-DD)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dailyMinsInput,
                        onValueChange = { dailyMinsInput = it },
                        label = { Text("Realistic Daily Study Time (mins)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = dailyMinsInput.toLongOrNull() ?: 120L
                        onUpdateExamPlan(examTypeInput, examDateInput, mins)
                        showEditPlanDialog = false
                    }
                ) {
                    Text("Save & Recalculate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPlanDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddTaskDialog) {
        var subjectInput by remember { mutableStateOf(userProfile?.subjects?.firstOrNull() ?: "Mathematics") }
        var topicInput by remember { mutableStateOf("") }
        var typeInput by remember { mutableStateOf("study") }
        var durationInput by remember { mutableStateOf("45") }

        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            title = { Text("Add Study Task for Today") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = subjectInput,
                        onValueChange = { subjectInput = it },
                        label = { Text("Subject") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = topicInput,
                        onValueChange = { topicInput = it },
                        label = { Text("Chapter or Topic") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("study", "revision", "practice").forEach { t ->
                            FilterChip(
                                selected = typeInput == t,
                                onClick = { typeInput = t },
                                label = { Text(t.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = durationInput,
                        onValueChange = { durationInput = it },
                        label = { Text("Planned Duration (mins)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (topicInput.isNotBlank()) {
                            onAddStudyTask(
                                subjectInput,
                                topicInput,
                                typeInput,
                                todayStr,
                                durationInput.toLongOrNull() ?: 45L,
                                "medium"
                            )
                            showAddTaskDialog = false
                        }
                    }
                ) {
                    Text("Add Task")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StudyTaskRowCard(
    task: StudyTask,
    onToggleCompleted: () -> Unit,
    onReschedule: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = { onToggleCompleted() },
                modifier = Modifier.testTag("task_checkbox_${task.id}")
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.subject.uppercase(Locale.US),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${task.taskType.uppercase(Locale.US)} • ${task.plannedDurationMinutes}m • ${task.scheduledDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = task.topicTitle,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
            if (!task.completed && onReschedule != null) {
                TextButton(onClick = onReschedule) {
                    Text("Defer", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
