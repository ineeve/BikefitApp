# UI Module

## Overview
The `ui` module contains all Android Activities, Fragments, and Custom Views. It is the consumer of all other modules.

## Key Components

### Activities
- `HomeActivity`: Entry point.
- `VideoAnalysisActivity`: The main capture and analysis session. Handles the live camera preview.
- `FitSummaryActivity`: Displays the final report after recording.

### Overlays
- `PoseOverlayView`: Draws the user's skeleton and live angle values on top of the camera preview.
- `BikeOverlayView`: Draws reference lines for the bike (e.g., vertical KOPS line).
- `CycleMetricsOverlayView`: Shows averaged stats (e.g., RPM, current max knee extension).

### View Components
- `RecordingGuidanceView`: Helper UI to get the user positioned correctly before recording starts.
- `AnalysisStatusView`: Indicates the state of the analysis (Searching for rider -> Calibrating -> Recording -> Done).
