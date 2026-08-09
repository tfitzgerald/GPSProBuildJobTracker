package ca.gpsprobuild.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ca.gpsprobuild.app.data.local.SyncMeta
import ca.gpsprobuild.app.domain.model.ContactMethod
import ca.gpsprobuild.app.domain.model.ContactRole
import ca.gpsprobuild.app.domain.model.CustomerStatus
import ca.gpsprobuild.app.domain.model.CustomerType
import ca.gpsprobuild.app.domain.model.EmploymentType
import ca.gpsprobuild.app.domain.model.JobEventType
import ca.gpsprobuild.app.domain.model.JobPhase
import ca.gpsprobuild.app.domain.model.JobStatus
import ca.gpsprobuild.app.domain.model.JobType
import ca.gpsprobuild.app.domain.model.PermitStatus
import ca.gpsprobuild.app.domain.model.Priority
import ca.gpsprobuild.app.domain.model.StaffRole
import ca.gpsprobuild.app.domain.model.TaskStatus
import ca.gpsprobuild.app.domain.model.Weather
import java.time.Instant
import java.time.LocalDate

// ---------------------------------------------------------------------------
// Customers
// ---------------------------------------------------------------------------

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["sync_id"], unique = true),
        Index("displayName"),
        Index("status"),
        Index("archivedAt")
    ]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val displayName: String,
    val companyName: String? = null,
    val customerType: CustomerType = CustomerType.RESIDENTIAL,
    val status: CustomerStatus = CustomerStatus.LEAD,

    val primaryPhone: String? = null,
    val secondaryPhone: String? = null,
    val email: String? = null,
    val preferredContact: ContactMethod = ContactMethod.ANY,

    val street1: String? = null,
    val street2: String? = null,
    val city: String? = "Pickering",
    val province: String? = "ON",
    val postalCode: String? = null,
    val country: String? = "Canada",
    val latitude: Double? = null,
    val longitude: Double? = null,

    val referralSource: String? = null,

    /** Lockbox code, dog, where to park. The "don't get locked out" information. */
    val gateCode: String? = null,
    val accessNotes: String? = null,

    val notes: String? = null,
    val isFavourite: Boolean = false,
    val archivedAt: Instant? = null
)

