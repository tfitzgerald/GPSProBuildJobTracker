package ca.gpsprobuild.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ca.gpsprobuild.app.data.local.dao.AppointmentAttendeeDao
import ca.gpsprobuild.app.data.local.dao.AppointmentDao
import ca.gpsprobuild.app.data.local.dao.ChangeOrderDao
import ca.gpsprobuild.app.data.local.dao.ContactDao
import ca.gpsprobuild.app.data.local.dao.CustomerDao
import ca.gpsprobuild.app.data.local.dao.DocumentDao
import ca.gpsprobuild.app.data.local.dao.ExpenseDao
import ca.gpsprobuild.app.data.local.dao.JobAssignmentDao
import ca.gpsprobuild.app.data.local.dao.JobDao
import ca.gpsprobuild.app.data.local.dao.JobEventDao
import ca.gpsprobuild.app.data.local.dao.MaterialDao
import ca.gpsprobuild.app.data.local.dao.PendingLeadDao
import ca.gpsprobuild.app.data.local.dao.PhotoDao
import ca.gpsprobuild.app.data.local.dao.StaffDao
import ca.gpsprobuild.app.data.local.dao.SupplierDao
import ca.gpsprobuild.app.data.local.dao.SyncDao
import ca.gpsprobuild.app.data.local.dao.TaskAssignmentDao
import ca.gpsprobuild.app.data.local.dao.TaskDao
import ca.gpsprobuild.app.data.local.dao.TimeEntryDao
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

/**
 * Schemas are exported to app/schemas and committed, so migrations can be tested
 * against real historical versions rather than guessed at.
 *
 * Never ship `fallbackToDestructiveMigration()` here. A field device that silently
 * wipes itself on upgrade loses a week of hours nobody wrote down anywhere else.
 */
@Database(
    entities = [
        CustomerEntity::class,
        ContactEntity::class,
        JobEntity::class,
        JobEventEntity::class,
        TaskEntity::class,
        TaskAssignmentEntity::class,
        StaffEntity::class,
        JobAssignmentEntity::class,
        SupplierEntity::class,
        MaterialEntity::class,
        PhotoEntity::class,
        DocumentEntity::class,
        TimeEntryEntity::class,
        ExpenseEntity::class,
        ChangeOrderEntity::class,
        AppointmentEntity::class,
        AppointmentAttendeeEntity::class,
        TombstoneEntity::class,
        SyncPeerEntity::class,
        SyncOutboxEntity::class,
        SyncLogEntity::class,
        PendingLeadEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun customerDao(): CustomerDao
    abstract fun contactDao(): ContactDao
    abstract fun jobDao(): JobDao
    abstract fun jobEventDao(): JobEventDao
    abstract fun taskDao(): TaskDao
    abstract fun taskAssignmentDao(): TaskAssignmentDao
    abstract fun staffDao(): StaffDao
    abstract fun jobAssignmentDao(): JobAssignmentDao
    abstract fun supplierDao(): SupplierDao
    abstract fun materialDao(): MaterialDao
    abstract fun photoDao(): PhotoDao
    abstract fun documentDao(): DocumentDao
    abstract fun timeEntryDao(): TimeEntryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun changeOrderDao(): ChangeOrderDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun appointmentAttendeeDao(): AppointmentAttendeeDao
    abstract fun syncDao(): SyncDao
    abstract fun pendingLeadDao(): PendingLeadDao

    companion object {
        const val NAME = "gpsprobuild.db"
    }
}
