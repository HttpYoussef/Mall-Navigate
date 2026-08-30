# AR Implementation Playbook — MallAR AR Subsystem

**Status:** Authoritative engineering operating manual for implementation. Governs *how* work is performed. It does not define *what* is built — that is fixed by `AR_Subsystem_Redesign_Final.md` (architecture), `AR_Engineering_Specification.md` (engineering contract), `AR_Implementation_Roadmap.md` (execution order), and `AR_Testing_and_Validation_Plan.md` (verification). None of those four documents is modified, reinterpreted, or extended by anything in this playbook.
**Audience:** Every human engineer and every AI implementation agent working on this subsystem, without distinction — the rules in this playbook apply identically to both.

---

## 1. Purpose

Four frozen documents already define everything about this subsystem except how the work of actually building it is conducted day to day: the architecture defines what the system is, the Engineering Specification defines what must exist, the Implementation Roadmap defines in what order, and the Testing & Validation Plan defines how each piece is proven correct. None of them defines the operating discipline that keeps a real, multi-session, possibly multi-agent implementation effort from drifting away from all four of them over time.

This playbook is that operating discipline. Its role in the engineering process is to sit above the four frozen documents as the process layer that governs every individual work session, commit, and review — ensuring that following the roadmap and satisfying the testing plan actually happens the way both were designed to be followed, rather than being reinterpreted informally as implementation proceeds.

---

## 2. Engineering Workflow

Every unit of implementation work follows the same six-step cycle, once per roadmap phase, with no step skipped and no step reordered.

```
Preparation
    ↓
Phase Implementation
    ↓
Validation
    ↓
Review
    ↓
Commit
    ↓
Next Phase
```

