# Phase 12 Summary

## Status

Phase 12 was implemented and verified on 2026-07-31.

Phase 12 replaces ontology-blind interpretation as the default path for new
document-analysis tasks. Entio now extracts a deterministic local candidate
inventory from verified located text, retrieves compact choices from every
authorized ontology and current-work scope, and asks the selected model to
interpret evidence using those server-issued choices. Kotlin then verifies the
response and compiles supported meaning through the existing typed-edit and
human-review workflow.

The model still makes variable semantic judgments. Candidate extraction,
retrieval, work identity, response verification, and compilation are
deterministic for frozen inputs; the whole pipeline is not.

## Delivered Behavior

- Local English candidate extraction using Apache OpenNLP 2.5.11 and the pinned
  1.3.0 sentence, tokenizer, part-of-speech, and lemmatizer resources.
- Stable candidate IDs and exact evidence locations for named entities,
  concepts, relationships, values, rules, and administrative text.
- Deterministic lexical retrieval over applied local and imported ontology
  state, private drafts, shared staging, the current proposal, same-task work,
  retained applied provenance, and the pinned FIBO catalog.
- Compact, server-issued top-20 choices with deterministic scoring, scope
  ordering, deduplication, tie-breaking, match reasons, and fingerprints.
- A frozen work key covering documents, evidence, candidates, retrieval,
  ontology and current work, provenance, FIBO, NLP resources, model, prompt,
  response, and ranking versions.
- A bounded, no-tools grounded provider contract with reuse, extend,
  propose-new, unresolved, and non-ontology dispositions.
- Kotlin verification of evidence, selection IDs, entity kinds, source
  permissions, prerequisites, connected references, duplicates, and freshness.
- Existing deterministic semantic-plan compilation, final-plan verification,
  connected editable review, and dependency-safe typed-draft batching.
- Separate candidate, grounded-item, recommendation, and expanded-edit counts.
- Aggregate-only provider diagnostics that exclude credentials, raw provider
  payloads, document text, and local filesystem paths.

There are no embeddings, vector database, second ontology index, or external
retrieval service. OpenNLP resources are ordinary pinned Maven dependencies;
no model asset was copied into the repository.

## Delivery Order And Slice Records

Slices 5 and 6 were completed in the explicitly authorized order 6 then 5.
This established grounded verification and the narrow existing-compiler entry
before production orchestration depended on it. All other dependency order was
unchanged.

| Delivery | Slice | Branch | Tip commit | Completion artifact |
| --- | --- | --- | --- | --- |
| 1 | 0 | `docs/phase-12-contract-audit` | `a678280` | `docs/decisions/phase-12-slice-0-contract-and-dependency-audit.md` |
| 2 | 1 | `feature/phase-12-grounded-contracts` | `cb373fc` | `docs/decisions/phase-12-slice-1-grounded-analysis-contracts.md` |
| 3 | 2 | `feature/phase-12-candidate-extraction` | `967ab5e` | `docs/decisions/phase-12-slice-2-deterministic-candidate-extraction.md` |
| 4 | 3 | `feature/phase-12-ontology-retrieval` | `49e6d5a` | `docs/decisions/phase-12-slice-3-deterministic-ontology-retrieval.md` |
| 5 | 4 | `feature/phase-12-grounded-provider` | `1f68656` | `docs/decisions/phase-12-slice-4-grounded-provider-boundary.md` |
| 6 | 6 | `feature/phase-12-grounded-verification` | `4936142` | `docs/decisions/phase-12-slice-6-verification-and-compilation.md` |
| 7 | 5 | `feature/phase-12-grounded-orchestration` | `ee452a1` | `docs/decisions/phase-12-slice-5-orchestration-and-work-key.md` |
| 8 | 7 | `feature/phase-12-connected-review` | `c3f4f6d` | `docs/decisions/phase-12-slice-7-connected-editable-review.md` |
| 9 | 8 | `test/phase-12-regression-benchmark` | `79df064` | `docs/decisions/phase-12-slice-8-benchmark-and-regression.md` |
| 10 | 9 | `docs/phase-12-completion` | this completion commit | `docs/decisions/phase-12-slice-9-phase-completion.md` and this summary |

Each slice was implemented on its own branch with focused tests and
verification, committed once, pushed without force, and merged locally into
`main` with a non-fast-forward merge. `main` was not pushed.

## Controlled Benchmark

The frozen two-PDF benchmark used `gpt-5.6-luna`, grounded prompt
`phase-12-grounded-model-prompt-v1`, and grounded response
`phase-12-grounded-model-response-v1`. The credential was read locally and was
not committed or included in diagnostics.

The final unchanged ten-run grounded gate passed:

- Complete concept coverage, expected reuse, and exact provenance: 10/10.
- Duplicate new entities for expected reuse targets: 0/10.
- Unresolved choices: 0/10.
- Prohibited executable output and automatic ontology writes: 0/10.
- One provider attempt per run and no safe failures in the passing gate.
- Run durations: 8,262–11,389 ms; mean 9,698.2 ms.

The retained semantic-compiler gate also passed ten runs with every 13 required
concepts, six relationships, and four review-only meanings present, 100%
supported compilation, exact provenance and complete coverage in every run,
and zero prohibited output or ontology writes. Its durations were
17,783–26,203 ms with a 19,599 ms mean.

Earlier provider failures were not waived and thresholds were not weakened.
Redacted diagnostics identified and corrected strict-schema field names and
harmless response ordering; the full gate was rerun until all ten trials
passed. The exact frozen hashes, trial history, and aggregate diagnostics are
recorded in the Slice 8 completion artifact.

## Verification

The following commands passed on the accumulated implementation and again on
local `main` after the final documentation merge:

```bash
./gradlew test
./gradlew build
./gradlew check
./gradlew :semantic-engine:verifyFiboCatalog
(cd web-app && npm ci && npm audit --omit=dev && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```

The production dependency audit reported zero web vulnerabilities. The web
suite passed 101 unit tests and four Playwright workflows, and the VS Code
extension passed all 37 tests.

## Trust, Write Boundary, And Non-Goals

Documents, extracted content, retrieval context, and provider output remain
untrusted. A provider cannot select an unissued entity, silently change entity
kind or source, write RDF, approve work, apply work, or bypass deterministic
verification. Imported and FIBO entities are read-only reuse targets.

The existing private-draft, proposal, validation, human approval, atomic apply,
reload, rollback, and applied-provenance path remains the only ontology write
path. Phase 12 adds no automatic approval, direct source write, raw RDF
fallback, second apply path, production document/task persistence, unrestricted
agent loop, CLI ingestion, or VS Code ingestion.

## Post-completion candidate-volume correction

The active implementation now distinguishes occurrence-level evidence mentions
from ontology-bearing candidates. Exact and safely normalized mentions are
grouped across documents, low-value and document-only content remains in a
coverage ledger, and deterministic promotion reasons decide which groups enter
retrieval. The review workspace reports the resulting funnel without rendering
document-only entries as recommendation cards.

Grounded requests still contain at most 40 candidates and retain one bounded
retry plus finite adaptive splitting, but no task-wide group count limits the
number of documents or promoted candidates. FIBO normalization is compiled once
and FIBO search results are reused within each candidate retrieval pass.
