package ca.gpsprobuild.app.data.repository

import android.net.Uri
import ca.gpsprobuild.app.core.util.Money
import ca.gpsprobuild.app.data.file.PhotoStore
import ca.gpsprobuild.app.data.local.SyncMeta
import ca.gpsprobuild.app.data.local.dao.ChangeOrderDao
import ca.gpsprobuild.app.data.local.dao.ExpenseDao
import ca.gpsprobuild.app.data.local.dao.JobEventDao
import ca.gpsprobuild.app.data.local.dao.MaterialDao
import ca.gpsprobuild.app.data.local.dao.PhotoDao
import ca.gpsprobuild.app.data.local.dao.StaffDao
import ca.gpsprobuild.app.data.local.dao.SyncDao
import ca.gpsprobuild.app.data.local.dao.TimeEntryDao
import ca.gpsprobuild.app.data.local.entity.ExpenseEntity
import ca.gpsprobuild.app.data.local.entity.JobEventEntity
import ca.gpsprobuild.app.data.local.entity.PhotoEntity
import ca.gpsprobuild.app.data.local.entity.SyncOutboxEntity
import ca.gpsprobuild.app.data.local.entity.TimeEntryEntity
import ca.gpsprobuild.app.data.prefs.SettingsRepository
import ca.gpsprobuild.app.domain.model.JobEventType
import ca.gpsprobuild.app.domain.model.PhotoCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepository @Inject constructor(
    private val photoDao: PhotoDao,
    private val jobEventDao: JobEventDao,
    private val photoStore: PhotoStore,
    private val syncDao: SyncDao,
    private val settings: SettingsRepository
) {
    fun observeForJob(jobId: Long): Flow<List<PhotoEntity>> = photoDao.observeForJob(jobId)

    fun fileFor(photo: PhotoEntity): File = photoStore.fileFor(photo.fileName)
    fun thumbFor(photo: PhotoEntity): File = photoStore.thumbFor(photo.thumbFileName)
    fun newCameraTarget() = photoStore.newCameraTarget()

    suspend fun importFromUri(uri: Uri, jobId: Long, category: PhotoCategory): Long? {
        val stored = photoStore.importFromUri(uri, jobId) ?: return null
        return persist(stored.contentHash, jobId, category) {
            PhotoEntity(
                jobId = jobId,
                fileName = stored.fileName,
                thumbFileName = stored.thumbFileName,
                contentHash = stored.contentHash,
                category = category,
                widthPx = stored.widthPx,
                heightPx = stored.heightPx,
                fileSizeBytes = stored.fileSizeBytes
            )
        }
    }

    suspend fun importFromCapture(file: File, jobId: Long, category: PhotoCategory): Long? {
        val stored = photoStore.importFromFile(file, jobId) ?: return null
        return persist(stored.contentHash, jobId, category) {
            PhotoEntity(
                jobId = jobId,
                fileName = stored.fileName,
                thumbFileName = stored.thumbFileName,
                contentHash = stored.contentHash,
                category = category,
                widthPx = stored.widthPx,
                heightPx = stored.heightPx,
                fileSizeBytes = stored.fileSizeBytes
            )
        }
    }

    suspend fun setCaption(photo: PhotoEntity, caption: String) {
        val deviceId = settings.settings.first().deviceId
        photoDao.update(
            photo.copy(
                caption = caption.trim().takeIf { it.isNotBlank() },
                sync = photo.sync.touched(deviceId)
            )
        )
    }

    suspend fun setCategory(photo: PhotoEntity, category: PhotoCategory) {
        val deviceId = settings.settings.first().deviceId
        photoDao.update(photo.copy(category = category, sync = photo.sync.touched(deviceId)))
    }

    suspend fun delete(photo: PhotoEntity) {
        photoDao.delete(photo)
        photoStore.delete(photo.fileName, photo.thumbFileName)
    }

    private suspend fun persist(
        hash: String,
        jobId: Long,
        category: PhotoCategory,
        build: () -> PhotoEntity
    ): Long? {
        // Content-addressed, so re-importing the same shot is a no-op rather than
        // a duplicate row and a second copy on disk.
        photoDao.getByHash(hash)?.let { return it.id }

        val deviceId = settings.settings.first().deviceId
        val entity = build().copy(sync = SyncMeta.new(deviceId))
        val id = photoDao.insert(entity)

        jobEventDao.insert(
            JobEventEntity(
                jobId = jobId,
                occurredAt = Instant.now(),
                type = JobEventType.PHOTO_ADDED,
                title = "Photo added",
                body = category.label,
                sync = SyncMeta.new(deviceId)
            )
        )
        if (settings.settings.first().isField) {
            syncDao.enqueueOutbox(
                SyncOutboxEntity(entityType = "photo", entitySyncId = entity.sync.syncId)
            )
        }
        return id
    }
}

