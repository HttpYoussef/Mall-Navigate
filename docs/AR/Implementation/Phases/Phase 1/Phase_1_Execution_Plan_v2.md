# Phase 1 Execution Plan v2 — Data Contract Extension

## Objective
The objective of Phase 1 is to extend the `LocalizationResult` data model to include a `landmarkCount` field and ensure it is accurately populated by the `LocalizationEngine`. This field will eventually serve as the primary signal for "Provisional" vs "Confirmed" fix-confidence tiering in Phase 5.

## Scope

### Included
- Modification of the `LocalizationResult` data class in `com.example.mallar.data`.
- Update of the `LocalizationEngine` in `com.example.mallar.ml` to populate the `landmarkCount` field based on unique landmarks contributing to a fix.
- Verification of the population logic through debug logging and manual end-to-end testing of the existing scan flow.

### Excluded
- Any changes to the underlying logo detection, matching, or PnP solver logic.
- Implementation of the actual "Provisional/Confirmed" logic (deferred to Phase 5).
- UI changes to display the landmark count (unless for temporary debug purposes).

## Existing Components

| Package / File | Classification | Role in Phase 1 |
|---|---|---|
| `com.example.mallar.data.LocalizationResult.kt` | **Modify** | Add `landmarkCount: Int` to the data class. |
| `com.example.mallar.ml.LocalizationEngine.kt` | **Modify** | Populate `landmarkCount` in all `LocalizationResult` return points. |
| `com.example.mallar.ml.LogoDetector.kt` | **Untouched** | Provides raw detections (already working). |
| `com.example.mallar.ui.localization.LogoScanScreen.kt` | **Untouched** | Target for regression testing and manual verification. |
| `com.example.mallar.navigation.NavigationSessionManager.kt` | **Untouched** | Downstream consumer (via `NavigationState`) that should remain unaffected. |

## New Components
No new components are introduced in Phase 1. This is a targeted modification of an existing data contract.

## Dependencies

### Existing Dependencies Used
- **Kotlin Standard Library**: For data class and collection operations.
- **Android Log**: For verification logging.

### New Dependencies Required
- **None**.

## Implementation Sequence

### 1. Preparation
- **Phase 0 Sign-Off**: Confirm that a recorded Engineering Sign-Off exists for Phase 0 (Environment & Manifest Baseline) per Implementation Rule 10.
- **Structure Verification**: Verify the current internal structure of `LocalizationEngine.kt` to confirm the four expected `LocalizationResult` instantiation points (`buildPnPResult`, `centroidFallback`, `singleLandmarkFallback`, `noDetectionResult`).
- **Documentation**: Re-read `AR_Implementation_Roadmap.md` Phase 1, `AR_Testing_and_Validation_Plan.md` §3, and `AR_Implementation_Playbook.md`.
- **Baseline Build**: Verify current build state of the `:app` module.

### 2. Git Workflow
Per Playbook §6, this phase follows a strict branching and commit discipline:
- **Branch**: All work performed on a dedicated phase-scoped branch (e.g., `feature/ar-phase-1-contract`).
- **Commits**: Atomic, logically grouped commits within the branch. Commit messages must reference Roadmap Phase 1.
- **Completion**: Merging to the integration branch occurs only after sign-off. The final merge commit will be marked: `Phase 1 complete, signed off`.

### 3. Data Model Update
- **File**: `com.example.mallar.data.LocalizationResult.kt`
- **Change**: Add `val landmarkCount: Int` as the last parameter in the `LocalizationResult` constructor.
- **Reason**: Additive change to the data contract.

### 4. Engine Population Update
- **File**: `com.example.mallar.ml.LocalizationEngine.kt`
- **Task**: Update all four `LocalizationResult` instantiation points:
    1. `buildPnPResult`: Set `landmarkCount = landmarks.size`.
    2. `centroidFallback`: Set `landmarkCount = landmarks.size`.
    3. `singleLandmarkFallback`: Set `landmarkCount = 1`.
    4. `noDetectionResult`: Set `landmarkCount = 0`.
- **Instrumentation**: Add `Log.d(TAG, "LocalizationResult created: landmarkCount=$landmarkCount")` to each point for verification.

### 5. Technical Verification
- Compile the project to ensure no breakages in existing consumers.
- Deploy to a physical device.

### 6. Functional Validation (Manual)
- **Test Case 1 (Empty)**: Scan a blank wall. Verify `landmarkCount == 0` in logs.
- **Test Case 2 (Single)**: Scan a single known store logo (e.g., "Zara"). Verify `landmarkCount == 1`.
- **Test Case 3 (Multi)**: Scan a corridor with at least two visible store logos. Verify `landmarkCount >= 2`.

### 7. Regression Testing
- Confirm that the existing "Medium" confidence dialog (human confirmation) still appears correctly in `LogoScanScreen`.
- Confirm that "High" confidence fixes still auto-accept as before.

## Risks

| Risk | Mitigation |
|---|---|
| **Data Surfacing Difficulty** (Roadmap Flagged) | If the pipeline's internal landmark-count data proves difficult to surface at the expected points, **stop and escalate** rather than inventing an alternate source. |
| **Count Discrepancy** | Review mapping logic. If the pipeline's existing behavior does not guarantee unique landmark counting (e.g., duplicate detections for one brand), **escalate for clarification** rather than deciding the uniqueness policy locally. |
| **Constructor Breakage** | Performed a project-wide `grep` which confirmed all instantiations are within `LocalizationEngine.kt`. |

## Validation Strategy
As per `AR_Testing_and_Validation_Plan.md` §3:
1. **Existing Flow Regression**: Execute `LogoScanScreen` flow and confirm zero deviation from baseline behavior.
2. **Field Correctness**: Use `Logcat` to monitor `LocalizationResult` creation and verify `landmarkCount` matches the physical count of landmarks detected in the frame.

## Rollback Strategy
Phase 1 changes are confined to two files.
1. **Git Revert**: Revert the phase-scoped branch or use `git checkout` to restore files.
2. **Manual Revert**: Remove the `landmarkCount` parameter and the corresponding constructor arguments in the engine.

## Completion Checklist
- [ ] `LocalizationResult` data class includes `landmarkCount: Int`.
- [ ] `LocalizationEngine` populates `landmarkCount` correctly in all return paths.
- [ ] Project compiles successfully.
- [ ] Logcat confirms correct `landmarkCount` for empty, single, and multi-landmark scenarios.
- [ ] `LogoScanScreen` functionality is regression-free.
- [ ] **Independent Review performed and recorded** (Separation of Duties per Playbook §7).
