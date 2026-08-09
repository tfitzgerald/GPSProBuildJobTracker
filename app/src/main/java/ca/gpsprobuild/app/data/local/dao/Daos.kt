package ca.gpsprobuild.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import ca.gpsprobuild.app.data.local.entity.AppointmentAttendeeEntity
import ca.gpsprobuild.app.data.local.entity.AppointmentEntity
import ca.gpsprobuild.app.data.local.entity.ChangeOrderEntity
import ca.gpsprobuild.app.data.local.entity.ContactEntity
import ca.gpsprobuild.app.data.local.entity.CustomerEntity
import ca.gpsprobuild.app.data.local.entity.DocumentEntity
import ca.gpsprobuild.app.data.local.entity.ExpenseEntity
import ca.gpsprobuild.app.data.local.entity.JobAssignmentEntity
import ca.gpsprobuild.app.data.local.entity.JobEntity
import ca.gpsprobuild.app.data.local.entity.JobEventEntity
import ca.gpsprobuild.app.data.local.entity.MaterialEntity
import ca.gpsprobuild.app.data.local.entity.PendingLeadEntity
import ca.gpsprobuild.app.data.local.entity.PhotoEntity
import ca.gpsprobuild.app.data.local.entity.StaffEntity
import ca.gpsprobuild.app.data.local.entity.SupplierEntity
import ca.gpsprobuild.app.data.local.entity.SyncLogEntity
import ca.gpsprobuild.app.data.local.entity.SyncOutboxEntity
import ca.gpsprobuild.app.data.local.entity.SyncPeerEntity
import ca.gpsprobuild.app.data.local.entity.TaskAssignmentEntity
import ca.gpsprobuild.app.data.local.entity.TaskEntity
import ca.gpsprobuild.app.data.local.entity.TimeEntryEntity
import ca.gpsprobuild.app.data.local.entity.TombstoneEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Shared write surface. Room supports generic base DAOs, which keeps the concrete
 * DAOs focused on the queries that are actually interesting.
 */
interface BaseDao<T> {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: T): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<T>): List<Long>

    @Update
    suspend fun update(entity: T)

    @Upsert
    suspend fun upsert(entity: T)

    @Delete
    suspend fun delete(entity: T)
}

// ---------------------------------------------------------------------------

@Dao
interface CustomerDao : BaseDao<CustomerEntity> {

    @Query("SELECT * FROM customers WHERE archivedAt IS NULL ORDER BY isFavourite DESC, displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE archivedAt IS NOT NULL ORDER BY archivedAt DESC")
    fun observeArchived(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observeById(id: Long): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): CustomerEntity?

    @Query(
        """
        SELECT * FROM customers
        WHERE archivedAt IS NULL AND (
            displayName LIKE '%' || :q || '%' COLLATE NOCASE OR
            companyName LIKE '%' || :q || '%' COLLATE NOCASE OR
            primaryPhone LIKE '%' || :q || '%' OR
            secondaryPhone LIKE '%' || :q || '%' OR
            email LIKE '%' || :q || '%' COLLATE NOCASE OR
            street1 LIKE '%' || :q || '%' COLLATE NOCASE OR
            city LIKE '%' || :q || '%' COLLATE NOCASE OR
            postalCode LIKE '%' || :q || '%' COLLATE NOCASE
        )
        ORDER BY isFavourite DESC, displayName COLLATE NOCASE
        """
    )
    fun search(q: String): Flow<List<CustomerEntity>>

    @Query("SELECT COUNT(*) FROM customers WHERE archivedAt IS NULL")
    fun observeCount(): Flow<Int>
}

@Dao
interface ContactDao : BaseDao<ContactEntity> {
    @Query("SELECT * FROM contacts WHERE customerId = :customerId ORDER BY name COLLATE NOCASE")
    fun observeForCustomer(customerId: Long): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): ContactEntity?
}

// ---------------------------------------------------------------------------

@Dao
interface JobDao : BaseDao<JobEntity> {

    @Query("SELECT * FROM jobs WHERE archivedAt IS NULL ORDER BY startDate DESC, id DESC")
    fun observeAll(): Flow<List<JobEntity>>

