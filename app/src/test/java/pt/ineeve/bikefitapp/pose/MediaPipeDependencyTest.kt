package pt.ineeve.bikefitapp.pose

import org.junit.Test
import org.junit.Assert.*

/**
 * Test to verify MediaPipe Tasks Vision dependency is available at compile time.
 * This test does not instantiate or use MediaPipe classes, only verifies they can be imported.
 */
class MediaPipeDependencyTest {
    
    @Test
    fun mediaPipePoseLandmarkerClass_isAvailable() {
        // Verify that MediaPipe PoseLandmarker class exists in the classpath
        val className = "com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker"
        val classExists = try {
            Class.forName(className)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
        
        assertTrue(
            "MediaPipe PoseLandmarker class should be available in classpath",
            classExists
        )
    }
    
    @Test
    fun mediaPipeTasksVision_isAvailable() {
        // Verify that MediaPipe Tasks Vision base classes exist
        val className = "com.google.mediapipe.tasks.vision.core.BaseVisionTaskApi"
        val classExists = try {
            Class.forName(className)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
        
        assertTrue(
            "MediaPipe Tasks Vision base classes should be available in classpath",
            classExists
        )
    }
}
