# Phase 3 Execution Plan v2 — Sensor Fusion Boundary

## Objective
Implement **Module 3 (Sensor Fusion / PDR Layer)** as a listener-free consumer of existing application data. This module provides an independent motion estimate (PDR-based) to corroboration ARCore tracking and serves as a fallback displacement source during tracking loss. It also monitors for sensor staleness without registering any new hardware listeners.

## Scope

### Included
- Implementation of **SensorFusionLayer (Module 3)**.
- Subscription to `NavigationSessionManager.sessionState` (StateFlow).
- Derivation of relative PDR displacement (ΔX, ΔY in facility coordinates) using `totalSteps` and `headingDeg`.
- Implementation of the **Sensor Staleness Heuristic**: Flags a reading as stale if it remains bitwise identical for ~2 seconds while other signals (steps) indicate motion.
- Production of the **Corroboration Signal**: Returns the current accumulated PDR vector and resets the internal accumulator for the next cycle.
- Production of the **Fallback Displacement** estimate: Returns total displacement since last reset/session start.
- JVM unit tests to verify PDR accumulation and staleness detection.

### Excluded
- Consumption of Module 3's output (Instructions/Fallback) — deferred to Phase 6 and Phase 8.
- Modifications to raw sensor listeners (Must remain in `SensorFusionManager` / `StepTracker`).
- **Unconditional Exclusion**: No modification of `NavigationSessionManager` or `NavSessionState`.

## Existing Components

| Component | File Path | Classification | Role |
|---|---|---|---|
| `NavigationSessionManager` | `com.example.mallar.navigation.NavigationSessionManager.kt` | **Reuse** | Source of the `sessionState` StateFlow. |
| `NavSessionState` | `com.example.mallar.navigation.NavigationSessionManager.kt` | **Reuse** | Contains `totalSteps` and `headingDeg`. |
| `NavConfig` | `com.example.mallar.navigation.NavConfig.kt` | **Reuse** | Physical constants (`DEFAULT_STRIDE_LENGTH_M`, `STEP_DEBOUNCE_MS`). |

## New Components

| Component | Package | Purpose |
|---|---|---|
| `SensorFusionLayer` | `com.example.mallar.ar` | Implements Module 3; manages PDR displacement and staleness monitoring. |
| `PdrDisplacement` | `com.example.mallar.ar.model` | Data model for the relative displacement vector (dx, dy). |

## Dependencies

### Existing Dependencies Used
- **Kotlin Coroutines & Flow**: To subscribe to and process state updates asynchronously.
- **JUnit & MockK**: For logic verification.

### New Dependencies Required
- **None**.

## Implementation Sequence

### 1. Preparation
- **Phase 2 Sign-Off**: Confirm Human Lead Architect sign-off for Phase 2.
- **Documentation**: Review `AR_Engineering_Specification.md` §3 (Module 3) and §10 (Performance Requirements).
- **Structure Verification**: Confirm exact field and constant names in the repository:
    - [x] `NavSessionState.totalSteps` (Long)
    - [x] `NavSessionState.headingDeg` (Float)
    - [x] `NavConfig.DEFAULT_STRIDE_LENGTH_M` (0.75f)
    - [x] `NavConfig.STEP_DEBOUNCE_MS` (400L)

### 2. Define Data Models
- Update `com.example.mallar.ar.model.ArDataModels.kt` to include `PdrDisplacement` (dx: Double, dy: Double, timestamp: Long).
- Define `SensorStalenessStatus` (isStale: Boolean, sensorName: String).

### 3. Implement Module 3: SensorFusionLayer
- Create `com.example.mallar.ar.SensorFusionLayer`.
- The class will accept a `CoroutineScope` and the `StateFlow<NavSessionState>`.
- **PDR Derivation**:
    - Observe `totalSteps`. On increment, calculate ΔX/ΔY using the current `headingDeg` and `NavConfig.DEFAULT_STRIDE_LENGTH_M`.
    - `ΔX = stride * sin(headingRad)`
    - `ΔY = -stride * cos(headingRad)`
    - Maintain an internal `accumulatedDisplacement` vector.
- **Staleness Heuristic**:
    - Monitor `headingDeg` updates.
    - If `totalSteps` increments but `headingDeg` has not changed (bitwise) for > `NavConfig.STEP_DEBOUNCE_MS * 5` (approx 2s), flag as stale.
- **Interface**:
    - `getCorroborationSignal()`: Returns the current accumulated PDR vector and resets the internal accumulator to (0,0).
    - `getFallbackDisplacement()`: Returns total displacement since last reset/session start.

### 4. Technical Validation (Unit Tests)
- **Listener-Free Check**: Verify no `android.hardware.SensorEventListener` is implemented or registered.
- **PDR Accumulation**: Test that multiple steps at different headings produce the correct cumulative vector.
- **Staleness Detection**: Test that frozen heading values trigger the flag when steps occur.
- **Motionless Stability**: Test that no displacement is accumulated if `totalSteps` remains constant.

## Integration Points
- **Input**: `NavigationSessionManager.sessionState`.
- **Output**: Methods to be called by Module 8 (Phase 8) and Module 6 (Phase 6).

## Risks

| Risk | Mitigation |
|---|---|
| **Stride Mismatch** | Using a default stride length might cause divergence from the primary tracker. | *Mitigation:* Module 3 is explicitly "strictly corroborating, never authoritative." The error is acceptable within the "weighted" fusion model of Module 8. |
| **Update Latency** | Collecting from StateFlow might introduce a small lag compared to raw sensors. | *Mitigation:* PDR is a slow-varying signal relative to the render loop. StateFlow updates on every step/heading change are sufficient for corroboration. |
| **Bitwise Staleness False Positives** | A user walking perfectly straight might trigger staleness. | *Mitigation:* Use a reasonable time window (~2s) and ensure the flag is treated as a "weighted input" rather than a hard failure trigger. |

## Validation Strategy
1. **Static Analysis**: Confirm `SensorFusionLayer` does not import `android.hardware.Sensor*`.
2. **Logic Verification**: JUnit tests with mocked `StateFlow` emits.
3. **Drift Verification**: Verify that the PDR displacement vector matches a manually-calculated expected value (Euclidean distance) within a 5% margin for a simulated 10-step straight-line path.

## Rollback Strategy
- Delete `com.example.mallar.ar.SensorFusionLayer` and any new models in `ArDataModels.kt`.
- Phase 3 is additive and isolated, making rollback low-risk.

## Completion Criteria
- [ ] `SensorFusionLayer` implemented and subscribed to `NavSessionState`.
- [ ] Relative PDR displacement correctly derived from steps and heading.
- [ ] Staleness heuristic correctly flags frozen readings during motion.
- [ ] Zero new sensor listeners registered.
- [ ] Unit tests pass (Displacement and Staleness).
- [ ] Project builds successfully.
- [ ] **Independent Review (Human)** performed and recorded.
