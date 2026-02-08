# BikefitApp Architecture

## System Overview

BikefitApp follows a modular, pipeline-based architecture where data flows unidirectionally from camera input through pose estimation, biomechanical analysis, fit rule evaluation, and finally to UI display.

## Processing Pipeline

The complete data processing pipeline consists of 12 stages:

```
1. Camera (CameraX)
   ↓
2. FrameSampler (adaptive frame rate control)
   ↓
3. ImageProxyConverter (YUV → RGB)
   ↓
4. PoseLandmarkerWrapper (MediaPipe inference)
   ↓
5. LandmarkSmoother (EMA smoothing) or OneEuroLandmarkSmoother (adaptive smoothing)
   ↓
6. PoseValidator (confidence/visibility checks)
   ↓
7. AngleCalculators (knee, hip, ankle, torso)
   ↓
8. PedalCycleDetector (BDC/TDC event detection)
   ↓
9. CycleAggregator (statistical aggregation)
   ↓
10. FitEngine (orchestration)
   ↓
11. FitRules (SaddleHeight, SaddleForeAft, Reach)
   ↓
12. UI Display (overlays, summaries)
```

**Frame Rate:** 24 FPS target for pose estimation  
**Minimum Data:** 3-10 pedal cycles required for reliable analysis  
**Threading:** Camera/MediaPipe on background executor, UI on main thread

## Coordinate Systems

The app uses three coordinate systems with transformations between them:

### 1. MediaPipe Normalized Coordinates (0-1)
- **Origin:** Top-left corner
- **Range:** x ∈ [0, 1], y ∈ [0, 1]
- **Used by:** PoseLandmarkerWrapper output
- **Invariant to:** Image resolution

### 2. Pixel Coordinates
- **Origin:** Top-left corner
- **Range:** x ∈ [0, width], y ∈ [0, height]
- **Used by:** Custom overlay views for rendering
- **Conversion:** `pixelX = normalizedX * imageWidth`

### 3. Physical Coordinates (mm)
- **Origin:** Bottom bracket (after calibration)
- **Used by:** KOPS analysis, distance measurements
- **Transformer:** `CoordinateTransformer` class
- **Requires:** Bike calibration (3 reference points)

### Coordinate Transformation

The `CoordinateTransformer` normalizes coordinates using bike calibration:

```kotlin
// Normalizes by saddle-to-BB distance (femur proxy)
val normalized = transformer.normalizeToFemurLength(
    point = kneePosition,
    calibration = bikeCalibration
)
```

## Modules

### camera
**Purpose:** Video capture and frame management

**Key Components:**
- `CameraManager` (257 lines) - CameraX wrapper with lifecycle management
- `FrameSampler` - Adaptive FPS control to prevent ML pipeline overload
- `ImageProxyConverter` - YUV to RGB conversion for MediaPipe
- `CameraPreviewActivity` (748 lines) - Main live analysis activity

**Configuration:**
- Preview + ImageAnalysis use cases
- 24 FPS target for pose estimation
- Zoom controls support
- Permission handling

### pose
**Purpose:** Pose estimation and landmark processing

**Key Components:**
- `PoseLandmarkerWrapper` (245 lines) - MediaPipe integration
  - 3 running modes: IMAGE, VIDEO, LIVE_STREAM
  - Configurable confidence thresholds (detection, presence, tracking)
  - Thread-safe single pose detection
- `LandmarkSmoother` (195 lines) - Exponential Moving Average (EMA)
  - Formula: `smoothed = α * current + (1-α) * previous`
  - Default α = 0.4
- `OneEuroFilter` (135 lines) - Adaptive low-pass filter
  - Based on Casiez et al. 2012
  - Adjusts cutoff frequency based on signal velocity
  - Parameters: minCutoff (1.0 Hz), beta (0.02), dCutoff (1.0 Hz)
- `OneEuroLandmarkSmoother` (170 lines) - One Euro filter for landmarks
  - Filters X, Y, Z coordinates independently
  - Targets hip, knee, ankle, toe landmarks (8 total)
  - Performance: ~0.006ms per frame
- `PoseValidator` - Visibility and confidence validation
- `PoseFrame`, `PoseResult` - Data models (33 MediaPipe landmarks)

**Model:** MediaPipe Pose Lite (`pose_landmarker_lite.task`, 11MB)

### calibration
**Purpose:** Bike reference point marking and coordinate transformation

**Key Components:**
- `BikeCalibration` (172 lines) - 3-point calibration data
  - Points: Saddle, Bottom Bracket, Handlebar
- `CalibrationActivity` - Interactive tap-to-mark UI
- `CalibrationOverlayView` - Visual overlay with point adjustment
- `CalibrationRepository` - In-memory singleton for session state
- `CoordinateTransformer` - Coordinate normalization and transformations

**State Machine:**
```
WaitingForSaddle → WaitingForBottomBracket → WaitingForHandlebar → Complete
```

**Purpose:** Bike geometry normalization for physical measurements

### biomechanics
**Purpose:** Mathematical analysis of pose data

**Key Components:**

**Angle Calculators:**
- `KneeAngleCalculator` (262 lines) - Knee flexion/extension with validation
- `HipAngleCalculator` - Hip range of motion
- `AnkleAngleCalculator` - Ankle flexion analysis
- `TorsoAngleCalculator` - Back angle relative to horizontal