/**
 * What a job has actually cost, versus what it was sold for.
 *
 * Every figure here is internal — it is stripped from assignment packets and
 * masked by privacy mode, because none of it is the client's business.
 */
data class JobCostSummary(
    val contractCents: Long = 0,
    val changeOrderCents: Long = 0,
    val materialCents: Long = 0,
    val labourCents: Long = 0,
    val expenseCents: Long = 0,
    val hoursLogged: Double = 0.0
) {
    val revenueCents: Long get() = contractCents + changeOrderCents
    val costCents: Long get() = materialCents + labourCents + expenseCents
    val marginCents: Long get() = revenueCents - costCents
    val marginFraction: Double? get() = Money.marginFraction(revenueCents, costCents)
}

@Singleton
class WorkLogRepository @Inject constructor(
    private val timeEntryDao: TimeEntryDao,
    private val expenseDao: ExpenseDao,
    private val changeOrderDao: ChangeOrderDao,
    private val materialDao: MaterialDao,
    private val staffDao: StaffDao,
    private val syncDao: SyncDao,
    private val settings: SettingsRepository
) {
    fun observeTimeEntries(jobId: Long): Flow<List<TimeEntryEntity>> =
        timeEntryDao.observeForJob(jobId)

    fun observeExpenses(jobId: Long): Flow<List<ExpenseEntity>> = expenseDao.observeForJob(jobId)

    fun observeMaterialCost(jobId: Long): Flow<List<ca.gpsprobuild.app.data.local.entity.MaterialEntity>> =
        materialDao.observeForJob(jobId)

    fun observeApprovedChangeOrderCents(jobId: Long): Flow<Long> =
        changeOrderDao.observeApprovedDeltaCents(jobId)

    /**
     * Logs hours against a job. The staff member's rate is captured at entry time
     * rather than looked up later, so giving someone a raise does not silently
     * rewrite what last spring's jobs appear to have cost.
     */
    suspend fun logHours(
        jobId: Long,
        staffId: Long,
        date: LocalDate,
        hours: Double,
        notes: String?
    ) {
        val deviceId = settings.settings.first().deviceId
        val rate = staffDao.getById(staffId)?.hourlyRateCents
        val entry = TimeEntryEntity(
            jobId = jobId,
            staffId = staffId,
            workDate = date,
            hours = hours,
            rateSnapshotCents = rate,
            notes = notes?.trim()?.takeIf { it.isNotBlank() },
            sync = SyncMeta.new(deviceId)
        )
        timeEntryDao.insert(entry)
        if (settings.settings.first().isField) {
            syncDao.enqueueOutbox(
                SyncOutboxEntity(entityType = "time_entry", entitySyncId = entry.sync.syncId)
            )
        }
    }

    suspend fun deleteTimeEntry(entry: TimeEntryEntity) = timeEntryDao.delete(entry)

    suspend fun addExpense(expense: ExpenseEntity): Long {
        val deviceId = settings.settings.first().deviceId
        val entity = expense.copy(sync = SyncMeta.new(deviceId))
        val id = expenseDao.insert(entity)
        if (settings.settings.first().isField) {
            syncDao.enqueueOutbox(
                SyncOutboxEntity(entityType = "expense", entitySyncId = entity.sync.syncId)
            )
        }
        return id
    }

    suspend fun deleteExpense(expense: ExpenseEntity) = expenseDao.delete(expense)
}
