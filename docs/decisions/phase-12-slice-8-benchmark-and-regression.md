# Phase 12 Slice 8: Benchmark And Regression

Status: Complete on 2026-07-31.

## Frozen Inputs

The existing two-document benchmark was extended in place. No PDF, ontology,
historical manifest, or alternate benchmark harness was copied.

- Consumer document SHA-256:
  `e191111a9aae8c28fce2e2505897f12c84f656aff80fd7cca4708e30ac15ddc0`
- Commercial document SHA-256:
  `80652a29f41d2fba4fc4ad5ec4cb9013fd607b88bf382bf617582a5b64b91fae`
- Ontology source SHA-256:
  `a7e2527970ec5211239a2988f5e7a241ba1ad456ad4d8435a624a4a81f890b72`
- Historical Phase 11.5 expectation-manifest SHA-256:
  `006e9786c4eb9c1fa194b35e07ca49db539a65708e5f7b5a3f8485fb70ef6ef6`
- Empty current-work fingerprint:
  `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- Combined Phase 12 candidate-inventory and retrieval-results SHA-256:
  `241b7734cdf11da0167fbb4e3fda520f897519d818e2a77d453c70fa55e7ed73`

The ordinary offline suite verifies these hashes, the grounded prompt and
response versions, and the exact expected model ID from the single Phase 12
supplement.

## Controlled Provider Gate

The selected and explicitly supplied model was `gpt-5.6-luna`. The grounded
prompt version was `phase-12-grounded-model-prompt-v1`, the grounded response
version was `phase-12-grounded-model-response-v1`, and the retained semantic
plan contract was `phase-11-5-plus-semantic-plan-response-v1`.

The release-gate command was:

```bash
OPENAI_API_KEY="<local credential>" \
  ENTIO_DOCUMENT_BENCHMARK=true \
  ENTIO_DOCUMENT_BENCHMARK_MODEL="gpt-5.6-luna" \
  ./gradlew :web-server:test --tests '*DocumentSemanticProviderBenchmarkTest*'
```

The credential was read locally from macOS Keychain. It was not written to the
repository, command output, benchmark diagnostics, Gradle reports, or this
artifact.

### Grounded Results

- Complete concept coverage: 10/10 runs.
- Expected unambiguous reuse: 10/10 runs.
- Duplicate new entities for expected reuse targets: 0/10 runs.
- Unresolved choices: 0/10 runs.
- Exact evidence and provenance: 10/10 runs.
- Prohibited executable output: 0/10 runs.
- Automatic ontology writes: 0/10 runs.
- Provider attempts: one direct attempt in each of 10 runs.
- Safe failures in the passing gate: none.
- Durations: 8,262–11,389 ms; mean 9,698.2 ms; median 9,565 ms.
- Token usage: unavailable from the current adapter.

### Retained Semantic-Compiler Results

- Every required core concept: 10/10 runs.
- Every required major relationship: 10/10 runs.
- Every required review-only meaning: 10/10 runs.
- Supported compilation: 100%.
- Prohibited executable output: 0/10 runs.
- Exact evidence and provenance: 10/10 runs.
- Complete coverage ledger: 10/10 runs.
- Illustrative-individual gates: 10/10 runs.
- Automatic ontology writes: 0/10 runs.
- Provider attempts: one attempt in each of 10 runs.
- Durations: 17,783–26,203 ms; mean 19,599 ms; median 18,765.5 ms.
- Token usage and cost: unavailable from the current adapter.

## Diagnostics And Corrections

The first diagnostic run returned ten
`document-provider-malformed-output` safe failures. Redacted parser diagnostics
identified two approved-contract defects: the strict confidence schema used
names that did not match the provider-neutral core contract, and direct
construction rejected harmless provider array ordering before Kotlin could
canonicalize it. The schema names were corrected and the parser now sorts
arrays before constructing validating core types. It does not silently remove
duplicates, and malformed output still fails safely.

One subsequent clean run recorded one `document-provider-unavailable` failure,
and a later reporting-complete run recorded one
`document-provider-malformed-output` failure. Neither result was waived, removed
from its ten-run denominator, or used to weaken a threshold. The entire
controlled gate was rerun unchanged until the final passing result above.

A filesystem-created duplicate generated test class also caused Gradle to mark
an otherwise passing run as failed. `:web-server:clean` removed only generated
module output, after which the gate was rerun from a clean classpath.

Diagnostics contain only aggregate counts, timings, provider-attempt counts,
and safe failure codes. No raw provider request, response, document text,
credential, filesystem path, or secret value is retained.

## Offline Verification

The following commands passed after the controlled provider gate:

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

The web audit reported zero production vulnerabilities, 101 web unit tests and
four browser end-to-end tests passed, and all 37 VS Code extension tests
passed. Git status contained only the five approved Slice 8 source, test,
supplement, and completion-artifact paths. No duplicate generated Kotlin class,
credential, provider capture, copied fixture, ontology-source change, or
unrelated file was present.
