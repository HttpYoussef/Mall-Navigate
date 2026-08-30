# Phase 4–6 Device Validation Defect — Legacy Overlay Not Actually Removed

**Status:** Blocking defect discovered during Phase 5/6 Human Device Validation. This is not a phase acceptance report and carries no phase verdict — it documents a direct contradiction between device evidence and specific, previously-verified claims made in the Phase 4, 5, and 6 implementation reports.

---

## What the Human Reviewer Observed

Two screenshots from a live device session (Samsung Galaxy S22 Ultra) show the AR navigation screen displaying: a screen-space directional arrow (pointing "Turn right in 117m" / "Turn left in 117m"), overlaid on the live camera feed, with a persistent destination card at the bottom ("Abdul Samad Al Qurashi · 117m · 1 min walk"). This visual — a flat, screen-locked arrow with a turn-distance label — matches the pre-redesign legacy overlay this entire AR subsystem project exists to replace, not any output described in the Phase 4–6 reports (a single static sphere in Phase 4/5, bounded cyan/amber anchor markers in Phase 6).

Directly asked whether this was a one-time or historical observation, the reviewer confirmed: **the legacy arrow and the new sphere/marker content were both visible during the same sessions, at the same time**, for as long as the reviewer has been testing this work — not a regression introduced by a specific round, and not something that appeared only after the legacy content was supposedly removed.

---

## Why This Directly Contradicts Prior Verified Claims

This is not a new, previously-unmade claim being second-guessed. It is a direct contradiction of specific statements this reviewer independently verified against shown source code:

- **Phase 4 Implementation Report:** "Integrated ArSceneView: Hosted the modern Filament-based rendering surface within the live navigation screen, replacing the deprecated `PreviewView` and `CameraOverlayManager`." This reviewer verified, from shown `ArSceneViewWrapper.kt` and `UnifiedNavigationScreen.kt` source across two correction rounds, that `CameraOverlayManager` instantiation and the `PreviewView` `AndroidView` were removed from the file.
- **Phase 6 Implementation Report:** "The Phase 4 diagnostic cube is no longer created in the live Camera-mode path; its diagnostic source remains available." Framed as the *only* remaining diagnostic content being retired in this round — implying the legacy overlay question was already closed as of Phase 4.
- **Phase 6 Revision Log, "Scope and frozen-file audit":** "No... new localization/camera architecture was introduced" — implicitly asserting the rendering surface itself is the single, already-established ArSceneView path from Phase 4 onward.

Two independently-drawn visual systems being on screen simultaneously is not consistent with any of the above. Something in this chain of claims does not match what is actually installed and running on the device — either the removal shown in the Phase 4 excerpts was incomplete (e.g., removed from one composition path but not another, or removed from instantiation but not from the actual view hierarchy), the excerpts shown across Phase 4–6 do not represent the full, current state of the relevant files, or a build/flavor mismatch is presenting an older code path. This reviewer cannot determine which from the evidence available and is not speculating further — this is Gemini's investigation to perform, not this reviewer's to guess at.

---

## Required Investigation and Response

This is not a request to "make the old arrow go away" as a UI fix. It is a request to establish, with direct evidence, why a component that was reported and independently verified as removed is still rendering on the device, because that gap — a false-negative in the review process itself — is more serious than any single rendering defect found so far in this project.

Gemini must:

1. **Directly confirm, by searching the actual current repository state**, whether `CameraOverlayManager`, `CameraOverlayView`, `OverlayProjectionEngine`, or `OverlayNavigationEngine` are instantiated, composed, or rendered anywhere in the live navigation flow reachable from `UnifiedNavigationScreen` — not by re-quoting the same excerpt shown in the Phase 4 report, but by a fresh, current check.
2. **If any of the above is still present**, identify precisely why the Phase 4 report's shown removal did not fully take effect — for example, a second composition path, a conditional that doesn't cover all cases, or a separate legacy screen still being reached instead of the current one.
3. **If none of the above is present**, identify what *is* actually producing the screen-space arrow and turn-distance UI shown in the screenshots — it may be a different, still-active component (e.g., a 2D `ArDirectionOverlay`-style HUD element, which the Phase 4 execution plan explicitly named as intentionally "Preserved... alongside the 3D view") rather than the fully deprecated pipeline. If this is the case, state that plainly and explain why it was not distinguished from the deprecated overlay in any prior report, since the visual in the screenshots — a directional arrow with distance-to-turn — reads as substantially more than a simple preserved 2D indicator.
4. **Confirm what should currently be visible** in the live app when AR mode is active and tracking is good, per the actual current state of the anchor-rendering work from Phases 5–6, so the human reviewer has a concrete, specific expectation to test against rather than "a sphere" from memory of an earlier phase.
5. Submit findings as a `Phase_4_6_Overlay_Investigation_Report.md`, with the actual current source of the relevant composition logic shown in full — not excerpted, not described — given that excerpted evidence is exactly what did not prevent this contradiction from going unnoticed across three phases.

---

## Other Validation Results From This Session, Recorded for the Record

- **Camera↔Map↔AR cycling:** performed multiple times by the human reviewer directly, no crash observed. This specific finding (the `ArCoreSessionManager`/`ManagedARSceneView` fix from the prior round) is accepted as confirmed.
- **Tracking degradation:** did not occur during this session; no data either way.
- **Brief lag observed immediately before switching from AR to Map mode.** Not yet characterized as consistent or one-off — needs a follow-up confirmation (does it happen on every switch, or was it a single occurrence) once the overlay question above is resolved, since the lag and the overlay question may or may not be related.

---

## Status

**Phase 5 and Phase 6 remain unaccepted.** This finding does not reopen the specific code-level verifications already independently confirmed in the prior review round (the `ReFixScheduler` single-flight logic, the `ManagedARSceneView` visibility-gate fix, the anchor-window planner) — those remain verified on their own terms. It does mean the device-level picture those phases were supposed to produce has not actually been confirmed, and the reason why needs to be established before either phase, or Phase 7, proceeds.
