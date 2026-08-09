package ca.gpsprobuild.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ca.gpsprobuild.app.data.local.SyncMeta
import ca.gpsprobuild.app.domain.model.DeviceRole
import ca.gpsprobuild.app.domain.model.LeadStatus
import ca.gpsprobuild.app.domain.model.PacketType
import ca.gpsprobuild.app.domain.model.SyncDirection
import java.time.Instant

/**
 * Deletions have to be recorded explicitly: on the receiving device, "this row is
 * absent from the packet" is indistinguishable from "this row was never sent".
 */
@Entity(
    tableName = "tombstones",
    indices = [
        Index(value = ["entityType", "entitySyncId"], unique = true),
        Index("deletedAt")
    ]
)
data class TombstoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entitySyncId: String,
    val deletedAt: Instant,
    val deletedByDevice: String
)

/**
 * The owner's roster of field devices. Created the first time an assignment packet
 * is built for someone.
 */
@Entity(
    tableName = "sync_peers",
    indices = [Index(value = ["deviceId"], unique = true)]
)
data class SyncPeerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val deviceName: String,
    val role: DeviceRole = DeviceRole.FIELD,
    val linkedStaffId: Long? = null,

    val lastExportAt: Instant? = null,
    val lastImportAt: Instant? = null,
    /** Watermark echoed back to the peer so it can clear its outbox. */
    val ackWatermark: Instant? = null,
    /** App version reported in the peer's last packet manifest — flags mismatches. */
    val lastSeenAppVersion: String? = null,
    val packetCount: Int = 0,
    val isActive: Boolean = true
)

/**
 * Field-device only. Every local mutation to a crew-writable entity appends here,
 * which makes "3 changes not yet sent" a cheap query and delta export a join
 * rather than a full-table diff.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["entityType", "entitySyncId"], unique = true),
        Index("isSent"),
        Index("changedAt")
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entitySyncId: String,
    val changedAt: Instant = Instant.now(),
    val isSent: Boolean = false,
    val sentInPacketId: String? = null,
    val sentAt: Instant? = null
)

/** Audit trail. Answers "did Dave's Tuesday hours ever actually come in?" */
@Entity(
    tableName = "sync_log",
    indices = [Index("occurredAt"), Index("peerDeviceId")]
)
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: SyncDirection,
    val packetId: String,
    val packetType: PacketType,
    val peerDeviceId: String?,
    val peerDeviceName: String?,
    val occurredAt: Instant = Instant.now(),
    /** JSON map of entity type to row count. */
    val itemCounts: String? = null,
    val acceptedCount: Int = 0,
    val rejectedCount: Int = 0,
    val fileName: String? = null,
    val notes: String? = null
)

/**
 * A field device cannot create customers, but a crew member who meets a neighbour
 * on site should not have to remember the number until Friday. Leads land here and
 * become customers only when the owner accepts them.
 */
@Entity(
    tableName = "pending_leads",
    indices = [Index(value = ["sync_id"], unique = true), Index("status")]
)
data class PendingLeadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val sync: SyncMeta = SyncMeta(),

    val capturedByDeviceId: String,
    val capturedByStaffId: Long? = null,
    val capturedAt: Instant = Instant.now(),

    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val street1: String? = null,
    val city: String? = null,
    val jobTypeInterest: String? = null,
    val notes: String? = null,
    val status: LeadStatus = LeadStatus.PENDING,
    val convertedCustomerId: Long? = null
)
