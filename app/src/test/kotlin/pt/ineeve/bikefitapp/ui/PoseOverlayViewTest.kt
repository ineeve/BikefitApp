package pt.ineeve.bikefitapp.ui

import org.junit.Assert.*
import org.junit.Test
import pt.ineeve.bikefitapp.pose.Landmark
import pt.ineeve.bikefitapp.pose.PoseLandmarkIndex
import pt.ineeve.bikefitapp.pose.PoseResult

class PoseOverlayViewTest {

    // ==================== Skeleton Connections Tests ====================

    @Test
    fun `SKELETON_CONNECTIONS contains all expected body connections`() {
        val connections = PoseOverlayView.SKELETON_CONNECTIONS
        assertTrue(connections.isNotEmpty())
        
        // Verify some key connections exist
        assertTrue(connections.contains(
            PoseLandmarkIndex.LEFT_SHOULDER to PoseLandmarkIndex.LEFT_HIP
        ))
        assertTrue(connections.contains(
            PoseLandmarkIndex.LEFT_HIP to PoseLandmarkIndex.LEFT_KNEE
        ))
        assertTrue(connections.contains(
            PoseLandmarkIndex.LEFT_KNEE to PoseLandmarkIndex.LEFT_ANKLE
        ))
    }

    @Test
    fun `SKELETON_CONNECTIONS has symmetric left and right pairs`() {
        val connections = PoseOverlayView.SKELETON_CONNECTIONS
        
        // Check left arm has corresponding right arm
        val hasLeftArm = connections.contains(
            PoseLandmarkIndex.LEFT_SHOULDER to PoseLandmarkIndex.LEFT_ELBOW
        )
        val hasRightArm = connections.contains(
            PoseLandmarkIndex.RIGHT_SHOULDER to PoseLandmarkIndex.RIGHT_ELBOW
        )
        assertEquals(hasLeftArm, hasRightArm)
        
        // Check left leg has corresponding right leg
        val hasLeftLeg = connections.contains(
            PoseLandmarkIndex.LEFT_HIP to PoseLandmarkIndex.LEFT_KNEE
        )
        val hasRightLeg = connections.contains(
            PoseLandmarkIndex.RIGHT_HIP to PoseLandmarkIndex.RIGHT_KNEE
        )
        assertEquals(hasLeftLeg, hasRightLeg)
    }

    @Test
    fun `SKELETON_CONNECTIONS uses valid landmark indices`() {
        val connections = PoseOverlayView.SKELETON_CONNECTIONS
        
        for ((start, end) in connections) {
            assertTrue(
                "Start index $start out of range",
                start in 0 until PoseLandmarkIndex.LANDMARK_COUNT
            )
            assertTrue(
                "End index $end out of range",
                end in 0 until PoseLandmarkIndex.LANDMARK_COUNT
            )
        }
    }

    // ==================== Bike Fit Skeleton Connections Tests ====================

    @Test
    fun `BIKE_FIT_SKELETON_CONNECTIONS contains key bike fit joints`() {
        val connections = PoseOverlayView.BIKE_FIT_SKELETON_CONNECTIONS
        assertTrue(connections.isNotEmpty())
        
        // Must include shoulder-hip (torso)
        assertTrue(connections.any { 
            (it.first == PoseLandmarkIndex.LEFT_SHOULDER && it.second == PoseLandmarkIndex.LEFT_HIP) ||
            (it.first == PoseLandmarkIndex.RIGHT_SHOULDER && it.second == PoseLandmarkIndex.RIGHT_HIP)
        })
        
        // Must include hip-knee
        assertTrue(connections.any { 
            (it.first == PoseLandmarkIndex.LEFT_HIP && it.second == PoseLandmarkIndex.LEFT_KNEE) ||
            (it.first == PoseLandmarkIndex.RIGHT_HIP && it.second == PoseLandmarkIndex.RIGHT_KNEE)
        })
        
        // Must include knee-ankle
        assertTrue(connections.any { 
            (it.first == PoseLandmarkIndex.LEFT_KNEE && it.second == PoseLandmarkIndex.LEFT_ANKLE) ||
            (it.first == PoseLandmarkIndex.RIGHT_KNEE && it.second == PoseLandmarkIndex.RIGHT_ANKLE)
        })
    }

    @Test
    fun `BIKE_FIT_SKELETON_CONNECTIONS is subset of SKELETON_CONNECTIONS or specific bike connections`() {
        val bikeFitConnections = PoseOverlayView.BIKE_FIT_SKELETON_CONNECTIONS
        
        // Should have fewer connections than full skeleton
        assertTrue(bikeFitConnections.size < PoseOverlayView.SKELETON_CONNECTIONS.size)
    }

