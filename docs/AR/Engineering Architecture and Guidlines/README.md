# AR Subsystem Overview

This folder contains the complete architecture record for MallAR's AR navigation subsystem — the component responsible for rendering floor-attached, world-locked directional guidance (arrows and turn markers) that stay visually glued to the physical floor as the user walks, in the manner of Google Maps Live View, but indoors.

The subsystem uses a two-tier hybrid design: ARCore's native visual-inertial odometry, plane detection, and anchors provide continuous, every-frame local tracking and rendering; the mall's existing store-signage recognition capability provides occasional, event-driven correction to keep that tracking grounded in the facility's real-world graph. It requires no new physical infrastructure and no backend dependency.

This document is the entry point for anyone — human or AI — working on this subsystem. Read it before opening any other file in this folder.

---

# Documentation Structure

| Document | Responsibility | Status |
|---|---|---|
| `AR_Subsystem_Redesign.md` | Original research and first-principles architecture proposal (v1): technology comparison, candidate approaches, and the initial seven-layer design. | Historical — superseded |
| `AR_Architecture_Stress_Test.md` | Independent adversarial engineering review of v1. Identifies Critical/High/Medium/Low risks, hidden assumptions, and missing design decisions. | Historical — review record |
| `AR_Subsystem_Redesign_v2.md` | Revised architecture (v2) resolving every finding raised in the stress test, with a full resolution matrix. | Historical — superseded |
| `AR_Architecture_Approval_Report.md` | Independent approval review of v2, verified directly against the live repository. Approved the internal design; identified five mandatory integration-boundary findings. | Historical — review record |
| `AR_Subsystem_Redesign_Final.md` | Final architecture resolving all mandatory findings from the approval report. Self-contained; assumes no prior document has been read. | **Current — source of truth** |

Each document states its own place in this sequence in its header. None of the historical documents should be edited going forward — see Folder Maintenance Rules.

---

# Recommended Reading Order

**Required:**
1. `AR_Subsystem_Redesign_Final.md` — this is self-contained and sufficient on its own for implementation. Nothing else in this folder needs to be read to begin work.

**Optional, for historical context (why the architecture looks the way it does):**
2. `AR_Subsystem_Redesign.md`
3. `AR_Architecture_Stress_Test.md`
4. `AR_Subsystem_Redesign_v2.md`
5. `AR_Architecture_Approval_Report.md`

Read the optional chain only if you need to understand the reasoning behind a specific decision, are reviewing whether a past finding was resolved correctly, or are onboarding into an architect/reviewer role for this subsystem rather than an implementer role.

---

# Source of Truth

**`AR_Subsystem_Redesign_Final.md` is the sole authoritative architecture for this subsystem.**

It has already passed two rounds of independent review — an adversarial stress test and a repository-verified approval review — and incorporates the resolution of every finding from both. No other document in this folder, including its own predecessors, should be treated as authoritative for implementation. If any historical document appears to conflict with the Final document, the Final document governs.

---

# Development Workflow

```
Architecture (this folder)
    ↓
Implementation Specification
    ↓
Implementation Roadmap
    ↓
Implementation
    ↓
Testing
```

- **Architecture** — this folder. Defines *what* the system is: layer responsibilities, ownership boundaries, state machines, and policies. Ends at `AR_Subsystem_Redesign_Final.md`.
- **Implementation Specification** — a separate document (not part of this folder) that translates the Final architecture into concrete Android-level interfaces, class boundaries, and file-level responsibilities.
- **Implementation Roadmap** — a separate document sequencing the specification into milestones and reviewable units of work.
- **Implementation** — actual code, written strictly against the Implementation Specification, which itself must trace back to the Final architecture with no deviation.
- **Testing** — validates the implementation against the architecture's own stated policies and numeric targets (state-machine transitions, performance targets, failure-recovery behavior, etc.), not against ad hoc criteria invented during implementation.

**No architectural decisions may be made during implementation.** If an implementer encounters a situation the Final architecture does not cover, or believes a specified decision is wrong, that is not something to resolve in code. It is a signal to trigger the Versioning Policy below — not a reason to quietly diverge from the document.

---

# Versioning Policy

- `AR_Subsystem_Redesign_Final.md` is **frozen** once approved. Implementation must not silently reinterpret, work around, or override it.
- **Minor implementation improvements** — decisions that fit entirely within a boundary the architecture already defines (for example, fine-tuning a fade duration within the "short, defined duration" the architecture already specifies) do not require a new architecture version and do not require this folder to be touched.
- **Major design changes** — anything that alters a layer's responsibility, an ownership boundary, a state-machine transition, a mandatory policy (the Fix Validation Gate's criteria, the drift-vs-deviation rule, the integration contract with the existing application), or a numeric target stated as an architectural parameter rather than a tuning detail — require a **new architecture version**, not an edit to the Final document in place.
- A new major version must go through the same process that produced the current one: draft the revision → independent adversarial stress test → resolve findings → repository-verified approval review → resolve findings → new Final document.
- The new Final document becomes the sole source of truth. The previous Final and every review document that led to it are archived, not deleted, per the Folder Maintenance Rules below.

---

# Folder Maintenance Rules

- **Adding documents:** new files belong at the root of `docs/AR` only when they are part of an active architecture revision cycle — a new draft, a new stress test, a new approval review, or a new Final document. Downstream artifacts (implementation specs, roadmaps, test plans) belong in their own sibling folders, not here, so this folder stays scoped to architecture only.
- **Archiving:** when a new Final document is produced, the previous Final and every review document that led to it are moved into a `docs/AR/archive/` subfolder, preserved in full. Nothing is deleted — the reasoning trail must remain auditable, since each Final document's own provenance section depends on being able to point back to the reviews that shaped it.
- **Naming:** draft and review documents keep their descriptive names as produced (e.g., `_Stress_Test`, `_Approval_Report`, `_v2`). The authoritative document is always named `AR_Subsystem_Redesign_Final.md`, so that anyone — human or AI — can always find current truth at a fixed, predictable path without needing to know the version history first.
- **No retroactive edits:** once a document is archived, it is not edited again. If something in an archived document is later found to be wrong or incomplete, that is corrected in the current Final document (or addressed in a new version), never by rewriting an archived record.
