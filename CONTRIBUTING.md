# Contributing to BikefitApp

## Quick Start

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle
4. Run on emulator or device (min SDK 26)

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

### Unit Tests
- Required for all `biomechanics` calculations
- Use known geometric cases (30-60-90 triangles, etc.)
- Test edge cases (zero vectors, parallel lines)

```kotlin
@Test
fun `knee angle calculation with known triangle`() {
    val hip = Vector2D(0f, 0f)
    val knee = Vector2D(1f, 0f)
    val ankle = Vector2D(1f, 1f)
    
    val angle = calculateKneeAngle(hip, knee, ankle)
    
    assertEquals(90f, angle, 0.1f)
}
```

### Test Location
- Unit tests: `app/src/test/java/pt/ineeve/bikefitapp/`
- Instrumented tests: `app/src/androidTest/java/pt/ineeve/bikefitapp/`

## Commit Messages

Use conventional commits:
- `feat(camera): implement CameraX preview`
- `fix(pose): handle missing landmarks gracefully`
- `test(biomechanics): add knee angle unit tests`
- `refactor(fit): extract threshold constants`
- `docs: update architecture.md`

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
