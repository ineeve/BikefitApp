package pt.ineeve.bikefitapp.biomechanics

import org.junit.Assert.*
import org.junit.Test
import pt.ineeve.bikefitapp.pose.Landmark
import pt.ineeve.bikefitapp.pose.PoseFrame
import pt.ineeve.bikefitapp.pose.PoseLandmarkIndex
import kotlin.math.abs

/**
 * Unit tests for KneeOverPedalOffset.
 * 
 * Tests verify:
 * - Correct computation of normalized knee-over-pedal offset
 * - Proper normalization by femur length
 * - Accurate directional labeling (forward/rearward/neutral)
 * - Edge cases (invalid landmarks, zero femur length, boundary conditions)
 */
class KneeOverPedalOffsetTest {

    private val offsetTolerance = 0.01f // Tolerance for offset comparisons

    /**
     * Creates a test Landmark with specified coordinates and visibility.
     */
    private fun createLandmark(
        x: Float,
        y: Float,
        visibility: Float = 1.0f
    ): Landmark {
        return Landmark(
            x = x,
            y = y,
            z = 0f,
            visibility = visibility,
            presence = 1.0f
        )
    }

    /**
     * Creates a list of 33 landmarks with specified leg landmarks.
     */
    private fun createLandmarksWithLeg(
        hipX: Float, hipY: Float,
        kneeX: Float, kneeY: Float,
        ankleX: Float, ankleY: Float,
        side: BodySide,
        visibility: Float = 1.0f
    ): List<Landmark> {
        val landmarks = MutableList(PoseLandmarkIndex.LANDMARK_COUNT) {
            createLandmark(0f, 0f, 1.0f)
        }

        val hipIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_HIP
        } else {
            PoseLandmarkIndex.RIGHT_HIP
        }