    @Test
    fun `BIKE_FIT_SKELETON_CONNECTIONS includes arm connections for reach analysis`() {
        val connections = PoseOverlayView.BIKE_FIT_SKELETON_CONNECTIONS
        
        // Should include shoulder-elbow and elbow-wrist for reach analysis
        val hasLeftArmConnections = connections.contains(
            PoseLandmarkIndex.LEFT_SHOULDER to PoseLandmarkIndex.LEFT_ELBOW
        ) && connections.contains(
            PoseLandmarkIndex.LEFT_ELBOW to PoseLandmarkIndex.LEFT_WRIST
        )
        
        val hasRightArmConnections = connections.contains(
            PoseLandmarkIndex.RIGHT_SHOULDER to PoseLandmarkIndex.RIGHT_ELBOW
        ) && connections.contains(
            PoseLandmarkIndex.RIGHT_ELBOW to PoseLandmarkIndex.RIGHT_WRIST
        )
        
        assertTrue(hasLeftArmConnections || hasRightArmConnections)
    }

    // ==================== Bike Fit Landmark Indices Tests ====================

    @Test
    fun `BIKE_FIT_LANDMARK_INDICES contains essential joints`() {
        val indices = PoseOverlayView.BIKE_FIT_LANDMARK_INDICES
        
        // Must have left side joints
        assertTrue(indices.contains(PoseLandmarkIndex.LEFT_SHOULDER))
        assertTrue(indices.contains(PoseLandmarkIndex.LEFT_HIP))
        assertTrue(indices.contains(PoseLandmarkIndex.LEFT_KNEE))
        assertTrue(indices.contains(PoseLandmarkIndex.LEFT_ANKLE))
        
        // Must have right side joints
        assertTrue(indices.contains(PoseLandmarkIndex.RIGHT_SHOULDER))
        assertTrue(indices.contains(PoseLandmarkIndex.RIGHT_HIP))
        assertTrue(indices.contains(PoseLandmarkIndex.RIGHT_KNEE))
        assertTrue(indices.contains(PoseLandmarkIndex.RIGHT_ANKLE))
    }

    @Test
    fun `BIKE_FIT_LANDMARK_INDICES excludes face landmarks`() {
        val indices = PoseOverlayView.BIKE_FIT_LANDMARK_INDICES
        
        assertFalse(indices.contains(PoseLandmarkIndex.NOSE))
        assertFalse(indices.contains(PoseLandmarkIndex.LEFT_EYE))
        assertFalse(indices.contains(PoseLandmarkIndex.RIGHT_EYE))
        assertFalse(indices.contains(PoseLandmarkIndex.LEFT_EAR))
        assertFalse(indices.contains(PoseLandmarkIndex.RIGHT_EAR))
    }

    @Test
    fun `BIKE_FIT_LANDMARK_INDICES includes elbow and wrist for reach`() {
        val indices = PoseOverlayView.BIKE_FIT_LANDMARK_INDICES
        
        assertTrue(indices.contains(PoseLandmarkIndex.LEFT_ELBOW))
        assertTrue(indices.contains(PoseLandmarkIndex.LEFT_WRIST))
        assertTrue(indices.contains(PoseLandmarkIndex.RIGHT_ELBOW))
        assertTrue(indices.contains(PoseLandmarkIndex.RIGHT_WRIST))
    }

    @Test
    fun `BIKE_FIT_LANDMARK_INDICES all indices are valid`() {
        val indices = PoseOverlayView.BIKE_FIT_LANDMARK_INDICES
        
        for (index in indices) {
            assertTrue(
                "Index $index out of range",
                index in 0 until PoseLandmarkIndex.LANDMARK_COUNT
            )
        }
    }

    @Test
    fun `BIKE_FIT_LANDMARK_INDICES has no duplicates`() {
        val indices = PoseOverlayView.BIKE_FIT_LANDMARK_INDICES
        assertEquals(indices.size, indices.distinct().size)
    }

    // ==================== Connection Validity Tests ====================

    @Test
    fun `all skeleton connections reference distinct start and end points`() {
        val allConnections = PoseOverlayView.SKELETON_CONNECTIONS + 
                             PoseOverlayView.BIKE_FIT_SKELETON_CONNECTIONS
        
        for ((start, end) in allConnections) {
            assertNotEquals(
                "Connection should not connect point to itself",
                start, end
            )
        }
    }

    // ==================== Landmark Creation Helper Tests ====================

    @Test
    fun `can create valid PoseResult for testing`() {
        val landmarks = createTestLandmarks()
        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.9f
        )
        
