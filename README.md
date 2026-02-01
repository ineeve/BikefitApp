# BikefitApp - AI-Powered Bike Fit Analysis

Android application that uses computer vision and pose estimation to analyze bicycle fit from side-view video recordings. Provides comprehensive biomechanical analysis and actionable recommendations for saddle height, fore-aft position, and reach adjustments.

## Features

### Analysis Modes
- ✅ **Real-time Camera Analysis** - Live pose detection and metrics during riding
- ✅ **Gallery Video Import** - Analyze previously recorded videos
- ✅ **Interactive 3-Point Calibration** - Visual bike geometry setup (saddle, bottom bracket, handlebar)

### Biomechanical Metrics
- ✅ **Knee Angle Analysis** - Flexion/extension at BDC and TDC with full statistics
- ✅ **Hip Angle Analysis** - Range of motion tracking with min/max/stddev
- ✅ **Hip Rocking Detection** - Variance and amplitude analysis
- ✅ **Ankle Flexion Analysis** - Ankle angle throughout pedal stroke
- ✅ **Torso Angle Analysis** - Back angle relative to horizontal with statistics
- ✅ **KOPS Analysis** - Knee Over Pedal Spindle position (normalized by femur length)
- ✅ **Pedal Cycle Detection** - Automatic BDC/TDC event identification
- ✅ **Statistical Aggregation** - Min/max/average/stddev calculated and displayed per metric
- ✅ **Cadence Tracking** - Average RPM across all cycles
- ✅ **Data Quality Assessment** - Real-time quality percentage with color coding

### Fit Recommendations
- ✅ **Saddle Height Rule** - Optimal knee angle 145-155° at BDC
- ✅ **Saddle Fore-Aft Rule** - KOPS method with ±3% tolerance
- ✅ **Reach Rule** - Torso angle 30-60° optimal
- ✅ **Multi-Discipline Support** - Road, Endurance, Gravel, Time Trial, Triathlon
- ✅ **Fit Grading System** - EXCELLENT / GOOD / FAIR / POOR
- ✅ **Prioritized Issues** - Severity levels (LOW / MEDIUM / HIGH)

### Visualization
- ✅ **Pose Skeleton Overlay** - Real-time pose visualization
- ✅ **Bike Geometry Overlay** - Calibration reference display
- ✅ **Live Metrics Overlay** - Real-time display of knee, hip, and torso angles (camera + video modes)
- ✅ **Cycle Metrics Display** - Max extension and min flexion per complete cycle
- ✅ **Analysis Status Indicators** - Error/warning/info messages
- ✅ **Recording Guidance** - Setup tips carousel

## Technical Requirements

### System Requirements
- **Android Version:** 8.0+ (API 26+)
- **Recommended RAM:** 2GB+
- **Camera:** Required for real-time analysis
- **Permissions:** Camera access

### SDK Configuration
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35 (Android 15)
- **Compile SDK:** 35
- **Java Version:** 17

## Technology Stack

- **Language:** Kotlin 2.0.21
- **Build System:** Gradle 8.7.3 with Kotlin DSL
- **Pose Estimation:** MediaPipe Pose Lite (v0.10.14) - 33 landmarks
- **Camera:** CameraX 1.4.1 (core, camera2, lifecycle, view)
- **UI Framework:** Material Design 3 (1.12.0)
- **Testing:** JUnit 5 Jupiter with JUnit 4 compatibility

## Installation & Build

### Prerequisites
1. **Android Studio:** Hedgehog (2023.1.1) or newer
2. **Android SDK:** Install SDK 26-35 via SDK Manager
3. **Java JDK:** Version 17 or newer

### Build Instructions

```bash
# Clone repository
git clone https://github.com/ineeve/BikefitApp.git
cd BikefitApp

# Sync dependencies
./gradlew build

# Install debug APK on connected device
./gradlew installDebug

# Run tests
./gradlew test
```

### MediaPipe Model Asset
The app includes `pose_landmarker_lite.task` (11MB) in [app/src/main/assets/](app/src/main/assets/pose_landmarker_lite.task). This is automatically packaged during build.

### Known Build Issues
- **MediaPipe 16KB Page Size Warning:** Native libraries not yet 16KB-aligned for Android 15+. App works correctly but shows warning on devices with 16KB page sizes. Tracked upstream: [mediapipe#5292](https://github.com/google-ai-edge/mediapipe/issues/5292)

## Quick Start

### For Users
1. **Setup:** Mount bike on stationary trainer, position camera for side view
2. **Launch:** Open app and select "Real-time Analysis" or "Analyze Video"
3. **Calibrate:** Tap to mark saddle, bottom bracket, and handlebar
4. **Record:** Pedal for 5-10 full cycles (~30-60 seconds at normal cadence)
5. **Review:** View fit analysis with prioritized recommendations

See [USERGUIDE.md](USERGUIDE.md) for detailed instructions.

### For Developers
1. Clone repository and open in Android Studio
2. Sync Gradle dependencies
3. Run on physical device (emulator has limited camera capabilities)
4. See [CONTRIBUTING.md](CONTRIBUTING.md) for coding standards

## Project Status

**Current Stage:** Feature-complete MVP with comprehensive testing

**Implemented:**
- Complete pose estimation pipeline with smoothing
- 4 angle calculators (knee, hip, ankle, torso)
- Sophisticated pedal cycle detection algorithm
- 3 fit rules with configurable thresholds
- 5 activities with full UI flows
- 33 unit test files with 95%+ coverage on business logic

**Known Limitations:**
- Side-view only (cannot analyze frontal plane motion)
- Single rider per session
- Requires stationary bike/trainer
- No data export or session history
- No frontal view analysis (lateral knee tracking, stance width)

**Future Considerations:**
- Video session persistence
- CSV/PDF report export
- Multi-session trend analysis
- Integration with training platforms
- Multi-angle camera support

## Documentation

- [USERGUIDE.md](USERGUIDE.md) - Setup instructions, usage guide, troubleshooting
- [architecture.md](architecture.md) - System architecture and module descriptions
- [CONTRIBUTING.md](CONTRIBUTING.md) - Development guidelines and coding standards
- [API.md](API.md) - Public API reference with usage examples
- [ALGORITHMS.md](ALGORITHMS.md) - Algorithm implementations and formulas
- [TODO.md](TODO.md) - Implementation status

## Architecture Overview

The app follows a modular architecture with clear separation of concerns:

```
Camera → Pose Detection → Biomechanics Analysis → Fit Rules → UI Display
```

See [architecture.md](architecture.md) for detailed module descriptions and data flow.

## License

[Include license information]

## Constraints & Design Decisions

- **Side View Only:** Biomechanical analysis focuses on sagittal plane (knee extension, hip angle, torso lean)
- **Stationary Bike:** Requires stable camera position and consistent pedaling
- **Single Rider:** One rider per analysis session
- **On-Device Processing:** All computation runs locally, no cloud services
- **No Network Calls:** Complete privacy, no data transmitted