# Phase 11.5 Summary

Phase 11.5, Multi-Stage AI Modeling and Connected Ontology Change Sets, was
implemented and verified on 2026-07-27.

On 2026-07-29, the provider path was consolidated after throughput testing.
Per-document discovery remains separate, connected modeling now owns
cross-document semantic synthesis, and one ontology-aware recommendation-planning
call owns alignment, modeling review, and final grouped planning. This removes
three unconditional provider calls while preserving deterministic verification
and human review.

## Delivered

- Replaced the production single-stage document-analysis path with
  per-document discovery, connected semantic synthesis, and ontology-aware
  recommendation planning.
- Kept discovery ontology-blind so the current ontology cannot prematurely
  narrow what a document says.
- Added connected model items for ontology structure, individuals, facts,
  shapes, constraints, and review-only complex rules, with supporting concepts
  ordered before dependent operations.
- Added cross-document and prior-applied-provenance reconciliation without
  letting dates or the model resolve conflicts or supersession automatically.
- Added bounded current-ontology alignment across local, imported, current-work,
  retained provenance, and approved pinned FIBO scopes.
- Added modeling review for metadata promotion, weak domain or range,
  missing support, flattened rules, illustrative individuals, and unjustified
  ontology links.
- Added independent evidence, modeling, and ontology-fit confidence values.
  Kotlin calculates overall confidence as the weakest of the three.
- Added grouped atomic recommendations with dependency-ordered typed operations,
  temporary references, collision-checked IRIs, discovery coverage, and critic
  dispositions verified by Kotlin.
- Added grouped web review for exact changes, provenance, critique, confidence,
  review-only findings, optional leaves, safe splits, and explicit individual
  confirmation.
- Reused the existing private-draft, shared staging, proposal, human approval,
  atomic apply, reload, and rollback workflow.
- Removed the old single-stage production execution path while preserving
  readable Phase 11 tasks and applied provenance.

## Bounds And Trust

- One discovery call is made per document.
- A task may use at most 15 planned logical calls, 20 provider attempts, and
  three automatic retries; one logical call may be retried only once with the
  same frozen input.
- Each provider call remains bounded to 120 seconds, and the full task remains
  bounded to 30 minutes.
- One atomic recommendation may expand to at most 20 typed edits, and one task
  may expand to at most 100 typed edits.
- Complete prompts, provider responses, uploads, extracted text, and intermediate
  stage state remain temporary. Only bounded successfully applied provenance is
  durable.
- Documents and provider output remain untrusted. The model cannot choose
  arbitrary endpoints, use tools, supply raw RDF, approve work, apply work, or
  write ontology sources.

## Accuracy And Verification

The permanent benchmark references the two existing example PDFs without
copying them. It pins accepted semantic aliases, required supporting evidence
and illustrative individuals, prohibited modeling patterns, and complex
meanings that must remain review-only. The deterministic call counts are 6 for
one document, 7 for two documents, and 15 for ten documents.

The final implementation gate passed:

```text
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm audit --omit=dev && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```

Results included 98 web unit tests, 4 Playwright journeys, 37 VS Code extension
tests, and zero production npm vulnerabilities. Deterministic Kotlin tests cover
the stage contracts, evidence, references, matching, confidence, limits,
authorization, cancellation, retries, stale state, typed conversion, proposal
integration, apply provenance, reload, rollback, and legacy compatibility.

The controlled real-provider smoke test was not run because the verification
process did not have access to a verified server-memory credential. It is kept
separate from deterministic CI and no credential or raw provider payload is
recorded.

## Known Limitations

- English PDF, DOCX, TXT, and Markdown remain the supported document formats.
- Uploads, extracted text, intermediate stages, incomplete tasks, and review
  workspaces are not a durable production job system.
- Complex aggregation, role-separation, temporal, and conditional rules that
  current typed operations cannot express remain visible and review-only.
- Model output is deterministically checked for evidence, contract shape,
  references, bounds, duplicates, sources, and freshness, but those checks do
  not prove that every modeling judgment is conceptually correct.
- There is no automatic approval, raw RDF path, unrestricted agent, external
  document index, second apply path, or CLI and VS Code ingestion surface.

## Planning And Evidence

- [Scope](../architecture/phase-11.5-scope.md)
- [Spec](../specs/0021-phase-11.5-multi-stage-ai-modeling-and-connected-ontology-change-sets.md)
- [ExecPlan](../execplans/0021-phase-11.5-multi-stage-ai-modeling-and-connected-ontology-change-sets.md)
- [Contract audit](../decisions/phase-11.5-slice-0-contract-audit.md)
- [Pipeline orchestration](../decisions/phase-11.5-slice-8-orchestration.md)
- [Grouped review UI](../decisions/phase-11.5-slice-10-review-ui.md)
- [Acceptance verification](../decisions/phase-11.5-slice-11-verification.md)
