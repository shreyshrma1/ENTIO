# Phase 12 Slice 0 Contract And Dependency Audit

Status: Complete

Date: 2026-07-31

## ExecPlan Slice Implemented

Slice 0: Freeze The Baseline And Resolve Contracts.

## Goal

Freeze the current Phase 11.5+ document-analysis boundary and resolve the NLP,
retrieval, contract, compatibility, safeguard, work-key, and benchmark choices
required before Phase 12 production code changes. This slice changes no
production or test code.

## Baseline

The slice started from clean local `main` commit
`2fc8a8704173b6aa7e1b0216a58c6a7f7667c9cc`, exactly matching `origin/main`.
The active path to replace for new tasks is:

```text
extraction
-> DocumentAnalysisService discovery per document
-> DocumentConnectedModelService chunk modeling and optional consolidation
-> DocumentPrerequisiteCompletionService
-> current ontology and current-work snapshot
-> DocumentSemanticPlanAssembler
-> DocumentSemanticPlanCompiler and DocumentChangeSetPlanVerifier
-> DocumentReviewWorkspaceStore
-> existing typed draft, proposal, approval, apply, rollback, and provenance
```

`DocumentIngestionOrchestrator` is the producer and coordinator. Legacy
reconciliation, ontology-alignment, critic, and final low-level planning
contracts remain readable and tested, but are not production calls for new
tasks. Phase 12 retains them only for compatibility and rollback until the new
path is verified.

## Audited NLP Dependency

Phase 12 will use Apache OpenNLP, an established Apache-2.0 JVM NLP library,
through these exact Maven Central artifacts:

- `org.apache.opennlp:opennlp-tools:2.5.11`;
- `org.apache.opennlp:opennlp-models-sentdetect-en:1.3.0`;
- `org.apache.opennlp:opennlp-models-tokenizer-en:1.3.0`;
- `org.apache.opennlp:opennlp-models-pos-en:1.3.0`;
- `org.apache.opennlp:opennlp-models-lemmatizer-en:1.3.0`.

The English models are the Apache OpenNLP UD English Web Treebank 1.3 models,
trained with OpenNLP 2.5.4 from UD 2.16. They are supplied as dependency JARs;
no model binary is copied into the repository and no runtime download is
allowed. The audited JAR sizes and SHA-256 values are:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `opennlp-tools-2.5.11.jar` | 1,397,282 | `58c0ee5e20d30ba47daf4cd546c6426a7fe762a7b197b2308eb0b89cf5b48c77` |
| sentence model | 27,930 | `df415d97fc05b67e60c153fd401d6e9241d59674318a13a71453bc10d7824aa6` |
| tokenizer model | 332,951 | `da6f43749f37162917b30f95c4eebfbbb24952ba590c12ae314c99d84ac540b2` |
| POS model | 1,173,113 | `58c0fcfe5fb584bb98b95f6b7276af4e27fe9d95565dc01940430ae76aedfb2c` |
| lemmatizer model | 527,582 | `afa988e6e25fec8c16f40fd4e9f4bc989de75cb6f01a80d3db7b132321249ce7` |

The direct audited footprint is 3,458,858 bytes. OpenNLP 2.5 is compatible
with the repository's Java 21 target. Slice 2 must load these immutable models
once in a lazy server-side adapter, perform no training or adaptive updates,
disable probabilistic beam alternatives, and sort all derived output by
server-owned stable keys. Fixed offline fixtures must initialize and process in
under two seconds on the supported development JVM after dependencies are
resolved; failure to load any exact resource fails with
`document-candidate-extraction-failed`. Named organizations, people, locations,
dates, identifiers, and amounts use the OpenNLP token/POS/lemma output plus the
small bounded Entio rules approved by the spec. This is not authorization for a
custom general-purpose NLP framework or a second NLP dependency.

## Retrieval Ownership And Inputs

One narrow `DocumentOntologyRetrievalService` in `semantic-engine` is required.
It coordinates rather than replaces:

- `SemanticDescriptionService` for applied and imported descriptors;
- `DocumentOntologyMatcher` record normalization and lexical/current-work
  comparison;
- `FiboSchemaSearchService` and the existing immutable FIBO session.

The semantic engine receives every web-owned scope as explicit immutable
records. It does not read web stores. `StagingWorkflowService` already owns the
loaded applied graph, staged entries, and proposal preview, but its current
`connectedDocumentSnapshot` exposes only the graph fingerprint, writable
sources, and normalized document-operation keys. Slice 5 therefore needs one
narrow additive read-only snapshot for private draft, shared staging, and
current proposal records. `DocumentReviewWorkspaceStore` already exposes the
current task's verified plan and evidence; it supplies same-task records.
`AppliedDocumentProvenanceRepository` remains the read-only durable provenance
source. Existing project loading supplies imports. Existing FIBO loading
supplies the pinned catalog. No new persistence or reversed dependency is
needed.

