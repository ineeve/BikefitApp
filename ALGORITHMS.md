# Algorithm Documentation

This document describes the key algorithms implemented in BikefitApp for pose processing, biomechanical analysis, and fit evaluation.

## Table of Contents
- [Landmark Smoothing (EMA)](#landmark-smoothing-ema)
- [Pedal Cycle Detection](#pedal-cycle-detection)
- [Statistical Cycle Aggregation](#statistical-cycle-aggregation)
- [Angle Calculations](#angle-calculations)
  - [Knee Angle](#knee-angle)
  - [Hip Angle](#hip-angle)
  - [Ankle Angle (Line Intersection)](#ankle-angle-line-intersection)
  - [Torso Angle](#torso-angle)
- [KOPS Calculation](#kops-calculation)
- [Hip Rocking Detection](#hip-rocking-detection)
- [Angle Arc Visualization](#angle-arc-visualization)

---

## Landmark Smoothing (EMA)

**File:** [LandmarkSmoother.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/pose/LandmarkSmoother.kt)  
**Lines:** 195

### Purpose
Reduce jitter and noise in MediaPipe landmark positions using Exponential Moving Average (EMA) smoothing.

### Algorithm

**Formula:**
```
smoothed[t] = α * current[t] + (1 - α) * smoothed[t-1]
```

**Parameters:**
- `α` (alpha): Smoothing factor, range [0, 1], default = 0.4
  - Lower α = more smoothing, more lag
  - Higher α = less smoothing, more responsive
  - Recommended: 0.3-0.5 for cycling motion

**Implementation:**
```kotlin
fun smooth(landmarks: List<Landmark>): List<Landmark> {
    if (previousSmoothed == null) {
        previousSmoothed = landmarks // Initialize on first frame
        return landmarks
    }
    
    return landmarks.mapIndexed { index, current ->
        val prev = previousSmoothed!![index]
        Landmark(
            x = alpha * current.x + (1 - alpha) * prev.x,
            y = alpha * current.y + (1 - alpha) * prev.y,
            visibility = current.visibility
        )
    }
}
```

**State Management:**
- Per-landmark state tracking (33 landmarks)
- Reset on new session
- Thread-safe with synchronized access

**Configuration Tuning:**
```kotlin
val smoother = LandmarkSmoother(alpha = 0.4f) // Balanced
val smoother = LandmarkSmoother(alpha = 0.3f) // Smoother, more lag
val smoother = LandmarkSmoother(alpha = 0.5f) // More responsive, less smooth
```

---

## Pedal Cycle Detection

**File:** [PedalCycleDetector.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/biomechanics/PedalCycleDetector.kt)  
**Lines:** 430

### Purpose
Automatically detect Bottom Dead Center (BDC) and Top Dead Center (TDC) events in the pedal stroke using ankle vertical position.

### Algorithm

**Approach:** Sliding window peak/valley detection on ankle Y-coordinate trajectory

**Key Concepts:**
- **BDC (Bottom Dead Center):** Ankle at lowest point (maximum Y, bottom of screen)
- **TDC (Top Dead Center):** Ankle at highest point (minimum Y, top of screen)
- **Sliding Window:** Analyzes recent frame history to identify local extrema

**Parameters:**
```kotlin
private const val WINDOW_SIZE = 15 // frames (~0.6 seconds at 24 FPS)
private const val MIN_PEAK_PROMINENCE = 0.02f // 2% of image height
private const val MIN_CYCLE_DURATION = 20 // frames (~0.8 seconds)
```

**Detection Logic:**

```kotlin
fun detectCycleEvents(frame: PoseFrame, side: BodySide): CycleEvent? {
    val ankle = frame.getAnkleLandmark(side)
    frameBuffer.add(ankle.y)
    
    if (frameBuffer.size < WINDOW_SIZE) return null
    
    // Check if current frame is local maximum (BDC)
    val isBDC = isLocalMaximum(
        values = frameBuffer,
        index = frameBuffer.size / 2,
        prominence = MIN_PEAK_PROMINENCE
    )
    
    // Check if current frame is local minimum (TDC)
    val isTDC = isLocalMinimum(
        values = frameBuffer,
        index = frameBuffer.size / 2,
        prominence = MIN_PEAK_PROMINENCE
    )
    
    // Enforce minimum cycle duration
    if (isBDC && framesSinceLastBDC >= MIN_CYCLE_DURATION) {
        return CycleEvent.BDC
    }
    if (isTDC && framesSinceLastTDC >= MIN_CYCLE_DURATION) {
        return CycleEvent.TDC
    }
    
    return null
}
```

**Peak Detection:**
```kotlin
private fun isLocalMaximum(values: List<Float>, index: Int, prominence: Float): Boolean {
    val center = values[index]
    val windowStart = maxOf(0, index - WINDOW_SIZE / 2)
    val windowEnd = minOf(values.size - 1, index + WINDOW_SIZE / 2)
    
    // Check if center is maximum in window
    for (i in windowStart..windowEnd) {
        if (i != index && values[i] >= center) return false
    }
    
    // Check prominence (must be significant peak)
    val minInWindow = values.subList(windowStart, windowEnd + 1).minOrNull() ?: 0f
    return (center - minInWindow) >= prominence
}
```

**Configuration Tuning:**
- **WINDOW_SIZE:** Increase for smoother/slower cadence, decrease for faster response
- **MIN_PEAK_PROMINENCE:** Increase to filter false positives, decrease for subtle motion
- **MIN_CYCLE_DURATION:** Adjust based on expected cadence (60-100 RPM typical)

---

## Statistical Cycle Aggregation

**File:** [CycleAggregator.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/biomechanics/CycleAggregator.kt)  
**Lines:** 480

### Purpose
Aggregate frame-by-frame measurements over complete pedal cycles to compute statistical summaries (min, max, average, standard deviation).

### Algorithm

**Approach:** Accumulate frame data between BDC/TDC events, compute statistics at cycle completion.

**Data Structure:**
```kotlin
data class AngleStats(
    val min: Float,
    val max: Float,
    val average: Float,
    val stddev: Float,
    val range: Float = max - min
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

**Accumulation Process:**

```kotlin
private val cycleFrames = mutableListOf<FrameData>()

fun onFrame(frame: PoseFrame, cycleEvent: CycleEvent?) {
    // Store frame measurements
    cycleFrames.add(FrameData(
        kneeAngle = calculateKneeAngle(frame),
        hipAngle = calculateHipAngle(frame),
        // ... other measurements
    ))
    
    // On cycle completion (e.g., BDC event)
    if (cycleEvent == CycleEvent.BDC) {
        val metrics = computeCycleMetrics(cycleFrames)
        emit(metrics)
        cycleFrames.clear()
    }
}
```

**Statistical Computation:**

```kotlin
private fun computeAngleStats(angles: List<Float>): AngleStats {
    val min = angles.minOrNull() ?: 0f
    val max = angles.maxOrNull() ?: 0f
    val avg = angles.average().toFloat()
    
    // Standard deviation: sqrt(Σ(x - μ)² / N)
    val variance = angles.map { (it - avg).pow(2) }.average()
    val stddev = sqrt(variance).toFloat()
    
    return AngleStats(
        min = min,
        max = max,
        average = avg,
        stddev = stddev,
        range = max - min
    )
}
```

**Statistical Formulas:**

**Average:**
```
avg = (Σ x_i) / N
```

**Standard Deviation:**
```
σ = sqrt( Σ(x_i - μ)² / N )
```

**Range:**
```
range = max - min
```

**Output Example:**
```kotlin
CycleMetrics(
    kneeAngle = AngleStats(min=140.2, max=152.8, avg=148.1, stddev=3.4, range=12.6),
    hipAngle = AngleStats(min=68.5, max=102.3, avg=85.7, stddev=9.2, range=33.8),
    cycleNumber = 5,
    frameCount = 72
)
```

---

## Angle Calculations

**Files:** 
- [KneeAngleCalculator.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/biomechanics/KneeAngleCalculator.kt)
- [HipAngleCalculator.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/biomechanics/HipAngleCalculator.kt)
- [AnkleAngleCalculator.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/biomechanics/AnkleAngleCalculator.kt)
- [TorsoAngleCalculator.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/biomechanics/TorsoAngleCalculator.kt)
- [Vector2D.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/biomechanics/Vector2D.kt)

### Purpose
Calculate biomechanically relevant joint angles for cycling analysis using MediaPipe pose landmarks.

### Common Components

**Vector2D Utilities:**
```kotlin
// Calculate angle at vertex B between points A-B-C
fun angleAtVertex(a: Vector2D, b: Vector2D, c: Vector2D): Float {
    val ba = a - b  // Vector from B to A
    val bc = c - b  // Vector from B to C
    return ba.angleTo(bc)  // Returns angle in degrees [0, 180]
}

// Find intersection of two lines (used for ankle angle)
fun lineIntersection(p1: Vector2D, p2: Vector2D, p3: Vector2D, p4: Vector2D): Vector2D? {
    val d1 = p2 - p1  // Direction of line 1
    val d2 = p4 - p3  // Direction of line 2
    val denominator = d1.cross(d2)
    
    if (abs(denominator) < EPSILON) return null  // Lines are parallel
    
    val d3 = p3 - p1
    val t = d3.cross(d2) / denominator
    return p1 + d1 * t  // Intersection point
}
```

### Knee Angle

**Algorithm:** Standard vertex angle calculation at knee joint.

**Landmarks:**
- Point A: Hip (landmark 23/24)
- Point B: Knee (landmark 25/26) - vertex
- Point C: Ankle (landmark 27/28)

**Formula:**
```kotlin
val hipPoint = Vector2D(hip.x, hip.y)
val kneePoint = Vector2D(knee.x, knee.y)
val anklePoint = Vector2D(ankle.x, ankle.y)

val vertexAngle = Vector2D.angleAtVertex(hipPoint, kneePoint, anklePoint)
```

**Range:** 0-180°, typically 60-155° during pedal stroke  
**Optimal at BDC:** 140-150° (prevents excessive knee extension)

### Hip Angle

**Algorithm:** Anterior (front) hip flexion angle.

**Landmarks:**
- Point A: Shoulder (landmark 11/12)
- Point B: Hip (landmark 23/24) - vertex
- Point C: Knee (landmark 25/26)

**Formula:**
```kotlin
val vertexAngle = Vector2D.angleAtVertex(shoulderPoint, hipPoint, kneePoint)
val anteriorAngle = 180f - vertexAngle  // Convert to anterior angle
```

**Conversion:** The raw vertex angle is the posterior (back) angle. We return the anterior angle (180° - vertex) which is more intuitive for cycling analysis.

**Range:** Typically 30-110° during pedal stroke  
**Optimal at TDC:** ~70-90° for efficient power transfer

### Ankle Angle (Line Intersection)

**Algorithm:** Plantarflexion angle calculated at the intersection of shin and foot lines.

**Why Intersection Method?**  
The traditional vertex-at-ankle method has a flaw: when the foot is parallel to the ground, it incorrectly reports a non-zero angle due to the offset between the ankle and foot index landmarks. The intersection method calculates the true geometric angle between the shin line (knee→ankle) and the foot line (heel→foot index).

**Landmarks:**
- Line 1: Knee (25/26) → Ankle (27/28) - shin line
- Line 2: Heel (29/30) → Foot Index (31/32) - foot line
- Vertex: Intersection point of the two lines

**Algorithm Steps:**
```kotlin
// 1. Create vectors for both lines
val kneePoint = Vector2D(knee.x, knee.y)
val anklePoint = Vector2D(ankle.x, ankle.y)
val heelPoint = Vector2D(heel.x, heel.y)
val footPoint = Vector2D(footIndex.x, footIndex.y)

// 2. Find intersection of shin line and foot line
val intersection = Vector2D.lineIntersection(
    kneePoint, anklePoint,  // Shin line
    heelPoint, footPoint     // Foot line
)

// 3. Calculate angle at intersection
val vertexAngle = Vector2D.angleAtVertex(kneePoint, intersection, footPoint)

// 4. Convert to plantarflexion (0° = neutral, + = toes down, - = toes up)
val plantarflexion = vertexAngle - 90f
```

**Physical Interpretation:**
- **0°** = neutral (foot perpendicular to shin)
- **Positive** = plantarflexion (toes pointing down)
- **Negative** = dorsiflexion (toes pointing up)

**Range:** Typically -10° to +35° during pedal stroke  
**Optimal at BDC:** 20-30° plantarflexion  
**Warning:** >35° may indicate Achilles tendon stress

**Visualization Note:**  
The arc is drawn at the computed intersection point (not at the ankle landmark). This is passed via `customVertexX` and `customVertexY` fields in `AngleDisplay`.

### Torso Angle

**Algorithm:** Angle of torso relative to horizontal reference.

**Landmarks:**
- Point A: Shoulder (landmark 11/12)
- Point B: Hip (landmark 23/24)
- Reference: Horizontal line (ground)

**Formula:**
```kotlin
val shoulderPoint = Vector2D(shoulder.x, shoulder.y)
val hipPoint = Vector2D(hip.x, hip.y)

// Vector from hip to shoulder (torso direction)
val torsoVector = shoulderPoint - hipPoint

// Horizontal reference (pointing right)
val horizontal = Vector2D.RIGHT

// Calculate angle from horizontal
val angle = torsoVector.angleTo(horizontal)

// Normalize to [0, 90] range
return if (angle > 90f) 180f - angle else angle
```

**Range:** 0-90°
- **0°** = horizontal (torso parallel to ground, extreme aero)
- **90°** = vertical (torso perpendicular to ground, upright)

**Typical Values:**
- Time trial/aero: 15-30°
- Road racing: 30-45°
- Endurance: 45-60°
- Upright/city: 60-80°

---

## Angle Arc Visualization

**File:** [PoseOverlayView.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/ui/PoseOverlayView.kt)

### Purpose
Draw geometric arc visualizations on the pose overlay to show measured angles clearly.

### Algorithm

Each angle is visualized with:
1. **Filled arc** at the vertex (semi-transparent)
2. **Arc outline** for clarity
3. **Ray lines** extending from the vertex along both rays

**Color Coding:**
- 🔵 **Knee:** Blue (#2196F3)
- 🟢 **Hip:** Green (#4CAF50)
- 🟠 **Ankle:** Orange (#FF9800)
- 🟣 **Torso:** Purple (#9C27B0)

**Arc Radii:**
- Default: 50px
- Torso: 35px (smaller to avoid overlap with hip at same landmark)

### Angle-Specific Arc Drawing

**Knee & Ankle:**
```kotlin
// Calculate ray angles using atan2
val fromAngle = atan2((fromPoint.y - vertex.y), (fromPoint.x - vertex.x))
val toAngle = atan2((toPoint.y - vertex.y), (toPoint.x - vertex.x))

// Normalize to draw smaller arc
var sweep = toAngle - fromAngle
while (sweep > 180) sweep -= 360
while (sweep < -180) sweep += 360

// Draw arc
canvas.drawArc(arcRect, fromAngle, sweep, true, arcPaint)
```

**Hip:**
```kotlin
// Calculate thigh direction (hip → knee)
val thighAngle = atan2((knee.y - hip.y), (knee.x - hip.x))

// Draw from thigh toward torso (anterior angle)
startAngle = thighAngle
sweepAngle = -anteriorAngle  // Negative to sweep toward front
```

**Torso:**
```kotlin
// Calculate actual hip→shoulder direction
val shoulderAngle = atan2((shoulder.y - hip.y), (shoulder.x - hip.x))

// Draw from horizontal to shoulder direction
startAngle = 180f  // Horizontal left
sweepAngle = shoulderAngle - 180f  // Sweep to shoulder
```

**Custom Vertex (Ankle):**
```kotlin
// For ankle, vertex is at line intersection (not at landmark)
val vertex = if (angleDisplay.hasCustomVertex) {
    transformPoint(angleDisplay.customVertexX, angleDisplay.customVertexY)
} else {
    transformedLandmarks[angleDisplay.landmarkIndex]
}
```

### Coordinate Transformation

**Challenge:** Landmarks are in normalized coordinates [0, 1], but arcs must be drawn in view pixel coordinates with proper scaling, cropping, and mirroring for camera preview.

**Solution:**
```kotlin
private fun transformPoint(normalizedX: Float, normalizedY: Float): PointF {
    // Create temporary landmark with normalized coordinates
    val tempLandmark = Landmark(normalizedX, normalizedY, z=0f, vis=1f, pres=1f)
    
    // Apply same transformation as real landmarks
    // - Scale to match PreviewView FILL_CENTER behavior
    // - Apply centering offsets for cropping
    // - Mirror for front camera
    return transformCoordinates(tempLandmark)
}
```

This ensures custom vertex positions (like ankle intersection) render correctly alongside landmark-based vertices.

---

## KOPS Calculation

**File:** [KneeOverPedalOffset.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/biomechanics/KneeOverPedalOffset.kt)  
**Lines:** 407

### Purpose
Calculate Knee Over Pedal Spindle (KOPS) position normalized by femur length. KOPS is a bike fitting method that evaluates fore-aft saddle position.

### Algorithm

**Approach:** Measure horizontal offset between knee and pedal spindle at 3 o'clock pedal position, normalized by femur length.

**Coordinate Transformation:**

```kotlin
fun calculateKOPS(
    poseFrame: PoseFrame,
    calibration: BikeCalibration,
    side: BodySide
): KOPSResult {
    // 1. Get knee position (normalized MediaPipe coords)
    val knee = poseFrame.getKneeLandmark(side)
    
    // 2. Transform to physical coordinates using calibration
    val kneePhysical = transformer.toPhysicalCoordinates(
        point = knee,
        calibration = calibration
    )
    
    // 3. Get pedal spindle position (bottom bracket from calibration)
    val pedalSpindle = calibration.bottomBracket
    
    // 4. Calculate horizontal offset
    val horizontalOffset = kneePhysical.x - pedalSpindle.x
    
    // 5. Normalize by femur length (saddle to BB distance)
    val femurLength = calibration.saddleToBottomBracketDistance()
    val normalizedOffset = horizontalOffset / femurLength
    
    return KOPSResult(
        offset = normalizedOffset,
        isValid = normalizedOffset in -0.10..0.10, // ±10% tolerance
        severity = calculateSeverity(normalizedOffset)
    )
}
```

**Normalization Formula:**
```
KOPS_normalized = (knee_x - pedal_spindle_x) / femur_length

where:
  femur_length ≈ saddle_to_BB_distance
```

**Interpretation:**
- `KOPS = 0.0`: Knee directly over pedal spindle (ideal)
- `KOPS > 0`: Knee ahead of pedal spindle (saddle too far back)
- `KOPS < 0`: Knee behind pedal spindle (saddle too far forward)

**Tolerance Ranges:**
```kotlin
object KOPSThresholds {
    const val OPTIMAL_MIN = -0.03f  // -3%
    const val OPTIMAL_MAX = 0.03f   // +3%
    const val WARNING_MIN = -0.05f  // -5%
    const val WARNING_MAX = 0.05f   // +5%
}
```

**Requires:**
- Bike calibration (3 reference points)
- Pedal at 3 o'clock position (90° crank angle)
- Side-view camera perspective

---

## Hip Rocking Detection

**File:** [HipRockingDetector.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/biomechanics/HipRockingDetector.kt)  
**Lines:** 316

### Purpose
Detect excessive hip motion (vertical oscillation) which often indicates saddle height is too high.

### Algorithm

**Approach:** Two-metric analysis combining variance and peak-to-peak amplitude.

**Metric 1: Variance Analysis**

```kotlin
fun calculateHipVariance(hipPositions: List<Float>): Float {
    val mean = hipPositions.average().toFloat()
    val variance = hipPositions.map { (it - mean).pow(2) }.average()
    return variance.toFloat()
}
```

**Metric 2: Peak-to-Peak Amplitude**

```kotlin
fun calculateHipAmplitude(hipPositions: List<Float>): Float {
    val min = hipPositions.minOrNull() ?: 0f
    val max = hipPositions.maxOrNull() ?: 0f
    return max - min
}
```

**Combined Detection:**

```kotlin
fun detectHipRocking(cycleMetrics: CycleMetrics): HipRockingResult {
    val hipYPositions = cycleMetrics.frames.map { it.hip.y }
    
    val variance = calculateHipVariance(hipYPositions)
    val amplitude = calculateHipAmplitude(hipYPositions)
    
    // Normalize by image height
    val normalizedVariance = variance / (imageHeight * imageHeight)
    val normalizedAmplitude = amplitude / imageHeight
    
    val isRocking = (normalizedVariance > VARIANCE_THRESHOLD) || 
                    (normalizedAmplitude > AMPLITUDE_THRESHOLD)
    
    return HipRockingResult(
        isRocking = isRocking,
        variance = normalizedVariance,
        amplitude = normalizedAmplitude,
        severity = when {
            normalizedAmplitude > 0.08f -> Severity.HIGH
            normalizedAmplitude > 0.05f -> Severity.MEDIUM
            else -> Severity.LOW
        }
    )
}
```

**Thresholds:**
```kotlin
object HipRockingThresholds {
    const val VARIANCE_THRESHOLD = 0.0002f   // Normalized variance
    const val AMPLITUDE_THRESHOLD = 0.05f    // 5% of image height
    
    // Severity thresholds for amplitude
    const val AMPLITUDE_LOW = 0.03f     // 3%
    const val AMPLITUDE_MEDIUM = 0.05f  // 5%
    const val AMPLITUDE_HIGH = 0.08f    // 8%
}
```

**Physical Interpretation:**
- **Normal Hip Motion:** Small, smooth oscillation (<3% image height)
- **Mild Rocking:** Noticeable motion (3-5%)
- **Significant Rocking:** Obvious up/down motion (5-8%)
- **Severe Rocking:** Excessive motion (>8%) - likely saddle too high

**Correlation with Saddle Height:**
Hip rocking typically indicates saddle is too high, causing rider to reach for pedals at bottom of stroke. Often correlates with:
- Knee angle at BDC > 155°
- Reduced power output
- Increased injury risk (lower back, hamstrings)

---

## Configuration Summary

### Global Parameters

**Frame Processing:**
```kotlin
const val TARGET_FPS = 24              // Pose estimation frame rate
const val MIN_CYCLES_FOR_ANALYSIS = 3  // Minimum cycles before analysis
const val VISIBILITY_THRESHOLD = 0.5f  // Landmark visibility minimum
```

**Smoothing:**
```kotlin
const val EMA_ALPHA = 0.4f  // Default smoothing factor
```

**Cycle Detection:**
```kotlin
const val CYCLE_WINDOW_SIZE = 15        // Frame window for peak detection
const val MIN_PEAK_PROMINENCE = 0.02f   // 2% minimum peak height
const val MIN_CYCLE_DURATION = 20       // Minimum frames between events
```

**KOPS:**
```kotlin
const val KOPS_OPTIMAL_TOLERANCE = 0.03f  // ±3%
const val KOPS_WARNING_TOLERANCE = 0.05f  // ±5%
```

**Hip Rocking:**
```kotlin
const val VARIANCE_THRESHOLD = 0.0002f     // Normalized variance
const val AMPLITUDE_THRESHOLD = 0.05f      // 5% of image height
```

### Tuning Guidance

**For Slower Cadence (<70 RPM):**
- Increase `CYCLE_WINDOW_SIZE` to 20-25
- Increase `MIN_CYCLE_DURATION` to 30+

**For Faster Cadence (>100 RPM):**
- Decrease `CYCLE_WINDOW_SIZE` to 10-12
- Decrease `MIN_CYCLE_DURATION` to 15

**For Noisy/Jittery Pose Detection:**
- Decrease `EMA_ALPHA` to 0.3 (more smoothing)
- Increase `VISIBILITY_THRESHOLD` to 0.6-0.7
- Increase `MIN_PEAK_PROMINENCE` to 0.03

**For More Responsive Detection:**
- Increase `EMA_ALPHA` to 0.5-0.6
- Decrease `CYCLE_WINDOW_SIZE` to 10

---

## Performance Characteristics

### Computational Complexity

- **Landmark Smoothing:** O(N) where N = 33 landmarks
- **Cycle Detection:** O(W) where W = window size (15 frames)
- **Statistical Aggregation:** O(F) where F = frames per cycle (~60-80)
- **KOPS Calculation:** O(1) per frame
- **Hip Rocking:** O(F) per cycle

### Memory Usage

- **Frame Buffer:** ~15 frames × 33 landmarks × 12 bytes ≈ 6KB
- **Cycle Accumulator:** ~80 frames × 200 bytes ≈ 16KB per cycle
- **Total Peak:** ~50KB for analysis pipeline

### Real-time Performance

On mid-range Android device (Snapdragon 765G):
- **Pose Estimation:** 24 FPS (MediaPipe Lite model)
- **Angle Calculations:** <1ms per frame
- **Cycle Detection:** <2ms per frame
- **Full Pipeline:** 42-45ms per frame (latency)

---

## References

### Implementation Files

- [LandmarkSmoother.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/pose/LandmarkSmoother.kt)
- [PedalCycleDetector.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/biomechanics/PedalCycleDetector.kt)
- [CycleAggregator.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/biomechanics/CycleAggregator.kt)
- [KneeOverPedalOffset.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/biomechanics/KneeOverPedalOffset.kt)
- [HipRockingDetector.kt](app/src/main/kotlin/pt/ineeve/bikefitapp/biomechanics/HipRockingDetector.kt)

### Testing

All algorithms have comprehensive unit tests:
- [LandmarkSmootherTest.kt](app/src/test/kotlin/pt/ineeve/bikefitapp/pose/LandmarkSmootherTest.kt)
- [PedalCycleDetectorTest.kt](app/src/test/kotlin/pt/ineeve/bikefitapp/biomechanics/PedalCycleDetectorTest.kt)
- [CycleAggregatorTest.kt](app/src/test/kotlin/pt/ineeve/bikefitapp/biomechanics/CycleAggregatorTest.kt)
- [KneeOverPedalOffsetTest.kt](app/src/test/kotlin/pt/ineeve/bikefitapp/biomechanics/KneeOverPedalOffsetTest.kt)
- [HipRockingDetectorTest.kt](app/src/test/kotlin/pt/ineeve/bikefitapp/biomechanics/HipRockingDetectorTest.kt)
