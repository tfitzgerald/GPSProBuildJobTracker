package ca.gpsprobuild.app.di

import android.content.Context
import androidx.room.Room
import ca.gpsprobuild.app.data.local.AppDatabase
import ca.gpsprobuild.app.data.local.dao.ChangeOrderDao
import ca.gpsprobuild.app.data.local.dao.ContactDao
import ca.gpsprobuild.app.data.local.dao.DocumentDao
import ca.gpsprobuild.app.data.local.dao.ExpenseDao
import ca.gpsprobuild.app.data.local.dao.CustomerDao
import ca.gpsprobuild.app.data.local.dao.JobAssignmentDao
import ca.gpsprobuild.app.data.local.dao.JobEventDao
import ca.gpsprobuild.app.data.local.dao.SupplierDao
import ca.gpsprobuild.app.data.local.dao.TaskAssignmentDao
import ca.gpsprobuild.app.data.local.dao.JobDao
import ca.gpsprobuild.app.data.local.dao.MaterialDao
import ca.gpsprobuild.app.data.local.dao.PhotoDao
import ca.gpsprobuild.app.data.local.dao.StaffDao
import ca.gpsprobuild.app.data.local.dao.SyncDao
import ca.gpsprobuild.app.data.local.dao.TaskDao
import ca.gpsprobuild.app.data.local.dao.TimeEntryDao
import ca.gpsprobuild.app.data.prefs.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Foreign keys are enforced rather than advisory: a material row
            // pointing at a deleted job is a bug we want to fail loudly, not a
            // row that quietly disappears from a total.
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context) = SettingsRepository(context)

    @Provides fun provideCustomerDao(db: AppDatabase): CustomerDao = db.customerDao()
    @Provides fun provideJobDao(db: AppDatabase): JobDao = db.jobDao()
    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun provideStaffDao(db: AppDatabase): StaffDao = db.staffDao()
    @Provides fun provideMaterialDao(db: AppDatabase): MaterialDao = db.materialDao()
    @Provides fun providePhotoDao(db: AppDatabase): PhotoDao = db.photoDao()
    @Provides fun provideSyncDao(db: AppDatabase): SyncDao = db.syncDao()
    @Provides fun provideContactDao(db: AppDatabase): ContactDao = db.contactDao()
    @Provides fun provideJobEventDao(db: AppDatabase): JobEventDao = db.jobEventDao()
    @Provides fun provideSupplierDao(db: AppDatabase): SupplierDao = db.supplierDao()
    @Provides fun provideJobAssignmentDao(db: AppDatabase): JobAssignmentDao = db.jobAssignmentDao()
    @Provides fun provideTaskAssignmentDao(db: AppDatabase): TaskAssignmentDao = db.taskAssignmentDao()
    @Provides fun provideTimeEntryDao(db: AppDatabase): TimeEntryDao = db.timeEntryDao()
    @Provides fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
    @Provides fun provideChangeOrderDao(db: AppDatabase): ChangeOrderDao = db.changeOrderDao()
    @Provides fun provideDocumentDao(db: AppDatabase): DocumentDao = db.documentDao()
}
