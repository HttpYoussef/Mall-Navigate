# Phase 2 Execution Plan — Integration Boundary Foundation

## Objective
Implement the foundation for the AR subsystem's integration with the existing navigation data. This involves creating **Module 9 (NavigationSessionInputAdapter)** and **Module 5 (Route/Path Layer)**. These components ensure that the AR subsystem has a stable, immutable view of the navigation session and follows the anti-global-state principle by isolating the subsystem from the existing `NavigationState` singleton.

## Scope

### Included
- Definition of the **Navigation Session Snapshot** and **Route Node Metadata** data models.
- Implementation of **NavigationSessionInputAdapter (Module 9)**: Performs a one-time read of `NavigationState` to produce a snapshot.
- Implementation of **Route/Path Layer (Module 5)**: Holds the active route in facility coordinates and provides metadata (turn angles, floor identifiers).
- Capability for Route/Path Layer to request recalculation from the existing pathfinding engine.
- JVM unit tests to verify the single-read guarantee, snapshot immutability, and route data equivalence.

### Excluded
- Any ARCore, anchor, or rendering logic (deferred to Phase 4+).
- The live trigger for route recalculation (the "Instruction" from Module 8 is deferred to Phase 8).
- Modifications to the existing pathfinding algorithm or `NavigationState` structure.

## Existing Components

| Component | File Path | Classification | Role |
|---|---|---|---|
| `NavigationState` | `com.example.mallar.ui.localization.LogoScanScreen.kt` | **Reuse** | Source of global navigation data (read once). |
| `MallGraphRepository` | `com.example.mallar.data.MallGraphRepository.kt` | **Reuse** | Provides the pathfinding engine (`aStarByNodeId`). |
| `AStarPath` | `com.example.mallar.data.MallGraphRepository.kt` | **Reuse** | Data structure for the planned path. |
| `GraphNode` | `com.example.mallar.data.MallGraphRepository.kt` | **Reuse** | Represents nodes in the mall graph. |

## New Components

| Component | Package | Purpose |
|---|---|---|
| `ArSubsystemModels` | `com.example.mallar.ar` | Contains `NavigationSessionSnapshot` and `RouteNodeMetadata` to strictly define the subsystem's data contracts. |
| `NavigationSessionInputAdapter` | `com.example.mallar.ar` | Implements Module 9; encapsulates the single-read logic from `NavigationState`. |
| `RoutePathLayer` | `com.example.mallar.ar` | Implements Module 5; manages the facility-coordinate route state. |

## Dependencies

### Existing Dependencies Used
- **Gson**: Used by `MallGraphRepository` (internal dependency).
- **Kotlin Coroutines/Flow**: For potential future-proofing (though not strictly required for this phase's logic).
- **JUnit & MockK**: For validation (integrated in Phase 1).

### New Dependencies Required
- **None**.

## Implementation Sequence

### 1. Preparation
- **Phase 1 Sign-Off**: Confirm recorded Engineering Sign-Off for Phase 1.
- **Documentation**: Re-read `AR_Engineering_Specification.md` §3 (Modules 5 & 9) and §5 (Data Contracts).

### 2. Define Data Contracts
- Create `com.example.mallar.ar.model.ArDataModels.kt`.
- Define `NavigationSessionSnapshot` as an immutable data class containing destination name, start node ID, and the list of node IDs from the path.
- Define `RouteNodeMetadata` containing coordinate (x, y, floor), turn information (derived from `NavInstruction`), and destination flag.

### 3. Implement Module 9: NavigationSessionInputAdapter
- Create `com.example.mallar.ar.NavigationSessionInputAdapter`.
- Implement `takeSnapshot()`:
    - Read `NavigationState` fields.
    - Validate presence of mandatory data (path, start/end).
    - Map `AStarPath` and `NavigationState` values to `NavigationSessionSnapshot`.

### 4. Implement Module 5: RoutePathLayer
- Create `com.example.mallar.ar.RoutePathLayer`.
- Constructor accepts a `NavigationSessionSnapshot` and a reference to `MallGraph`.
- Initialize internal state by resolving node IDs to full `GraphNode` data via `MallGraphRepository`.
- Implement `recalculate(currentPositionNodeId: Int)`: Calls `MallGraphRepository.aStarByNodeId` and updates internal state.
- Expose methods to get the full polyline and specific node metadata.

### 5. Technical Validation (Unit Tests)
- **Snapshot Immutability**: Test that changing `NavigationState` after `takeSnapshot()` does not affect the produced snapshot.
- **Single Read Guarantee**: Use a spy or instrumentation to verify `NavigationState` is accessed only during `takeSnapshot()`.
- **Route Equivalence**: Verify `RoutePathLayer`'s polyline matches the original `AStarPath` node coordinates.
- **Recalculation Logic**: Verify `recalculate()` updates the internal route without touching global state.

## Risks

| Risk | Mitigation |
|---|---|
| `NavigationState` fields are null | Add defensive checks and logging in `NavigationSessionInputAdapter`. Return a failure state if mandatory data is missing at the moment of snapshot. |
| Inconsistency between `AStarPath` and Graph | Ensure the snapshot holds node IDs and re-resolves them against the current `MallGraph` instance in the Route/Path Layer. |
| Global state mutation during AR startup | The snapshot must be taken at the precise moment AR navigation enters its initialization lifecycle to minimize race conditions. |

## Validation Strategy
Per `AR_Testing_and_Validation_Plan.md` §3:
1. **Single-read guarantee**: Instrument every read of `NavigationState` in a simulated session start. Expect exactly one read.
2. **Route equivalence**: Compare `RoutePathLayer` output against `MallGraphRepository.aStarByNodeId` output for identical parameters.
3. **Snapshot Immutability**: Modify `NavigationState` fields post-snapshot and assert no change in the snapshot's data.

## Rollback Strategy
Phase 2 introduces new files in a new package.
- **Rollback**: Delete the `com.example.mallar.ar` package (or the specific Phase 2 files) and revert any experimental integration points in `MainActivity` or ViewModels if any were added for testing.

## Completion Criteria
- [ ] `NavigationSessionSnapshot` and `RouteNodeMetadata` models are defined.
- [ ] `NavigationSessionInputAdapter.takeSnapshot()` verifiably performs a one-time read of `NavigationState`.
- [ ] `RoutePathLayer` correctly represents the facility-coordinate route.
- [ ] `RoutePathLayer.recalculate()` correctly invokes the existing pathfinding engine.
- [ ] All Phase 2 unit tests pass successfully.
- [ ] Project compiles successfully.
- [ ] **Independent Review performed and recorded.**
