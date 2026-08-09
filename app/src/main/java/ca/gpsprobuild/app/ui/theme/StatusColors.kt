package ca.gpsprobuild.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ca.gpsprobuild.app.domain.model.JobStatus
import ca.gpsprobuild.app.domain.model.MaterialStatus
import ca.gpsprobuild.app.domain.model.Priority
import ca.gpsprobuild.app.domain.model.TaskStatus

/**
 * Semantic status colours sit outside the Material scheme deliberately: they carry
 * meaning rather than hierarchy, so they should not shift when the scheme does.
 */
@Immutable
data class StatusColors(
    val lead: Color,
    val quoting: Color,
    val scheduled: Color,
    val inProgress: Color,
    val onHold: Color,
    val complete: Color,
    val cancelled: Color,
    val overdue: Color
) {
    fun forJob(status: JobStatus): Color = when (status) {
        JobStatus.LEAD, JobStatus.SITE_VISIT -> lead
        JobStatus.ESTIMATING, JobStatus.QUOTED -> quoting
        JobStatus.APPROVED, JobStatus.SCHEDULED -> scheduled
        JobStatus.IN_PROGRESS, JobStatus.PUNCH_LIST -> inProgress
        JobStatus.ON_HOLD -> onHold
        JobStatus.COMPLETE, JobStatus.INVOICED, JobStatus.PAID -> complete
        JobStatus.CANCELLED, JobStatus.LOST -> cancelled
    }

    fun forTask(status: TaskStatus): Color = when (status) {
        TaskStatus.NOT_STARTED -> cancelled
        TaskStatus.IN_PROGRESS -> inProgress
        TaskStatus.BLOCKED -> onHold
        TaskStatus.DONE -> complete
        TaskStatus.SKIPPED -> cancelled
    }

    fun forMaterial(status: MaterialStatus): Color = when (status) {
        MaterialStatus.NEEDED -> overdue
        MaterialStatus.QUOTED -> quoting
        MaterialStatus.ORDERED, MaterialStatus.PARTIAL -> inProgress
        MaterialStatus.BACKORDERED -> onHold
        MaterialStatus.RECEIVED, MaterialStatus.INSTALLED -> complete
        MaterialStatus.RETURNED, MaterialStatus.CANCELLED -> cancelled
    }

    fun forPriority(priority: Priority): Color = when (priority) {
        Priority.LOW -> cancelled
        Priority.NORMAL -> scheduled
        Priority.HIGH -> onHold
        Priority.URGENT -> overdue
    }
}

val LightStatusColors = StatusColors(
    lead = Color(0xFF7B61FF),
    quoting = Color(0xFF8A6D1F),
    scheduled = Color(0xFF1F6F68),
    inProgress = Color(0xFF1565C0),
    onHold = Color(0xFFB26A00),
    complete = Color(0xFF2E7D32),
    cancelled = Color(0xFF6B7280),
    overdue = Color(0xFFB3261E)
)

val DarkStatusColors = StatusColors(
    lead = Color(0xFFB9A9FF),
    quoting = Color(0xFFE3C46B),
    scheduled = Color(0xFF7FD6CC),
    inProgress = Color(0xFF8FC0F5),
    onHold = Color(0xFFFFC078),
    complete = Color(0xFF9BD79E),
    cancelled = Color(0xFFA3ABB5),
    overdue = Color(0xFFFFB4AB)
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
