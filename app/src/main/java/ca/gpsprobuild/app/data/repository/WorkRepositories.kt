package ca.gpsprobuild.app.data.repository

import ca.gpsprobuild.app.data.local.SyncMeta
import ca.gpsprobuild.app.data.local.dao.JobAssignmentDao
import ca.gpsprobuild.app.data.local.dao.JobEventDao
import ca.gpsprobuild.app.data.local.dao.MaterialDao
import ca.gpsprobuild.app.data.local.dao.StaffDao
import ca.gpsprobuild.app.data.local.dao.SupplierDao
import ca.gpsprobuild.app.data.local.dao.SyncDao
import ca.gpsprobuild.app.data.local.dao.TaskAssignmentDao
import ca.gpsprobuild.app.data.local.dao.TaskDao
import ca.gpsprobuild.app.data.local.entity.JobAssignmentEntity
import ca.gpsprobuild.app.data.local.entity.JobEventEntity
import ca.gpsprobuild.app.data.local.entity.MaterialEntity
import ca.gpsprobuild.app.data.local.entity.StaffEntity
import ca.gpsprobuild.app.data.local.entity.SupplierEntity
import ca.gpsprobuild.app.data.local.entity.SyncOutboxEntity
import ca.gpsprobuild.app.data.local.entity.TaskAssignmentEntity
import ca.gpsprobuild.app.data.local.entity.TaskEntity
import ca.gpsprobuild.app.data.prefs.SettingsRepository
import ca.gpsprobuild.app.domain.model.JobEventType
import ca.gpsprobuild.app.domain.model.MaterialStatus
import ca.gpsprobuild.app.domain.model.TaskStatus
import ca.gpsprobuild.app.domain.template.TaskTemplateLoader
import ca.gpsprobuild.app.domain.model.JobType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val taskAssignmentDao: TaskAssignmentDao,
    private val jobAssignmentDao: JobAssignmentDao,
    private val jobEventDao: JobEventDao,
    private val syncDao: SyncDao,
    private val templates: TaskTemplateLoader,
    private val settings: SettingsRepository
) {
    fun observeForJob(jobId: Long): Flow<List<TaskEntity>> = taskDao.observeForJob(jobId)
    fun observeOverdue(today: LocalDate = LocalDate.now()): Flow<List<TaskEntity>> =
        taskDao.observeOverdue(today)
    fun observeDueOn(day: LocalDate): Flow<List<TaskEntity>> = taskDao.observeDueOn(day)
    fun observeAssignments(taskId: Long): Flow<List<TaskAssignmentEntity>> =
        taskAssignmentDao.observeForTask(taskId)

    suspend fun save(task: TaskEntity): Long {
        val deviceId = settings.settings.first().deviceId
        return if (task.id == 0L) {
            val sortOrder = if (task.sortOrder == 0) taskDao.nextSortOrder(task.jobId) else task.sortOrder
            val withMeta = task.copy(sortOrder = sortOrder, sync = SyncMeta.new(deviceId))
            val id = taskDao.insert(withMeta)
            enqueue(withMeta.sync.syncId)
            id
        } else {
            val withMeta = task.copy(sync = task.sync.touched(deviceId))
            taskDao.update(withMeta)
            enqueue(withMeta.sync.syncId)
            task.id
        }
    }

    /**
     * Toggling completion is the single most-used write in the app, so it also
     * stamps who and when. `completedByStaffId` is left null on the owner device
     * for now — it gets populated on field devices once sync lands, where "who
     * ticked this" actually matters.
     */
    suspend fun setDone(task: TaskEntity, done: Boolean) {
        val updated = task.copy(
            status = if (done) TaskStatus.DONE else TaskStatus.NOT_STARTED,
            completedAt = if (done) Instant.now() else null
        )
        save(updated)
        if (done) {
            writeEvent(task.jobId, "Task completed", task.title)
        }
    }

    suspend fun setStatus(task: TaskEntity, status: TaskStatus) {
        save(
            task.copy(
                status = status,
                completedAt = if (status.isFinished) Instant.now() else null
            )
        )
    }

    suspend fun delete(task: TaskEntity) {
        taskDao.delete(task)
    }

    /** Persists a drag-reorder in one pass rather than one write per row. */
    suspend fun reorder(tasks: List<TaskEntity>) {
        val deviceId = settings.settings.first().deviceId
        tasks.forEachIndexed { index, task ->
            if (task.sortOrder != index) {
                taskDao.update(task.copy(sortOrder = index, sync = task.sync.touched(deviceId)))
            }
        }
    }

    fun templateFor(jobType: JobType) = templates.forJobType(jobType)

    /**
     * Applies a starter checklist. Appended rather than replacing, so running it
     * twice by accident does not wipe work already ticked off.
     */
    suspend fun applyTemplate(jobId: Long, jobType: JobType): Int {
        val deviceId = settings.settings.first().deviceId
        val template = templates.forJobType(jobType) ?: return 0
        var order = taskDao.nextSortOrder(jobId)
        val rows = template.tasks.map { item ->
            TaskEntity(
                jobId = jobId,
                title = item.title,
                phase = item.phase,
                estimatedHours = item.hours,
                isMilestone = item.milestone,
                requiresInspection = item.inspection,
                sortOrder = order++,
                sync = SyncMeta.new(deviceId)
            )
        }
        taskDao.insertAll(rows)
        writeEvent(jobId, "Task list added", "${rows.size} tasks from the ${template.label} template")
        return rows.size
    }

    suspend fun assign(task: TaskEntity, staffId: Long) {
        val deviceId = settings.settings.first().deviceId
        taskAssignmentDao.insert(
            TaskAssignmentEntity(
                taskId = task.id,
                staffId = staffId,
                sync = SyncMeta.new(deviceId)
            )
        )
        // Assigning someone to a task implies they are on the job. Making that
        // implicit avoids a class of "why isn't Dave on the crew list" confusion.
        if (jobAssignmentDao.find(task.jobId, staffId) == null) {
            jobAssignmentDao.insert(
                JobAssignmentEntity(
                    jobId = task.jobId,
                    staffId = staffId,
                    sync = SyncMeta.new(deviceId)
                )
            )
        }
    }

    suspend fun unassign(taskId: Long, staffId: Long) = taskAssignmentDao.unassign(taskId, staffId)

    private suspend fun writeEvent(jobId: Long, title: String, body: String?) {
        val deviceId = settings.settings.first().deviceId
        jobEventDao.insert(
            JobEventEntity(
                jobId = jobId,
                occurredAt = Instant.now(),
                type = JobEventType.TASK_COMPLETED,
                title = title,
                body = body,
                sync = SyncMeta.new(deviceId)
            )
        )
    }

    private suspend fun enqueue(syncId: String) {
        if (settings.settings.first().isField) {
            syncDao.enqueueOutbox(SyncOutboxEntity(entityType = "task", entitySyncId = syncId))
        }
    }
}

