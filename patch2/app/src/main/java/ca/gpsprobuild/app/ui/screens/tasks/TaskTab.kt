@file:OptIn(ExperimentalMaterial3Api::class)

package ca.gpsprobuild.app.ui.screens.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ca.gpsprobuild.app.core.util.Hours
import ca.gpsprobuild.app.data.local.entity.TaskEntity
import ca.gpsprobuild.app.data.repository.JobRepository
import ca.gpsprobuild.app.data.repository.TaskRepository
import ca.gpsprobuild.app.domain.model.JobPhase
import ca.gpsprobuild.app.domain.model.JobType
import ca.gpsprobuild.app.ui.components.SectionHeader
import ca.gpsprobuild.app.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskTabUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val jobType: JobType = JobType.OTHER,
    val templateCount: Int = 0,
    val hideDone: Boolean = false
) {
    val doneCount: Int get() = tasks.count { it.status.isFinished }
    val progress: Float get() = if (tasks.isEmpty()) 0f else doneCount.toFloat() / tasks.size
    val estimatedHours: Double get() = tasks.sumOf { it.estimatedHours ?: 0.0 }

    val visible: List<TaskEntity>
        get() = if (hideDone) tasks.filterNot { it.status.isFinished } else tasks

    val byPhase: List<Pair<JobPhase, List<TaskEntity>>>
        get() = visible.groupBy { it.phase }
            .toList()
            .sortedBy { it.first.order }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskTabViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    jobRepository: JobRepository
) : ViewModel() {

    private val jobId: Long = savedStateHandle.get<String>("jobId")?.toLongOrNull() ?: 0L
    private val hideDone = MutableStateFlow(false)

    val state: StateFlow<TaskTabUiState> = combine(
        taskRepository.observeForJob(jobId),
        jobRepository.observeById(jobId),
        hideDone
    ) { tasks, job, hide ->
        val type = job?.jobType ?: JobType.OTHER
        TaskTabUiState(
            tasks = tasks,
            jobType = type,
            templateCount = taskRepository.templateFor(type)?.tasks?.size ?: 0,
            hideDone = hide
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskTabUiState())

    fun toggleHideDone() = hideDone.update { !it }

    fun setDone(task: TaskEntity, done: Boolean) {
        viewModelScope.launch { taskRepository.setDone(task, done) }
    }

    fun addTask(title: String, phase: JobPhase) {
        if (title.isBlank()) return
        viewModelScope.launch {
            taskRepository.save(TaskEntity(jobId = jobId, title = title.trim(), phase = phase))
        }
    }

    fun applyTemplate() {
        viewModelScope.launch {
            taskRepository.applyTemplate(jobId, state.value.jobType)
        }
    }

    fun delete(task: TaskEntity) {
        viewModelScope.launch { taskRepository.delete(task) }
    }
}

/**
 * Rendered inside the job detail tab strip rather than as its own screen, so the
 * job number and title stay on screen while you work down the list.
 */
@Composable
fun TaskTab(viewModel: TaskTabViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var newTaskTitle by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth()) {
        if (state.tasks.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${state.doneCount} of ${state.tasks.size} done",
                    style = MaterialTheme.typography.titleMedium
                )
                if (state.estimatedHours > 0) {
                    Text(
                        Hours.format(state.estimatedHours) + " estimated",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = viewModel::toggleHideDone) {
                Text(if (state.hideDone) "Show completed" else "Hide completed")
            }
        }

        if (state.tasks.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Checklist, contentDescription = null)
                        Text(
                            "No tasks yet",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                    Text(
                        text = if (state.templateCount > 0) {
                            "Start from the standard ${state.jobType.label} checklist, then edit it " +
                                "to suit this job."
                        } else {
                            "Add the first task below."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (state.templateCount > 0) {
                        OutlinedButton(
                            onClick = viewModel::applyTemplate,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text("Add ${state.templateCount} standard tasks")
                        }
                    }
                }
            }
            Spacer(Modifier.height(Dimens.cardGap))
        }

        state.byPhase.forEach { (phase, tasks) ->
            Spacer(Modifier.height(Dimens.cardGap))
            SectionHeader("${phase.label} (${tasks.count { it.status.isFinished }}/${tasks.size})")
            tasks.forEach { task ->
                TaskRow(
                    task = task,
                    onToggle = { viewModel.setDone(task, !task.status.isFinished) }
                )
            }
        }

        Spacer(Modifier.height(Dimens.sectionGap))
        OutlinedTextField(
            value = newTaskTitle,
            onValueChange = { newTaskTitle = it },
            label = { Text("Add a task") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(
                    onClick = {
                        viewModel.addTask(newTaskTitle, JobPhase.PREP)
                        newTaskTitle = ""
                    },
                    enabled = newTaskTitle.isNotBlank()
                ) { Icon(Icons.Filled.Add, contentDescription = "Add task") }
            }
        )
    }
}

@Composable
private fun TaskRow(task: TaskEntity, onToggle: () -> Unit) {
    val done = task.status.isFinished
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                task.estimatedHours?.takeIf { it > 0 }?.let {
                    Text(
                        Hours.format(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (task.isMilestone) {
                Icon(
                    Icons.Filled.Flag,
                    contentDescription = "Milestone",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (task.requiresInspection) {
                Icon(
                    Icons.Filled.Verified,
                    contentDescription = "Inspection",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp).padding(start = 4.dp)
                )
            }
        }
    }
}
