# Phase 10 Implementation Summary

## Status

Complete. Phase 10 delivers controlled materialization of supported inferred relationships through all nine ordered ExecPlan slices.

## Delivered Behavior

- Reasoning displays asserted facts separately from inferred materialization candidates.
- Users can stage inferred subclass relationships, individual types, and object-property assertions.
- Requests contain a completed applied-graph reasoning job ID, opaque server-issued fact IDs, optional explicit target-source choices, and an idempotency key. Browsers do not submit triples, edit types, authors, timestamps, or provenance.
- Kotlin reruns reasoning and checks project/user ownership, current graph fingerprint, fact membership, duplicates, writable subject-owned sources, and import safety immediately before staging.
- One request accepts 1–100 facts and appends all new entries or none. Existing staged equivalents return their current IDs without duplication.
- Materialized entries use existing typed edits, shared staging, validation, semantic diff, reasoning/SHACL impact, human approval, atomic multi-source apply, reload verification, and rollback.
- Proposal details show safe reasoning provenance. Provenance is workflow metadata, not an ontology annotation or graph-diff statement.

## Architecture

`core-types` defines immutable inference identity, stageability, batch, source-choice, and optional provenance contracts. `semantic-engine` converts fresh inferred facts to existing `AddSuperclassEdit`, `AssignTypeEdit`, or `AddObjectPropertyAssertionEdit` requests. `web-server` owns retained job authorization, fresh-rerun orchestration, source safety, idempotency, atomic staging, and in-memory provenance. `web-app` owns bounded selection, source-choice presentation, safe submission, and Changes navigation.

No module, dependency, database, queue, or alternate proposal/apply path was added.

## Bounds And Identity

- Materializable result: complete, completed, current, applied-graph reasoning owned by the current user.
- Supported request size: 1–100 facts.
- Browser fact identity: versioned, opaque, job/user/project/graph-bound `factId`.
- Server duplicate identity: canonical, label-independent `semanticFactKey`.
- Source resolution: writable local subject ownership; one candidate is automatic, several require explicit choice, and none blocks.
- Imported, bundled, FIBO, external, and read-only sources are never write targets.

## Verification

Every slice passed focused checks before commit, remote branch push, and clean local merge. Phase completion ran:

```bash
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm audit --omit=dev && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```

All checks passed. The web production audit reported zero vulnerabilities; web tests passed 74 unit tests and 3 Playwright journeys; VS Code passed 37 tests. Copied-fixture timing was 313 ms for 1 fact including warm-up, 24 ms for 25 facts, and 29 ms for 100 facts.

## Limitations

- Materialization is explicit and web-only; there is no automatic, background, AI-selected, CLI, or VS Code materialization.
- Reasoning results, staging sessions, proposals, idempotency records, and provenance remain in-memory development state.
- Only the three listed inferred fact types are supported. Literals, datatype assertions, annotations, equivalence, same-as, anonymous expressions, negative assertions, and SHACL findings are excluded.
- The Phase 9 ontology map remains read-only and asserted-only.

## Rollback

Phase 10 has no schema or data migration. UI controls and the materialization route can be removed first, followed by web orchestration, semantic conversion, and neutral contracts after consumers are gone. Applied assertions are ordinary reviewed ontology statements and are not automatically removed by a feature rollback.
