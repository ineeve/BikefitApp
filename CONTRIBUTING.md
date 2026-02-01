# Contributing to BikefitApp

## Quick Start

1. Clone the repository
2. Open in Android Studio Hedgehog (2023.1.1) or newer
3. Sync Gradle dependencies (auto-prompt or File → Sync Project with Gradle Files)
4. Run on physical device (min SDK 26, emulator has limited camera capabilities)

## Development Environment

### Required Software
- **Android Studio:** Hedgehog (2023.1.1) or newer
- **JDK:** Version 17 or newer
- **Android SDK:** API levels 26-35 (install via SDK Manager)
- **Gradle:** 8.7.3 (included via wrapper, no separate install needed)

### SDK Components
Install via Android Studio SDK Manager (Tools → SDK Manager):
- Android SDK Platform 26 (minimum)
- Android SDK Platform 35 (target)
- Android SDK Build-Tools 35.x
- Android Emulator (optional, physical device recommended)

### First-Time Setup

1. **Clone Repository:**
   ```bash
   git clone https://github.com/ineeve/BikefitApp.git
   cd BikefitApp
   ```

2. **Open in Android Studio:**
   - File → Open → Select `BikefitApp` directory
   - Wait for Gradle sync to complete

3. **Verify Build:**
   ```bash
   ./gradlew build
   ```

4. **Run on Device:**
   - Connect Android device via USB
   - Enable Developer Options and USB Debugging
   - Select device in device dropdown
   - Click Run (▶) button

## Project Structure

```
app/src/main/java/pt/ineeve/bikefitapp/
├── camera/          # CameraX video capture, frame sampling
├── pose/            # MediaPipe Pose wrapper, landmark smoothing
├── calibration/     # Bike reference points, coordinate normalization
├── biomechanics/    # Angle calculations, pedal cycle detection
├── fit/             # Rule-based recommendation engine
└── ui/              # Video overlay, angle visualization, fit summary
```

## Coding Standards

### Language & Style
- **Kotlin** only
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful, descriptive names

### Architecture Principles
- **Modular**: Each package has a single responsibility
- **Testable**: Prefer pure functions, dependency injection
- **Simple**: Explicit code over clever abstractions

### Data Classes
Use data classes for all measurement types:
```kotlin
data class PoseFrame(
    val timestamp: Long,
    val landmarks: List<Landmark>,
    val confidence: Float
)

data class Landmark(
    val x: Float,
    val y: Float,
    val visibility: Float
)
```

### Pure Functions for Calculations
All biomechanics calculations must be pure functions:
```kotlin
// ✅ Good - pure function
fun calculateAngle(a: Vector2D, b: Vector2D, c: Vector2D): Float {
    // implementation
}

// ❌ Bad - side effects
fun calculateAngle(pose: PoseFrame): Float {
    logToAnalytics(pose)  // side effect!
    // implementation
}
```

### Constants & Thresholds
All thresholds must be documented constants:
```kotlin
object FitThresholds {
    /** Minimum knee angle at BDC indicating saddle too low */
    const val KNEE_ANGLE_MIN_BDC = 140f
    
    /** Maximum knee angle at BDC indicating saddle too high */
    const val KNEE_ANGLE_MAX_BDC = 160f
    
    /** Optimal knee angle range at BDC */
    val KNEE_ANGLE_OPTIMAL_RANGE = 145f..155f
}
```

### No Magic Numbers
```kotlin
// ❌ Bad
if (kneeAngle < 140f) { ... }

// ✅ Good
if (kneeAngle < FitThresholds.KNEE_ANGLE_MIN_BDC) { ... }
```

## Testing

### Test Framework
- **JUnit 5 (Jupiter):** Primary test framework
- **JUnit 4 Compatibility:** Via Vintage engine for legacy tests
- **Espresso:** UI instrumentation tests

### Test Structure
Tests mirror source structure:
```
app/src/test/kotlin/pt/ineeve/bikefitapp/
├── biomechanics/
│   ├── Vector2DTest.kt
│   ├── KneeAngleCalculatorTest.kt
│   ├── PedalCycleDetectorTest.kt
│   └── CycleAggregatorTest.kt
├── fit/
│   ├── FitEngineTest.kt
│   ├── SaddleHeightRuleTest.kt
│   └── FitSummaryTest.kt
├── pose/
│   ├── LandmarkSmootherTest.kt
│   └── PoseValidatorTest.kt
└── calibration/
    └── CoordinateTransformerTest.kt
```

### Running Tests

**In Android Studio:**
- Right-click on test file/directory → Run Tests
- View results in Run panel