The approved scope order is applied local, imported, private draft, shared
staging, current proposal, same task, durable provenance, and curated FIBO.
Retrieval query normalization is Unicode-aware trim, internal-whitespace
collapse, camel-case boundary splitting, punctuation-to-space replacement, and
`Locale.ROOT` lowercasing. Exact results are deduplicated only by
`scope + canonical IRI + source ID`. Scores are normalized to integer
`0..100`. Ordering is descending score, then fixed scope order, entity kind,
canonical IRI, and source ID. Match reasons are unique and ordered by reason
kind then stable detail. Selection IDs are SHA-256-derived opaque IDs over the
candidate ID, canonical IRI, kind, scope, source, ranking version, and all
applicable fingerprints. Only the top 20 enter a prompt; full-state duplicate
and no-op checks remain separate and unbounded by that prompt limit.

## Compact Retrieval Context

Every result carries selection ID, canonical IRI, kind, scope, source,
preferred label, at most five alternate labels, at most one 500-character
definition, score, reasons, and fingerprints. Structural context is bounded to
direct facts only:

- class: at most five direct superclasses;
- object property: at most five direct domains and five direct ranges;
- datatype property: at most five direct domains and five datatype ranges;
- individual: at most five asserted types;
- annotation property: no invented domain/range context.

All lists use canonical IRI order. Context is descriptive and conveys no write
authority; imported and FIBO records remain read-only.

## Phase 12 Contract Versions

Slice 1 will define these exact constants in the existing neutral pipeline
version family:

- `CANDIDATE_EXTRACTION_CONTRACT = "phase-12-candidate-extraction-v1"`;
- `NLP_RESOURCE_SET = "phase-12-opennlp-en-1.3-v1"`;
- `RETRIEVAL_QUERY = "phase-12-ontology-retrieval-query-v1"`;
- `RETRIEVAL_RANKING = "phase-12-ontology-retrieval-ranking-v1"`;
- `RETRIEVAL_RESULT = "phase-12-ontology-retrieval-result-v1"`;
- `GROUNDED_PROMPT = "phase-12-grounded-model-prompt-v1"`;
- `GROUNDED_REQUEST = "phase-12-grounded-model-request-v1"`;
- `GROUNDED_RESPONSE = "phase-12-grounded-model-response-v1"`;
- `GROUNDED_VERIFICATION = "phase-12-grounded-verification-v1"`;
- `PUBLIC_REVIEW = "phase-12-document-review-v1"`;
- `PROGRESS_COUNTS = "phase-12-analysis-counts-v1"`;
- `WORK_KEY = "phase-12-grounded-work-key-v1"`;
- `BENCHMARK_MANIFEST = "phase-12-two-document-benchmark-v1"`;
- `BENCHMARK_SCORING = "phase-12-benchmark-scoring-v1"`.

New stages are `CandidateExtraction`, `OntologyRetrieval`, `GroundedModeling`,
`GroundedVerification`, `SemanticAssembly`, `DeterministicVerification`, and
`AwaitingReview`. Candidate extraction, retrieval, grounded verification,
assembly, deterministic verification, and review are provider-neutral local
stages with zero provider attempts.

Public HTTP `apiVersion` remains additive `v1`; Phase 12 adds a separate
`contractVersion` and optional fields so existing Phase 11.5+ response readers
remain compatible. `NeedsInput` is a distinct recommendation status, not an
alias for `Blocked`, `ReviewOnly`, or the legacy `NeedsClarification` decision.
The count contract carries named evidence-block, retained/rejected NLP
candidate, retained/unresolved/rejected grounded-item, recommendation-by-status,
and expanded-typed-edit values. No count is inferred from another unit.

## Frozen Work Key And Freshness

The Phase 12 work key hashes project/task identity; document IDs, checksums, and
metadata; extractor and OCR versions; evidence inventory; NLP contract and
resource versions; candidate inventory; ontology/import fingerprints;
private-draft, shared-staging, proposal, same-task, and provenance fingerprints;
FIBO version/fingerprint; retrieval query/ranking/result versions and ordered
results; selected verified model ID; grounded prompt/request/response versions;
and semantic pattern/compiler versions. It contains no credential, raw
document, filesystem path, prompt body, or response body.

Any changed stale-sensitive input requires retrieval refresh before a provider
call. Any change after the call invalidates its result before compilation.
Reviewer-authorized refreshed selections produce a new work key and are
reverified; labels or IRIs cannot recover an expired selection ID.

## Safeguard Classification