@Singleton
class MaterialRepository @Inject constructor(
    private val materialDao: MaterialDao,
    private val supplierDao: SupplierDao,
    private val syncDao: SyncDao,
    private val settings: SettingsRepository
) {
    fun observeForJob(jobId: Long): Flow<List<MaterialEntity>> = materialDao.observeForJob(jobId)
    fun observeBuyList(): Flow<List<MaterialEntity>> = materialDao.observeBuyList()
    fun observeSuppliers(): Flow<List<SupplierEntity>> = supplierDao.observeAll()

    suspend fun save(material: MaterialEntity): Long {
        val deviceId = settings.settings.first().deviceId
        return if (material.id == 0L) {
            val sortOrder = if (material.sortOrder == 0) {
                materialDao.nextSortOrder(material.jobId)
            } else {
                material.sortOrder
            }
            val withMeta = material.copy(sortOrder = sortOrder, sync = SyncMeta.new(deviceId))
            val id = materialDao.insert(withMeta)
            enqueue(withMeta.sync.syncId)
            id
        } else {
            val withMeta = material.copy(sync = material.sync.touched(deviceId))
            materialDao.update(withMeta)
            enqueue(withMeta.sync.syncId)
            material.id
        }
    }

    /**
     * Advances a material one step along the buying cycle. Tapping the status chip
     * is the whole interaction — nobody standing at a supply desk wants a form.
     */
    suspend fun advanceStatus(material: MaterialEntity) {
        val today = LocalDate.now()
        val next = when (material.status) {
            MaterialStatus.NEEDED -> material.copy(
                status = MaterialStatus.ORDERED,
                orderedDate = today
            )
            MaterialStatus.QUOTED -> material.copy(
                status = MaterialStatus.ORDERED,
                orderedDate = today
            )
            MaterialStatus.ORDERED, MaterialStatus.PARTIAL, MaterialStatus.BACKORDERED ->
                material.copy(
                    status = MaterialStatus.RECEIVED,
                    receivedDate = today,
                    quantityReceived = material.quantity
                )
            MaterialStatus.RECEIVED -> material.copy(status = MaterialStatus.INSTALLED)
            else -> material.copy(status = MaterialStatus.NEEDED)
        }
        save(next)
    }

    suspend fun setStatus(material: MaterialEntity, status: MaterialStatus) {
        save(material.copy(status = status))
    }

    suspend fun delete(material: MaterialEntity) = materialDao.delete(material)

    suspend fun saveSupplier(supplier: SupplierEntity): Long {
        val deviceId = settings.settings.first().deviceId
        return if (supplier.id == 0L) {
            supplierDao.insert(supplier.copy(sync = SyncMeta.new(deviceId)))
        } else {
            supplierDao.update(supplier.copy(sync = supplier.sync.touched(deviceId)))
            supplier.id
        }
    }

    private suspend fun enqueue(syncId: String) {
        if (settings.settings.first().isField) {
            syncDao.enqueueOutbox(SyncOutboxEntity(entityType = "material", entitySyncId = syncId))
        }
    }
}

