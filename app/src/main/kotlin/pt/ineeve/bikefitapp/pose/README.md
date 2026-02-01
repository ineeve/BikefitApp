# Pose Module

## Overview
The `pose` module encapsulates the MediaPipe Vision Tasks API. It takes raw image frames and returns structured `PoseResult` objects containing landmarks.

## Key Components

### `PoseLandmarkerWrapper`
- Configures the MediaPipe `PoseLandmarker`.
- Loads the model asset (`pose_landmarker_lite.task`).
- Runs inference on provided images.

### `LandmarkSmoother`
- Raw ML output can be jittery. This component applies smoothing algorithms (e.g., exponential smoothing or OneEuroFilter) to stabilize landmark coordinates over time.

### `PoseValidator`
- Checks if necessary landmarks (e.g., hip, knee, ankle, foot) are visible in the frame.
- Filters out low-confidence detections to prevent bad data from entering the biomechanics engine.