- **Preparation.** Confirm every prerequisite phase (per the Implementation Roadmap's dependency graph) has a recorded Engineering Sign-Off (Testing & Validation Plan §12) — not merely that it "looks done." Re-read that phase's specific entries in the Roadmap and the Testing & Validation Plan's Phase Validation Matrix (§3.6 below explains why this re-read is required even for engineers who already read every document once at onboarding). Confirm no open escalation (§9) currently blocks this phase's Components.
- **Phase Implementation.** Implement exactly the scope defined for this one phase — the Components, Scope (included/excluded), and Dependencies stated in that phase's Implementation Roadmap entry, nothing more and nothing less. No other roadmap phase's work is touched during this step.
- **Validation.** Execute this phase's full Phase Validation Matrix entry (Testing & Validation Plan §3) and its Regression Validation minimum set (Testing & Validation Plan §9), recording evidence for each — not just running them and moving on.
- **Review.** Self-review against the Phase Completion Checklist (Testing & Validation Plan §10), then independent review per §7 of this playbook. Self-review is necessary and never sufficient on its own.
- **Commit.** Per the Git Workflow (§6 of this playbook) — a phase's work lands as a single, complete, reviewable, phase-scoped unit in the shared integration branch, per the Implementation Roadmap's own incremental-integration rule.
- **Next Phase.** Begins only once Engineering Sign-Off (Testing & Validation Plan §12) is recorded for the just-completed phase, and only for whichever phase the Implementation Roadmap's dependency graph now unblocks — never chosen at the implementer's discretion.

---

## 3. Required Reading Order

**Before any implementation work begins on this subsystem, in this exact order:**

1. `AR_Subsystem_Redesign_Final.md`
2. `AR_Engineering_Specification.md`
3. `AR_Implementation_Roadmap.md`
4. `AR_Testing_and_Validation_Plan.md`
5. This document (`AR_Implementation_Playbook.md`)

**Why this order matters.** Each document assumes the reader already holds the context of the ones before it. The Engineering Specification cannot be understood correctly without the architecture's layer/module ownership model already in mind. The Implementation Roadmap's phase sequencing only makes sense against the Engineering Specification's module boundaries. The Testing & Validation Plan's acceptance criteria are written as verifications of the Roadmap's phases, not as a standalone spec. This playbook, in turn, assumes all four are already known — it governs process around content it does not itself restate.

**This extends, rather than replaces, the general reading guidance in `docs/AR/README.md`.** The README's guidance ("read only the Final architecture; the rest is optional historical context") is written for someone trying to *understand* the subsystem. It does not apply to someone about to *implement* it — implementation requires all four frozen documents above, in full, not just the architecture. Anyone about to write code who has only read the Final architecture has not yet satisfied this playbook's reading requirement.

**A second, narrower reading pass is required before each individual phase**, in addition to the one full pass required at onboarding: re-read that specific phase's entries in the Implementation Roadmap and the Testing & Validation Plan's Phase Validation Matrix immediately before beginning that phase's Preparation step. This is not redundant with the onboarding pass — it exists because implementation sessions are frequently separated in time (across days, across engineers, across AI agent sessions with no persistent memory of prior sessions), and a phase begun from memory rather than from the current text of the frozen documents is the single most common way small, undetected drift enters an implementation.

---

## 4. Implementation Rules

**The Implementation Roadmap's own twelve Implementation Rules remain fully in force and are not superseded by anything below.** The rules in this section are additional, process-level rules specific to how implementation sessions are conducted, not a replacement for the roadmap's rules.

1. **Never modify the frozen architecture, Engineering Specification, Implementation Roadmap, or Testing & Validation Plan during implementation**, under any circumstance, for any reason, including a change that seems obviously correct or trivial. See Escalation Rules (§9).
2. **Never introduce an architectural decision during implementation.** If a choice would alter a module's responsibility, ownership, interface, the runtime state machine, or a mandatory policy, it is not an implementation decision, regardless of how small it appears.
3. **Never combine multiple roadmap phases into a single implementation session.** One phase, fully completed (through Engineering Sign-Off) before the next phase's Preparation step begins.
4. **Complete only one roadmap phase at a time.** This applies per-engineer and per-agent — an individual implementer does not work ahead on a later phase's code while an earlier phase awaits sign-off, even if the later phase's work seems independent.
5. **The project must compile before progressing** — not "before the phase is marked complete," but continuously, at every commit within a phase's work, per the Implementation Roadmap's Build Strategy.
6. **Every completed phase must satisfy the Testing & Validation Plan in full** — a phase is not complete because it builds and appears to work; it is complete only when Engineering Sign-Off (Testing & Validation Plan §12) is recorded.
7. **Preserve subsystem boundaries exactly as defined in the Engineering Specification's Module Responsibilities (§5 of that document).** A module's "Never Does" list is a hard constraint on the implementation, not a stylistic preference.
8. **Respect module ownership.** No module's implementation writes to state the Engineering Specification assigns to a different module's exclusive ownership, under any circumstance, including convenience or apparent efficiency gains.
9. **Avoid unnecessary refactoring outside the current phase's declared scope.** Discovering an unrelated improvement while implementing a phase is not license to make it inside that phase's commit — see §6 (Git Workflow) on commit scope.
10. **No phase's code may be written before every prerequisite phase has a recorded Engineering Sign-Off** — an informal sense that a prerequisite "is basically done" does not satisfy this rule.
11. **The same individual or agent that implements a phase does not also perform that phase's independent review.** Self-review (§2, §7) is mandatory and is not a substitute for independent review — this is a separation-of-duties rule, not a courtesy.
12. **Any shortcut taken for implementation expediency must be explicitly flagged and tied to a tracked follow-up**, never introduced silently. See Engineering Quality Standards (§11).

---

## 5. AI Agent Operating Rules

These rules apply to any AI agent performing implementation work on this subsystem, without exception, and are not advisory — they are enforced with the same weight as §4.

**What AI agents are allowed to decide:**
- Implementation-level choices entirely internal to a single module's own code, within a boundary the Engineering Specification has already fully fixed (internal code organization, naming, helper decomposition, choice among functionally-equivalent standard approaches).
- The specific value used for a numeric parameter, *within* a range a frozen document already states as a target (for example, selecting a specific value within the specified 3–5 second recognition throttle range) — never the choice of which mechanism or parameter governs a behavior.
- The specific inputs/fixtures used when executing a Testing & Validation Plan procedure (for example, specific test coordinates), provided the procedure's defined structure is followed exactly as written.

**What AI agents must never decide:**
- Any change to a module's responsibility, ownership, or interface, as defined in the Engineering Specification.
- Any change to the runtime state machine — no new state, no new transition, no altered transition condition, regardless of how well-motivated it appears in the moment.
- Any resolution of a specification gap by inventing behavior to fill it. A gap is escalated (§9), never silently filled, no matter how small or how confident the agent is in the "obvious" answer.
- Any decision about which mechanism governs a behavior (for example, whether re-fix triggering is proximity-gated or vision-triggered) — this is architecture, even when the alternative appears to work better in practice.
- Its own Engineering Sign-Off. An agent may execute validation procedures and assemble the resulting evidence, but sign-off is granted through the process in Testing & Validation Plan §12 and this playbook's §7 — never self-certified.
- Combining, reordering, or skipping roadmap phases for its own convenience or perceived efficiency.
- Any edit to the frozen documents or to this playbook itself, including edits framed as clarification, correction, or formatting cleanup.

**When implementation must stop, immediately, mid-session if necessary:**
- The agent encounters a behavior, component, or scenario the frozen documents do not cover.
- A frozen document's stated requirement cannot be satisfied as written — a genuine impossibility, not mere implementation difficulty.
- Two frozen documents, or two passages within the same document, appear to require incompatible things.
- A Testing & Validation Plan procedure's Failure Criteria is met and cannot be resolved by a straightforward bug fix within the phase's already-declared scope.

**When clarification must be requested (distinct from escalation):** when the frozen documents are internally consistent and complete, but their application to a specific situation the agent has encountered is not obvious from the text alone. Clarification is resolved by a human (or a designated architect role) reading the same frozen text and providing an interpretation — it does not produce new content in any frozen document, and it does not require the versioning process in §12.

**When architectural escalation is required (distinct from clarification):** when the frozen documents are genuinely silent, contradictory, or incorrect for the situation encountered — not merely unclear. This is the condition described throughout §9 below, and it may ultimately require a new architecture version through the process defined in `docs/AR/README.md`'s Versioning Policy. An agent's role in this situation is to stop, document the specific gap or conflict with precise references to the frozen text, and route it per §9 — never to guess which of the two possibilities (clarification or escalation) applies and proceed on that guess.

---

## 6. Git Workflow

- **Branch usage.** One branch per roadmap phase. A branch is never created to span more than one phase, and a phase's branch is not merged into the shared integration branch until that phase's Engineering Sign-Off is recorded.
- **Commit frequency.** Small, frequent, logically atomic commits within a phase's branch during active work — a phase's implementation is not a single monolithic commit. This is compatible with, not contradictory to, the Implementation Roadmap's incremental-integration principle: many small commits accumulate on the phase branch, and the branch merges into the shared integration branch as one complete, reviewable unit only once sign-off is obtained.
- **Commit scope.** No commit spans more than one roadmap phase. No commit mixes a phase's declared-scope work with unrelated changes, however small (see Implementation Rule 9).
- **Commit message quality.** Every commit references the roadmap phase by name and number, and describes what changed in terms traceable to that phase's Components and Scope. The commit that completes a phase additionally references where its recorded validation evidence and sign-off can be found.
- **Phase completion commits.** Each phase's merge into the shared integration branch is a single, distinctly identifiable event ("Phase N complete, signed off") — this is the traceable marker that Preparation (§2) and the Implementation Roadmap's dependency graph rely on to confirm a prerequisite phase is genuinely finished, not just informally believed to be.

---

## 7. Code Review Workflow

- **Self-review.** The implementer (human or AI agent) works through the Phase Completion Checklist (Testing & Validation Plan §10) themselves, with recorded evidence for every item, before requesting independent review. This step is mandatory and is never treated as equivalent to independent review.
- **Validation checklist.** The full Phase Validation Matrix entry for the phase (Testing & Validation Plan §3) is executed and its results recorded — described, not merely asserted.
- **Regression check.** The phase's Regression Validation minimum set (Testing & Validation Plan §9) is re-executed with fresh evidence specific to this phase's completion, not carried over from an earlier point in development.
- **Human review.** An independent reviewer — someone who did not implement this phase — reviews both the code and the recorded validation evidence against the Engineering Specification's module boundaries and the phase's declared Scope. For any phase implemented by an AI agent, this review step must be performed by a human, or by a distinct process independent of the implementing agent's own reasoning — an AI agent does not satisfy this requirement by reviewing its own output, even under a different invocation, because the separation-of-duties principle (Implementation Rule 11) requires genuine independence, not merely a second pass by the same process.
- **Approval criteria.** Exactly the criteria in Testing & Validation Plan §12 — evidence-based, binary, no partial credit. This playbook does not define a separate or additional approval standard; it only operationalizes who performs the review and in what order.

---

## 8. Phase Completion Rules

A roadmap phase is complete only when every condition below holds, in this order:

1. **Compilation success** — continuous, per the Implementation Roadmap's Build Strategy, not merely at the moment of phase completion.
2. **Validation success** — the phase's full Phase Validation Matrix entry (Testing & Validation Plan §3) passed with recorded evidence.
3. **Testing success** — every applicable row in the Testing & Validation Plan's Functional, Integration, Performance, Reliability, and Edge Case Validation sections (§4–§8 of that document) that this phase's Components make newly testable has been executed and passed.
4. **Engineering review** — independent review per §7 of this playbook, completed and recorded.
5. **Documentation consistency** — confirmed that completing this phase did not require, and has not produced, any change to any frozen document. If it did, the phase is not complete; the situation is routed through Escalation (§9) instead, and the phase remains open until that resolves.
6. **Readiness for the next phase** — the Implementation Roadmap's dependency graph is consulted to determine which phase(s) this completion unblocks; Preparation (§2) for the next phase may begin only after this determination, never before it.

---

## 9. Escalation Rules

**Situations requiring an immediate stop to implementation on the affected scope:**
- **Missing engineering specification** — a behavior or component the implementer needs is not defined anywhere in the Engineering Specification.
- **Missing architecture** — a scenario encountered during implementation has no basis anywhere in the frozen architecture.
- **Undefined behaviour** — a real, encountered situation (most likely to surface during Phase 8 or Phase 9's cross-cutting scenario work) has no specified required behavior in any frozen document.
- **Architectural conflict** — two frozen documents, or two passages within one document, specify requirements that cannot both be satisfied.
- **Unexpected integration issue** — an existing system this subsystem depends on (the pathfinding engine, `NavigationSessionManager`, `DriftMonitor`, the pre-navigation scan flow, `NavigationState`) behaves differently in practice than the frozen documents assumed.
- **Specification inconsistency** — a numeric target, named condition, or module boundary appears to differ between two frozen documents.

**How these are handled, in order:**
1. Implementation on the affected phase or component halts immediately. No workaround, no local invention, no partial implementation intended to be "fixed properly later" — Implementation Rules 2 and 6 apply in full at this moment.
2. The issue is documented precisely: which frozen document(s) and section(s) are involved, and the specific evidence (a test result, a real encountered scenario, a direct textual conflict) that triggered the escalation.
3. The issue is routed to the Lead AR Systems Architect role (or whatever role/process an organization designates to hold that authority) for resolution. Resolution takes one of two forms: a **clarification**, where the existing frozen text is reaffirmed with a documented interpretation and no frozen document changes (see §5's distinction between clarification and escalation); or a **new architecture version**, following `docs/AR/README.md`'s Versioning Policy in full — draft revision, adversarial stress test, resolve findings, repository-verified approval review, resolve findings, new Final document — after which the Engineering Specification, Implementation Roadmap, Testing & Validation Plan, and this Playbook are each updated to match, per §12 below.
4. Implementation does not resume on the affected scope until the escalation is resolved and, if a new document version was required, that version is available and this playbook's Required Reading Order (§3) has been satisfied against it.

---

## 10. Definition of Done

**For a single roadmap phase**, done means all of the following, simultaneously:
- Every condition in the Phase Completion Rules (§8) is satisfied.
- Engineering Sign-Off (Testing & Validation Plan §12) is recorded, with evidence, not asserted.
- No open escalation (§9) exists against this phase's scope.

There is no partial or conditional "done" state for a phase, consistent with Testing & Validation Plan §12's own binary approval standard.

**For the complete subsystem**, done means all of the following, simultaneously:
- Final System Acceptance (Testing & Validation Plan §11) is achieved — all five readiness categories satisfied with recorded evidence.
- Every phase, 0 through 9, has an individually recorded Engineering Sign-Off.
- No escalation (§9) remains open anywhere in the implementation history.
- Every frozen document (architecture, Engineering Specification, Implementation Roadmap, Testing & Validation Plan) is verified unmodified against its approved state — this subsystem's implementation must be checkable, at completion, as having been built against exactly the frozen documents it started with, not a drifted or informally-reinterpreted version of them.

---

## 11. Engineering Quality Standards

- **Maintainability.** Every piece of implemented code is traceable to a specific module (Engineering Specification §3) and a specific roadmap phase. No code exists that does not map to a defined module's declared responsibility.
- **Modularity.** Module boundaries — Owns, Reads From, Writes To, Never Does (Engineering Specification §5) — are enforced in the actual code structure, not merely honored in intent. A module's "Never Does" constraints should be verifiable by inspection.
- **Readability.** Code within a given module should be understandable by an engineer who has read that module's Engineering Specification entry alone, without needing to reverse-engineer intent from the implementation itself.
- **Performance awareness.** Every numeric target in the Testing & Validation Plan's Performance Validation (§6 of that document) is treated as a constraint to implement toward from the start, not a number to check against after the fact and retrofit if missed.
- **Consistency.** Naming, structure, and pattern choices for one module should not gratuitously diverge from the patterns used for another module performing an analogous role, absent a reason grounded in that specific module's frozen requirements.
- **Scalability.** Implementation choices must not foreclose the Future Extension Points already named in the frozen architecture and the Engineering Specification's "MAY extend" list (§14 of that document) — for example, the Rendering Layer's future-swappability must remain genuinely real in the code, not accidentally defeated by an implementation detail that happens to hardcode an assumption the architecture explicitly reserved as changeable.
- **Technical debt avoidance.** Any shortcut taken for implementation expediency is explicitly flagged in the phase's documentation and tied to a specific, tracked follow-up — never introduced silently. This mirrors how the frozen documents themselves treat deferred concerns (for example, the long-session thermal-aware mode) as named, tracked future work rather than silent gaps, and this playbook holds implementation to the same standard.

---

## 12. Future Maintenance

- **Extending the subsystem.** Only the paths already pre-authorized in the Engineering Specification's "MAY extend" list (§14) and the frozen architecture's Future Extension Points are available without triggering a new architecture version. Anything else is a new architectural decision and follows the escalation path in §9.
- **Introducing new features.** Treated identically to extension above if the feature fits within an already-authorized MAY-extend boundary. Otherwise, it requires a new major architecture version, following `docs/AR/README.md`'s Versioning Policy in full.
- **Handling architectural changes.** Exactly the README's Versioning Policy: minor implementation-level tuning within an already-stated target range does not require a new version; any change to a module's responsibility, an ownership boundary, the state machine, or a mandatory policy requires one.
- **Creating future architecture versions.** The same five-stage process already demonstrated across this documentation's own history: draft the revised architecture, subject it to an independent adversarial stress test, resolve every finding, subject the revision to a repository-verified approval review, resolve every finding, and produce a new Final document. Once that new Final document exists, the Engineering Specification, Implementation Roadmap, Testing & Validation Plan, and this Playbook are each re-derived to match it — the previous versions of all five documents are archived per the README's archiving rules, never deleted, preserving the full reasoning trail.
- **This playbook's own maintenance.** This document is itself subject to the same "frozen unless deliberately revised" discipline as the other four — a future change to process alone (for example, a revised Git branching convention) does not require a new architecture version, since it is process rather than architecture, but it does require an explicit, deliberate, documented revision to this playbook. It is never edited silently or informally, by a human or by an AI agent, regardless of how minor the change appears.
