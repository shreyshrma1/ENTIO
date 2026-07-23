# Phase 10 Slice 0: Planning Approval And Contract Audit

## ExecPlan Slice

Slice 0: Planning Approval And Contract Audit.

## Goal

Approve the Phase 10 planning documents and pin the additive contracts needed to
materialize selected inferred facts without changing ontology semantics or the normal
proposal workflow.

## Files Modified

- `docs/specs/0018-phase-10-materialize-inferred-relationships.md`
- `docs/execplans/0018-phase-10-materialize-inferred-relationships.md`
- `docs/decisions/phase-10-slice-0-contract-audit.md`

No production code, tests, fixtures, dependencies, lockfiles, or scope documents were
changed.

## Contract Audit

- `ReasoningResult` already distinguishes asserted and inferred subclass, individual
  type, and property relationships and carries graph/import fingerprints.
- `AddSuperclassEdit`, `AssignTypeEdit`, and
  `AddObjectPropertyAssertionEdit` represent all three approved materializations.
  `TypedOntologyEditTranslator` already translates them into ordinary graph additions.
- `EntioProject.ontologies`, `ResolvedOntologySource`, loaded symbols, and per-source
  asserted graphs can identify local sources declaring a subject. Imported closure
  documents and external/FIBO catalog assets are not project write targets.
- Import dependence is conservative: retained fact source identity, loaded
  declarations, and import-closure metadata may prove local-only or imported
  participation. Anything unproven is `Unknown` and remains read-only.
- Existing semantic-job details are already bounded to 100. No pagination is added.
- Identity uses versioned, length-prefixed UTF-8 components and lowercase hexadecimal
  SHA-256. Browser `factId` remains job/user/project/fingerprint-bound; server-only
  `semanticFactKey` remains stable across a fresh rerun of the same semantic fact.
- `StagingWorkflowService` will add one synchronized
  `stageMaterializations(projectId, userId, idempotencyKey, preparedItems)` operation.
  It checks the complete batch and idempotency key before mutation, returns existing
  entries idempotently, appends only new entries, and clears a prepared proposal once.
- Semantic jobs gain internal immutable `submittedByUserId`. Existing project-scoped
  job reads remain compatible; materialization alone requires owner equality.

## Tests Added Or Updated

No executable tests were permitted in Slice 0. The spec and ExecPlan were reviewed
against current core, semantic-engine, and web-server contracts.

## Verification

- `git diff --check`: passed.
- `git status --short`: passed; only the three approved Slice 0 documentation files
  were modified or added.
- Acceptance-criterion traceability: passed; every Phase 10 criterion maps to an
  ordered ExecPlan slice.
- Scope open-question review: passed; all contract-level questions were resolved
  without adding product scope.

## Git Commit

Yes. This record is included in the focused Slice 0 commit.

## Assumptions And Limitations

- The user's 2026-07-23 instruction to implement the approved Phase 10 ExecPlan is the
  explicit approval signal recorded here.
- Phase 10 does not add pagination, persistence, raw RDF fallback, automatic
  materialization, or an imported-source write path.
- Private helper and UI component names may be chosen in their approved slices when
  compilation evidence requires them.

## Notable Decisions

- No-subject-owner means not stageable; Entio does not choose an arbitrary local source.
- Unknown import dependence is unsafe rather than guessed.
- Already staged/proposal-represented facts are successful idempotent results, while an
  already asserted fact rejects the complete request.
