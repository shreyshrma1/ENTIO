# Write Spec Skill

Use this skill when asking an agent to write a feature spec for Entio.

## Purpose

A spec defines what a feature should do before implementation begins.

The goal is to clarify behavior, scope, non-goals, inputs, outputs, validation expectations, and tests. The agent should not write implementation code while creating the spec.

## Required Context

Before writing the spec, the agent must read:

* `AGENTS.md`
* `README.md`
* relevant files in `docs/architecture/`

`AGENTS.md` is the source of truth for the current active phase, phase boundaries, and current non-goals.

## Spec Requirements

The spec should include:

* Title
* Status
* Problem
* Goals
* Non-goals
* Proposed behavior
* Inputs
* Outputs
* Validation behavior
* Error behavior
* Test cases
* Acceptance criteria
* Open questions

## Scope Rules

The spec should describe only the feature being requested.

It should not introduce unrelated infrastructure, future product surfaces, integrations, or later-phase capabilities unless the user explicitly asks to update the project phase.

Feature-specific non-goals should be listed in the spec. Current phase-level non-goals should be referenced from `AGENTS.md` rather than duplicated in full.

## Boundary Check

Before finalizing the spec, the agent should confirm:

* The feature fits the current active phase.
* The feature does not conflict with current non-goals in `AGENTS.md`.
* The feature does not require speculative infrastructure.
* The feature preserves the rule that Entio should use existing RDF, OWL, and SHACL tooling rather than reinventing standards.

If the feature does not fit the current active phase, the agent should explain the mismatch and ask for explicit approval before expanding scope.

## Completion Response

After writing the spec, the agent should summarize:

1. The feature being specified.
2. The intended behavior.
3. The feature-specific non-goals.
4. The acceptance criteria.
5. Any open questions.
