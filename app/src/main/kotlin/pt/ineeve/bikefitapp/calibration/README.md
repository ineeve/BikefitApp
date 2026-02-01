# Calibration Module

## Overview
The `calibration` module defines the relationship between the 2D video frame and real-world measurements.

## Key Components

### `CalibrationActivity`
- A dedicated UI flow where the user positions markers on known reference objects (typically the bike wheels).

### `CoordinateTransformer`
- Uses the reference points from calibration to calculate a "Pixels per Millimeter" ratio.
- Transforms raw landmark coordinates (x, y) into physical coordinates for absolute measurements (if needed) or validates relative angles.

### `BikeCalibration`
- Data model persisting the calibration state (e.g., wheel center points, wheel diameter).
