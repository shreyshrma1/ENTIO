# Write ExecPlan Skill

Use this skill when asking an agent to write an implementation plan for an approved Entio feature spec.

## Purpose

An ExecPlan explains how an approved spec will be implemented.

The goal is to create a concrete, reviewable plan before code is changed. The agent should not implement the feature while writing the ExecPlan.

## Required Context

Before writing the ExecPlan, the agent must read:

* `AGENTS.md`
* `README.md`
* relevant files in `docs/architecture/`
* the approved feature spec

`AGENTS.md` is the source of truth for the current active phase, phase boundaries, and current non-goals.

## ExecPlan Requirements

The ExecPlan should include:

* Goal
* Related spec
* Current state
* Target state
* Affected modules and files
* Step-by-step implementation plan
* Test plan
* Verification commands
* Rollback notes
* Risks and assumptions
* Definition of done

## Scope Rules

The plan should stay small and focused.

It should only include work needed to satisfy the approved spec. It should not introduce unrelated infrastructure, future product surfaces, or later-phase capabilities.

If the approved spec appears to conflict with the current phase boundaries in `AGENTS.md`, the agent should call out the conflict clearly in the ExecPlan rather than silently expanding the scope.

## Boundary Check

Before finalizing the ExecPlan, the agent should explicitly check:

* Does this plan fit the current active phase?
* Does it avoid current non-goals?
* Does it avoid speculative infrastructure?
* Does it keep implementation responsibilities in the right modules?
* Does it preserve the rule that Entio should use existing RDF, OWL, and SHACL tooling rather than reinventing standards?

## Completion Response

After writing the ExecPlan, the agent should summarize:

1. The implementation approach.
2. The modules and files expected to change.
3. The verification commands.
4. Any risks or assumptions.
5. Any open questions that should be resolved before implementation.