**Advanced Analysis:**
- `PedalCycleDetector` (430 lines) - Sliding window algorithm for BDC/TDC detection
- `CycleAggregator` (480 lines) - Statistical aggregation (min/max/avg/stddev per cycle)
- `HipRockingDetector` (316 lines) - Excessive hip motion detection (variance + amplitude)
- `KneeOverPedalOffset` (407 lines) - KOPS analysis normalized by femur length
- `KeyFrameSelector` - Identifies key positions in pedal stroke
- `AnkleFlexionAtBdc`, `KneeFlexionAtBdc`, `HipAngleAtTdc` - Specialized extractors

**Math Library:**
- `Vector2D` (315 lines) - Complete 2D vector operations
  - Magnitude, normalization, dot/cross products
  - Angle calculations (signed, unsigned, directional)
  - Rotation, projection, lerp, distance utilities

**Data Models:**
- `CycleMetrics` (232 lines) - Complete cycle data
- `AngleStats` - min/max/avg/stddev/range for each metric

**Design:** Pure functions, no side effects, fully testable

### fit
**Purpose:** Expert system for fit recommendations

**Key Components:**

**Orchestration:**
- `FitEngine` (419 lines) - Rule orchestration
  - Configurable rule enabling/disabling
  - Minimum cycle threshold (default: 3 cycles)
  - Priority-based issue sorting

**Implemented Rules:**
1. `SaddleHeightRule` (343 lines)
   - Optimal: 145-155° knee angle at BDC
   - Too low: <140°, Too high: >160°
   - Hip rocking correlation analysis

2. `SaddleForeAftRule` (352 lines)
   - KOPS method with ±3% tolerance
   - Requires bike calibration
   - Forward/backward offset detection

3. `ReachRule` (313 lines)
   - Optimal: 30-60° torso angle
   - Too aggressive: <25°, Too upright: >70°
   - Shoulder-to-handlebar offset analysis

**Discipline Support:**
- `CyclingDiscipline` - 5 disciplines (ROAD, ENDURANCE, GRAVEL, TT, TRI)
- `RangeLookup` - Discipline-specific optimal ranges
- `MetricRange` - Range definitions with status

**Output Models:**
- `FitIssue` (295 lines) - 7 issue types with severity (LOW/MEDIUM/HIGH)
- `FitSummary` (389 lines) - Complete analysis summary
  - Fit grade: EXCELLENT / GOOD / FAIR / POOR
  - Category grouping: SADDLE / COCKPIT / PEDALING / STABILITY
  - Metric ranges and status
  - Actionable recommendations

### ui
**Purpose:** Visual display and user interaction

**Activities (5 total):**
1. `HomeActivity` - Entry point with mode selection (real-time / gallery)
2. `CameraPreviewActivity` (748 lines) - Live camera analysis with overlays
3. `CalibrationActivity` - Interactive 3-point bike marking
4. `VideoAnalysisActivity` (396 lines) - Gallery video analysis with live metrics overlay
5. `FitSummaryActivity` (275 lines) - Results display with comprehensive statistics (min/max/avg/stddev for all metrics), session summary (cycle count, cadence, data quality), and prioritized recommendations

**Custom Views (6 total):**
1. `PoseOverlayView` - Skeleton rendering with customizable colors
2. `BikeOverlayView` - Bike reference geometry display
3. `CycleMetricsOverlayView` - Real-time metrics overlay (cycle count, knee/hip/torso angles, max extension, min flexion)
4. `AnalysisStatusView` - Status messages with icons (error/warning/info)
5. `RecordingGuidanceView` - Setup tips carousel
6. `CalibrationOverlayView` - Interactive calibration markers

**Adapters:**
- `FitRecommendationAdapter` - RecyclerView adapter for fit issues

## State Management

### Session State
- `CalibrationRepository` - Singleton in-memory storage
  - Stores active bike calibration
  - Lifecycle: Single analysis session
  - No persistence across app restarts

### Activity State
- Static companion objects for inter-activity data passing
- `pendingCalibration` pattern for calibration flow
- View state in custom views (overlay positions, visibility)

### Threading Model
- **Main Thread:** UI updates, view rendering
- **Background Executor:** Camera callbacks, MediaPipe inference
- **Synchronization:** Thread-safe landmark smoothing with synchronized state

## Data Flow Example

### Real-time Analysis Flow

```
User taps "Start Analysis" in CameraPreviewActivity
  ↓
CameraManager.startCamera() initializes CameraX
  ↓
FrameSampler controls analysis frequency (24 FPS)
  ↓
Each frame: ImageProxyConverter → RGB bitmap
  ↓
PoseLandmarkerWrapper.detectAsync() → MediaPipe inference
  ↓
LandmarkSmoother applies EMA to reduce jitter
  ↓
PoseValidator checks visibility/confidence thresholds
  ↓
Parallel angle calculations (knee, hip, ankle, torso)
  ↓
PedalCycleDetector identifies BDC/TDC events
  ↓
CycleAggregator accumulates frame data per cycle
  ↓
After N cycles: FitEngine.analyze(metrics)
  ↓
Each FitRule evaluates metrics → generates FitIssue objects
  ↓
FitSummary created with sorted, prioritized issues
  ↓
User navigates to FitSummaryActivity to view results
```

## Design Principles

### Modularity
- Each module has single responsibility
- Clear interfaces between modules
- Minimal inter-module dependencies

### Testability
- Pure functions for all biomechanics calculations
- Dependency injection for Android framework components
- 33 unit test files with comprehensive coverage

### Simplicity
- Explicit code over abstractions
- Documented constants (no magic numbers)
- KDoc comments on public APIs

### Performance
- Adaptive frame sampling prevents CPU overload
- Efficient vector math with pre-allocated objects
- Minimal allocations in hot path (pose processing loop)