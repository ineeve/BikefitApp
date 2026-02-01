# BikefitApp User Guide

Complete guide for setting up, calibrating, recording, and interpreting bike fit analysis results.

## Table of Contents
- [Camera Setup](#camera-setup)
- [Calibration](#calibration)
- [Recording a Session](#recording-a-session)
- [Analyzing Videos](#analyzing-videos)
- [Understanding Results](#understanding-results)
- [Troubleshooting](#troubleshooting)

---

## Camera Setup

### Equipment Requirements
- **Bike:** Mounted on stationary trainer or turbo
- **Camera:** Android device (API 26+) with camera permission
- **Mount:** Stable phone mount or tripod
- **Space:** Clear side-view of entire bike and rider

### Camera Positioning

**Position:** Side view perpendicular to bike

**Distance:** 2-4 meters (6-12 feet) from bike
- Too close: Not enough field of view
- Too far: Pose detection accuracy decreases

**Height:** Camera at saddle height
- Align camera horizontally with saddle
- Avoid angled views (looking up or down)

**Framing:** Entire bike and rider visible
```
┌────────────────────────────┐
│                            │
│    [Rider on bike]         │  ← Full body visible
│    ┌─┐                     │  ← Head to feet
│   /│ │\                    │
│  / │ │ \                   │
│    │ │                     │
│   / \_/                    │
│  ───────                   │
└────────────────────────────┘
```

### Lighting
- **Adequate Lighting:** Well-lit room or outdoor shade
- **Avoid:** Backlighting (window behind rider)
- **Avoid:** Harsh shadows on rider's side
- **Goal:** Clear visibility of body landmarks

### Rider Setup
- **Clothing:** Form-fitting clothing (cycling kit ideal)
- **Avoid:** Loose/baggy clothing that obscures body position
- **Markers:** No special markers needed (MediaPipe detects body)
- **Position:** Normal riding position, hands on hoods/drops

---

## Calibration

### Purpose
3-point bike calibration establishes reference geometry for KOPS analysis and physical measurements.

### When to Calibrate
- **Required for:** Saddle fore-aft analysis (KOPS)
- **Optional for:** Other metrics (knee angle, torso angle)
- **Re-calibrate when:** Camera moved, bike adjusted, or new session

### Calibration Process

**Step 1: Start Calibration**
- Tap "Calibrate Bike" button in analysis screen
- Camera preview appears with overlay instructions

**Step 2: Mark Saddle**
```
Instruction: "Tap on the saddle top"
```
- Tap on the **top center** of the saddle
- A marker appears at tapped location
- Adjust by dragging marker if needed

**Step 3: Mark Bottom Bracket**
```
Instruction: "Tap on the bottom bracket center"
```
- Tap on the **center of the bottom bracket** (crank axle)
- This is the reference origin for measurements
- Adjust marker position if needed

**Step 4: Mark Handlebar**
```
Instruction: "Tap on the handlebar grip"
```
- Tap on **handlebar grip** (where hands rest on hoods)
- Used for reach/cockpit analysis
- Adjust marker position if needed

**Step 5: Confirm**
- Review all three markers
- Tap "Confirm" to save calibration
- Returns to analysis screen

### Calibration Tips
- **Zoom In:** Use pinch-to-zoom for precise marking
- **Static Bike:** Ensure bike is stationary during calibration
- **Clear View:** All three points should be clearly visible
- **Accuracy:** Precise marking improves KOPS analysis accuracy

### Calibration State
- **Stored:** In-memory for current session
- **Persistence:** Not saved across app restarts
- **Re-use:** Same calibration used for all analysis in session

---

## Recording a Session

### Real-Time Camera Analysis

**Step 1: Start Analysis**
- From home screen, tap "Real-time Analysis"
- Grant camera permission if prompted
- Camera preview appears

**Step 2: Calibrate (Optional)**
- Tap "Calibrate Bike" if KOPS analysis desired
- Follow calibration steps above
- Or skip for knee/hip/torso analysis only

**Step 3: Position Check**
- **Guidance Overlay:** Shows setup tips
  - "Position camera for full side view"
  - "Ensure entire bike visible"
  - "Good lighting required"
- **Status Indicator:** Green = ready, Yellow = warning, Red = error

**Step 4: Start Pedaling**
- Begin pedaling at normal cadence (70-90 RPM)
- **Pose Skeleton:** Appears when body detected
- **Live Metrics Overlay:** Shows real-time angles:
  - Cycle count (in header)
  - Knee angle (primary, largest)
  - Hip angle
  - Torso angle
- **Cycle Metrics:** Max extension and min flexion update after each cycle

**Step 5: Record Data**
- Pedal for **5-10 complete cycles** (30-60 seconds)
- Maintain consistent cadence and position
- **Minimum Required:** 3 cycles for analysis

**Step 6: Stop & View Results**
- Tap "Stop Analysis" when complete
- Automatically navigates to Fit Summary
- Shows analysis results and recommendations

### Gallery Video Analysis

**Step 1: Select Video**
- From home screen, tap "Analyze Video"
- Browse and select pre-recorded video
- Supported formats: MP4, AVI, MOV

**Step 2: Calibrate**
- Video pauses on first frame
- Tap "Calibrate Bike" and mark 3 points
- Or skip calibration

**Step 3: Play & Analyze**
- Tap "Start Analysis" to begin processing
- Video plays with pose overlay
- **Live Metrics Overlay:** Shows real-time angles:
  - Cycle count (in header)
  - Knee angle (primary, largest)
  - Hip angle
  - Torso angle
- **Cycle Metrics:** Max extension and min flexion update after each cycle
- **Progress Bar:** Shows analysis progress

**Step 4: View Results**
- Processing completes automatically
- Navigates to Fit Summary
- Review recommendations

### Recording Best Practices

**Pedaling:**
- Maintain **consistent cadence** (avoid accelerating/decelerating)
- Use **normal riding intensity** (not sprint or recovery)
- Keep **stable position** (don't rock or shift excessively)
- Pedal **smoothly** (avoid stomping or bouncing)

**Session Duration:**
- **Minimum:** 3 complete cycles (~20-30 seconds)
- **Recommended:** 5-10 cycles (~40-60 seconds)
- **Maximum:** No limit, but diminishing returns after 15+ cycles

**What to Avoid:**
- Standing up / out of saddle
- Looking at camera (maintain forward gaze)
- Excessive arm movement or body motion
- Stopping or coasting

---

## Understanding Results

### Fit Summary Screen

**Session Summary:**
At the top of the results, you'll see an overview of your recording:
```
Cycles: 8 | Cadence: 85 RPM | Quality: 92%
```
- **Cycles:** Number of complete pedal cycles analyzed
- **Cadence:** Average pedaling speed in revolutions per minute
- **Quality:** Data quality percentage (green >80%, yellow >60%, red <60%)

**Biomechanical Metrics:**
Each metric displays comprehensive statistics:
- **Average Value:** Bold number in center column
- **Detailed Stats:** Min/Max/Standard Deviation shown below each metric
- **Ideal Range:** Right column shows optimal target range

Metrics displayed:
1. **Knee Extension** - Angle at bottom dead center (BDC)
2. **Knee Flexion** - Angle at top dead center (TDC)
3. **Hip Angle** - Hip joint angle throughout cycle
4. **Torso Angle** - Back angle relative to horizontal
5. **Knee fwd of Pedal** - KOPS measurement when available

**Overall Grade:**
```
Fit Grade: EXCELLENT / GOOD / FAIR / POOR
```
- **EXCELLENT:** All metrics in optimal ranges
- **GOOD:** Minor issues, generally well-fitted
- **FAIR:** Several issues requiring adjustment
- **POOR:** Significant issues, major adjustments needed

**Issue Categories:**
Results grouped by category:
- **SADDLE:** Height and fore-aft position
- **COCKPIT:** Reach and handlebar position  
- **PEDALING:** Ankle and pedaling dynamics
- **STABILITY:** Hip rocking and stability issues

### Understanding Recommendations

**Format:**
```
[Priority Badge] Issue Title
↳ Description
↳ Recommendation
↳ Metrics: Current value vs. Optimal range
```

**Priority Levels:**
- 🔴 **HIGH:** Significant issue, address immediately
- 🟡 **MEDIUM:** Moderate issue, adjust when possible
- 🟢 **LOW:** Minor issue, optional adjustment

### Common Issues & Interpretations

#### Saddle Too High
```
Issue: Saddle Height Too High
Priority: HIGH
Metric: Knee angle at BDC = 163° (Optimal: 145-155°)
Recommendation: Lower saddle by 10-15mm
```

**Symptoms:**
- Knee angle > 155° at bottom of pedal stroke
- Often accompanied by hip rocking
- Reaching for pedals at bottom

**Action:** Lower saddle in 5mm increments, re-test

#### Saddle Too Low
```
Issue: Saddle Height Too Low
Priority: HIGH
Metric: Knee angle at BDC = 138° (Optimal: 145-155°)
Recommendation: Raise saddle by 10-15mm
```

**Symptoms:**
- Knee angle < 145° at bottom of pedal stroke
- Excessive knee flexion
- Reduced power output

**Action:** Raise saddle in 5mm increments, re-test

#### Saddle Too Far Forward
```
Issue: Saddle Fore-Aft Position Too Far Forward
Priority: MEDIUM
Metric: KOPS = -4.2% (Optimal: ±3%)
Recommendation: Move saddle 8-10mm backward
```

**Symptoms:**
- Knee behind pedal spindle at 3 o'clock
- Quads overloaded
- Poor power distribution

**Action:** Slide saddle rails backward 5-10mm, re-test

#### Saddle Too Far Back
```
Issue: Saddle Fore-Aft Position Too Far Back
Priority: MEDIUM
Metric: KOPS = +5.1% (Optimal: ±3%)
Recommendation: Move saddle 10-12mm forward
```

**Symptoms:**
- Knee ahead of pedal spindle at 3 o'clock
- Hamstrings/glutes overloaded
- Inefficient power transfer

**Action:** Slide saddle rails forward 5-10mm, re-test

#### Reach Too Long
```
Issue: Reach Too Aggressive
Priority: MEDIUM
Metric: Torso angle = 22° (Optimal: 30-60°)
Recommendation: Shorten reach via shorter stem or spacers
```

**Symptoms:**
- Torso angle < 30° (very horizontal)
- Shoulder/neck strain
- Uncomfortable on long rides

**Action:** Shorten stem by 10-20mm OR add headset spacers

#### Reach Too Short
```
Issue: Reach Too Upright
Priority: LOW
Metric: Torso angle = 73° (Optimal: 30-60°)
Recommendation: Lengthen reach via longer stem or remove spacers
```

**Symptoms:**
- Torso angle > 60° (very upright)
- Less aerodynamic
- Weight too far back

**Action:** Longer stem by 10-20mm OR remove headset spacers

#### Hip Rocking
```
Issue: Excessive Hip Rocking Detected
Priority: HIGH
Metric: Hip amplitude = 7.2% (Normal: <5%)
Correlation: Likely caused by saddle too high
Recommendation: Lower saddle to reduce hip motion
```

**Symptoms:**
- Visible up/down hip motion
- Often indicates saddle too high
- Reduced efficiency, increased injury risk

**Action:** Lower saddle by 5-10mm increments until rocking stops

### Metric Ranges by Discipline

Different cycling disciplines have different optimal ranges:

**Road / Endurance:**
- Knee angle at BDC: 145-155°
- Torso angle: 35-55°

**Gravel / All-Road:**
- Knee angle at BDC: 145-155°
- Torso angle: 40-60° (more upright)

**Time Trial / Triathlon:**
- Knee angle at BDC: 145-155°
- Torso angle: 20-40° (more aggressive)

Select your discipline in settings to see appropriate ranges.

---

## Troubleshooting

### Pose Not Detected

**Symptoms:** No skeleton overlay appears

**Solutions:**
- ✅ **Check Lighting:** Ensure rider clearly visible
- ✅ **Check Distance:** Move camera 2-4m from bike
- ✅ **Check Framing:** Ensure full body visible in frame
- ✅ **Check Clothing:** Wear form-fitting clothing
- ✅ **Check Permissions:** Grant camera permission
- ✅ **Restart App:** Close and reopen app

### Jittery/Unstable Skeleton

**Symptoms:** Skeleton jumps or flickers

**Solutions:**
- ✅ **Improve Lighting:** Add more light to scene
- ✅ **Reduce Motion Blur:** Ensure adequate shutter speed
- ✅ **Clean Camera Lens:** Wipe camera lens
- ✅ **Stable Mount:** Ensure camera not shaking
- Already mitigated by EMA smoothing in app

### No Cycles Detected

**Symptoms:** Cycle counter stays at 0

**Solutions:**
- ✅ **Pedal Continuously:** Maintain steady pedaling
- ✅ **Normal Cadence:** 60-100 RPM recommended
- ✅ **Full Strokes:** Complete full pedal revolutions
- ✅ **Check Ankle Visibility:** Ensure ankle landmark visible
- ✅ **Side View:** Camera must be perpendicular to bike

### Inaccurate Angles

**Symptoms:** Angles seem wrong or inconsistent

**Solutions:**
- ✅ **Perpendicular View:** Camera must be exactly side-on
- ✅ **Camera Height:** Align camera with saddle height
- ✅ **No Angle:** Avoid looking up or down at rider
- ✅ **Stable Bike:** Ensure trainer is level and stable
- ✅ **Calibration:** Re-calibrate bike if using KOPS

### Calibration Issues

**Symptoms:** Cannot mark points accurately

**Solutions:**
- ✅ **Zoom In:** Use pinch-to-zoom for precision
- ✅ **Good Lighting:** Ensure bike points clearly visible
- ✅ **Pause Video:** For gallery analysis, pause on clear frame
- ✅ **Adjust Markers:** Drag markers after initial tap
- ✅ **Re-calibrate:** Start calibration over if needed

### App Crashes or Freezes

**Solutions:**
- ✅ **Close Background Apps:** Free up RAM
- ✅ **Restart Device:** Reboot Android device
- ✅ **Update App:** Check for app updates
- ✅ **Check Storage:** Ensure adequate free storage
- ✅ **Lower Video Quality:** Use lower resolution videos

### Results Seem Wrong

**Symptoms:** Recommendations don't match feeling on bike

**Considerations:**
- ⚠️ **Minimum Data:** Ensure 5+ cycles recorded
- ⚠️ **Consistent Pedaling:** Maintain steady cadence
- ⚠️ **Normal Position:** Use typical riding position
- ⚠️ **Discipline:** Select correct discipline in settings
- ⚠️ **Professional Fit:** App supplements, not replaces, pro fit
- ⚠️ **Individual Variation:** Optimal fit varies by rider

### Performance Issues

**Symptoms:** App slow or laggy

**Solutions:**
- ✅ **Close Background Apps:** Free up CPU/RAM
- ✅ **Mid-Range Device:** App designed for mid-range+ phones
- ✅ **Reduce Frame Rate:** Lower camera resolution if supported
- ✅ **Shorter Videos:** Analyze shorter clips (30-60 seconds)

---

## Best Practices

### For Accurate Analysis
1. **Consistent Setup:** Use same camera position for each session
2. **Warm Up:** Pedal 2-3 minutes before recording
3. **Multiple Sessions:** Record 2-3 sessions, average results
4. **Document Changes:** Note saddle adjustments between sessions
5. **Incremental Adjustments:** Change one thing at a time (5-10mm)

### Safety & Health
- ⚠️ **Not Medical Advice:** App provides fit guidance, not medical diagnosis
- ⚠️ **Consult Professionals:** See professional fitter for complex issues
- ⚠️ **Pain/Discomfort:** Address pain immediately, don't ignore
- ⚠️ **Injury History:** Consider previous injuries when adjusting
- ⚠️ **Gradual Changes:** Allow adaptation time (1-2 weeks) between major changes

### Getting Professional Help

**When to See a Bike Fitter:**
- Persistent pain or discomfort despite adjustments
- Complex fit issues (multiple conflicting metrics)
- Special circumstances (injuries, asymmetry, flexibility issues)
- Performance optimization for racing
- New bike purchase or major equipment change

**Using App with Fitter:**
- Share app results with professional fitter
- Use app for monitoring between fit sessions
- Track adjustments made during professional fit
- Validate fitter's recommendations with data

---

## Frequently Asked Questions

**Q: How accurate is the app?**  
A: Knee angle accuracy ±2-3° with proper setup. KOPS analysis ±3-5mm. Accuracy depends on camera setup, lighting, and pose detection quality.

**Q: Can I use the app on a stationary bike at the gym?**  
A: Yes, as long as you can position camera for clear side view and maintain consistent position.

**Q: Do I need calibration for every analysis?**  
A: No. Calibration only required for KOPS (saddle fore-aft) analysis. Knee and torso angles work without calibration.

**Q: Can I save my results?**  
A: Currently no data persistence. Take screenshots of Fit Summary screen for records.

**Q: Can I export data to CSV or PDF?**  
A: Not in current version. Planned for future release.

**Q: Does the app work with a regular stationary bike (not road bike)?**  
A: Yes, app works with any bike configuration as long as pose can be detected.

**Q: What if I have asymmetric fit issues (left vs. right side)?**  
A: App analyzes both sides independently. Results show which side has issues.

**Q: Can I use the app while riding on rollers or outdoor?**  
A: Not recommended. Camera must be stationary and stable for accurate analysis.

**Q: How often should I re-analyze my fit?**  
A: After any bike adjustment, after 2-3 weeks adaptation period, or if experiencing new discomfort.

**Q: Is my video data uploaded anywhere?**  
A: No. All processing is on-device. No network calls, no data transmission. Complete privacy.

---

## Support

For additional support, troubleshooting, or feedback:
- **Documentation:** See [README.md](README.md) and [CONTRIBUTING.md](CONTRIBUTING.md)
- **Technical Details:** See [architecture.md](architecture.md) and [ALGORITHMS.md](ALGORITHMS.md)
- **Issues:** Report bugs via GitHub Issues
- **Email:** [Your support email if available]
