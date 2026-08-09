package ca.gpsprobuild.app.data.local

import androidx.room.ColumnInfo
import java.time.Instant
import java.util.UUID

/**
 * Cross-device identity, embedded in every syncable entity.
 *
 * Room's autoincrement `id` is device-local and will collide the moment two phones
 * both create a task, so [syncId] is the real identity. Foreign keys are stored
 * locally as `Long` for query performance but serialized into sync packets as
 * `syncId` and re-resolved on import.
 *
 * This lives in one @Embedded struct on purpose: one converter, one place to reason
 * about, and impossible to forget when adding a new entity.
 */
data class SyncMeta(
    /** Stable UUID identity across all devices. Never changes once assigned. */
    @ColumnInfo(name = "sync_id")
    val syncId: String = UUID.randomUUID().toString(),

    /** Device UUID that last wrote this row. Used to break last-write-wins ties. */
    @ColumnInfo(name = "updated_by_device")
    val updatedByDevice: String = "",

    /** Monotonic per-row counter, incremented on every local mutation. */
    @ColumnInfo(name = "sync_version")
    val syncVersion: Long = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    /** Doubles as the last-write-wins clock. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now()
) {
    /** Call on every local edit before persisting. */
    fun touched(deviceId: String, at: Instant = Instant.now()): SyncMeta =
        copy(updatedByDevice = deviceId, syncVersion = syncVersion + 1, updatedAt = at)

    /**
     * Last-write-wins comparison. Ties go to the owner device, which is passed in
     * rather than assumed, so the same function serves both sides of a merge.
     */
    fun supersedes(other: SyncMeta, ownerDeviceId: String): Boolean = when {
        updatedAt.isAfter(other.updatedAt) -> true
        updatedAt.isBefore(other.updatedAt) -> false
        else -> updatedByDevice == ownerDeviceId && other.updatedByDevice != ownerDeviceId
    }

    companion object {
        fun new(deviceId: String, at: Instant = Instant.now()) = SyncMeta(
            syncId = UUID.randomUUID().toString(),
            updatedByDevice = deviceId,
            syncVersion = 1,
            createdAt = at,
            updatedAt = at
        )
    }
}