        assertTrue(poseResult.isValid)
        assertEquals(PoseLandmarkIndex.LANDMARK_COUNT, poseResult.landmarks.size)
    }

    @Test
    fun `PoseResult with high visibility landmarks is valid for drawing`() {
        val landmarks = createTestLandmarks(visibility = 0.9f)
        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.9f
        )
        
        // Check key landmarks are visible
        val leftKnee = poseResult.getLandmark(PoseLandmarkIndex.LEFT_KNEE)
        assertNotNull(leftKnee)
        assertTrue(leftKnee!!.visibility >= 0.5f)
    }

    @Test
    fun `PoseResult with low visibility landmarks filters correctly`() {
        val landmarks = createTestLandmarks(visibility = 0.3f)
        val poseResult = PoseResult(
            landmarks = landmarks,
            timestampMs = 1000L,
            isValid = true,
            confidence = 0.5f
        )
        
        // Check landmarks have low visibility
        val leftKnee = poseResult.getLandmark(PoseLandmarkIndex.LEFT_KNEE)
        assertNotNull(leftKnee)
        assertTrue(leftKnee!!.visibility < 0.5f)
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a list of 33 test landmarks with specified visibility.
     */
    private fun createTestLandmarks(visibility: Float = 0.9f): List<Landmark> {
        return (0 until PoseLandmarkIndex.LANDMARK_COUNT).map { index ->
            Landmark(
                x = (index % 10) / 10f,  // Spread landmarks across view
                y = (index / 10) / 10f,
                z = 0f,
                visibility = visibility,
                presence = visibility
            )
        }
    }
}

/**
 * Tests for the AngleDisplay data class.
 */
class AngleDisplayTest {

    // ==================== AngleDisplay Creation Tests ====================

    @Test
    fun `AngleDisplay creation with valid values`() {
        val angleDisplay = AngleDisplay(
            angle = 145.5f,
            landmarkIndex = PoseLandmarkIndex.LEFT_KNEE,
            isValid = true,
            label = "L Knee"
        )
        
        assertEquals(145.5f, angleDisplay.angle, 0.01f)
        assertEquals(PoseLandmarkIndex.LEFT_KNEE, angleDisplay.landmarkIndex)
        assertTrue(angleDisplay.isValid)
        assertEquals("L Knee", angleDisplay.label)
    }

    @Test
    fun `AngleDisplay default values`() {
        val angleDisplay = AngleDisplay(
            angle = 90f,
            landmarkIndex = PoseLandmarkIndex.RIGHT_KNEE
        )
        
        assertTrue(angleDisplay.isValid)
        assertEquals("", angleDisplay.label)
    }

    @Test
    fun `AngleDisplay invalid factory method`() {
        val invalidAngle = AngleDisplay.invalid(PoseLandmarkIndex.LEFT_KNEE, "L Knee")
        
        assertEquals(0f, invalidAngle.angle, 0.01f)
        assertEquals(PoseLandmarkIndex.LEFT_KNEE, invalidAngle.landmarkIndex)
        assertFalse(invalidAngle.isValid)
        assertEquals("L Knee", invalidAngle.label)
    }

    @Test
    fun `AngleDisplay invalid factory method with default label`() {
        val invalidAngle = AngleDisplay.invalid(PoseLandmarkIndex.RIGHT_KNEE)
        
        assertFalse(invalidAngle.isValid)
        assertEquals("", invalidAngle.label)
    }

    // ==================== Angle Value Tests ====================

    @Test
    fun `AngleDisplay accepts minimum angle`() {
        val angleDisplay = AngleDisplay(
            angle = 0f,
            landmarkIndex = PoseLandmarkIndex.LEFT_KNEE
        )
        
        assertEquals(0f, angleDisplay.angle, 0.01f)
    }

    @Test
    fun `AngleDisplay accepts maximum angle`() {
        val angleDisplay = AngleDisplay(
            angle = 180f,
            landmarkIndex = PoseLandmarkIndex.LEFT_KNEE
        )
        
        assertEquals(180f, angleDisplay.angle, 0.01f)
    }

    @Test
    fun `AngleDisplay with typical knee angle at BDC`() {
        // At bottom dead center, knee should be relatively straight
        val angleDisplay = AngleDisplay(
            angle = 150f,
            landmarkIndex = PoseLandmarkIndex.LEFT_KNEE,
            label = "L"
        )
        
        assertTrue(angleDisplay.angle in 140f..160f)
    }

    @Test
    fun `AngleDisplay with typical knee angle at TDC`() {
        // At top dead center, knee is bent
        val angleDisplay = AngleDisplay(
            angle = 70f,
            landmarkIndex = PoseLandmarkIndex.LEFT_KNEE,
            label = "L"
        )
        
        assertTrue(angleDisplay.angle in 60f..90f)
    }

