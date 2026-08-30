# Phase 7 Execution Plan Review — MallAR AR Subsystem

**Subject:** `Phase_7_Execution_Plan.md`
**Compared against:** frozen architecture, Engineering Specification, Roadmap, and the accepted Phase 5/6 state (including its full defect history).

---

## Assessment

Scope and exclusions are correct: full chevron/turn-marker visuals, render-level pose smoothing, and floor-plane-confidence fallback are exactly Phase 7's mandate, and Transition Mode, Arrival, deviation logic, and overlay deletion are correctly deferred. `RenderPoseSmoother` is explicitly and correctly stated as not touching anchor coordinates — separation from Module 6's correction smoothing is preserved, not blurred.

The floor-plane fallback design is a direct, well-applied lesson from Phase 6: it reuses `resolveFloorHeight`-style plane querying rather than reintroducing a hardcoded elevation, which is exactly the class of bug (Defect 4) that phase's device testing found.

Two required corrections.

**1. The chevron heading formula risks reintroducing a coordinate-frame bug of the same shape as Phase 6's Defect 3.** `θ = atan2(X[i+1] - X[i], Z[i+1] - Z[i])` is not stated as being computed from `transform.worldPositionFor(...)` outputs (the fix that resolved Defect 3) or from raw facility coordinates. Phase 6's entire multi-round defect chain came from exactly this ambiguity — mixing facility-space and ARCore-world-space math without being explicit about which frame a value is in. This plan must state explicitly that chevron orientation is computed from already-converted world-space node positions, not facility pixel coordinates, before implementation begins.

**2. No numeric acceptance criterion for pose-smoothing effectiveness.** The Testing & Validation Plan requires "measurable variance reduction... verified by comparison" for this exact deliverable. "Confirm zero micro-shimmering" (device protocol) and "verify measurable variance reduction... without phase lag" (unit test) are both currently unquantified. State a concrete comparison method (e.g., recorded pose variance with filter on vs. off over an identical motion sample) before this is implementation-ready.

---

## Final Decision

**APPROVED WITH REQUIRED CHANGES**

**May Gemini begin Phase 7 implementation? NO**

Mandatory corrections: (1) explicitly state the coordinate frame chevron heading is computed in, confirming it uses world-space positions, not facility coordinates; (2) define a concrete, measurable pose-smoothing variance comparison method. Both are corrections to stated detail, not to the plan's design.
