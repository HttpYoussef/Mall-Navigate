# MallAR — Agent Operating Rules

## Purpose

This document defines the operating rules for any AI coding agent working on the MallAR project.

These rules apply to Codex, Gemini, Claude-assisted workflows, or any other coding agent.

The agent must read and follow this document before modifying the project.

---

# 1. Source of Truth

The repository documentation is the primary source of truth.

Before making changes, inspect the relevant documentation under `docs/`.

For AR work, the documentation under `docs/AR/` is authoritative.

Do not replace documented architectural decisions with assumptions based only on the current code.

When documentation and implementation appear inconsistent:

1. Identify the inconsistency.
2. Determine which document governs the current phase.
3. Do not silently redesign the architecture.
4. Stop and report the conflict if it requires an architectural decision.

---

# 2. Architecture Is Frozen

Approved architecture must be treated as frozen.

An agent must NOT:

- Redesign approved architecture.
- Introduce new architectural patterns without authorization.
- Replace approved libraries or technologies without justification.
- Change subsystem boundaries.
- Move responsibilities between components arbitrarily.
- Implement future architectural concepts early.

If implementation appears impossible without changing the architecture:

STOP and report the architectural conflict.

Do not improvise.

---

# 3. Phase Discipline

Development is performed phase by phase according to the approved roadmap.

The agent must:

- Work only on the current authorized phase.
- Respect phase boundaries.
- Never implement future-phase functionality prematurely.
- Never expand scope simply because an improvement seems useful.
- Preserve functionality from previously accepted phases.

The current phase must always be established before implementation begins.

---

# 4. Understand Before Modifying

Never modify code immediately after receiving a task.

First:

1. Read the relevant documentation.
2. Understand the current implementation.
3. Trace the relevant data/control flow.
4. Identify the affected components.
5. Determine the root cause or required behavior.
6. Determine how the change will be validated.

For bugs:

**Understand → Trace → Identify Root Cause → Plan → Implement → Validate**

Do not patch symptoms without understanding the cause.

---

# 5. Planning Gate

For every major roadmap phase:

1. Create an execution plan.
2. Review the plan against the architecture and documentation.
3. Obtain approval.
4. Implement only after approval.

Do not skip the planning gate for major subsystem work.

For small corrective changes within an already-approved phase, use engineering judgment while remaining within the approved scope.

---

# 6. Implementation Rules

When implementation is authorized:

- Make the smallest correct change.
- Preserve existing behavior outside the task.
- Avoid unrelated refactoring.
- Avoid speculative improvements.
- Keep changes modular and reviewable.
- Follow existing project conventions.
- Prefer clear and maintainable code over clever code.
- Do not duplicate existing functionality.
- Reuse existing components where appropriate.

Every change must have a reason connected to the current requirement.

---

# 7. Dependencies

Never add a dependency simply because it is convenient.

Before adding one:

1. Check whether the project already provides the required capability.
2. Determine whether the dependency is genuinely necessary.
3. Verify compatibility with the project.
4. Explain why it is required.
5. Add only what is necessary for the authorized scope.

Do not silently upgrade major project dependencies or toolchains unless required and justified.

---

# 8. Testing and Validation

A successful build does NOT automatically mean the feature is correct.

Validation must match the nature of the requirement.

### Automated validation

Use when appropriate:

- Unit tests
- Instrumentation tests
- Integration tests
- Build verification
- Static analysis
- Logs and runtime assertions

### Human/device validation

Required when behavior depends on:

- Camera behavior
- AR tracking
- World locking
- Physical positioning
- Visual rendering
- Real device sensors
- Real-world interaction

Never claim that automated tests prove physical AR behavior.

If human validation is required, clearly state what the human must test and what evidence should be captured.

---

# 9. Evidence Over Claims

Do not declare something "working" merely because:

- The code compiles.
- A test exists.
- A test passed without verifying what it tests.
- The implementation report says it works.

Claims must be supported by appropriate evidence.

For implementation tasks, report:

- What changed.
- Why it changed.
- What was tested.
- What passed.
- What remains unverified.

---

# 10. Human-in-the-Loop

Some project requirements require physical verification by the developer.

When human validation is required:

- Do not fabricate results.
- Do not claim the test was performed.
- Clearly identify the required human test.
- Provide reproducible test steps.
- State what evidence should be captured.

The human developer is the authority for physical/device behavior that the agent cannot directly verify.

---

# 11. Review and Acceptance

Implementation is not automatically accepted when the agent finishes.

The workflow is:

```text
Approved Plan
    ↓
Implementation
    ↓
Build
    ↓
Automated Validation
    ↓
Human Validation (when required)
    ↓
Engineering Review
    ↓
Accepted / Corrections Required
```

If corrections are requested:

- Read the review completely.
- Understand the root cause.
- Fix only the required issues.
- Revalidate.
- Do not start the next phase until acceptance.

---

# 12. Handling Review Rejections

Never repeatedly patch a rejected implementation without understanding the rejection.

For every finding:

1. Understand the finding.
2. Identify the root cause.
3. Determine the required behavior.
4. Implement the correction.
5. Validate the correction.
6. Verify no regression.
7. Report the evidence.

If a finding cannot be resolved without changing frozen architecture:

STOP and escalate.

---

# 13. Documentation

Engineering decisions and important implementation results should be documented.

Do not create unnecessary documentation.

Prefer updating the relevant existing project documentation rather than creating duplicate documents.

Documentation should explain:

- What was decided.
- Why it was decided.
- What was implemented.
- What was validated.
- What remains unresolved.

---

# 14. Git Discipline

Do not perform destructive Git operations unless explicitly authorized.

Never:

- Delete unrelated work.
- Reset or revert unrelated changes.
- Force-push.
- Rewrite history.
- Remove files merely because they are inconvenient.

Keep changes attributable to the current task.

Before finishing a task, clearly identify the files changed.

---

# 15. Stop Conditions

STOP and ask for clarification when:

- The requirement is ambiguous.
- Documentation conflicts with itself.
- The architecture would need to change.
- The required behavior belongs to another phase.
- A dependency decision has significant architectural consequences.
- A test cannot validate an important requirement.
- A required human/device test cannot be performed by the agent.
- Continuing would require assumptions that are not supported by the documentation.

Do not invent decisions to keep moving.

---

# 16. Phase Completion

A phase is complete only when:

- All authorized objectives are implemented.
- Required validation is complete.
- No known mandatory blocker remains.
- Documentation is updated where necessary.
- The implementation remains within scope.
- Engineering acceptance has been obtained.

Never begin the next phase simply because the code compiles.

---

# 17. Agent Communication

When reporting work, be concise and factual.

Use:

### Completed
What was actually implemented.

### Validation
What was actually tested and the result.

### Remaining
Anything not verified or unresolved.

### Blockers
Anything preventing continuation.

Do not exaggerate confidence.

Do not hide failures.

Do not claim completion without evidence.

---

# 18. Current Project Workflow

The standard development workflow is:

```text
Documentation
    ↓
Phase Execution Plan
    ↓
Plan Review / Approval
    ↓
Implementation
    ↓
Automated Validation
    ↓
Human Validation (if required)
    ↓
Engineering Acceptance
    ↓
Git Milestone
    ↓
Next Phase
```

This workflow must not be bypassed for major phase work.

---

# 19. Final Principle

The objective is not to produce code quickly.

The objective is to produce code that is:

- Architecturally correct.
- Within scope.
- Testable.
- Maintainable.
- Evidence-backed.
- Safe to build upon.

When uncertain:

**Do not guess. Read the documentation, inspect the implementation, identify the uncertainty, and escalate it.**
