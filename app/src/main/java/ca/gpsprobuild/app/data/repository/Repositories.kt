package ca.gpsprobuild.app.data.repository

import ca.gpsprobuild.app.data.local.SyncMeta
import ca.gpsprobuild.app.data.local.dao.ContactDao
import ca.gpsprobuild.app.data.local.dao.CustomerDao
import ca.gpsprobuild.app.data.local.dao.JobDao
import ca.gpsprobuild.app.data.local.dao.JobEventDao
import ca.gpsprobuild.app.data.local.dao.SyncDao
import ca.gpsprobuild.app.data.local.entity.ContactEntity
import ca.gpsprobuild.app.data.local.entity.CustomerEntity
import ca.gpsprobuild.app.data.local.entity.JobEntity
import ca.gpsprobuild.app.data.local.entity.JobEventEntity
import ca.gpsprobuild.app.data.local.entity.SyncOutboxEntity
import ca.gpsprobuild.app.data.prefs.SettingsRepository
import ca.gpsprobuild.app.domain.model.JobEventType
import ca.gpsprobuild.app.domain.model.JobStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositories are the only place that writes [SyncMeta]. Screens and view models
 * never construct it themselves — if they did, the first entity someone forgot
 * would silently break merging months later, at which point the cause is invisible.
 */
@Singleton
class CustomerRepository @Inject constructor(
    private val customerDao: CustomerDao,
    private val contactDao: ContactDao,
    private val syncDao: SyncDao,
    private val settings: SettingsRepository
) {
    fun observeAll(): Flow<List<CustomerEntity>> = customerDao.observeAll()
    fun search(query: String): Flow<List<CustomerEntity>> = customerDao.search(query)
    fun observeById(id: Long): Flow<CustomerEntity?> = customerDao.observeById(id)
    fun observeContacts(customerId: Long): Flow<List<ContactEntity>> =
        contactDao.observeForCustomer(customerId)

    suspend fun getById(id: Long): CustomerEntity? = customerDao.getById(id)

    /** Insert when id is 0, update otherwise. Returns the row id either way. */
    suspend fun save(customer: CustomerEntity): Long {
        val deviceId = settings.settings.first().deviceId
        return if (customer.id == 0L) {
            val withMeta = customer.copy(sync = SyncMeta.new(deviceId))
            val id = customerDao.insert(withMeta)
            enqueue("customer", withMeta.sync.syncId)
            id
        } else {
            val withMeta = customer.copy(sync = customer.sync.touched(deviceId))
            customerDao.update(withMeta)
            enqueue("customer", withMeta.sync.syncId)
            customer.id
        }
    }

    suspend fun setFavourite(customer: CustomerEntity, favourite: Boolean) {
        save(customer.copy(isFavourite = favourite))
    }

    /** Soft delete. Hard delete only ever happens from Settings → Deleted items. */
    suspend fun archive(customer: CustomerEntity) {
        save(customer.copy(archivedAt = Instant.now()))
    }

    suspend fun restore(customer: CustomerEntity) {
        save(customer.copy(archivedAt = null))
    }

    private suspend fun enqueue(type: String, syncId: String) {
        if (settings.settings.first().isField) {
            syncDao.enqueueOutbox(SyncOutboxEntity(entityType = type, entitySyncId = syncId))
        }
    }
}

@Singleton
class JobRepository @Inject constructor(
    private val jobDao: JobDao,
    private val jobEventDao: JobEventDao,
    private val customerDao: CustomerDao,
    private val syncDao: SyncDao,
    private val settings: SettingsRepository
) {
    fun observeAll(): Flow<List<JobEntity>> = jobDao.observeAll()
    fun observeOpen(): Flow<List<JobEntity>> = jobDao.observeOpen()
    fun observeForCustomer(customerId: Long): Flow<List<JobEntity>> =
        jobDao.observeForCustomer(customerId)
    fun observeById(id: Long): Flow<JobEntity?> = jobDao.observeById(id)
    fun search(query: String): Flow<List<JobEntity>> = jobDao.search(query)
    fun observeEvents(jobId: Long): Flow<List<JobEventEntity>> = jobEventDao.observeForJob(jobId)

    suspend fun getById(id: Long): JobEntity? = jobDao.getById(id)
    suspend fun customerFor(job: JobEntity): CustomerEntity? = customerDao.getById(job.customerId)

    /**
     * Creates a job, reserving the next number from settings.
     *
     * Job numbers are generated on the owner device only. A field device that
     * minted its own would collide with the office the first time both created a
     * job between syncs, and a duplicated job number on an invoice is the kind of
     * mistake a customer notices.
     */
    suspend fun create(job: JobEntity): Long {
        val current = settings.settings.first()
        val number = if (job.jobNumber.isBlank()) {
            settings.reserveJobNumber(LocalDate.now().year)
        } else {
            job.jobNumber
        }
        val withMeta = job.copy(
            jobNumber = number,
            sync = SyncMeta.new(current.deviceId)
        )
        val id = jobDao.insert(withMeta)
        enqueue("job", withMeta.sync.syncId)
        writeEvent(
            jobId = id,
            type = JobEventType.STATUS_CHANGE,
            title = "Job created",
            body = "Status set to ${job.status.label}"
        )
        return id
    }

    suspend fun save(job: JobEntity): Long {
        if (job.id == 0L) return create(job)
        val deviceId = settings.settings.first().deviceId
        val withMeta = job.copy(sync = job.sync.touched(deviceId))
        jobDao.update(withMeta)
        enqueue("job", withMeta.sync.syncId)
        return job.id
    }

    /**
     * Every status change writes a timeline entry. This is the table that settles
     * a warranty argument two years from now, so it is never optional and never
     * left to the caller to remember.
     */
    suspend fun changeStatus(job: JobEntity, newStatus: JobStatus) {
        if (job.status == newStatus) return
        val today = LocalDate.now()
        val updated = job.copy(
            status = newStatus,
            quotedDate = if (newStatus == JobStatus.QUOTED && job.quotedDate == null) today else job.quotedDate,
            approvedDate = if (newStatus == JobStatus.APPROVED && job.approvedDate == null) today else job.approvedDate,
            startDate = if (newStatus == JobStatus.IN_PROGRESS && job.startDate == null) today else job.startDate,
            actualEndDate = if (newStatus == JobStatus.COMPLETE && job.actualEndDate == null) today else job.actualEndDate
        )
        save(updated)
        writeEvent(
            jobId = job.id,
            type = JobEventType.STATUS_CHANGE,
            title = "${job.status.label} → ${newStatus.label}",
            body = null
        )
    }

    suspend fun addNote(jobId: Long, text: String) {
        writeEvent(jobId, JobEventType.NOTE, "Note", text)
    }

    suspend fun archive(job: JobEntity) {
        save(job.copy(archivedAt = Instant.now()))
    }

    private suspend fun writeEvent(
        jobId: Long,
        type: JobEventType,
        title: String,
        body: String?
    ) {
        val deviceId = settings.settings.first().deviceId
        jobEventDao.insert(
            JobEventEntity(
                jobId = jobId,
                occurredAt = Instant.now(),
                type = type,
                title = title,
                body = body,
                sync = SyncMeta.new(deviceId)
            )
        )
    }

    private suspend fun enqueue(type: String, syncId: String) {
        if (settings.settings.first().isField) {
            syncDao.enqueueOutbox(SyncOutboxEntity(entityType = type, entitySyncId = syncId))
        }
    }
}
