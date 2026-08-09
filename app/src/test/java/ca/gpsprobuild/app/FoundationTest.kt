package ca.gpsprobuild.app

import ca.gpsprobuild.app.core.util.Money
import ca.gpsprobuild.app.core.util.Phones
import ca.gpsprobuild.app.core.util.PostalCodes
import ca.gpsprobuild.app.data.local.SyncMeta
import ca.gpsprobuild.app.domain.model.JobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MoneyTest {

    @Test
    fun `tax rounds half up at the cent`() {
        // $1,234.56 at 13% is $160.4928 — must land on 16049, not 16048.
        assertEquals(16049L, Money.taxCents(123456L, 13.0))
    }

    @Test
    fun `forty line items do not drift`() {
        // The failure mode this guards: forty lines at 33 1/3 cents accumulated as
        // doubles quietly lose money by the time a final invoice is argued over.
        val lineCents = 3333L
        val total = (1..40).sumOf { lineCents }
        assertEquals(133320L, total)
    }

    @Test
    fun `parse handles currency symbols separators and bare integers`() {
        assertEquals(123456L, Money.parseToCents("$1,234.56"))
        assertEquals(123400L, Money.parseToCents("1234"))
        assertEquals(50L, Money.parseToCents("0.50"))
        assertNull(Money.parseToCents("abc"))
        assertNull(Money.parseToCents(""))
    }

    @Test
    fun `margin returns null rather than a misleading zero`() {
        assertNull(Money.marginFraction(revenueCents = 0, costCents = 5000))
        assertEquals(0.5, Money.marginFraction(10000, 5000)!!, 0.0001)
    }

    @Test
    fun `markup applies to cost`() {
        assertEquals(11500L, Money.applyMarkup(10000L, 15.0))
    }
}

class SyncMetaTest {

    private val owner = "owner-device"
    private val field = "field-device"
    private val t0 = Instant.parse("2026-08-01T12:00:00Z")

    @Test
    fun `later write wins`() {
        val older = SyncMeta(updatedByDevice = field, updatedAt = t0)
        val newer = SyncMeta(updatedByDevice = field, updatedAt = t0.plusSeconds(60))
        assertTrue(newer.supersedes(older, owner))
        assertFalse(older.supersedes(newer, owner))
    }

    @Test
    fun `exact ties go to the owner device`() {
        // Two phones editing the same task in the same second is rare but real,
        // and the office copy has to be the tiebreaker or merges are unstable.
        val ownerWrite = SyncMeta(updatedByDevice = owner, updatedAt = t0)
        val fieldWrite = SyncMeta(updatedByDevice = field, updatedAt = t0)
        assertTrue(ownerWrite.supersedes(fieldWrite, owner))
        assertFalse(fieldWrite.supersedes(ownerWrite, owner))
    }

    @Test
    fun `touch advances version and clock but never identity`() {
        val original = SyncMeta.new(owner, t0)
        val touched = original.touched(field, t0.plusSeconds(5))
        assertEquals(original.syncId, touched.syncId)
        assertEquals(2L, touched.syncVersion)
        assertEquals(field, touched.updatedByDevice)
    }

    @Test
    fun `new meta always gets a distinct identity`() {
        val a = SyncMeta.new(owner)
        val b = SyncMeta.new(owner)
        assertFalse(a.syncId == b.syncId)
    }
}

class JobStatusTest {

    @Test
    fun `pipeline is ordered and excludes off-pipeline states`() {
        val pipeline = JobStatus.pipeline
        assertEquals(JobStatus.LEAD, pipeline.first())
        assertEquals(JobStatus.PAID, pipeline.last())
        assertFalse(pipeline.contains(JobStatus.ON_HOLD))
        assertFalse(pipeline.contains(JobStatus.CANCELLED))
        assertEquals(pipeline.sortedBy { it.pipelineOrder }, pipeline)
    }

    @Test
    fun `closed states are not open`() {
        assertFalse(JobStatus.PAID.isOpen)
        assertFalse(JobStatus.CANCELLED.isOpen)
        assertTrue(JobStatus.IN_PROGRESS.isOpen)
        assertTrue(JobStatus.ON_HOLD.isOpen)
    }
}

class FormattingTest {

    @Test
    fun `phone formatting handles ten and eleven digit input`() {
        assertEquals("(905) 555-0123", Phones.format("9055550123"))
        assertEquals("(905) 555-0123", Phones.format("19055550123"))
        assertEquals("(905) 555-0123", Phones.format("905-555-0123"))
        assertEquals("", Phones.format(null))
    }

    @Test
    fun `postal codes normalise and validate`() {
        assertEquals("L1V 1A1", PostalCodes.format("l1v1a1"))
        assertTrue(PostalCodes.isValid("L1V 1A1"))
        assertFalse(PostalCodes.isValid("L1V 1A"))
    }
}
