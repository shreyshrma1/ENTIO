# Phase 13 Slice 0: Contract, Dependency, License, Atomicity, And Benchmark Audit

Date: 2026-08-08

Status: Complete

Branch: `feature/phase-13-slice-0-contract-audit`

## Scope

This documentation-only slice resolved the Phase 13 implementation gates. It
changed no production Kotlin or TypeScript, Gradle dependency, source ontology,
model binary, generated search asset, route, or UI file. Temporary probes lived
outside the repository.

The normative decisions are split across:

- `phase-13-local-hybrid-retrieval.md`;
- `phase-13-project-domain-profile-persistence.md`;
- `phase-13-editable-external-iri-reuse.md`.

## Source and corpus audit

`master_2026Q2` is an Entio-approved snapshot of FIBO master at commit
`f59157fe156e3d91b1c045222d0a7dc06b7d78a2`; it is not an official production
publication. User-facing text must say **Entio-approved FIBO master snapshot**.
The embedded archive and approved source manifest both contain 297 RDF/Turtle
source files.

The complete eligible corpus has 4,579 unique canonical IRIs:

| Source family | Class | Object property | Datatype property | Total |
| --- | ---: | ---: | ---: | ---: |
| FIBO | 3,021 | 948 | 263 | 4,232 |
| OMG Commons 1.3 | 148 | 166 | 33 | 347 |

Commons is recognized only by the verified
`dependencies/omg-commons-1.3/` path. It appears by default only through a FIBO
dependency or active project context; broad search may include it explicitly.

Phase 5 compatibility hashes before Phase 13 assets exist are:

- `manifest.yaml`:
  `05e9c612bd308fec918ff3e4edc3b5bda422b23fad79bffae10e8ebce03373a5`;
- `catalog-metadata-v1.json`:
  `65ec3b1bf37bc703163c2bf82f1da2e4108b704acda22462ccf31c05af32acfc`;
- `catalog-v1.jsonl`:
  `8194bc5cad5827aa98a2a6586c6a9a9da1cdf40c5f77681b0d56fa4e5868cb05`;
- `curated-foundations-v1.json`:
  `5d538592282548be0b021248e3c0a398e268a3a4d6c1de2627af81ee0f29da50`.

Phase 13 generators must assert these inputs remain byte-for-byte unchanged.

## Dependency and model audit

Approved Maven coordinates are Lucene Core and Analysis Common `10.5.0`, ONNX
Runtime CPU `1.28.0`, and DJL Hugging Face tokenizers/API `0.36.0`. Lucene and
DJL are Apache-2.0; ONNX Runtime is MIT. OSV returned zero entries for the four
selected direct coordinates on 2026-08-08. Distribution still runs the normal
dependency audit and includes all transitive license/NOTICE obligations.

The selected model is unquantized `all-MiniLM-L6-v2` at revision
`94ea1512acaefbfe2e255b2d2ea4bf0d9d7b3dc3`. Exact files, checksums, tokenizer,
pooling, normalization, limit, dimension, text composition, and attribution are
fixed in the retrieval ADR. The alternative L12 candidate was permissively
licensed and locally runnable, but doubled corpus generation time and had lower
combined development/regression MRR.

The selected direct artifact bytes total approximately 158.6 MB before the
small API/transitive jars, 7.0 MB vectors, descriptors, and Lucene index. The
projected committed Phase 13 bundle remains below 190 MB and the 250 MiB gate.
It is separate from the 79 MB Phase 5 package.

## Supported machine and measured results

The performance acceptance machine is Apple M2 ARM64, 16 GiB RAM, macOS
26.5.2 build 25F84, and Temurin `21.0.12+8`. Full retrieval supports this
platform. Approved natives also identify Linux x64/ARM64 and Windows x64 as
Slice 3 functional smoke-test targets. An unavailable native enters explicit
lexical-structural degraded mode; Intel macOS is not a full-mode target.

- unquantized L6 warm probe inference: 5.712 ms mean over 20 requests;
- Java/reference maximum vector error: `1.5e-8` across 384 dimensions;
- exact 4,579 × 384 Java scan: 2.294 ms p50, 2.360 ms p95, 2.578 ms p99;
- vector storage: 7,033,344 bytes;
- quantized comparison corpus generation: L6 52.506 seconds, L12 103.105
  seconds;
