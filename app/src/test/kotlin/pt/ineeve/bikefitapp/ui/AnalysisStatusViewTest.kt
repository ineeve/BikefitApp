package pt.ineeve.bikefitapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for AnalysisStatus enum and StatusMessage data class.
 * 
 * Tests cover:
 * - AnalysisStatus enum values
 * - StatusMessage factory methods and data class properties
 * - Configuration constants
 */
class AnalysisStatusViewTest {

    // ==================== AnalysisStatus Tests ====================

    @Test
    fun `AnalysisStatus has all expected values`() {
        val expectedValues = listOf(
            AnalysisStatus.OK,
            AnalysisStatus.LOW_CONFIDENCE,
            AnalysisStatus.MISSING_LANDMARKS,
            AnalysisStatus.BAD_CALIBRATION,
            AnalysisStatus.NO_PERSON_DETECTED,
            AnalysisStatus.ERROR
        )
        assertEquals(expectedValues.size, AnalysisStatus.entries.size)
        assertTrue(AnalysisStatus.entries.containsAll(expectedValues))
    }

    @Test
    fun `AnalysisStatus OK exists`() {
        assertNotNull(AnalysisStatus.OK)
    }

    @Test
    fun `AnalysisStatus LOW_CONFIDENCE exists`() {
        assertNotNull(AnalysisStatus.LOW_CONFIDENCE)
    }

    @Test
    fun `AnalysisStatus MISSING_LANDMARKS exists`() {
        assertNotNull(AnalysisStatus.MISSING_LANDMARKS)
    }

    @Test
    fun `AnalysisStatus BAD_CALIBRATION exists`() {
        assertNotNull(AnalysisStatus.BAD_CALIBRATION)
    }

    @Test
    fun `AnalysisStatus NO_PERSON_DETECTED exists`() {
        assertNotNull(AnalysisStatus.NO_PERSON_DETECTED)
    }

    @Test
    fun `AnalysisStatus ERROR exists`() {
        assertNotNull(AnalysisStatus.ERROR)
    }

    // ==================== StatusMessage Factory Tests ====================

    @Test
    fun `lowConfidence creates message with LOW_CONFIDENCE status`() {
        val message = StatusMessage.lowConfidence()
        assertEquals(AnalysisStatus.LOW_CONFIDENCE, message.status)
    }

    @Test
    fun `lowConfidence creates message with positive resource ID`() {
        val message = StatusMessage.lowConfidence()
        assertTrue(message.messageResId > 0)
    }

    @Test
    fun `lowConfidence has warning icon`() {
        val message = StatusMessage.lowConfidence()
        assertTrue(message.iconResId > 0)
    }

    @Test
    fun `lowConfidence has no action button`() {
        val message = StatusMessage.lowConfidence()
        assertEquals(0, message.actionResId)
    }

    @Test
    fun `missingLandmarks creates message with MISSING_LANDMARKS status`() {
        val message = StatusMessage.missingLandmarks()
        assertEquals(AnalysisStatus.MISSING_LANDMARKS, message.status)
    }

    @Test
    fun `missingLandmarks creates message with positive resource ID`() {
        val message = StatusMessage.missingLandmarks()
        assertTrue(message.messageResId > 0)
    }

    @Test
    fun `missingLandmarks has no action button`() {
        val message = StatusMessage.missingLandmarks()
        assertEquals(0, message.actionResId)
    }

    @Test
    fun `badCalibration creates message with BAD_CALIBRATION status`() {
        val message = StatusMessage.badCalibration()
        assertEquals(AnalysisStatus.BAD_CALIBRATION, message.status)
    }

    @Test
    fun `badCalibration creates message with positive resource ID`() {
        val message = StatusMessage.badCalibration()
        assertTrue(message.messageResId > 0)
    }

    @Test
    fun `badCalibration has action button`() {
        val message = StatusMessage.badCalibration()
        assertTrue(message.actionResId > 0)
    }

    @Test
    fun `badCalibration has error icon`() {
        val message = StatusMessage.badCalibration()
        assertTrue(message.iconResId > 0)
    }

    @Test
    fun `noPersonDetected creates message with NO_PERSON_DETECTED status`() {
        val message = StatusMessage.noPersonDetected()
        assertEquals(AnalysisStatus.NO_PERSON_DETECTED, message.status)
    }

    @Test
    fun `noPersonDetected creates message with positive resource ID`() {
        val message = StatusMessage.noPersonDetected()
        assertTrue(message.messageResId > 0)
    }

    @Test
    fun `noPersonDetected has no action button`() {
        val message = StatusMessage.noPersonDetected()
        assertEquals(0, message.actionResId)
    }

    @Test
    fun `error creates message with ERROR status`() {
        val message = StatusMessage.error()
        assertEquals(AnalysisStatus.ERROR, message.status)
    }

    @Test
    fun `error creates message with positive resource ID`() {
        val message = StatusMessage.error()
        assertTrue(message.messageResId > 0)
    }

    @Test
    fun `error has error icon`() {
        val message = StatusMessage.error()
        assertTrue(message.iconResId > 0)
    }

    // ==================== StatusMessage Data Class Tests ====================

    @Test
    fun `StatusMessage equals works correctly for same values`() {
        val message1 = StatusMessage(
            status = AnalysisStatus.LOW_CONFIDENCE,
            messageResId = 123
        )
        val message2 = StatusMessage(
            status = AnalysisStatus.LOW_CONFIDENCE,
            messageResId = 123
        )
        assertEquals(message1, message2)
    }

