# Camera Module

## Overview
The `camera` module is responsible for abstracting the CameraX API and providing a clean stream of frames for analysis. It handles the optimization of frame delivery to ensure the ML pipeline isn't overwhelmed.

## Key Components

### `CameraManager`
- Initializes and binds CameraX use cases (Preview, ImageAnalysis).
- Manages camera lifecycle and permissions.
- Configures camera parameters (e.g., target resolution, frame rate).

### `FrameSampler`
- Decouples the camera frame rate from the analysis frame rate.
- Drops frames if the downstream consumer is busy or if a lower sampling rate is requested.

### `ImageProxyConverter`
- Converts Android's YUV `ImageProxy` objects into the format required by MediaPipe (typically RGB Bitmaps or specific Image containers).
- Handles rotation and scaling logic.