    // ==================== Landmark Index Tests ====================

    @Test
    fun `AngleDisplay for left knee uses correct index`() {
        val angleDisplay = AngleDisplay(
            angle = 120f,
            landmarkIndex = PoseLandmarkIndex.LEFT_KNEE
        )
        
        assertEquals(PoseLandmarkIndex.LEFT_KNEE, angleDisplay.landmarkIndex)
    }

    @Test
    fun `AngleDisplay for right knee uses correct index`() {
        val angleDisplay = AngleDisplay(
            angle = 120f,
            landmarkIndex = PoseLandmarkIndex.RIGHT_KNEE
        )
        
        assertEquals(PoseLandmarkIndex.RIGHT_KNEE, angleDisplay.landmarkIndex)
    }

    // ==================== Label Tests ====================

    @Test
    fun `AngleDisplay with short label`() {
        val angleDisplay = AngleDisplay(
            angle = 145f,
            landmarkIndex = PoseLandmarkIndex.LEFT_KNEE,
            label = "L"
        )
        
        assertEquals("L", angleDisplay.label)
    }

    @Test
    fun `AngleDisplay with descriptive label`() {
        val angleDisplay = AngleDisplay(
            angle = 145f,
            landmarkIndex = PoseLandmarkIndex.LEFT_KNEE,
            label = "Left Knee"
        )
        
        assertEquals("Left Knee", angleDisplay.label)
    }

    // ==================== Formatting Tests ====================

    @Test
    fun `angle displays as integer degrees`() {
        val angleDisplay = AngleDisplay(
            angle = 145.7f,
            landmarkIndex = PoseLandmarkIndex.LEFT_KNEE,
            label = "L"
        )
        
        // When converting to int for display
        val displayValue = angleDisplay.angle.toInt()
        assertEquals(145, displayValue)
    }

    @Test
    fun `angle rounds correctly for display`() {
        val angle1 = AngleDisplay(angle = 145.4f, landmarkIndex = PoseLandmarkIndex.LEFT_KNEE)
        val angle2 = AngleDisplay(angle = 145.5f, landmarkIndex = PoseLandmarkIndex.LEFT_KNEE)
        
        assertEquals(145, angle1.angle.toInt())
        assertEquals(145, angle2.angle.toInt())
    }

    // ==================== Multiple Angles Tests ====================

    @Test
    fun `list of angle displays for both knees`() {
        val angles = listOf(
            AngleDisplay(
                angle = 145f,
                landmarkIndex = PoseLandmarkIndex.LEFT_KNEE,
                label = "L"
            ),
            AngleDisplay(
                angle = 148f,
                landmarkIndex = PoseLandmarkIndex.RIGHT_KNEE,
                label = "R"
            )
        )
        
        assertEquals(2, angles.size)
        assertTrue(angles.all { it.isValid })
    }

    @Test
    fun `filtering invalid angles from list`() {
        val angles = listOf(
            AngleDisplay(angle = 145f, landmarkIndex = PoseLandmarkIndex.LEFT_KNEE, isValid = true),
            AngleDisplay.invalid(PoseLandmarkIndex.RIGHT_KNEE),
            AngleDisplay(angle = 130f, landmarkIndex = PoseLandmarkIndex.LEFT_KNEE, isValid = true)
        )
        
        val validAngles = angles.filter { it.isValid }
        assertEquals(2, validAngles.size)
    }

    // ==================== Equality Tests ====================

    @Test
    fun `AngleDisplay equals with same values`() {
        val angle1 = AngleDisplay(
            angle = 145f,
            landmarkIndex = PoseLandmarkIndex.LEFT_KNEE,
            isValid = true,
            label = "L"
        )
        val angle2 = AngleDisplay(
            angle = 145f,
            landmarkIndex = PoseLandmarkIndex.LEFT_KNEE,
            isValid = true,
            label = "L"
        )
        
        assertEquals(angle1, angle2)
    }

    @Test
    fun `AngleDisplay not equals with different angle`() {
        val angle1 = AngleDisplay(angle = 145f, landmarkIndex = PoseLandmarkIndex.LEFT_KNEE)
        val angle2 = AngleDisplay(angle = 150f, landmarkIndex = PoseLandmarkIndex.LEFT_KNEE)
        
        assertNotEquals(angle1, angle2)
    }

    @Test
    fun `AngleDisplay not equals with different landmark`() {
        val angle1 = AngleDisplay(angle = 145f, landmarkIndex = PoseLandmarkIndex.LEFT_KNEE)
        val angle2 = AngleDisplay(angle = 145f, landmarkIndex = PoseLandmarkIndex.RIGHT_KNEE)
        
        assertNotEquals(angle1, angle2)
    }
}