@Singleton
class StaffRepository @Inject constructor(
    private val staffDao: StaffDao,
    private val jobAssignmentDao: JobAssignmentDao,
    private val settings: SettingsRepository
) {
    fun observeAll(): Flow<List<StaffEntity>> = staffDao.observeAll()
    fun observeActive(): Flow<List<StaffEntity>> = staffDao.observeActive()
    fun observeById(id: Long): Flow<StaffEntity?> = staffDao.observeById(id)
    fun observeJobAssignments(jobId: Long): Flow<List<JobAssignmentEntity>> =
        jobAssignmentDao.observeForJob(jobId)
    fun observeAssignmentsForStaff(staffId: Long): Flow<List<JobAssignmentEntity>> =
        jobAssignmentDao.observeForStaff(staffId)

    /** Insurance lapse is a real liability, so the warning window is generous. */
    fun observeExpiringCompliance(): Flow<List<StaffEntity>> =
        staffDao.observeExpiringCompliance(LocalDate.now().plusDays(30))

    suspend fun getById(id: Long): StaffEntity? = staffDao.getById(id)

    suspend fun save(staff: StaffEntity): Long {
        val deviceId = settings.settings.first().deviceId
        return if (staff.id == 0L) {
            staffDao.insert(staff.copy(sync = SyncMeta.new(deviceId)))
        } else {
            staffDao.update(staff.copy(sync = staff.sync.touched(deviceId)))
            staff.id
        }
    }

    suspend fun archive(staff: StaffEntity) {
        save(staff.copy(archivedAt = Instant.now(), isActive = false))
    }

    suspend fun assignToJob(jobId: Long, staffId: Long, isLead: Boolean = false) {
        if (jobAssignmentDao.find(jobId, staffId) != null) return
        val deviceId = settings.settings.first().deviceId
        jobAssignmentDao.insert(
            JobAssignmentEntity(
                jobId = jobId,
                staffId = staffId,
                isLead = isLead,
                sync = SyncMeta.new(deviceId)
            )
        )
    }

    suspend fun removeFromJob(jobId: Long, staffId: Long) =
        jobAssignmentDao.unassign(jobId, staffId)
}
