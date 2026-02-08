# Crank Angle & Cadence Analysis - Issues and Fixes

## Issues Identified from Log Analysis

### **Issue 1: Cadence Stuck at ~40 RPM (LEFT side)**

**Symptom:** LEFT leg cadence displayed as constant 40.2 RPM across 20+ frames while RIGHT leg shows varying values (28-65 RPM).

**Root Cause:** Large angle jumps being rejected as outliers, causing the velocity estimate to stagnate.

Evidence from logs:
```
Frame 5 LEFT:  raw=352.93°, delta=-143.85° → REJECTED (threshold: 30°)
Frame 6 LEFT:  raw=345.6°,  delta=-151.14° → REJECTED  
Frame 24 LEFT: raw=27.3°,   delta=-110.2°  → REJECTED
Frame 25 LEFT: raw=355.4°,  delta=-142.1°  → REJECTED
```

**Why it happens:** 
- When outliers are rejected, the last smoothed angle is retained
- But `recentDeltas` list is NOT updated with rejected measurements
- So `estimateAngularVelocity()` has no new data and keeps returning the same velocity
- Same velocity → same RPM forever

### **Issue 2: Crank Angle Wrapping Detected as Outliers**

**Symptom:** Valid angle transitions crossing 0°/360° boundary are incorrectly rejected.

Example progression in Frame 22-24 (LEFT leg):
```
Frame 22: filtered=122.0°
Frame 23: filtered=126.7° (delta ≈ 4.7°)  ✓ VALID
Frame 24: raw=27.3° → delta from 126.7° = -99.4°
  But log shows delta=-110.2°  
  Expected: valid ~4° or wrapping change
  Got: REJECTED as outlier (exceeds 30° threshold with velocity multiplier)
```

This is likely a **boundary condition near the 0°/360° transition** where the ankle actually should be at ~120-150° on the next cycle, but MediaPipe occasionally gives 355-7° as the crank starts rotating backward from upper position.

### **Issue 3: Windowed RPM Not Building**

**Symptom:** Windowed RPM (0.5s window) stays near 0 or very small (0.1-2.3).

**Root Cause:** 
1. Many frames rejected as outliers → buffer has few valid measurements
2. When buffer is sparse, angle delta is small and time window is short
3. Windowedpm formula: `RPM = (angle_delta / 360°) × (60,000 ms / timeDelta_ms)`

With frequent rejections, this can't accumulate enough angle change in the rolling window.

---

## Fixes Applied

### **Fix 1: Increased Outlier Threshold**

**Changed:**
```kotlin
maxAngleChangePerFrame: 30f → 45f
velocityThresholdMultiplier: 2.5f → 1.8f
```

**Rationale:**
- At 100 RPM with 30 FPS: ~20°/frame (theoretical max)
- 45° gives ~2.25x safety margin for jitter
- Reduced multiplier (2.5→1.8) makes velocity-based threshold less aggressive
- This allows wrapping transitions to pass through more often

### **Fix 2: Increased Velocity History**

**Changed:**
```kotlin
velocityHistorySize: 5 → 10
```

**Rationale:**
- Longer history (10 frames ≈ 330ms) gives more stable velocity estimate
- Reduces noise from single large jumps
- Helps with the "stagnant velocity" problem

### **Fix 3: Comment Updates**

Updated documentation to reflect proper thresholds for 90-100 RPM range.

---

## Expected Behavior After Fixes

✅ **Cadence** should now show varied values reflecting actual leg motion (90-100 RPM expected)  
✅ **Crank angle** should display full range (0-360°) as pedal rotates through cycle  
✅ **Windowed RPM** should accumulate smoothly over 0.5s windows  
✅ **Fewer outlier rejections** near angle boundaries  

## Remaining Observations

**Note:** The RIGHT leg appears to have **lower visibility** (0.18-0.29 vs LEFT 0.95):
```
Frame 0 LEFT:  visibility=0.968
Frame 0 RIGHT: visibility=0.370  ← Much lower
```

This could indicate:
- RIGHT leg pose detection quality is worse
- Bike setup favors view of LEFT leg
- Camera angle or lighting affecting right side

The outlier rejections on RIGHT leg seem appropriate given the visibility constraints. Focus on LEFT leg for accurate cadence calculation.

---

## Testing Recommendations

1. **Verify cadence range** - Should see 90-100 RPM band from log
2. **Check angle transitions** - Angles should sweep 0-360° smoothly through cycle
3. **Monitor outlier count** - Fewer "OUTLIER REJECTED" logs than before (from ~5-10 per frame → 1-2)
4. **Windowed RPM growth** - Should accumulate over 0.5s window, not stay 0