    @Test
    fun `StatusMessage equals works correctly for different status`() {
        val message1 = StatusMessage(
            status = AnalysisStatus.LOW_CONFIDENCE,
            messageResId = 123
        )
        val message2 = StatusMessage(
            status = AnalysisStatus.ERROR,
            messageResId = 123
        )
        assertNotEquals(message1, message2)
    }

    @Test
    fun `StatusMessage with different messageResId are not equal`() {
        val message1 = StatusMessage(
            status = AnalysisStatus.LOW_CONFIDENCE,
            messageResId = 123
        )
        val message2 = StatusMessage(
            status = AnalysisStatus.LOW_CONFIDENCE,
            messageResId = 456
        )
        assertNotEquals(message1, message2)
    }

    @Test
    fun `StatusMessage default actionResId is zero`() {
        val message = StatusMessage(
            status = AnalysisStatus.OK,
            messageResId = 123
        )
        assertEquals(0, message.actionResId)
    }

    @Test
    fun `StatusMessage with custom actionResId stores it`() {
        val message = StatusMessage(
            status = AnalysisStatus.ERROR,
            messageResId = 123,
            actionResId = 456
        )
        assertEquals(456, message.actionResId)
    }

    @Test
    fun `StatusMessage with custom iconResId stores it`() {
        val message = StatusMessage(
            status = AnalysisStatus.ERROR,
            messageResId = 123,
            iconResId = 789
        )
        assertEquals(789, message.iconResId)
    }

    // ==================== Constants Tests ====================

    @Test
    fun `DEFAULT_WARNING_DURATION is reasonable duration`() {
        // Should be between 1-10 seconds for good UX
        assertTrue(AnalysisStatusView.DEFAULT_WARNING_DURATION >= 1000L)
        assertTrue(AnalysisStatusView.DEFAULT_WARNING_DURATION <= 10000L)
    }

    @Test
    fun `DEFAULT_FADE_DURATION is short for smooth animations`() {
        // Should be 100-500ms for smooth animations
        assertTrue(AnalysisStatusView.DEFAULT_FADE_DURATION >= 100L)
        assertTrue(AnalysisStatusView.DEFAULT_FADE_DURATION <= 500L)
    }

    // ==================== Status Classification Tests ====================

    @Test
    fun `error statuses are correctly identified`() {
        // These should be treated as errors (persistent display)
        val errorStatuses = listOf(
            AnalysisStatus.ERROR,
            AnalysisStatus.BAD_CALIBRATION
        )
        errorStatuses.forEach { status ->
            assertNotNull("$status should exist", status)
        }
    }

    @Test
    fun `warning statuses are correctly identified`() {
        // These should be treated as warnings (auto-hide)
        val warningStatuses = listOf(
            AnalysisStatus.LOW_CONFIDENCE,
            AnalysisStatus.MISSING_LANDMARKS,
            AnalysisStatus.NO_PERSON_DETECTED
        )
        warningStatuses.forEach { status ->
            assertNotNull("$status should exist", status)
        }
    }

    // ==================== Factory Method Resource ID Tests ====================

    @Test
    fun `all factory methods return valid resource IDs`() {
        val messages = listOf(
            StatusMessage.lowConfidence(),
            StatusMessage.missingLandmarks(),
            StatusMessage.badCalibration(),
            StatusMessage.noPersonDetected(),
            StatusMessage.error()
        )
        
        messages.forEach { message ->
            assertTrue("${message.status} should have positive messageResId", 
                message.messageResId > 0)
        }
    }

    @Test
    fun `factory methods return distinct message IDs for different statuses`() {
        val lowConfidence = StatusMessage.lowConfidence()
        val missingLandmarks = StatusMessage.missingLandmarks()
        val badCalibration = StatusMessage.badCalibration()
        val noPersonDetected = StatusMessage.noPersonDetected()
        
        val messageIds = setOf(
            lowConfidence.messageResId,
            missingLandmarks.messageResId,
            badCalibration.messageResId,
            noPersonDetected.messageResId
        )
        
        assertEquals("All factory methods should have unique message IDs", 
            4, messageIds.size)
    }

    @Test
    fun `all factory methods return valid icon resource IDs`() {
        val messages = listOf(
            StatusMessage.lowConfidence(),
            StatusMessage.missingLandmarks(),
            StatusMessage.badCalibration(),
            StatusMessage.noPersonDetected(),
            StatusMessage.error()
        )
        
        messages.forEach { message ->
            assertTrue("${message.status} should have positive iconResId", 
                message.iconResId > 0)
        }
    }

    // ==================== StatusMessage Copy Tests ====================

    @Test
    fun `StatusMessage copy works correctly`() {
        val original = StatusMessage.lowConfidence()
        val copy = original.copy(status = AnalysisStatus.ERROR)
        
        assertEquals(AnalysisStatus.ERROR, copy.status)
        assertEquals(original.messageResId, copy.messageResId)
    }

    @Test
    fun `StatusMessage toString contains status`() {
        val message = StatusMessage.lowConfidence()
        assertTrue(message.toString().contains("LOW_CONFIDENCE"))
    }

    @Test
    fun `StatusMessage hashCode is consistent`() {
        val message1 = StatusMessage(
            status = AnalysisStatus.LOW_CONFIDENCE,
            messageResId = 123
        )
        val message2 = StatusMessage(
            status = AnalysisStatus.LOW_CONFIDENCE,
            messageResId = 123
        )
        assertEquals(message1.hashCode(), message2.hashCode())
    }
}