@Entity(
    tableName = "contacts",
    foreignKeys = [ForeignKey(
        entity = CustomerEntity::class,
        parentColumns = ["id"],
        childColumns = ["customerId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["sync_id"], unique = true), Index("customerId")]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val customerId: Long,
    val name: String,
    val role: ContactRole = ContactRole.OTHER,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null
)

// ---------------------------------------------------------------------------
// Jobs
// ---------------------------------------------------------------------------

@Entity(
    tableName = "jobs",
    foreignKeys = [ForeignKey(
        entity = CustomerEntity::class,
        parentColumns = ["id"],
        childColumns = ["customerId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [
        Index(value = ["sync_id"], unique = true),
        Index(value = ["jobNumber"], unique = true),
        Index("customerId"),
        Index("status"),
        Index("startDate"),
        Index("archivedAt")
    ]
)
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val customerId: Long,

    /** Generated only on the owner device — field devices never touch the counter. */
    val jobNumber: String,
    val title: String,
    val jobType: JobType = JobType.OTHER,
    val customJobType: String? = null,
    val scopeOfWork: String? = null,
    val status: JobStatus = JobStatus.LEAD,
    val priority: Priority = Priority.NORMAL,

    // Site address. Defaults to the customer's, overridable for rentals and
    // second properties.
    val useCustomerAddress: Boolean = true,
    val siteStreet1: String? = null,
    val siteStreet2: String? = null,
    val siteCity: String? = null,
    val siteProvince: String? = null,
    val sitePostalCode: String? = null,
    val siteLatitude: Double? = null,
    val siteLongitude: Double? = null,

    // Money is always cents in a Long. Never a Double, never a float.
    // Stripped entirely when exporting an assignment packet to a field device.
    val estimateAmountCents: Long? = null,
    val approvedAmountCents: Long? = null,
    val depositReceivedCents: Long? = null,
    val estimatedHours: Double? = null,

    val leadDate: LocalDate? = null,
    val quotedDate: LocalDate? = null,
    val approvedDate: LocalDate? = null,
    val startDate: LocalDate? = null,
    val targetEndDate: LocalDate? = null,
    val actualEndDate: LocalDate? = null,

    val permitRequired: Boolean = false,
    val permitNumber: String? = null,
    val permitStatus: PermitStatus? = null,

    /** Warranty clock starts at [actualEndDate]. */
    val warrantyMonths: Int? = null,

    val coverPhotoId: Long? = null,
    val colorTag: Int? = null,
    val notes: String? = null,
    val archivedAt: Instant? = null
)

/**
 * The job timeline. Status changes, completed tasks and added photos are written
 * here automatically by the repositories; site logs are entered by hand.
 *
 * This is the table that settles warranty arguments two years later, so it is
 * append-only by design and never edited after the fact.
 */
@Entity(
    tableName = "job_events",
    foreignKeys = [ForeignKey(
        entity = JobEntity::class,
        parentColumns = ["id"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["sync_id"], unique = true), Index("jobId"), Index("occurredAt")]
)
data class JobEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val jobId: Long,
    val occurredAt: Instant,
    val type: JobEventType,
    val title: String,
    val body: String? = null,
    val authorStaffId: Long? = null,
    val weather: Weather? = null,
    val temperatureC: Int? = null,
    val crewCount: Int? = null
)

// ---------------------------------------------------------------------------
// Tasks
// ---------------------------------------------------------------------------

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(
        entity = JobEntity::class,
        parentColumns = ["id"],
        childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["sync_id"], unique = true),
        Index("jobId"),
        Index("status"),
        Index("dueDate"),
        Index("parentTaskId")
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val jobId: Long,
    /** One level of subtasks only. Deeper nesting is not worth the UI cost. */
    val parentTaskId: Long? = null,

    val title: String,
    val details: String? = null,
    val phase: JobPhase = JobPhase.PREP,
    val status: TaskStatus = TaskStatus.NOT_STARTED,
    val blockedReason: String? = null,
    val priority: Priority = Priority.NORMAL,
    val sortOrder: Int = 0,

    val estimatedHours: Double? = null,
    val scheduledDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val completedAt: Instant? = null,
    val completedByStaffId: Long? = null,

    val isMilestone: Boolean = false,
    val requiresInspection: Boolean = false,

    /** Created on a field device — surfaced for owner review after import. */
    val crewAdded: Boolean = false,
    val reviewedByOwner: Boolean = true
)

@Entity(
    tableName = "task_assignments",
    foreignKeys = [
        ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(StaffEntity::class, ["id"], ["staffId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["sync_id"], unique = true),
        Index(value = ["taskId", "staffId"], unique = true),
        Index("staffId")
    ]
)
data class TaskAssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val taskId: Long,
    val staffId: Long,
    val assignedAt: Instant = Instant.now(),
    val hoursAllocated: Double? = null
)

// ---------------------------------------------------------------------------
// Staff
// ---------------------------------------------------------------------------

@Entity(
    tableName = "staff",
    indices = [Index(value = ["sync_id"], unique = true), Index("isActive"), Index("fullName")]
)
data class StaffEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val fullName: String,
    val role: StaffRole = StaffRole.LABOURER,
    val employmentType: EmploymentType = EmploymentType.EMPLOYEE,
    val companyName: String? = null,

    val phone: String? = null,
    val email: String? = null,

    /** Internal figure — never leaves the owner device. */
    val hourlyRateCents: Long? = null,

    /** Comma-separated tags, rendered as chips. "tile, framing, ESA". */
    val skills: String? = null,
    val licenceNumber: String? = null,

    /** The dashboard warns 30 days out. Working an uninsured sub is a real risk. */
    val insuranceExpiry: LocalDate? = null,

    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,

    /** Deterministic default from the sync id, user-overridable. */
    val avatarColor: Int = 0,
    val isActive: Boolean = true,
    val notes: String? = null,
    val archivedAt: Instant? = null
)

@Entity(
    tableName = "job_assignments",
    foreignKeys = [
        ForeignKey(JobEntity::class, ["id"], ["jobId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(StaffEntity::class, ["id"], ["staffId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["sync_id"], unique = true),
        Index(value = ["jobId", "staffId"], unique = true),
        Index("staffId")
    ]
)
data class JobAssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val jobId: Long,
    val staffId: Long,
    val roleOnJob: String? = null,
    /** The site lead, shown on the job card. */
    val isLead: Boolean = false,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
)
