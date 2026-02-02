# API Reference

Public API documentation for BikefitApp modules with usage examples.

## Table of Contents
- [Biomechanics Module](#biomechanics-module)
- [Pose Module](#pose-module)
- [Camera Module](#camera-module)
- [Fit Module](#fit-module)
- [Calibration Module](#calibration-module)

---

## Biomechanics Module

### Angle Calculators

#### KneeAngleCalculator

Calculate knee flexion/extension angle from pose landmarks.

```kotlin
val result = KneeAngleCalculator.calculateKneeAngle(
    poseResult = poseResult,
    side = BodySide.LEFT
)

if (result.isValid) {
    println("Knee angle: ${result.angle}°")
    println("Confidence: ${result.confidence}")
} else {
    println("Invalid: ${result.reason}")
}
```

**Parameters:**
- `poseResult: PoseResult` - Pose detection result with landmarks
- `side: BodySide` - LEFT or RIGHT leg

**Returns:** `AngleResult` with `angle`, `isValid`, `confidence`, `reason`

#### HipAngleCalculator

```kotlin
val result = HipAngleCalculator.calculateHipAngle(
    poseResult = poseResult,
    side = BodySide.RIGHT
)
```

**Returns:** Hip angle in degrees (range of motion during stroke)

#### AnkleAngleCalculator

```kotlin
val result = AnkleAngleCalculator.calculateAnkleAngle(
    poseResult = poseResult,
    side = BodySide.LEFT
)
```

**Returns:** Ankle plantarflexion angle in degrees (0° = neutral, positive = plantarflexion, typical: 20-30° at BDC)

#### TorsoAngleCalculator

```kotlin
val result = TorsoAngleCalculator.calculateTorsoAngle(
    poseResult = poseResult
)
```

**Returns:** Torso angle relative to horizontal (0° = horizontal, 90° = vertical)

### Cycle Detection

#### PedalCycleDetector

Detect BDC/TDC events in pedal stroke.

```kotlin
val detector = PedalCycleDetector()

// For each frame
val event = detector.detectCycleEvent(
    frame = poseFrame,
    side = BodySide.LEFT
)

when (event) {
    CycleEvent.BDC -> println("Bottom Dead Center")
    CycleEvent.TDC -> println("Top Dead Center")
    null -> println("No event")
}
```

**Configuration:**
```kotlin
val detector = PedalCycleDetector(
    windowSize = 15,              // Frame window for peak detection
    minPeakProminence = 0.02f,   // Minimum peak height
    minCycleDuration = 20         // Minimum frames between events
)
```

### Statistical Aggregation

#### CycleAggregator

Accumulate frame data and compute cycle statistics.

```kotlin
val aggregator = CycleAggregator()

// For each frame
aggregator.addFrame(poseFrame, cycleEvent)

// On cycle completion
if (cycleEvent == CycleEvent.BDC) {
    val metrics = aggregator.getCurrentCycleMetrics()
    println("Knee angle: ${metrics.kneeAngle}")
    println("  Min: ${metrics.kneeAngle.min}°")
    println("  Max: ${metrics.kneeAngle.max}°")
    println("  Avg: ${metrics.kneeAngle.average}°")
}
```

**Output:** `CycleMetrics` with `AngleStats` for each metric

### Vector Math

#### Vector2D

2D vector operations for geometric calculations.

```kotlin
val a = Vector2D(1.0f, 0.0f)
val b = Vector2D(0.0f, 1.0f)

// Angle between vectors
val angle = a.angleTo(b)  // 90.0°

// Normalize
val normalized = a.normalized()

// Distance
val distance = a.distanceTo(b)
```

**Common Operations:**
- `magnitude()`, `normalized()`
- `dot(other)`, `cross(other)`
- `angleTo(other)`, `signedAngleTo(other, up)`
- `distanceTo(other)`, `distanceSquaredTo(other)`
- `lerp(other, t)`, `project(onto)`

---

## Pose Module

### PoseLandmarkerWrapper

MediaPipe Pose integration for pose detection.

```kotlin
val wrapper = PoseLandmarkerWrapper(
    context = context,
    runningMode = RunningMode.LIVE_STREAM,
    minPoseDetectionConfidence = 0.5f,
    minPosePresenceConfidence = 0.5f,
    minTrackingConfidence = 0.5f
) { result ->
    // Callback with pose result
    handlePoseResult(result)
}

// For live stream
wrapper.detectAsync(bitmap, timestampMs)

// For single image
val result = wrapper.detect(bitmap)

// Cleanup
wrapper.close()
```

**Running Modes:**
- `RunningMode.IMAGE` - Single image detection
- `RunningMode.VIDEO` - Video file processing
- `RunningMode.LIVE_STREAM` - Real-time camera (requires callback)

**Configuration:**
```kotlin
val wrapper = PoseLandmarkerWrapper(
    minPoseDetectionConfidence = 0.5f,  // Detection threshold
    minPosePresenceConfidence = 0.5f,   // Presence threshold
    minTrackingConfidence = 0.5f        // Tracking threshold
)
```

### LandmarkSmoother

Apply EMA smoothing to reduce landmark jitter.

```kotlin
val smoother = LandmarkSmoother(alpha = 0.4f)

// For each frame
val smoothedLandmarks = smoother.smooth(rawLandmarks)

// Reset state
smoother.reset()
```

**Alpha Values:**
- `0.3` - Heavy smoothing, more lag
- `0.4` - Balanced (default)
- `0.5` - Light smoothing, more responsive

### PoseValidator

Validate landmark visibility and confidence.

```kotlin
val isValid = PoseValidator.validateLandmark(
    landmark = landmark,
    minVisibility = 0.5f
)

val poseValid = PoseValidator.validatePose(
    poseResult = poseResult,
    requiredLandmarks = listOf(
        PoseLandmarkIndex.LEFT_KNEE,
        PoseLandmarkIndex.LEFT_ANKLE
    )
)
```

---

## Camera Module

### CameraManager

CameraX wrapper for video capture and frame analysis.

```kotlin
val cameraManager = CameraManager(
    context = context,
    lifecycleOwner = this,
    previewView = previewView,
    targetFps = 24
)

// Start camera
cameraManager.startCamera { imageProxy ->
    // Process frame
    processFrame(imageProxy)
    imageProxy.close()
}

// Control zoom
cameraManager.setZoomLevel(2.0f)  // 2x zoom

// Stop camera
cameraManager.stopCamera()
```

**Configuration:**
```kotlin
val cameraManager = CameraManager(
    targetFps = 24,           // Target frame rate
    enableZoom = true         // Enable zoom controls
)
```

### FrameSampler

Control frame analysis rate to prevent overload.

```kotlin
val sampler = FrameSampler(targetFps = 24)

// In frame callback
if (sampler.shouldProcess()) {
    // Process this frame
    analyzeFrame(frame)
}
```

**Auto-adjusts** based on processing time to maintain target FPS.

---

## Fit Module

### FitEngine

Orchestrate fit rules and generate recommendations.

```kotlin
val engine = FitEngine(
    enableSaddleHeightRule = true,
    enableSaddleForeAftRule = true,
    enableReachRule = true,
    minCyclesRequired = 3
)

val summary = engine.analyze(
    cycleMetrics = metrics,
    calibration = bikeCalibration,
    discipline = CyclingDiscipline.ROAD
)

println("Grade: ${summary.grade}")
summary.issues.forEach { issue ->
    println("${issue.severity}: ${issue.title}")
    println("  ${issue.recommendation}")
}
```

**Configuration:**
```kotlin
val engine = FitEngine(
    enableSaddleHeightRule = true,    // Enable/disable rules
    enableSaddleForeAftRule = true,
    enableReachRule = true,
    minCyclesRequired = 3              // Minimum cycles for analysis
)
```

### Fit Rules

#### SaddleHeightRule

```kotlin
val rule = SaddleHeightRule()
val issues = rule.evaluate(
    cycleMetrics = metrics,
    discipline = CyclingDiscipline.ENDURANCE
)
```

**Thresholds:**
- Optimal: 145-155° knee angle at BDC
- Too low: <140°
- Too high: >160°

#### SaddleForeAftRule

```kotlin
val rule = SaddleForeAftRule()
val issues = rule.evaluate(
    cycleMetrics = metrics,
    calibration = bikeCalibration
)
```

**Requires:** Bike calibration  
**Tolerance:** ±3% KOPS optimal

#### ReachRule

```kotlin
val rule = ReachRule()
val issues = rule.evaluate(
    cycleMetrics = metrics,
    discipline = CyclingDiscipline.GRAVEL
)
```

**Optimal:** 30-60° torso angle

### Cycling Disciplines

```kotlin
enum class CyclingDiscipline {
    ROAD,
    ENDURANCE,
    GRAVEL,
    TIME_TRIAL,
    TRIATHLON
}
```

**Discipline-specific ranges** defined in `RangeLookup`.

### FitSummary

Access analysis results:

```kotlin
summary.grade               // EXCELLENT/GOOD/FAIR/POOR
summary.issues              // List<FitIssue>
summary.metricRanges        // Map<FitMetricType, MetricRange>
summary.cycleCount          // Number of cycles analyzed

// Issues by category
summary.issuesByCategory[FitIssueCategory.SADDLE]
summary.issuesByCategory[FitIssueCategory.COCKPIT]

// Issues by severity
summary.highSeverityIssues
summary.mediumSeverityIssues
summary.lowSeverityIssues
```

---

## Calibration Module

### BikeCalibration

3-point bike calibration data.

```kotlin
val calibration = BikeCalibration(
    saddlePoint = PointF(x, y),
    bottomBracketPoint = PointF(x, y),
    handlebarPoint = PointF(x, y)
)

// Calculate distances
val saddleHeight = calibration.saddleToBottomBracketDistance()
val reach = calibration.handlebarToSaddleDistance()
```

### CalibrationRepository

In-memory storage for active calibration.

```kotlin
// Save calibration
CalibrationRepository.setCalibration(calibration)

// Retrieve calibration
val calibration = CalibrationRepository.getCalibration()

// Check if calibrated
val isCalibrated = CalibrationRepository.hasCalibration()

// Clear calibration
CalibrationRepository.clear()
```

**Lifecycle:** Single session, not persisted

### CoordinateTransformer

Transform between coordinate systems.

```kotlin
val transformer = CoordinateTransformer()

// MediaPipe normalized → pixel coordinates
val pixelCoords = transformer.normalizedToPixel(
    normalized = landmark,
    imageWidth = width,
    imageHeight = height
)

// Normalize by bike geometry
val normalized = transformer.normalizeByFemurLength(
    point = kneePosition,
    calibration = calibration
)
```

---

## Data Models

### Common Types

```kotlin
enum class BodySide { LEFT, RIGHT }

data class AngleResult(
    val angle: Float,
    val isValid: Boolean,
    val confidence: Float,
    val reason: String?
)

data class AngleStats(
    val min: Float,
    val max: Float,
    val average: Float,
    val stddev: Float,
    val range: Float
)

data class CycleMetrics(
    val kneeAngle: AngleStats,
    val hipAngle: AngleStats,
    val ankleAngle: AngleStats,
    val torsoAngle: AngleStats,
    val cycleNumber: Int,
    val frameCount: Int
)
```

### FitIssue

```kotlin
data class FitIssue(
    val type: FitIssueType,
    val severity: Severity,
    val title: String,
    val description: String,
    val recommendation: String,
    val affectedMetrics: List<String>,
    val category: FitIssueCategory
)

enum class Severity { LOW, MEDIUM, HIGH }

enum class FitIssueCategory {
    SADDLE, COCKPIT, PEDALING, STABILITY
}
```

---

## Extension Points

### Adding Custom Angle Calculator

```kotlin
object CustomAngleCalculator {
    fun calculate(poseResult: PoseResult): AngleResult {
        val landmark1 = poseResult.landmarks[INDEX_1]
        val landmark2 = poseResult.landmarks[INDEX_2]
        val landmark3 = poseResult.landmarks[INDEX_3]
        
        val v1 = Vector2D(landmark1.x, landmark1.y)
        val v2 = Vector2D(landmark2.x, landmark2.y)
        val v3 = Vector2D(landmark3.x, landmark3.y)
        
        val angle = calculateAngle(v1, v2, v3)
        
        return AngleResult(
            angle = angle,
            isValid = true,
            confidence = minOf(landmark1.visibility, landmark2.visibility, landmark3.visibility),
            reason = null
        )
    }
}
```

### Adding Custom Fit Rule

```kotlin
class CustomFitRule : FitRule {
    override fun evaluate(
        cycleMetrics: CycleMetrics,
        calibration: BikeCalibration?,
        discipline: CyclingDiscipline
    ): List<FitIssue> {
        val issues = mutableListOf<FitIssue>()
        
        // Evaluate metric
        val metric = cycleMetrics.customMetric.average
        
        if (metric < THRESHOLD_MIN) {
            issues.add(FitIssue(
                type = FitIssueType.CUSTOM,
                severity = Severity.MEDIUM,
                title = "Custom Issue Detected",
                description = "Metric below threshold",
                recommendation = "Adjust component",
                affectedMetrics = listOf("custom_metric"),
                category = FitIssueCategory.PEDALING
            ))
        }
        
        return issues
    }
}
```

### Integrating Custom Rule

```kotlin
// Add to FitEngine
val engine = FitEngine(
    customRules = listOf(CustomFitRule())
)
```

---

## Error Handling

### Common Exceptions

```kotlin
try {
    val result = poseWrapper.detect(bitmap)
} catch (e: MediaPipeException) {
    Log.e(TAG, "Pose detection failed", e)
}

try {
    cameraManager.startCamera { frame ->
        // Process
    }
} catch (e: CameraException) {
    Log.e(TAG, "Camera initialization failed", e)
}
```

### Validation Checks

```kotlin
// Check calibration before KOPS
if (!CalibrationRepository.hasCalibration()) {
    Log.w(TAG, "KOPS analysis requires calibration")
    return null
}

// Check minimum cycles
if (cycleMetrics.size < MIN_CYCLES) {
    Log.w(TAG, "Insufficient cycles for analysis")
    return null
}

// Check landmark visibility
if (landmark.visibility < 0.5f) {
    Log.w(TAG, "Landmark not visible enough")
    return null
}
```

---

## Testing APIs

### Test Helpers

```kotlin
// Create test pose result
fun createTestPoseResult(
    kneeAngle: Float = 150f,
    hipAngle: Float = 85f
): PoseResult {
    // Build test landmarks
    val landmarks = createTestLandmarks(kneeAngle, hipAngle)
    return PoseResult(landmarks, confidence = 0.9f)
}

// Create test cycle metrics
fun createTestCycleMetrics(
    avgKneeAngle: Float = 150f
): CycleMetrics {
    return CycleMetrics(
        kneeAngle = AngleStats(
            min = avgKneeAngle - 5,
            max = avgKneeAngle + 5,
            average = avgKneeAngle,
            stddev = 2.0f
        ),
        // ... other metrics
    )
}
```

### Mocking

```kotlin
// Mock CameraManager
val mockCamera = mock<CameraManager>()
whenever(mockCamera.startCamera(any())).thenAnswer { invocation ->
    val callback = invocation.getArgument<(ImageProxy) -> Unit>(0)
    callback(mockImageProxy)
}

// Mock PoseLandmarker
val mockLandmarker = mock<PoseLandmarkerWrapper>()
whenever(mockLandmarker.detect(any())).thenReturn(testPoseResult)
```

---

## Performance Considerations

### Thread Safety

```kotlin
// LandmarkSmoother is thread-safe
val smoother = LandmarkSmoother()
synchronized(smoother) {
    val smoothed = smoother.smooth(landmarks)
}

// CalibrationRepository uses synchronized methods
CalibrationRepository.setCalibration(calibration)  // Thread-safe
```

### Memory Management

```kotlin
// Close MediaPipe resources
poseWrapper.close()

// Stop camera to release resources
cameraManager.stopCamera()

// Clear calibration when done
CalibrationRepository.clear()
```

### Optimization Tips

```kotlin
// Reuse Vector2D instances in hot paths
private val tempVector = Vector2D.ZERO

// Use distanceSquaredTo() instead of distanceTo()
val distSq = a.distanceSquaredTo(b)  // Avoids sqrt()

// Batch process frames when possible
val results = frames.map { detector.detect(it) }
```

---

## Configuration Constants

### Adjustable Parameters

```kotlin
// Pose detection thresholds
const val MIN_DETECTION_CONFIDENCE = 0.5f
const val MIN_PRESENCE_CONFIDENCE = 0.5f
const val MIN_TRACKING_CONFIDENCE = 0.5f

// Smoothing
const val LANDMARK_SMOOTHING_ALPHA = 0.4f

// Cycle detection
const val CYCLE_WINDOW_SIZE = 15
const val MIN_PEAK_PROMINENCE = 0.02f
const val MIN_CYCLE_DURATION = 20

// Analysis requirements
const val MIN_CYCLES_FOR_ANALYSIS = 3
const val TARGET_ANALYSIS_FPS = 24

// Fit thresholds
const val KNEE_ANGLE_OPTIMAL_MIN = 145f
const val KNEE_ANGLE_OPTIMAL_MAX = 155f
const val KOPS_TOLERANCE = 0.03f  // ±3%
const val TORSO_ANGLE_OPTIMAL_MIN = 30f
const val TORSO_ANGLE_OPTIMAL_MAX = 60f
```

See [ALGORITHMS.md](ALGORITHMS.md) for detailed algorithm configuration guidance.

---

## Additional Resources

- [README.md](README.md) - Project overview
- [architecture.md](architecture.md) - System architecture
- [ALGORITHMS.md](ALGORITHMS.md) - Algorithm details
- [USERGUIDE.md](USERGUIDE.md) - User instructions
- [CONTRIBUTING.md](CONTRIBUTING.md) - Development guidelines