        val kneeIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_KNEE
        } else {
            PoseLandmarkIndex.RIGHT_KNEE
        }

        val ankleIndex = if (side == BodySide.LEFT) {
            PoseLandmarkIndex.LEFT_ANKLE
        } else {
            PoseLandmarkIndex.RIGHT_ANKLE
        }

        landmarks[hipIndex] = createLandmark(hipX, hipY, visibility)
        landmarks[kneeIndex] = createLandmark(kneeX, kneeY, visibility)
        landmarks[ankleIndex] = createLandmark(ankleX, ankleY, visibility)

        return landmarks
    }

    /**
     * Creates a test pose frame with specified leg landmarks.
     */
    private fun createPoseFrame(
        frameNumber: Long,
        timestampMs: Long,
        hipX: Float, hipY: Float,
        kneeX: Float, kneeY: Float,
        ankleX: Float, ankleY: Float,
        side: BodySide,
        visibility: Float = 1.0f
    ): PoseFrame {
        val landmarks = createLandmarksWithLeg(
            hipX, hipY,
            kneeX, kneeY,
            ankleX, ankleY,
            side,
            visibility
        )

        return PoseFrame(
            frameNumber = frameNumber,
            timestampMs = timestampMs,
            landmarks = landmarks,
            confidence = 0.9f
        )
    }

    @Test
    fun `test knee forward of pedal - positive normalized offset`() {
        // Arrange: Knee is forward (larger X) of ankle/pedal
        // Hip at (0.3, 0.3), Knee at (0.6, 0.5), Ankle at (0.5, 0.7)
        // Femur length = sqrt((0.6-0.3)^2 + (0.5-0.3)^2) = sqrt(0.09 + 0.04) = sqrt(0.13) ≈ 0.36
        // Horizontal offset = 0.6 - 0.5 = 0.1
        // Normalized offset = 0.1 / 0.36 ≈ 0.278
        val frame = createPoseFrame(
            frameNumber = 1L,
            timestampMs = 100L,
            hipX = 0.3f, hipY = 0.3f,
            kneeX = 0.6f, kneeY = 0.5f,
            ankleX = 0.5f, ankleY = 0.7f,
            side = BodySide.LEFT
        )

        // Act
        val result = KneeOverPedalOffset.computeAtFrame(frame, BodySide.LEFT)

        // Assert
        assertTrue(result.isValid)
        assertTrue(result.normalizedOffset > 0f)
        assertEquals(KneeAlignment.FORWARD, result.alignment)
        assertEquals(0.1f, result.rawOffset, offsetTolerance)
        assertTrue(result.femurLength > 0f)
        assertEquals(BodySide.LEFT, result.side)
    }

    @Test
    fun `test knee behind pedal - negative normalized offset`() {
        // Arrange: Knee is behind (smaller X) ankle/pedal
        // Hip at (0.3, 0.3), Knee at (0.4, 0.5), Ankle at (0.5, 0.7)
        // Femur length ≈ sqrt((0.4-0.3)^2 + (0.5-0.3)^2) = sqrt(0.01 + 0.04) = sqrt(0.05) ≈ 0.224
        // Horizontal offset = 0.4 - 0.5 = -0.1
        // Normalized offset = -0.1 / 0.224 ≈ -0.446
        val frame = createPoseFrame(
            frameNumber = 1L,
            timestampMs = 100L,
            hipX = 0.3f, hipY = 0.3f,
            kneeX = 0.4f, kneeY = 0.5f,
            ankleX = 0.5f, ankleY = 0.7f,
            side = BodySide.LEFT
        )

        // Act
        val result = KneeOverPedalOffset.computeAtFrame(frame, BodySide.LEFT)

        // Assert
        assertTrue(result.isValid)
        assertTrue(result.normalizedOffset < 0f)
        assertEquals(KneeAlignment.REARWARD, result.alignment)
        assertEquals(-0.1f, result.rawOffset, offsetTolerance)
        assertTrue(result.femurLength > 0f)
    }

    @Test
    fun `test knee aligned with pedal - neutral alignment`() {
        // Arrange: Knee X is very close to ankle X
        // Hip at (0.3, 0.3), Knee at (0.5, 0.5), Ankle at (0.5, 0.7)
        // Horizontal offset = 0.5 - 0.5 = 0.0
        val frame = createPoseFrame(
            frameNumber = 1L,
            timestampMs = 100L,
            hipX = 0.3f, hipY = 0.3f,
            kneeX = 0.5f, kneeY = 0.5f,
            ankleX = 0.5f, ankleY = 0.7f,
            side = BodySide.LEFT
        )

        // Act
        val result = KneeOverPedalOffset.computeAtFrame(frame, BodySide.LEFT)

        // Assert
        assertTrue(result.isValid)
        assertEquals(0.0f, result.normalizedOffset, offsetTolerance)
        assertEquals(KneeAlignment.NEUTRAL, result.alignment)
        assertEquals(0.0f, result.rawOffset, offsetTolerance)
    }

    @Test
    fun `test normalized offset is scale-free`() {
        // Arrange: Create two scenarios with different body sizes but same relative position
        // Scenario 1: Small body
        val frame1 = createPoseFrame(
            frameNumber = 1L,
            timestampMs = 100L,
            hipX = 0.3f, hipY = 0.3f,
            kneeX = 0.4f, kneeY = 0.4f,  // Femur length = sqrt(0.01 + 0.01) = sqrt(0.02) ≈ 0.141
            ankleX = 0.35f, ankleY = 0.5f, // Offset = 0.4 - 0.35 = 0.05
            side = BodySide.LEFT
        )

        // Scenario 2: Larger body (2x scale)
        val frame2 = createPoseFrame(
            frameNumber = 2L,
            timestampMs = 200L,
            hipX = 0.2f, hipY = 0.2f,
            kneeX = 0.4f, kneeY = 0.4f,  // Femur length = sqrt(0.04 + 0.04) = sqrt(0.08) ≈ 0.283
            ankleX = 0.3f, ankleY = 0.6f, // Offset = 0.4 - 0.3 = 0.1
            side = BodySide.LEFT
        )

        // Act
        val result1 = KneeOverPedalOffset.computeAtFrame(frame1, BodySide.LEFT)
        val result2 = KneeOverPedalOffset.computeAtFrame(frame2, BodySide.LEFT)

        // Assert - Both should have similar normalized offsets despite different scales
        assertTrue(result1.isValid)
        assertTrue(result2.isValid)
        // Both scenarios have offset / femur = 0.05/0.141 ≈ 0.354 and 0.1/0.283 ≈ 0.354
        assertEquals(result1.normalizedOffset, result2.normalizedOffset, 0.01f)
        assertEquals(KneeAlignment.FORWARD, result1.alignment)
        assertEquals(KneeAlignment.FORWARD, result2.alignment)
    }

    @Test
    fun `test invalid result when landmarks not visible`() {
        // Arrange: Create frame with low visibility landmarks
        val frame = createPoseFrame(
            frameNumber = 1L,
            timestampMs = 100L,
            hipX = 0.3f, hipY = 0.3f,
            kneeX = 0.5f, kneeY = 0.5f,
            ankleX = 0.5f, ankleY = 0.7f,
            side = BodySide.LEFT,
            visibility = 0.3f  // Below default threshold of 0.5
        )

        // Act
        val result = KneeOverPedalOffset.computeAtFrame(frame, BodySide.LEFT)

        // Assert
        assertFalse(result.isValid)
        assertEquals(0f, result.normalizedOffset, offsetTolerance)
        assertEquals(BodySide.LEFT, result.side)
    }

    @Test
    fun `test invalid result when not enough landmarks`() {
        // Arrange: Create frame with insufficient landmarks
        val frame = PoseFrame(
            frameNumber = 1L,
            timestampMs = 100L,
            landmarks = emptyList(),
            confidence = 0.9f
        )

        // Act
        val result = KneeOverPedalOffset.computeAtFrame(frame, BodySide.LEFT)

        // Assert
        assertFalse(result.isValid)
    }

    @Test
    fun `test computation for right side`() {
        // Arrange: Create frame for right leg with knee forward
        val frame = createPoseFrame(
            frameNumber = 1L,
            timestampMs = 100L,
            hipX = 0.7f, hipY = 0.3f,
            kneeX = 0.6f, kneeY = 0.5f,
            ankleX = 0.5f, ankleY = 0.7f,
            side = BodySide.RIGHT
        )

        // Act
        val result = KneeOverPedalOffset.computeAtFrame(frame, BodySide.RIGHT)

        // Assert
        assertTrue(result.isValid)
        assertEquals(BodySide.RIGHT, result.side)
        assertTrue(result.normalizedOffset > 0f)
        assertEquals(KneeAlignment.FORWARD, result.alignment)
    }

    @Test
    fun `test computeFromLandmarks directly`() {
        // Arrange
        val landmarks = createLandmarksWithLeg(
            hipX = 0.3f, hipY = 0.3f,
            kneeX = 0.6f, kneeY = 0.5f,
            ankleX = 0.5f, ankleY = 0.7f,
            side = BodySide.LEFT
        )

        // Act
        val result = KneeOverPedalOffset.computeFromLandmarks(landmarks, BodySide.LEFT)

        // Assert
        assertTrue(result.isValid)
        assertTrue(result.normalizedOffset > 0f)
        assertEquals(KneeAlignment.FORWARD, result.alignment)
        assertEquals(0.1f, result.rawOffset, offsetTolerance)
    }

    @Test
    fun `test computeFromFrames with multiple frames`() {
        // Arrange: Create multiple frames with varying offsets
        val frames = listOf(
            createPoseFrame(1L, 100L, 0.3f, 0.3f, 0.6f, 0.5f, 0.5f, 0.7f, BodySide.LEFT),
            createPoseFrame(2L, 200L, 0.3f, 0.3f, 0.55f, 0.5f, 0.5f, 0.7f, BodySide.LEFT),
            createPoseFrame(3L, 300L, 0.3f, 0.3f, 0.65f, 0.5f, 0.5f, 0.7f, BodySide.LEFT)
        )

        // Act
        val summary = KneeOverPedalOffset.computeFromFrames(frames, BodySide.LEFT)

        // Assert
        assertTrue(summary.isValid)
        assertEquals(3, summary.measurementCount)
        assertTrue(summary.averageNormalizedOffset > 0f)
        assertEquals(KneeAlignment.FORWARD, summary.averageAlignment)
        assertTrue(summary.minNormalizedOffset <= summary.averageNormalizedOffset)
        assertTrue(summary.maxNormalizedOffset >= summary.averageNormalizedOffset)
        assertTrue(summary.standardDeviation >= 0f)
    }

    @Test
    fun `test computeFromFrames with empty list`() {
        // Act
        val summary = KneeOverPedalOffset.computeFromFrames(emptyList(), BodySide.LEFT)

        // Assert
        assertFalse(summary.isValid)
        assertEquals(0, summary.measurementCount)
        assertEquals(BodySide.LEFT, summary.side)
    }

    @Test
    fun `test computeFromFrames filters invalid frames`() {
        // Arrange: Mix of valid and invalid frames
        val frames = listOf(
            createPoseFrame(1L, 100L, 0.3f, 0.3f, 0.6f, 0.5f, 0.5f, 0.7f, BodySide.LEFT, 1.0f),
            createPoseFrame(2L, 200L, 0.3f, 0.3f, 0.55f, 0.5f, 0.5f, 0.7f, BodySide.LEFT, 0.3f), // Invalid
            createPoseFrame(3L, 300L, 0.3f, 0.3f, 0.65f, 0.5f, 0.5f, 0.7f, BodySide.LEFT, 1.0f)
        )

        // Act
        val summary = KneeOverPedalOffset.computeFromFrames(frames, BodySide.LEFT)

        // Assert
        assertTrue(summary.isValid)
        assertEquals(2, summary.measurementCount) // Only 2 valid frames
    }

    @Test
    fun `test neutral threshold configuration`() {
        // Arrange: Small offset that should be neutral with higher threshold
        val frame = createPoseFrame(
            frameNumber = 1L,
            timestampMs = 100L,
            hipX = 0.3f, hipY = 0.3f,
            kneeX = 0.5f, kneeY = 0.5f,
            ankleX = 0.49f, ankleY = 0.7f,  // Very small offset
            side = BodySide.LEFT
        )

        val config = KneeOverPedalOffsetConfig(neutralThreshold = 0.1f)

        // Act
        val result = KneeOverPedalOffset.computeAtFrame(frame, BodySide.LEFT, config)

        // Assert
        assertTrue(result.isValid)
        // The normalized offset should be small and within neutral threshold
        assertEquals(KneeAlignment.NEUTRAL, result.alignment)
    }

    @Test
    fun `test confidence calculation`() {
        // Arrange: Frame with specific visibilities
        val landmarks = createLandmarksWithLeg(
            hipX = 0.3f, hipY = 0.3f,
            kneeX = 0.6f, kneeY = 0.5f,
            ankleX = 0.5f, ankleY = 0.7f,
            side = BodySide.LEFT,
            visibility = 0.8f
        )

        val frame = PoseFrame(
            frameNumber = 1L,
            timestampMs = 100L,
            landmarks = landmarks,
            confidence = 0.9f
        )

        // Act
        val result = KneeOverPedalOffset.computeAtFrame(frame, BodySide.LEFT)

        // Assert
        assertTrue(result.isValid)
        assertEquals(0.8f, result.confidence, offsetTolerance)
    }

    @Test
    fun `test frame metadata is preserved`() {
        // Arrange
        val frameNumber = 42L
        val timestampMs = 1234L
        val frame = createPoseFrame(
            frameNumber = frameNumber,
            timestampMs = timestampMs,
            hipX = 0.3f, hipY = 0.3f,
            kneeX = 0.6f, kneeY = 0.5f,
            ankleX = 0.5f, ankleY = 0.7f,
            side = BodySide.LEFT
        )

        // Act
        val result = KneeOverPedalOffset.computeAtFrame(frame, BodySide.LEFT)

        // Assert
        assertTrue(result.isValid)
        assertEquals(frameNumber, result.frameNumber)
        assertEquals(timestampMs, result.timestampMs)
    }

    @Test
    fun `test very small femur length - edge case`() {
        // Arrange: Hip and knee very close together (near-zero femur length)
        // With such a small femur and knee behind ankle, we get large negative normalized offset
        val frame = createPoseFrame(
            frameNumber = 1L,
            timestampMs = 100L,
            hipX = 0.5f, hipY = 0.5f,
            kneeX = 0.5f, kneeY = 0.5001f,  // Almost same position
            ankleX = 0.6f, ankleY = 0.7f,   // Knee X (0.5) < Ankle X (0.6), so REARWARD
            side = BodySide.LEFT
        )

        // Act
        val result = KneeOverPedalOffset.computeAtFrame(frame, BodySide.LEFT)

        // Assert
        // Should still be valid but has extreme normalized offset due to tiny femur
        assertTrue(result.isValid)
        assertEquals(KneeAlignment.REARWARD, result.alignment)
        assertTrue(result.normalizedOffset < 0f)
        assertEquals(-0.1f, result.rawOffset, offsetTolerance)
    }

    @Test
    fun `test summary with mixed alignments`() {
        // Arrange: Frames with different alignments
        val frames = listOf(
            createPoseFrame(1L, 100L, 0.3f, 0.3f, 0.6f, 0.5f, 0.5f, 0.7f, BodySide.LEFT),  // Forward
            createPoseFrame(2L, 200L, 0.3f, 0.3f, 0.4f, 0.5f, 0.5f, 0.7f, BodySide.LEFT),  // Rearward
            createPoseFrame(3L, 300L, 0.3f, 0.3f, 0.65f, 0.5f, 0.5f, 0.7f, BodySide.LEFT)  // Forward
        )

        // Act
        val summary = KneeOverPedalOffset.computeFromFrames(frames, BodySide.LEFT)

        // Assert
        assertTrue(summary.isValid)
        assertEquals(3, summary.measurementCount)
        // Most common alignment should be FORWARD (2 out of 3)
        assertEquals(KneeAlignment.FORWARD, summary.averageAlignment)
    }

    @Test
    fun `test invalid result helper method`() {
        // Act
        val invalidResult = KneeOverPedalOffsetResult.invalid(BodySide.RIGHT)

        // Assert
        assertFalse(invalidResult.isValid)
        assertEquals(0f, invalidResult.normalizedOffset)
        assertEquals(0f, invalidResult.rawOffset)
        assertEquals(0f, invalidResult.femurLength)
        assertEquals(BodySide.RIGHT, invalidResult.side)
        assertEquals(KneeAlignment.NEUTRAL, invalidResult.alignment)
    }

    @Test
    fun `test invalid summary helper method`() {
        // Act
        val invalidSummary = KneeOverPedalOffsetSummary.invalid(BodySide.LEFT)

        // Assert
        assertFalse(invalidSummary.isValid)
        assertEquals(0, invalidSummary.measurementCount)
        assertEquals(BodySide.LEFT, invalidSummary.side)
        assertEquals(KneeAlignment.NEUTRAL, invalidSummary.averageAlignment)
    }
}