- development/regression recall@10 average: 0.925 for both candidates;
- L6 combined development/regression MRR was higher than L12.

Vector-only locked-set output was retained as a diagnostic and did not change
the already fixed hybrid weights or confidence thresholds. Slice 4 must run the
untuned completed hybrid service against the locked acceptance gates.

## Project persistence and atomicity

The exact profile schema, derived paths, transaction journal, phases, commit
point, safe-path rules, orphan cleanup, hash-based recovery, and loaded-project
behavior are fixed in the profile ADR. The provenance schema, source-snapshot
bounds, materialization classes, and existing typed-operation mapping are fixed
in the editable-reuse ADR.

No additional removal or mapping operation is required. The existing typed
removal edits cover labels/annotations/hierarchy/domain/range, and an
IRI-valued mapping uses `AnnotationValue.Resource`. Whole-entity removal uses
the existing dependency-review path.

Atomic provenance does not require another apply path. A narrow optional
sidecar transaction participant extends `MultiSourceAtomicApplier`; existing
document-ingestion provenance remains unchanged.

## Bounds

The approved dependency limits remain 20 explicit selections, 100 closure
entities, 2,000 generated RDF statements, 2 MiB preview bytes, traversal depth
16, and 10 seconds. A descriptor-graph probe found a maximum single-entity
closure of 31 and maximum depth 10. A deterministic six-selection probe reached
101 distinct closure entities; the sixth addition must reject the entire
affected batch with `domain-dependency-entity-limit` and produce no partial
plan. Slice 1 introduces reusable bound contracts and Slice 6 verifies the
complete materializer against every bound.

Recommendation state remains a 30-minute non-refreshing TTL, 500 records and
50 plans per project/user, expired-first then oldest-sequence eviction, restart
invalidation, and cleanup on bounded reads/writes. Slice 4 owns its executable
tests.

## Benchmark approval

Benchmark v1 has 10 development, 10 regression, and 15 locked cases. Cases
cover exact labels, paraphrases, finance terminology, abbreviation, three
entity kinds, directional opposites, Commons, hard negatives, and no-match.
The implementation reviewer supplied reviewer 1 judgments. The repository
owner reviewed and approved all locked judgments as reviewer 2 on 2026-08-08.
The set was then locked before ranking implementation.

Development cases may tune examples and calibration. Regression cases detect
implementation drift. Locked cases may only determine acceptance; changing a
judgment requires benchmark v2 and an audit note. Phase 5 baselines are the
existing exact-IRI, agreement, borrower, situation, kind/compatibility, and
repeated-ordering tests.

## Workflow integration matrix

| Workflow | Intent/context | UI owner | Slice | Required focused test |
| --- | --- | --- | ---: | --- |
| Global semantic search | text, optional kind, project context | Explore/global search | 8 | merged local/FIBO ordering and inactive behavior |
| Create class | label, definition, parent | shared recommendation component in class form | 9 | reuse/local/extend action choice |
| Create object property | label, domain, range | property form | 9 | object-kind and domain/range compatibility |
| Create datatype property | label, domain, datatype | property form | 9 | datatype-kind and datatype compatibility |
| Individual type selection | individual wording and nearby graph | individual form | 9 | class-only candidate and no auto-materialization |
| Edit label/definition | current entity and changed text | entity details | 9 | substantially changed meaning triggers advice |
| Edit class hierarchy | child/parent context | hierarchy picker | 9 | named-class compatibility and extension |
| Edit property hierarchy | property kind and parent | property hierarchy picker | 9 | object/datatype cross-kind suppression |
| Edit domain | property and candidate class | domain picker | 9 | domain structural filter |
| Edit range/datatype | property kind, range/datatype | range picker | 9 | class-versus-datatype hard filter |
| Add assertion/value | subject, predicate, object/value | assertion/value form | 9 | predicate and object/value compatibility |
| Delete/replace entity | deletion dependencies and current graph | deletion review | 10 | optional replacement preserves deletion safety |
| Proposal reuse review | staged semantic change | staging panel | 10 | conversion regenerates server preview |
| SHACL target class | shape context | SHACL form | 10 | class-only recommendation without client validity |
| SHACL property path | shape/property context | SHACL form | 10 | property-kind recommendation |
| SHACL class/datatype constraint | constraint context | SHACL form | 10 | correct class/datatype separation |
| Ontology-map related search | selected node and neighborhood | ontology map panel | 10 | bounded read-only related results |
| Reasoning related search | selected asserted/inferred fact | reasoning workspace | 10 | no inference mutation or materialization |
| Foundation expansion | group/member selections | domain setup workspace | 5–7 | frozen bounded plan and existing staging path |

