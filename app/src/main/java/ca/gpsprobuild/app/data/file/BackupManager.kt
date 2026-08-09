package ca.gpsprobuild.app.data.file

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import ca.gpsprobuild.app.data.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class BackupManifest(
    val schemaVersion: Int,
    val appVersion: String,
    val createdAt: String,
    val deviceName: String,
    val customerCount: Int,
    val jobCount: Int,
    val photoCount: Int
)

sealed interface RestoreResult {
    data class Ready(val manifest: BackupManifest) : RestoreResult
    data class Failed(val reason: String) : RestoreResult
}

/**
 * The whole dataset as one `.zip` the owner controls and stores wherever they
 * choose — Drive, a card, a cable. This is the supported recovery path, which is
 * why cloud auto-backup is deliberately switched off for the database.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase
) {
    companion object {
        const val SCHEMA_VERSION = 1
        private const val MANIFEST = "manifest.json"
        private const val DB_DIR = "database/"
        private const val PHOTOS_DIR = "photos/"
        private const val DOCS_DIR = "docs/"
    }

    fun suggestedFileName(): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        return "GPSProBuild-Backup-$stamp.zip"
    }

    suspend fun writeBackup(target: Uri, appVersion: String, deviceName: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Fold the write-ahead log back into the main database file first,
                // or the copy captures a snapshot missing the most recent writes —
                // exactly the ones the person was worried about losing.
                database.openHelper.writableDatabase
                    .query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"))
                    .use { it.moveToFirst() }

                val counts = countRows()
                var entries = 0

                context.contentResolver.openOutputStream(target)?.use { raw ->
                    ZipOutputStream(raw.buffered()).use { zip ->
                        val manifest = JSONObject().apply {
                            put("schemaVersion", SCHEMA_VERSION)
                            put("appVersion", appVersion)
                            put("createdAt", Instant.now().toString())
                            put("deviceName", deviceName)
                            put("customerCount", counts.first)
                            put("jobCount", counts.second)
                            put("photoCount", counts.third)
                        }
                        zip.putNextEntry(ZipEntry(MANIFEST))
                        zip.write(manifest.toString(2).toByteArray())
                        zip.closeEntry()
                        entries++

                        databaseFiles().forEach { file ->
                            entries += addFile(zip, file, DB_DIR + file.name)
                        }
                        File(context.filesDir, "photos").let { dir ->
                            entries += addTree(zip, dir, PHOTOS_DIR)
                        }
                        File(context.filesDir, "docs").let { dir ->
                            entries += addTree(zip, dir, DOCS_DIR)
                        }
                    }
                } ?: error("Could not open the destination file")

                entries
            }
        }

    /** Reads only the manifest, so the person can confirm before anything is replaced. */
    suspend fun inspect(source: Uri): RestoreResult = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(source)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == MANIFEST) {
                            val json = JSONObject(zip.readBytes().decodeToString())
                            val version = json.getInt("schemaVersion")
                            return@runCatching if (version != SCHEMA_VERSION) {
                                RestoreResult.Failed(
                                    "This backup is version $version and this app reads " +
                                        "version $SCHEMA_VERSION. Update the app that made it, " +
                                        "or the app on this phone."
                                )
                            } else {
                                RestoreResult.Ready(
                                    BackupManifest(
                                        schemaVersion = version,
                                        appVersion = json.optString("appVersion"),
                                        createdAt = json.optString("createdAt"),
                                        deviceName = json.optString("deviceName"),
                                        customerCount = json.optInt("customerCount"),
                                        jobCount = json.optInt("jobCount"),
                                        photoCount = json.optInt("photoCount")
                                    )
                                )
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            RestoreResult.Failed("No manifest found — this is not a GPS ProBuild backup.")
        }.getOrElse { RestoreResult.Failed("Could not read the file: ${it.message}") }
    }

    /**
     * Replaces everything. The caller must restart the process afterwards: Room
     * has the old database open, and continuing to use that handle against
     * swapped-out files is how you corrupt both copies.
     */
    suspend fun restore(source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val staging = File(context.cacheDir, "restore").apply {
                deleteRecursively()
                mkdirs()
            }

            context.contentResolver.openInputStream(source)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val safeName = entry.name.replace("..", "")
                            val out = File(staging, safeName)
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Could not open the backup file")

            database.close()

            databaseFiles().forEach { it.delete() }
            File(staging, "database").listFiles()?.forEach { file ->
                file.copyTo(File(context.getDatabasePath(AppDatabase.NAME).parentFile, file.name), overwrite = true)
            }

            replaceTree(File(staging, "photos"), File(context.filesDir, "photos"))
            replaceTree(File(staging, "docs"), File(context.filesDir, "docs"))

            staging.deleteRecursively()
            Unit
        }
    }

    private fun databaseFiles(): List<File> {
        val main = context.getDatabasePath(AppDatabase.NAME)
        return listOf(
            main,
            File(main.parentFile, "${AppDatabase.NAME}-wal"),
            File(main.parentFile, "${AppDatabase.NAME}-shm")
        ).filter { it.exists() }
    }

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String): Int {
        if (!file.exists() || file.isDirectory) return 0
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
        return 1
    }

    private fun addTree(zip: ZipOutputStream, dir: File, prefix: String): Int {
        if (!dir.exists()) return 0
        var count = 0
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(dir).path.replace(File.separatorChar, '/')
            count += addFile(zip, file, prefix + relative)
        }
        return count
    }

    private fun replaceTree(from: File, to: File) {
        if (!from.exists()) return
        to.deleteRecursively()
        to.mkdirs()
        from.copyRecursively(to, overwrite = true)
    }

    private fun countRows(): Triple<Int, Int, Int> {
        fun count(table: String): Int =
            database.openHelper.readableDatabase
                .query(SimpleSQLiteQuery("SELECT COUNT(*) FROM $table"))
                .use { if (it.moveToFirst()) it.getInt(0) else 0 }
        return Triple(count("customers"), count("jobs"), count("photos"))
    }
}