**Via Command Line:**
```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests KneeAngleCalculatorTest

# Run specific test method
./gradlew test --tests KneeAngleCalculatorTest.testKneeAngleWithKnownTriangle

# Run with coverage
./gradlew testDebugUnitTest

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

### Writing Tests

**JUnit 5 Syntax:**
```kotlin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class ExampleTest {
    private lateinit var calculator: KneeAngleCalculator
    
    @BeforeEach
    fun setup() {
        calculator = KneeAngleCalculator()
    }
    
    @Test
    fun `test knee angle with known triangle`() {
        val hip = Vector2D(0f, 0f)
        val knee = Vector2D(1f, 0f)
        val ankle = Vector2D(1f, 1f)
        
        val result = calculator.calculate(hip, knee, ankle)
        
        assertEquals(90f, result.angle, 0.1f)
        assertTrue(result.isValid)
    }
}
```

### Test Coverage
- **Required:** All biomechanics calculations (angle calculators, cycle detection, aggregation)
- **Required:** All fit rules (saddle height, fore-aft, reach)
- **Required:** Core algorithms (smoothing, KOPS, hip rocking)
- **Goal:** 90%+ coverage on business logic modules
- **Optional:** UI components, Android framework wrappers

### Test Data Strategies

**Known Geometric Cases:**
```kotlin
@Test
fun `90 degree angle test`() {
    val v1 = Vector2D(1f, 0f)
    val v2 = Vector2D(0f, 1f)
    assertEquals(90f, v1.angleTo(v2), 0.1f)
}
```

**Boundary Conditions:**
```kotlin
@Test
fun `knee angle at threshold`() {
    val result = rule.evaluate(kneeAngle = 145f)  // Optimal min
    assertTrue(result.issues.isEmpty())
}
```

**Edge Cases:**
```kotlin
@Test
fun `zero vector handling`() {
    val result = calculator.calculate(Vector2D.ZERO, Vector2D.ZERO)
    assertFalse(result.isValid)
}
```

## Extending the App

### Adding a New Angle Calculator

1. **Create Calculator File:**
   ```kotlin
   // app/src/main/kotlin/.../biomechanics/ElbowAngleCalculator.kt
   object ElbowAngleCalculator {
       fun calculate(poseResult: PoseResult, side: BodySide): AngleResult {
           val shoulder = poseResult.getLandmark(SHOULDER_INDEX[side])
           val elbow = poseResult.getLandmark(ELBOW_INDEX[side])
           val wrist = poseResult.getLandmark(WRIST_INDEX[side])
           
           // Validate visibility
           if (!PoseValidator.validateLandmark(elbow)) {
               return AngleResult.invalid("Elbow not visible")
           }
           
           // Calculate angle
           val v1 = Vector2D(shoulder.x, shoulder.y)
           val v2 = Vector2D(elbow.x, elbow.y)
           val v3 = Vector2D(wrist.x, wrist.y)
           
           val angle = calculateAngle(v1, v2, v3)
           
           return AngleResult(
               angle = angle,
               isValid = true,
               confidence = elbow.visibility
           )
       }
   }
   ```

2. **Add to CycleAggregator:**
   ```kotlin
   // In CycleAggregator.kt
   data class CycleMetrics(
       // ... existing metrics
       val elbowAngle: AngleStats  // Add new metric
   )
   ```

3. **Create Test File:**
   ```kotlin
   // app/src/test/kotlin/.../biomechanics/ElbowAngleCalculatorTest.kt
   class ElbowAngleCalculatorTest {
       @Test
       fun `elbow angle with known positions`() {
           // Test implementation
       }
   }
   ```

### Adding a New Fit Rule

1. **Create Rule Class:**
   ```kotlin
   // app/src/main/kotlin/.../fit/CustomRule.kt
   class CustomRule : FitRule {
       override fun evaluate(
           cycleMetrics: CycleMetrics,
           calibration: BikeCalibration?,
           discipline: CyclingDiscipline
       ): List<FitIssue> {
           val issues = mutableListOf<FitIssue>()
           
           // Evaluation logic
           val metric = cycleMetrics.customMetric.average
           if (metric < THRESHOLD) {
               issues.add(FitIssue(
                   type = FitIssueType.CUSTOM,
                   severity = Severity.MEDIUM,
                   title = "Custom Issue Detected",
                   description = "...",
                   recommendation = "...",
                   affectedMetrics = listOf("custom_metric"),
                   category = FitIssueCategory.PEDALING
               ))
           }
           
           return issues
       }
       
       companion object {
           const val THRESHOLD = 50f
       }
   }
   ```

2. **Register in FitEngine:**
   ```kotlin
   // Add to FitEngine constructor or rules list
   val customRule = CustomRule()
   ```

3. **Add Tests:**
   ```kotlin
   class CustomRuleTest {
       @Test
       fun `detects issue when below threshold`() {
           // Test implementation
       }
   }
   ```

### Module Extension Guide

**Biomechanics Module:**
- Add angle calculators for new body measurements
- Extend CycleAggregator with new metrics
- Add specialized detectors (e.g., cadence, power)

**Fit Module:**
- Implement FitRule interface for new recommendations
- Add discipline-specific ranges in RangeLookup
- Extend FitIssueType enum

**Calibration Module:**
- Add new calibration points to BikeCalibration
- Extend CoordinateTransformer with new transformations

**UI Module:**
- Create custom views extending View
- Add overlay layers in PoseOverlayView
- Implement RecyclerView adapters for new data

## Debugging

### Pose Detection Issues

**Enable Debug Logging:**
```kotlin
companion object {
    private const val TAG = "PoseLandmarker"
    private const val DEBUG = true
}

