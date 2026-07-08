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

`AGENTS.md` is the source of truth for the current active phase, phase boundaries, and current non-goals.

## Implementation Rules

The agent should:

* Implement only the files and behavior described in the approved ExecPlan.
* Keep changes small and focused.
* Keep product logic out of `shared` unless it is truly generic and reusable.
* Use existing RDF, OWL, and SHACL libraries where appropriate instead of inventing standards machinery.
* Add or update focused tests for the implemented behavior.
* Update relevant documentation if the implementation changes expected behavior.
* Run the relevant verification commands before reporting completion.

## Boundary Check

Before implementing, the agent must confirm that the work fits within the active phase described in `AGENTS.md` and the relevant architecture documents.

If the implementation appears to require later-phase infrastructure, the agent should stop and explain the conflict instead of adding that infrastructure.

Examples of later-phase infrastructure may include product surfaces, integrations, ingestion pipelines, autonomous agents, or other capabilities that are not part of the current active phase.

## Completion Response

After implementation, the agent should summarize:

1. What changed.
2. Which files were modified.
3. Which tests were added or updated.
4. Which commands were run.
5. Any assumptions, limitations, or follow-up work.
