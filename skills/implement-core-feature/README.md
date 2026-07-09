# Implement Core Feature Skill

Use this skill when asking an agent to implement a small approved Entio feature from an existing spec and ExecPlan.

## Purpose

This skill helps ensure implementation work stays narrow, testable, and aligned with the current project phase.

The agent should not decide product scope on its own. It should implement only what has already been approved in the spec and ExecPlan.

## Required Context

Before writing code, the agent must read:

* `AGENTS.md`
* `README.md`
* relevant files in `docs/architecture/`
* the approved feature spec
* the approved ExecPlan

`AGENTS.md` is the source of truth for general agent guidelines, current active phase, phase boundaries, current non-goals, and Git/version control behavior.

## Implementation Rules

The agent should:

* Implement one approved ExecPlan slice at a time.
* Before editing code, identify the exact ExecPlan slice being implemented.
* Restate the slice’s goal, allowed files, forbidden files, expected output, tests, and verification commands.
* Implement only the concrete behavior described in that ExecPlan slice.
* Do not infer or implement future slices.
* Do not expand the slice because a nearby change seems useful.
* If the slice is too vague to implement safely, stop and ask for clarification.
* If the implementation requires changing files outside the slice’s allowed scope, stop and ask before editing them.
* If the implementation requires changing shared contracts, module boundaries, dependencies, build files, or phase scope, stop and ask before continuing.
* If no concrete implementation is required for the slice, report that clearly instead of adding placeholder logic or speculative utilities.

## Boundary Check

Before implementing, the agent must confirm that the selected ExecPlan slice is compatible with:

* `AGENTS.md`
* the approved feature spec
* the approved ExecPlan
* relevant architecture documents

If the slice conflicts with project guidance, phase scope, module boundaries, or allowed files, the agent should stop and explain the conflict instead of implementing.

If the slice is underspecified, the agent should stop and ask for clarification instead of guessing.

## Version Control

Each completed implementation slice should be treated as a separate version-controlled change.

After completing and verifying a slice, the agent should prepare a focused Git commit for that slice, or create the commit if the task prompt explicitly authorizes it, following the Git and version control rules in `AGENTS.md`.

Do not combine multiple unrelated slices into one commit.

Do not commit or push if the slice is incomplete, tests or verification failed, required review is pending, or the task prompt explicitly says not to commit or push.

## Completion Response

After implementation, the agent should summarize:

1. What changed.
2. Which ExecPlan slice was implemented.
3. Which files were modified.
4. Which tests were added or updated.
5. Which commands were run.
6. Whether a Git commit was created.
7. Any assumptions, limitations, or follow-up work.
