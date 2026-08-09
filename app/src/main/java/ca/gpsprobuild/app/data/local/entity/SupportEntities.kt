package ca.gpsprobuild.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ca.gpsprobuild.app.data.local.SyncMeta
import ca.gpsprobuild.app.domain.model.AppointmentType
import ca.gpsprobuild.app.domain.model.ChangeOrderStatus
import ca.gpsprobuild.app.domain.model.DocumentType
import ca.gpsprobuild.app.domain.model.ExpenseCategory
import ca.gpsprobuild.app.domain.model.MaterialCategory
import ca.gpsprobuild.app.domain.model.MaterialStatus
import ca.gpsprobuild.app.domain.model.MaterialUnit
import ca.gpsprobuild.app.domain.model.PaymentMethod
import ca.gpsprobuild.app.domain.model.PhotoCategory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// ---------------------------------------------------------------------------
// Materials and suppliers
// ---------------------------------------------------------------------------

@Entity(
    tableName = "suppliers",
    indices = [Index(value = ["sync_id"], unique = true), Index("name")]
)
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val accountNumber: String? = null,
    val street1: String? = null,
    val city: String? = null,
    val province: String? = "ON",
    val postalCode: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    val archivedAt: Instant? = null
)

@Entity(
    tableName = "materials",
    foreignKeys = [
        ForeignKey(JobEntity::class, ["id"], ["jobId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TaskEntity::class, ["id"], ["linkedTaskId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(SupplierEntity::class, ["id"], ["supplierId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [
        Index(value = ["sync_id"], unique = true),
        Index("jobId"),
        Index("status"),
        Index("supplierId"),
        Index("linkedTaskId")
    ]
)
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val jobId: Long,
    /** "This is for the tile step" — lets the buy list be ordered by the schedule. */
    val linkedTaskId: Long? = null,

    val name: String,
    val category: MaterialCategory = MaterialCategory.OTHER,
    val sku: String? = null,
    /** "Benjamin Moore Aura, Chantilly Lace, eggshell" */
    val specNotes: String? = null,

    val quantity: Double = 1.0,
    val unit: MaterialUnit = MaterialUnit.EA,
    val unitCostCents: Long? = null,
    val isTaxable: Boolean = true,
    val supplierId: Long? = null,

    val status: MaterialStatus = MaterialStatus.NEEDED,
    val quantityReceived: Double = 0.0,
    val orderedDate: LocalDate? = null,
    val expectedDate: LocalDate? = null,
    val receivedDate: LocalDate? = null,

    /** Excluded from cost totals and flagged in the list. */
    val isClientSupplied: Boolean = false,
    val sortOrder: Int = 0,
    val notes: String? = null
) {
    val lineTotalCents: Long
        get() = unitCostCents?.let { Math.round(quantity * it) } ?: 0L
}

// ---------------------------------------------------------------------------
// Media
// ---------------------------------------------------------------------------

/**
 * File names are stored relative to the app's photos directory, never as absolute
 * paths and never as `content://` URIs — neither survives a backup restore onto a
 * different device.
 */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(JobEntity::class, ["id"], ["jobId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CustomerEntity::class, ["id"], ["customerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(MaterialEntity::class, ["id"], ["materialId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [
        Index(value = ["sync_id"], unique = true),
        Index("jobId"),
        Index("customerId"),
        Index("taskId"),
        Index("materialId"),
        Index("category"),
        Index("contentHash")
    ]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val jobId: Long? = null,
    val customerId: Long? = null,
    val taskId: Long? = null,
    val materialId: Long? = null,
    val expenseId: Long? = null,

    val fileName: String,
    val thumbFileName: String,
    /** SHA-256 of the file bytes. Makes packet transfer deduplicate for free. */
    val contentHash: String,

    val category: PhotoCategory = PhotoCategory.PROGRESS,
    val caption: String? = null,
    val capturedAt: Instant = Instant.now(),

    /** Only populated when the user has enabled geotagging, which is off by default. */
    val latitude: Double? = null,
    val longitude: Double? = null,

    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val fileSizeBytes: Long = 0,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "documents",
    foreignKeys = [ForeignKey(JobEntity::class, ["id"], ["jobId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sync_id"], unique = true), Index("jobId"), Index("type")]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val jobId: Long,
    val type: DocumentType = DocumentType.OTHER,
    val title: String,
    val fileName: String,
    val contentHash: String,
    val mimeType: String? = null,
    val fileSizeBytes: Long = 0
)

// ---------------------------------------------------------------------------
// Time, expenses, change orders
// ---------------------------------------------------------------------------

@Entity(
    tableName = "time_entries",
    foreignKeys = [
        ForeignKey(JobEntity::class, ["id"], ["jobId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(StaffEntity::class, ["id"], ["staffId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["sync_id"], unique = true),
        Index("jobId"), Index("staffId"), Index("workDate"), Index("taskId")
    ]
)
data class TimeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val jobId: Long,
    val taskId: Long? = null,
    val staffId: Long,

    val workDate: LocalDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val breakMinutes: Int = 0,
    /** Authoritative value. Derived from start/end when those are used. */
    val hours: Double,

    val isBillable: Boolean = true,
    /** Rate captured at entry time so historical costing survives a raise. */
    val rateSnapshotCents: Long? = null,
    val notes: String? = null
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(JobEntity::class, ["id"], ["jobId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(StaffEntity::class, ["id"], ["paidByStaffId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [
        Index(value = ["sync_id"], unique = true),
        Index("jobId"), Index("expenseDate"), Index("paidByStaffId")
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val jobId: Long,
    val expenseDate: LocalDate,
    val description: String,
    val category: ExpenseCategory = ExpenseCategory.MATERIALS,
    val vendor: String? = null,
    val amountCents: Long = 0,
    val taxCents: Long = 0,
    val paidByStaffId: Long? = null,
    val paymentMethod: PaymentMethod? = null,
    val needsReimbursement: Boolean = false,
    val isReimbursed: Boolean = false,
    val receiptPhotoId: Long? = null,
    val notes: String? = null
) {
    val totalCents: Long get() = amountCents + taxCents
}

@Entity(
    tableName = "change_orders",
    foreignKeys = [ForeignKey(JobEntity::class, ["id"], ["jobId"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index(value = ["sync_id"], unique = true),
        Index(value = ["jobId", "number"], unique = true)
    ]
)
data class ChangeOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val jobId: Long,
    /** Sequential within the job: CO-1, CO-2. */
    val number: Int,
    val title: String,
    val description: String? = null,
    val amountDeltaCents: Long = 0,
    val scheduleDeltaDays: Int = 0,
    val status: ChangeOrderStatus = ChangeOrderStatus.DRAFT,
    val presentedDate: LocalDate? = null,
    val decisionDate: LocalDate? = null,
    val approvedByName: String? = null,
    val signaturePhotoId: Long? = null,
    val notes: String? = null
)

// ---------------------------------------------------------------------------
// Schedule
// ---------------------------------------------------------------------------

@Entity(
    tableName = "appointments",
    foreignKeys = [
        ForeignKey(JobEntity::class, ["id"], ["jobId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CustomerEntity::class, ["id"], ["customerId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["sync_id"], unique = true),
        Index("jobId"), Index("customerId"), Index("startAt")
    ]
)
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val jobId: Long? = null,
    val customerId: Long? = null,
    val title: String,
    val type: AppointmentType = AppointmentType.WORK_DAY,
    val startAt: Instant,
    val endAt: Instant? = null,
    val isAllDay: Boolean = false,
    val location: String? = null,
    val reminderMinutesBefore: Int? = null,
    val notes: String? = null
)

@Entity(
    tableName = "appointment_attendees",
    foreignKeys = [
        ForeignKey(AppointmentEntity::class, ["id"], ["appointmentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(StaffEntity::class, ["id"], ["staffId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["sync_id"], unique = true),
        Index(value = ["appointmentId", "staffId"], unique = true),
        Index("staffId")
    ]
)
data class AppointmentAttendeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val appointmentId: Long,
    val staffId: Long
)