Document ingestion and the ontology assistant are deliberately excluded from
new hybrid integration. Their existing FIBO adapters remain compatibility
boundaries.

## Current contract inventory

- CLI/VS Code compatibility commands: `external-sources`, `external-manifest`,
  `external-browse`, `external-describe`, `external-search`,
  `external-dependencies`, and `external-proposal`; no Phase 13 CLI mutation is
  added.
- Current web routes:
  `/external/fibo/modules`, `/module-elements`, `/search`, `/details`, and
  `/proposals` beneath the project API. They remain until Slice 8 migration and
  are not silently repurposed.
- React currently owns `ExternalOntologyPanel` and Phase 5 project API/query
  adapters. Slice 8 replaces the always-present navigation with optional domain
  setup/browse behavior.
- `AiProposalService` calls `FiboWebService` only when assistant FIBO context is
  requested. Phase 13 does not replace that contract.
- document ingestion builds `DocumentRetrievalContext` with
  `FiboCatalogLoader` and `DocumentOntologyRetrievalService`; Phase 13 does not
  alter that ranking or work key.

Phase 12 fingerprint baselines are:

- `DocumentAnalysisPipelineContracts.kt`:
  `c6d3143003e27c463fab828553d08db14cb5f588e46d94be6bb2c3cfe3305c69`;
- `DocumentIngestionOrchestrator.kt`:
  `b3cf356d33763fed204132e655ec6f216a5eb5d02a18cae25922c57674442185`;
- `DocumentOntologyRetrievalService.kt`:
  `dc7089f4618e390db4b7d0b3d4c0ba17d5376ef0d00246e3a652ad62eaba0f90`.

The version constants remain candidate extraction v2, retrieval query/ranking/
result v1, grounded prompt v2, grounded request v2, grounded response v1, and
work key v2. Later slices must preserve these files and baseline tests unless a
separate approved compatibility amendment is made.

## Temporary audit commands

The disposable audit directory was created by `mktemp -d` outside the
repository. Representative commands were:

```bash
shasum -a 256 external-ontologies/fibo/manifest.yaml \
  external-ontologies/fibo/indexes/catalog-metadata-v1.json \
  external-ontologies/fibo/indexes/catalog-v1.jsonl \
  external-ontologies/fibo/indexes/curated-foundations-v1.json

unzip -l external-ontologies/fibo/source/fibo-master_2026Q2-f59157f.zip

curl -fsS https://api.osv.dev/v1/query \
  -H 'Content-Type: application/json' -d '<Maven package/version query>'

java -version
javac -cp '<ONNX Runtime and DJL audit jars>' EmbeddingProbe.java
java -cp '<audit classpath>' EmbeddingProbe '<pinned model directory>'
python reference_embedding.py '<pinned model directory>'
python evaluate_models.py '<candidate model directory>'
javac ExactScanProbe.java
java -Xms128m -Xmx256m ExactScanProbe
```

The Temurin archive checksum was
`021d629349ebc12a409faa517b837ec80ceee8f58a5ac85c788ecad07ca6881c`.
All downloaded model and library artifacts were temporary and are not part of
this slice.

## Verification

The required repository verification results are recorded below after the
documentation review:

The original ExecPlan filter `*Ai*Fibo*` failed because it matched no existing
test. With repository-owner approval, the plan was corrected to name the two
existing assistant/FIBO compatibility tests by exact class and method. No test
was added, skipped, renamed, or weakened.

- `./gradlew :semantic-engine:verifyFiboCatalog` — passed;
- `./gradlew :semantic-engine:test --tests '*Fibo*'` — passed;
- `./gradlew :web-server:test --tests '*Document*Retrieval*'` — passed;
- `./gradlew :web-server:test --tests
  'com.entio.web.ai.OpenAiProposalClientTest.asksTheModelForFocusedExternalContextWhenNeeded'
  --tests
  'com.entio.web.AiProposalWorkflowTest.followUpCanReviseAndRetractPrivateDraftEdits'`
  — passed;
- `git diff --check` — passed.
