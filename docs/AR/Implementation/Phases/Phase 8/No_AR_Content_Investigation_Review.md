# No AR Content Investigation Report — Review

**Subject:** `No_AR_Content_Investigation_Report.md`
**Decision:** Diagnosis and fix conditionally accepted on code merits. Device confirmation is not yet established and is required before this defect is closed.

---

## What Is Accepted

The unification of Hypothesis 1 and Hypothesis 2 under a single root cause — `startPlace.id` (a shop ID) being passed where a graph node ID was required — is a coherent, well-reasoned diagnosis, not a guess. It correctly explains both possible on-device symptoms as two branches of the same bug depending on whether a node happened to exist at the colliding ID, rather than picking one hypothesis and ignoring the other. The fix (`path.nodeIds.firstOrNull() ?: startPlace.id`) directly targets the actual mismatch and is a minimal, correctly-scoped change.

---

## What Is Not Yet Accepted

**No part of this report confirms the fix was reproduced on a physical device.** Section 3 contains only unit-test and build-log evidence. Given this project's history, a passing test suite confirms the code is internally self-consistent — it does not confirm anchors are actually visible in a real AR session, which is the entire question this investigation was opened to answer.

**One specific concern needs a direct answer, not an inference:** the diagnostic log lines in §2.2 (`node=142`, `node=143`, facility coordinates `(1200.0, 850.0)` / `(1200.0, 760.0)`) use numbers that closely match the *hypothetical* walkthrough numbers used earlier in §1 to illustrate Case B. This is presented in a section titled "Diagnostic Instrumentation," which reads as describing what the logging *will* look like once run, not as a captured excerpt from an actual run. If these are illustrative sample values rather than real captured output, that must be stated plainly — presenting illustrative log text in the same format as evidence, without labeling it as illustrative, is exactly the ambiguity this project has had to correct more than once already.

---

## Required Before This Defect Is Closed

1. **State directly whether the log lines in §2.2 are real, captured device output or illustrative examples of the logging format.** If illustrative, they should be relabeled as such and replaced with, or supplemented by, an actual capture.
2. **Reproduce on the physical device**, at home, exactly as the original report ("no arrows, only white dots") — successful scan, enter navigation, and report whether an anchor now appears at the user's feet as the fix's own stated design predicts.
3. **Human confirmation, directly, in the human reviewer's own words**, of what was actually seen — not a report's account of what was seen.

The diagnosis and fix are not being rejected — they are well-reasoned enough to proceed to a real device test on their strength. What's missing is the one thing this entire investigation exists to establish: that AR content is now actually visible on the device that reported it wasn't.