    @Query(
        """
        SELECT * FROM jobs
        WHERE archivedAt IS NULL AND status NOT IN ('PAID', 'CANCELLED', 'LOST')
        ORDER BY
            CASE status
                WHEN 'IN_PROGRESS' THEN 0
                WHEN 'PUNCH_LIST' THEN 1
                WHEN 'SCHEDULED' THEN 2
                WHEN 'APPROVED' THEN 3
                ELSE 4
            END,
            startDate
        """
    )
    fun observeOpen(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE customerId = :customerId AND archivedAt IS NULL ORDER BY startDate DESC, id DESC")
    fun observeForCustomer(customerId: Long): Flow<List<JobEntity>>

    /** Pass [JobStatus.name] values — enum binding as a query parameter is avoided on purpose. */
    @Query("SELECT * FROM jobs WHERE status IN (:statusNames) AND archivedAt IS NULL")
    fun observeByStatusNames(statusNames: List<String>): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun observeById(id: Long): Flow<JobEntity?>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getById(id: Long): JobEntity?

    @Query("SELECT * FROM jobs WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): JobEntity?

    @Query(
        """
        SELECT * FROM jobs
        WHERE archivedAt IS NULL AND (
            jobNumber LIKE '%' || :q || '%' COLLATE NOCASE OR
            title LIKE '%' || :q || '%' COLLATE NOCASE OR
            scopeOfWork LIKE '%' || :q || '%' COLLATE NOCASE OR
            siteStreet1 LIKE '%' || :q || '%' COLLATE NOCASE
        )
        ORDER BY startDate DESC, id DESC
        """
    )
    fun search(q: String): Flow<List<JobEntity>>

    /** Jobs sitting in QUOTED with no decision — the follow-up nudge on the dashboard. */
    @Query("SELECT * FROM jobs WHERE status = 'QUOTED' AND quotedDate IS NOT NULL AND quotedDate <= :before AND archivedAt IS NULL")
    fun observeStaleQuotes(before: LocalDate): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE status = 'COMPLETE' AND archivedAt IS NULL")
    fun observeAwaitingInvoice(): Flow<List<JobEntity>>

    @Query("SELECT COUNT(*) FROM jobs WHERE status NOT IN ('PAID','CANCELLED','LOST') AND archivedAt IS NULL")
    fun observeOpenCount(): Flow<Int>
}

@Dao
interface JobEventDao : BaseDao<JobEventEntity> {
    @Query("SELECT * FROM job_events WHERE jobId = :jobId ORDER BY occurredAt DESC")
    fun observeForJob(jobId: Long): Flow<List<JobEventEntity>>

    @Query("SELECT * FROM job_events ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<JobEventEntity>>

    @Query("SELECT * FROM job_events WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): JobEventEntity?
}

// ---------------------------------------------------------------------------

@Dao
interface TaskDao : BaseDao<TaskEntity> {

    @Query("SELECT * FROM tasks WHERE jobId = :jobId ORDER BY sortOrder, id")
    fun observeForJob(jobId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE dueDate IS NOT NULL AND dueDate < :today AND status NOT IN ('DONE','SKIPPED')")
    fun observeOverdue(today: LocalDate): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE dueDate = :day AND status NOT IN ('DONE','SKIPPED')")
    fun observeDueOn(day: LocalDate): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE scheduledDate BETWEEN :from AND :to ORDER BY scheduledDate")
    fun observeScheduledBetween(from: LocalDate, to: LocalDate): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT t.* FROM tasks t
        INNER JOIN task_assignments a ON a.taskId = t.id
        WHERE a.staffId = :staffId AND t.status NOT IN ('DONE','SKIPPED')
        ORDER BY t.dueDate IS NULL, t.dueDate, t.sortOrder
        """
    )
    fun observeOpenForStaff(staffId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE crewAdded = 1 AND reviewedByOwner = 0")
    fun observeCrewAddedNeedingReview(): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE jobId = :jobId AND status IN ('DONE','SKIPPED')")
    suspend fun completedCount(jobId: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE jobId = :jobId")
    suspend fun totalCount(jobId: Long): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM tasks WHERE jobId = :jobId")
    suspend fun nextSortOrder(jobId: Long): Int

}

@Dao
interface TaskAssignmentDao : BaseDao<TaskAssignmentEntity> {
    @Query("SELECT * FROM task_assignments WHERE taskId = :taskId")
    fun observeForTask(taskId: Long): Flow<List<TaskAssignmentEntity>>

    @Query("SELECT * FROM task_assignments WHERE staffId = :staffId")
    fun observeForStaff(staffId: Long): Flow<List<TaskAssignmentEntity>>

    @Query("DELETE FROM task_assignments WHERE taskId = :taskId AND staffId = :staffId")
    suspend fun unassign(taskId: Long, staffId: Long)

    @Query("SELECT * FROM task_assignments WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): TaskAssignmentEntity?
}

// ---------------------------------------------------------------------------

@Dao
interface StaffDao : BaseDao<StaffEntity> {
    @Query("SELECT * FROM staff WHERE archivedAt IS NULL ORDER BY isActive DESC, fullName COLLATE NOCASE")
    fun observeAll(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff WHERE isActive = 1 AND archivedAt IS NULL ORDER BY fullName COLLATE NOCASE")
    fun observeActive(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff WHERE id = :id")
    fun observeById(id: Long): Flow<StaffEntity?>

    @Query("SELECT * FROM staff WHERE id = :id")
    suspend fun getById(id: Long): StaffEntity?

    @Query("SELECT * FROM staff WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): StaffEntity?

    /** Insurance lapse is a real liability, so the dashboard warns 30 days out. */
    @Query("SELECT * FROM staff WHERE insuranceExpiry IS NOT NULL AND insuranceExpiry <= :before AND isActive = 1 AND archivedAt IS NULL")
    fun observeExpiringCompliance(before: LocalDate): Flow<List<StaffEntity>>
}

@Dao
interface JobAssignmentDao : BaseDao<JobAssignmentEntity> {
    @Query("SELECT * FROM job_assignments WHERE jobId = :jobId")
    fun observeForJob(jobId: Long): Flow<List<JobAssignmentEntity>>

    @Query("SELECT * FROM job_assignments WHERE staffId = :staffId")
    fun observeForStaff(staffId: Long): Flow<List<JobAssignmentEntity>>

    @Query("SELECT * FROM job_assignments WHERE jobId = :jobId AND staffId = :staffId LIMIT 1")
    suspend fun find(jobId: Long, staffId: Long): JobAssignmentEntity?

    @Query("DELETE FROM job_assignments WHERE jobId = :jobId AND staffId = :staffId")
    suspend fun unassign(jobId: Long, staffId: Long)

    @Query("SELECT * FROM job_assignments WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): JobAssignmentEntity?
}

// ---------------------------------------------------------------------------

@Dao
interface SupplierDao : BaseDao<SupplierEntity> {
    @Query("SELECT * FROM suppliers WHERE archivedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getById(id: Long): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): SupplierEntity?
}

@Dao
interface MaterialDao : BaseDao<MaterialEntity> {
    @Query("SELECT * FROM materials WHERE jobId = :jobId ORDER BY sortOrder, id")
    fun observeForJob(jobId: Long): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): MaterialEntity?

    /**
     * The cross-job buy list: everything still to be bought or chased, across all
     * open jobs. This is the screen that gets used at the contractor desk.
     */
    @Query(
        """
        SELECT m.* FROM materials m
        INNER JOIN jobs j ON j.id = m.jobId
        WHERE m.status IN ('NEEDED','QUOTED','ORDERED','PARTIAL','BACKORDERED')
          AND m.isClientSupplied = 0
          AND j.archivedAt IS NULL
          AND j.status NOT IN ('PAID','CANCELLED','LOST')
        ORDER BY m.supplierId, m.category, m.name COLLATE NOCASE
        """
    )
    fun observeBuyList(): Flow<List<MaterialEntity>>

    @Query("SELECT COUNT(*) FROM materials WHERE status = 'NEEDED' AND isClientSupplied = 0")
    fun observeNeededCount(): Flow<Int>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM materials WHERE jobId = :jobId")
    suspend fun nextSortOrder(jobId: Long): Int
}

// ---------------------------------------------------------------------------

@Dao
interface PhotoDao : BaseDao<PhotoEntity> {
    @Query("SELECT * FROM photos WHERE jobId = :jobId ORDER BY capturedAt DESC")
    fun observeForJob(jobId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE taskId = :taskId ORDER BY capturedAt DESC")
    fun observeForTask(taskId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getById(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): PhotoEntity?

    /** Content-addressed, so an already-transferred photo is never stored twice. */
    @Query("SELECT * FROM photos WHERE contentHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): PhotoEntity?

    @Query("SELECT COUNT(*) FROM photos")
    fun observeCount(): Flow<Int>
}

@Dao
interface DocumentDao : BaseDao<DocumentEntity> {
    @Query("SELECT * FROM documents WHERE jobId = :jobId ORDER BY created_at DESC")
    fun observeForJob(jobId: Long): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): DocumentEntity?
}

// ---------------------------------------------------------------------------

@Dao
interface TimeEntryDao : BaseDao<TimeEntryEntity> {
    @Query("SELECT * FROM time_entries WHERE jobId = :jobId ORDER BY workDate DESC")
    fun observeForJob(jobId: Long): Flow<List<TimeEntryEntity>>

    @Query("SELECT * FROM time_entries WHERE staffId = :staffId AND workDate BETWEEN :from AND :to ORDER BY workDate DESC")
    fun observeForStaffBetween(staffId: Long, from: LocalDate, to: LocalDate): Flow<List<TimeEntryEntity>>

    @Query("SELECT * FROM time_entries WHERE workDate BETWEEN :from AND :to ORDER BY workDate")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<TimeEntryEntity>>

    @Query("SELECT COALESCE(SUM(hours), 0) FROM time_entries WHERE jobId = :jobId")
    fun observeTotalHours(jobId: Long): Flow<Double>

    @Query("SELECT * FROM time_entries WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): TimeEntryEntity?
}

@Dao
interface ExpenseDao : BaseDao<ExpenseEntity> {
    @Query("SELECT * FROM expenses WHERE jobId = :jobId ORDER BY expenseDate DESC")
    fun observeForJob(jobId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT COALESCE(SUM(amountCents + taxCents), 0) FROM expenses WHERE jobId = :jobId")
    fun observeTotalCents(jobId: Long): Flow<Long>

    @Query("SELECT * FROM expenses WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): ExpenseEntity?
}

@Dao
interface ChangeOrderDao : BaseDao<ChangeOrderEntity> {
    @Query("SELECT * FROM change_orders WHERE jobId = :jobId ORDER BY number")
    fun observeForJob(jobId: Long): Flow<List<ChangeOrderEntity>>

    @Query("SELECT COALESCE(MAX(number), 0) + 1 FROM change_orders WHERE jobId = :jobId")
    suspend fun nextNumber(jobId: Long): Int

    @Query("SELECT COALESCE(SUM(amountDeltaCents), 0) FROM change_orders WHERE jobId = :jobId AND status = 'APPROVED'")
    fun observeApprovedDeltaCents(jobId: Long): Flow<Long>

    @Query("SELECT * FROM change_orders WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): ChangeOrderEntity?
}

// ---------------------------------------------------------------------------

@Dao
interface AppointmentDao : BaseDao<AppointmentEntity> {
    @Query("SELECT * FROM appointments WHERE startAt BETWEEN :from AND :to ORDER BY startAt")
    fun observeBetween(from: Instant, to: Instant): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE jobId = :jobId ORDER BY startAt")
    fun observeForJob(jobId: Long): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE startAt >= :from ORDER BY startAt LIMIT :limit")
    fun observeUpcoming(from: Instant, limit: Int): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): AppointmentEntity?
}

@Dao
interface AppointmentAttendeeDao : BaseDao<AppointmentAttendeeEntity> {
    @Query("SELECT * FROM appointment_attendees WHERE appointmentId = :appointmentId")
    fun observeForAppointment(appointmentId: Long): Flow<List<AppointmentAttendeeEntity>>

    @Query("DELETE FROM appointment_attendees WHERE appointmentId = :appointmentId AND staffId = :staffId")
    suspend fun remove(appointmentId: Long, staffId: Long)
}

// ---------------------------------------------------------------------------

@Dao
interface SyncDao {

    @Upsert
    suspend fun upsertTombstone(tombstone: TombstoneEntity)

    @Query("SELECT * FROM tombstones WHERE deletedAt > :since")
    suspend fun tombstonesSince(since: Instant): List<TombstoneEntity>

    @Query("SELECT * FROM tombstones WHERE entityType = :type AND entitySyncId = :syncId LIMIT 1")
    suspend fun findTombstone(type: String, syncId: String): TombstoneEntity?

    @Upsert
    suspend fun upsertPeer(peer: SyncPeerEntity)

    @Query("SELECT * FROM sync_peers WHERE isActive = 1 ORDER BY deviceName COLLATE NOCASE")
    fun observePeers(): Flow<List<SyncPeerEntity>>

    @Query("SELECT * FROM sync_peers WHERE deviceId = :deviceId LIMIT 1")
    suspend fun findPeer(deviceId: String): SyncPeerEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueOutbox(entry: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox WHERE isSent = 0 ORDER BY changedAt")
    fun observePendingOutbox(): Flow<List<SyncOutboxEntity>>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE isSent = 0")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM sync_outbox WHERE isSent = 0 ORDER BY changedAt")
    suspend fun pendingOutbox(): List<SyncOutboxEntity>

    @Query("UPDATE sync_outbox SET isSent = 1, sentInPacketId = :packetId, sentAt = :at WHERE id IN (:ids)")
    suspend fun markOutboxSent(ids: List<Long>, packetId: String, at: Instant)

    /** Called when a peer acknowledges receipt — until then entries are re-sent. */
    @Query("DELETE FROM sync_outbox WHERE isSent = 1 AND sentAt <= :ackWatermark")
    suspend fun clearAcknowledgedOutbox(ackWatermark: Instant)

    @Insert
    suspend fun insertLog(entry: SyncLogEntity): Long

    @Query("SELECT * FROM sync_log ORDER BY occurredAt DESC LIMIT :limit")
    fun observeLog(limit: Int): Flow<List<SyncLogEntity>>
}

@Dao
interface PendingLeadDao : BaseDao<PendingLeadEntity> {
    @Query("SELECT * FROM pending_leads WHERE status = :statusName ORDER BY capturedAt DESC")
    fun observeByStatusName(statusName: String): Flow<List<PendingLeadEntity>>

    @Query("SELECT COUNT(*) FROM pending_leads WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM pending_leads WHERE sync_id = :syncId")
    suspend fun getBySyncId(syncId: String): PendingLeadEntity?
}