| Existing bound | Classification | Phase 12 treatment |
| --- | --- | --- |
| 10 documents, 25 MiB each, 500 PDF pages, extraction/OCR/time limits | resource safeguard | preserve; fail visibly |
| 200 discoveries/document and 2,000/task | legacy product ceiling | retire for new Phase 12 candidate semantics; replace with bounded groups and explicit incomplete-work failure |
| 2,000 `MAX_DOCUMENT_CANDIDATES` | legacy product ceiling | do not apply to valid Phase 12 task totals |
| 300 connected items/provider response | per-response bound | preserve for one grounded response |
| 15 logical calls, 20 provider attempts, 3 automatic retries | emergency/provider safeguard | preserve initially; failure must be explicit and incomplete, never successful truncation |
| 1,000,000 response characters | per-response bound | preserve |
| 20 retrieval choices/candidate | prompt bound | add; does not bound full-state safety checks |
| 20 expanded edits/recommendation | atomic compilation boundary | allow deterministic dependency-safe splitting; not a task ceiling |
| 20 typed edits/draft batch | per-atomic-batch bound | preserve and batch without data loss |
| provenance record/byte bounds | durable resource safeguard | preserve; unrelated to prompt candidate limits |

`maximumAcceptedEdits` is deprecated additive compatibility data only and must
lose enforcement in Slice 7. Any emergency bound reached before complete
coverage produces an explicit incomplete-work state and processed/total counts.

## Benchmark Manifest

The Phase 12 supplement reuses the existing simple ontology, the historical
Phase 11.5 manifest, and both PDFs in place. Frozen hashes are:

- commercial payment PDF:
  `80652a29f41d2fba4fc4ad5ec4cb9013fd607b88bf382bf617582a5b64b91fae`;
- consumer lending PDF:
  `e191111a9aae8c28fce2e2505897f12c84f656aff80fd7cca4708e30ac15ddc0`;
- historical expectation manifest:
  `006e9786c4eb9c1fa194b35e07ca49db539a65708e5f7b5a3f8485fb70ef6ef6`.

The frozen ontology is the clean `examples/simple-ontology` project at the
slice base, with no staged entries or proposal. The Phase 12 supplement must
record the project graph/import fingerprint, empty current-work fingerprint,
pinned FIBO version, extracted evidence hashes, deterministic candidate
inventory, and ordered top-20 retrieval results generated by Slices 2 and 3.
Expected local/imported/FIBO top-20 targets are recorded only where those
scopes actually contain the verified entity; same-task expectations cover
cross-document Payment, Account, Payment Instruction, Payment Approval Record,
Supporting Record, Invoice, Payment Destination, Consumer Loan, Loan Servicing,
Payment Suspense, and Servicing Control. Positive expectations require reuse of
unambiguous existing targets and connected property context. Negative
expectations forbid duplicate-new versions of expected reuse targets and keep
linked-payment aggregation, separation of duties, temporal sequencing, and
conditional applicability non-executable. Scoring uses the exact thresholds in
the approved spec and ten frozen trials.

The controlled command and environment names remain:

```bash
OPENAI_API_KEY="<local credential>" \
  ENTIO_DOCUMENT_BENCHMARK=true \
  ENTIO_DOCUMENT_BENCHMARK_MODEL="<exact verified model id>" \
  ./gradlew :web-server:test --tests '*DocumentSemanticProviderBenchmarkTest*'
```

Neither required environment value was present during Slice 0, so no controlled
network benchmark ran. This is permitted for Slice 0 but blocks Slice 8 and
phase completion unless the user supplies both values or explicitly approves a
recorded waiver.

## Files Modified

- `docs/decisions/phase-12-slice-0-contract-and-dependency-audit.md`

No production, test, dependency, fixture, planning, or benchmark file changed.

## Tests And Verification

- `./gradlew :core-types:test` — passed.
- `./gradlew :semantic-engine:test` — passed.
- `./gradlew :web-server:test --tests '*DocumentAnalysis*' --tests '*DocumentIngestion*' --tests '*DocumentReview*'` — passed.
- `npm --prefix web-app test -- --run DocumentIngestionWorkspace` — passed, 8 tests.
- The two permanent PDFs and historical manifest were found exactly once at
  their approved paths and hashed above.
- `git diff --check` — required after this record is finalized.
- Controlled provider benchmark — not run; credential and exact model ID were
  not available.

## Git

The focused Slice 0 commit is created from this completed record on branch
`docs/phase-12-contract-audit`, then pushed before the required local
non-fast-forward merge into `main`.

## Assumptions, Limitations, And Decisions

- OpenNLP provides deterministic linguistic primitives; small bounded Entio
  rules identify business candidate spans but do not decide ontology meaning.
- Lexical retrieval may return no result or ambiguity. Kotlin exposes that
  outcome and does not infer semantic identity from token similarity.
- The existing compiler and typed translators accept a verified mapping to
  current semantic-plan references; no second compiler is required.
- No embeddings, vector store, external retrieval service, new persistence,
  automatic approval, raw RDF, or second write path is introduced.
- Slice 8 remains an explicit credential-gated release checkpoint.