if (DEBUG) {
    Log.d(TAG, "Detection confidence: ${result.confidence}")
    result.landmarks.forEach { landmark ->
        Log.d(TAG, "Landmark ${landmark.index}: visibility=${landmark.visibility}")
    }
}
```

**Common Issues:**
- **No pose detected:** Check lighting, camera framing, visibility thresholds
- **Jittery landmarks:** Decrease smoothing alpha (0.3-0.4)
- **Slow detection:** Check targetFps setting, device performance

### Biomechanics Validation

**Log Intermediate Values:**
```kotlin
Log.d(TAG, "Hip: ${hip.x}, ${hip.y}")
Log.d(TAG, "Knee: ${knee.x}, ${knee.y}")
Log.d(TAG, "Ankle: ${ankle.x}, ${ankle.y}")
Log.d(TAG, "Calculated angle: $angle")
```

**Visualize Vectors:**
```kotlin
// In custom overlay view
canvas.drawLine(hip.x, hip.y, knee.x, knee.y, paint)
canvas.drawLine(knee.x, knee.y, ankle.x, ankle.y, paint)
```

**Common Issues:**
- **Angles seem wrong:** Check coordinate system, ensure side-view camera
- **Cycle detection missing:** Adjust WINDOW_SIZE, MIN_PEAK_PROMINENCE
- **Stats inconsistent:** Ensure minimum cycle count met

### Camera/CameraX Issues

**Enable CameraX Logging:**
```kotlin
CameraX.setLoggingLevel(Log.DEBUG)
```

**Common Issues:**
- **Preview frozen:** Check lifecycle binding
- **Frame rate low:** Reduce targetFps, check device load
- **ImageProxy errors:** Ensure close() called after processing

### Performance Profiling

**Android Studio Profiler:**
- View → Tool Windows → Profiler
- Monitor CPU, memory, network usage
- Identify hot spots in pose processing loop

**Timing Measurements:**
```kotlin
val startTime = System.currentTimeMillis()
// ... operation ...
val elapsed = System.currentTimeMillis() - startTime
Log.d(TAG, "Operation took ${elapsed}ms")
```

## Commit Messages

Use conventional commits:
- `feat(camera): implement CameraX preview`
- `fix(pose): handle missing landmarks gracefully`
- `test(biomechanics): add knee angle unit tests`
- `refactor(fit): extract threshold constants`
- `docs: update architecture.md`

## Pull Request Process

### Before Submitting PR

**Checklist:**
- [ ] All tests pass (`./gradlew test`)
- [ ] New code has tests (90%+ coverage for business logic)
- [ ] Documentation updated (module READMEs, KDoc comments)
- [ ] Code follows Kotlin conventions
- [ ] No magic numbers (constants extracted and documented)
- [ ] Commit messages follow conventional commits
- [ ] Branch name follows pattern: `feature/issue-{number}-{short-description}`

**Required for Review:**
- Link to related issue
- Description of changes
- Test results screenshot/log
- Before/after comparison (if UI change)

### Review Criteria
- Code quality and readability
- Test coverage and quality
- Documentation completeness
- Performance impact
- Adherence to architecture principles

## Issue Workflow

1. Check issue dependencies ("Blocked by #X")
2. Create feature branch: `feature/issue-{number}-{short-description}`
3. Implement following acceptance criteria
4. Add/update tests
5. Create PR linking the issue

## What NOT to Implement

- 3D pose estimation
- Machine learning training
- Cloud services / network calls
- Multi-camera setups
- iOS support
- Tablet-specific layouts

## When Uncertain

- Leave `TODO` comments with questions
- Ask for clarification in issue comments
- Don't guess on thresholds - document assumptions
