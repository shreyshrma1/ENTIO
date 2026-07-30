# Phase 11.5 Slice 0: Contract, Migration, Prompt, And Benchmark Audit

## Status

Approved on 2026-07-27.

This decision completes ExecPlan Slice 0. Production implementation may begin with
Slice 1 after this slice is verified, committed, pushed, and merged into local
`main`.

## Slice And Goal

ExecPlan Slice 0 approves the Phase 11.5 provider-neutral contracts, strict
provider schemas, prompt boundaries, limits, retry and reconsideration behavior,
migration rules, semantic reuse points, durable-provenance compatibility, and
permanent benchmark expectations.

No production code, test code, fixture, dependency, lockfile, README, or AGENTS
file changes in this slice.

## Repository Reuse Audit

The audit inspected the current Phase 11 implementation and approved these reuse
points:

- `DocumentIngestionContracts.kt` retains task, document, located text, evidence,
  authority, extraction, and applied-provenance identity.
- `DocumentRecommendationContracts.kt` remains readable for legacy tasks and
  records. New Phase 11.5 records belong in the focused
  `DocumentAnalysisPipelineContracts.kt` file approved by the ExecPlan.
- `DocumentEvidenceVerifier` remains the authority for exact document, block,
  offset, excerpt, extraction-method, and OCR-confidence verification.
- `DocumentOntologyMatcher` remains the deterministic canonical-match and
  duplicate-check foundation. Phase 11.5 alignment adds structured provider
  advice but never trusts it without Kotlin resolution.
- `DocumentRecommendationDraftTranslator` remains the typed-operation boundary.
  Slice 9 extends it to translate a complete connected recommendation atomically.
- `DeterministicIriGenerator` and the project `iriNamespace` remain the only
  final-IRI generation path for proposed local entities.
- `SemanticDescriptionService`, loaded import identities, current-work snapshots,
  `FiboSchemaSearchService`, and the pinned curated FIBO catalog remain the
  bounded semantic search sources.
- `AppliedDocumentProvenanceRepository` remains the project-authorized store for
  applied document-derived workflow provenance. It remains separate from
  ontology sources and from temporary ingestion storage.
- The existing project-scoped task, review, evidence, decision, draft,
  cancellation, and deletion route family remains the public boundary.
- The two benchmark PDFs and `examples/simple-ontology/ontology/simple.ttl` are
  used in place. No copies or alternate fixtures are created.

The current OpenAI adapter already demonstrates that the fixed Responses API can
enforce strict JSON Schema with `additionalProperties: false`, no tools,
`store: false`, a fixed HTTPS endpoint, bounded response parsing, cancellation,
and safe error codes. Separate strict schemas are therefore feasible without a
new provider, SDK, tool runtime, or apply path.

## Stage Contract Versions

All provider requests contain the task ID, frozen work-key hash, exact stage name,
prompt version, request schema version, and only the bounded records listed
below. Every response contains its response schema version. Unknown fields,
unknown enum values, oversized collections, invalid identifiers, and references
outside the supplied input fail the complete stage response.

| Stage | Prompt | Request schema | Response schema |
| --- | --- | --- | --- |
| Discovery | `phase-11-5-document-discovery-v2` | `phase-11-5-document-discovery-request-v2` | `phase-11-5-document-discovery-response-v2` |
| Connected modeling | `phase-11-5-connected-model-v1` | `phase-11-5-connected-model-request-v1` | `phase-11-5-connected-model-response-v1` |
| Model consolidation | `phase-11-5-model-consolidation-v1` | `phase-11-5-model-consolidation-request-v1` | `phase-11-5-model-consolidation-response-v1` |
| Reconciliation | `phase-11-5-reconciliation-v1` | `phase-11-5-reconciliation-request-v1` | `phase-11-5-reconciliation-response-v1` |
| Ontology alignment | `phase-11-5-ontology-alignment-v1` | `phase-11-5-ontology-alignment-request-v1` | `phase-11-5-ontology-alignment-response-v1` |
| Modeling critic | `phase-11-5-modeling-critic-v1` | `phase-11-5-modeling-critic-request-v1` | `phase-11-5-modeling-critic-response-v1` |
| Final planning | `phase-11-5-final-plan-v1` | `phase-11-5-final-plan-request-v1` | `phase-11-5-final-plan-response-v1` |

Model consolidation is conditional and is not an alias for the connected-model
schema. Deterministic verification is a Kotlin stage with no prompt or provider
schema.

### Discovery Context

One request is made per document. It contains:

- opaque task, document, and block IDs;
- bounded located text from only that document;
- page and section location metadata;
- extraction method and OCR confidence;
- user-supplied authority and applicability metadata;
- included-block and omitted-block counts.

It contains no applied ontology, imports, FIBO data, current work, prior
provenance, candidate category, executable operation, target source, domain,
range, or final IRI.

The response inventory uses these discovery kinds:

- concept;
- definition;
- individual;
- relationship;
- attribute;
- value;
- requirement;
- control;
- conditional rule;
- conflict;
- ambiguity;
- metadata.

Every item has one content classification (`BusinessContent` or
`AdministrativeMetadata`), one assertion classification (`ExplicitFact`,
`ImpliedFact`, `ModelInterpretation`, or `IllustrativeExample`), a concise
description, exact evidence references, related discovery IDs, evidence
confidence, and an individual classification when applicable. It produces no
ontology edit.

### Connected-Model Context

Connected modeling receives only verified discovery records and their verified
evidence identities. It receives no ontology match. Deterministic packing keeps
whole discovery records together. When one request cannot hold the inventory,
the server sends ordered chunks and then one consolidation request containing the
bounded chunk-model records and complete discovery trace.

Connected model items may represent classes, object properties, datatype
properties, annotation properties, hierarchy, individuals, types, assertions,
values, shapes, constraints, and complex review-only rules. Model-local
references are scoped to this intermediate model, are declared before use, and
must trace every modeled item to at least one verified discovery.

### Reconciliation Context

Reconciliation receives verified discoveries, the consolidated connected model,
bounded authority and applicability facts, and project-scoped summaries of prior
applied provenance. It compares:

- discovery to discovery;
- model item to model item;
- model item to prior applied provenance.

It may report duplicate, alternate-label, support, refinement, conflict,
supersession claim, or context-specific interpretation. It never resolves a
conflict or treats a newer date alone as authority.

### Ontology-Alignment Context

Alignment receives the reconciled connected model and Entio-built bounded
snapshots of:

- applied local ontology entities;
- configured imports;
- the current user's private draft;
- shared staged work;
- the current proposal;
- other modeled items in this task;
- project-scoped durable prior provenance;
- approved curated FIBO results.

It may advise reuse, extend, revise, create, split, merge, conflict review, leave
unchanged, or unsupported. Existing-entity, source, current-work, prior-record,
and FIBO references are server-issued opaque or canonical references. Kotlin
resolves every advised match and records graph and current-work fingerprints.

### Critic Context

The critic receives verified discoveries, the connected model, reconciliation,
alignment, and the same bounded ontology snapshot used for alignment. It returns
findings targeting supplied IDs only, with an action of approve, revise, split,
replace, downgrade, reject, or request clarification and a concise reason. It
does not mutate upstream records and does not store or expose hidden
chain-of-thought.

### Final-Plan Context

Final planning receives all verified upstream stage records and bounded user
clarification. It returns grouped recommendations, operation dependencies,
review-only findings, evidence links, critic dispositions, coverage
dispositions, confidence dimensions, and target-source advice. It may use
temporary references but may not provide a final IRI for a temporary item.

## Neutral Contract And Invariant Inventory

Slice 1 must define immutable provider-neutral records for:

- stage name, stage state, safe failure, progress, timing, attempt counts, model
  ID, prompt/schema versions, and SHA-256 input/output hashes;
- discovery identity, kind, content classification, assertion classification,
  evidence confidence, related discoveries, and individual classification;
- connected-model item identity, kind, rationale, discovery trace, local
  references, and review-only eligibility;
- reconciliation identity, relationship kind, source and target references,
  context explanation, evidence, and human-decision-required state;
- alignment identity, action, advised target, Kotlin-resolved matches, target
  source choices, rationale, and ontology-fit input;
- critic finding identity, action, target, concise reason, confidence downgrade,
  and final disposition;
- three confidence dimensions and Kotlin-owned minimum overall confidence;
- final-plan identity, recommendation status, atomic operations, dependencies,
  review-only findings, coverage outcomes, and expanded typed-edit count;
- temporary-reference identity, kind, local name, declaration position, and
  resolved final IRI held only at the draft-preparation boundary;
- frozen analysis work key and additive durable stage provenance.

Fixed records use enums or sealed types instead of loose state strings.
Collections are immutable, bounded, unique, and in deterministic stable order.
Public APIs have explicit visibility and return types. Core contracts contain no
Ktor, Jackson, provider, PDFBox, POI, Tesseract, Jena, React, path, credential,
or transport assumptions.

## Temporary References And Dependencies

The exact case-sensitive grammar is:

```text
new:<kind>:<localName>
```

Approved kinds are:

- `class`;
- `objectProperty`;
- `datatypeProperty`;
- `annotationProperty`;
- `individual`;
- `shape`.

`localName` starts with an ASCII letter and otherwise contains only ASCII
letters, digits, and underscores. The reference is unique within one final plan.
It cannot shadow or contain an existing IRI. Labels are never references.

Declarations precede use. References point only to declarations in the same
plan, kinds must be compatible with the operation operand, and the dependency
graph must be acyclic. A replacement or split is produced only by final
planning. Kotlin resolves references with `DeterministicIriGenerator`, preserves
dependency order, checks current symbols for collisions, and never writes the
temporary value into a typed edit or source file.

One invalid, unresolved, duplicate, forward-invalid, cyclic, or kind-incompatible
reference blocks the complete recommendation. Valid sibling recommendations
remain reviewable.

## Limits, Calls, Retries, And Time

| Limit | Approved value |
| --- | ---: |
| Documents | 10 per task |
| Discovery items | 200 per document; 2,000 per task |
| Connected-model items | 300 per task |
| Final recommendations | 100 per task |
| Expanded typed edits | 20 per atomic recommendation; 100 per task |
| Evidence blocks | 8 per recommendation |
| Planned logical calls | 15 for a ten-document task |
| Provider attempts | 20 per task |
| Provider timeout | 120 seconds per attempt |
| Retry reserve | 3 attempts per task |
| Reconsideration reserve | 2 attempts per task |
| Analysis wall time | 30 minutes before review |
| Prompt input | 60,000 characters per call |
| Provider response | 1,000,000 characters per call |

The initial call formula is:

```text
document count
+ connected-model chunk-call count
+ one consolidation call when chunk count is greater than one
+ one reconciliation call
+ one alignment call
+ one critic call
+ one final-plan call
```

Every attempted provider request counts. Before starting a request, the server
reserves enough attempts for all mandatory downstream calls. A transient
timeout, 408, 429, 500, 502, 503, or 504 may receive one exact-input retry for
that logical call, with at most three retry attempts across the task.
Authorization, refusal, content-filter, malformed-schema, evidence, reference,
limit, and permanent 4xx failures are not retried.

Review-time reconsideration uses exactly two calls: a reconsideration critic over
the frozen alignment, followed by a new final plan. The critic may recommend a
different final alignment choice, but discovery, connected modeling,
reconciliation, and the alignment-stage record do not rerun or mutate. A
clarification that would change those frozen records requires a new task.
Reconsideration starts only when both reserved attempts remain.

The 30-minute analysis clock starts when frozen analysis inputs are recorded and
ends when the task reaches awaiting review or a terminal analysis state. It
includes provider waits, retries, deterministic packing, stage verification, and
final Kotlin verification. Human review time is excluded.

## Approved Executable Operation Matrix

Phase 11.5 may translate only through current typed operations.

Ontology operations:

- `CreateClassEdit`;
- `CreateObjectPropertyEdit`;
- `CreateDatatypePropertyEdit`;
- `CreateIndividualEdit`;
- `SetEntityLabelEdit`;
- `AddSuperclassEdit`;
- `RemoveSuperclassEdit`;
- `SetPropertyDomainEdit`;
- `RemovePropertyDomainEdit`;
- `SetPropertyRangeEdit`;
- `RemovePropertyRangeEdit`;
- `AssignTypeEdit`;
- `AddObjectPropertyAssertionEdit`;
- `AddDatatypePropertyAssertionEdit`.

Semantic operations:

- `SemanticEditRequest.CreateAnnotationProperty`;
- `SemanticEditRequest.AddDefinition`;
- `SemanticEditRequest.ReplaceDefinition`;
- `SemanticEditRequest.RemoveDefinition`;
- `SemanticEditRequest.AddAlternateLabel`;
- `SemanticEditRequest.ReplaceAlternateLabel`;
- `SemanticEditRequest.RemoveAlternateLabel`;
- `SemanticEditRequest.AddAnnotation`;
- `SemanticEditRequest.RemoveAnnotation`.

External reuse uses the existing approved `ExternalProposalIntentTranslator`.

SHACL operations:

- `TypedShaclEdit.CreateNodeShape`;
- `TypedShaclEdit.CreatePropertyShape`;
- `TypedShaclEdit.UpdateConstraint`;
- `TypedShaclEdit.RemoveConstraint`;
- `TypedShaclEdit.UpdateShapeLabel`;
- `TypedShaclEdit.DeleteShape`.

SHACL constraint kinds are exactly `MinCount`, `MaxCount`, `Datatype`, `Class`,
`MinInclusive`, `MaxInclusive`, and `Pattern`.

Removal and revision operations require an exact current value and explicit
review acceptance. General deletion, raw RDF, arbitrary annotations, unsupported
OWL, SPARQL, aggregation, temporal sequencing, separation of duties, or
multi-record conditions are not executable. Unsupported meaning remains
review-only.

## Discovery Coverage And Critic Dispositions

Every verified discovery has exactly one disposition:

- executable recommendation;
- review-only finding;
- merged into another discovery;
- duplicate;
- administrative metadata;
- illustrative example;
- unsupported;
- rejected with rationale.

Every critic finding has exactly one final disposition:

- accepted and incorporated;
- rejected with concise rationale;
- unresolved.

Missing, duplicate, or contradictory dispositions fail final-plan verification.
An unresolved critic finding blocks its affected recommendation.

## Individuals And Reviewer Gates

Every possible individual is classified as `Illustrative`, `Production`,
`Ambiguous`, or `Unknown`.

All document-derived individual creation requires an explicit reviewer
confirmation. Illustrative, ambiguous, and unknown individuals are
non-executable by default. Changing one to production requires a separate
confirmation and rationale. The decision, actor, classification, and evidence
are retained in provenance.

## Confidence

Evidence, modeling, and ontology-fit confidence are integers from 0 through 100.
Kotlin calculates overall confidence as their minimum. A provider-supplied
overall value is ignored or rejected. The critic may lower but never raise a
dimension. An unresolved confidence dispute blocks the recommendation. Overall
confidence below 60 retains the current mandatory-clarification gate and never
grants approval.

## Matching, IRI, And Current-Work Reuse

Alignment and final verification reuse:

- semantic descriptors and search for local and imported entities;
- import-closure and source identities;
- private draft, shared staging, and current proposal snapshots;
- normalized typed-operation identities for duplicate and no-op checks;
- project-scoped prior applied provenance;
- curated-only pinned FIBO search and existing external-reuse translation;
- writable ontology and SHACL source roles;
- `DeterministicIriGenerator`, project namespace configuration, label
  resolution, and current symbol collision checks;
- existing preview, diff, validation, reasoning, SHACL impact, atomic apply,
  reload, and rollback services.

Provider-selected matches, targets, sources, and fit scores are advisory. Kotlin
must resolve them within the frozen project and current-work fingerprints.

## Public Routes And Migration

The current project-scoped route family remains:

```text
POST   /api/v1/projects/{projectId}/document-ingestion/tasks
GET    /api/v1/projects/{projectId}/document-ingestion/tasks
GET    /api/v1/projects/{projectId}/document-ingestion/tasks/{taskId}
GET    /api/v1/projects/{projectId}/document-ingestion/tasks/{taskId}/review
GET    /api/v1/projects/{projectId}/document-ingestion/tasks/{taskId}/evidence/{evidenceId}
POST   /api/v1/projects/{projectId}/document-ingestion/tasks/{taskId}/recommendations/{recommendationId}/decision
POST   /api/v1/projects/{projectId}/document-ingestion/tasks/{taskId}/draft
POST   /api/v1/projects/{projectId}/document-ingestion/tasks/{taskId}/cancel
DELETE /api/v1/projects/{projectId}/document-ingestion/tasks/{taskId}
```

Responses gain additive stage, grouped-recommendation, confidence, critique,
coverage, review-only, dependency, and decision fields. Authorization and
not-found behavior remain unchanged.

During implementation, existing Phase 11 tasks and completed records remain
readable. After activation:

- all new tasks use only Phase 11.5;
- incomplete legacy tasks remain readable but cannot resume;
- old applied provenance remains readable and available to reconciliation;
- the Phase 11 single-stage prompt, schema, and production execution path are
  removed after verification;
- no automatic or silent fallback remains;
- no second production pipeline or apply path is retained.

## Invalidation And Provenance Migration

The frozen work key includes project and task IDs, sorted document checksums,
authority/applicability metadata, extractor versions, applied ontology
fingerprint, private draft fingerprint, shared staging/proposal fingerprint,
pinned FIBO version, prior-provenance snapshot hash, selected provider/model,
and every prompt/request/response schema version.

A change to any frozen value invalidates downstream results. Cancellation or
restart makes temporary stage records non-draftable. Complete prompts,
responses, discoveries, connected models, reconciliation, alignment, critic,
and final plans remain temporary in memory.

`AppliedDocumentProvenance` receives additive fields with defaults:

- stage names and prompt/response schema versions;
- selected model ID;
- stage input/output hashes;
- confidence dimensions;
- critic disposition IDs;
- coverage disposition IDs;
- related review-only findings for the applied recommendation;
- individual classification and confirmation when relevant.

The repository keeps existing schema-1 snapshots readable without rewriting old
records. New optional fields decode to empty or absent values for Phase 11
records. Complete prompts, provider payloads, documents, page images, unrelated
recommendations, and temporary paths are never retained.

Review-only findings related to an applied executable recommendation remain
visible in proposal/apply history and later reconciliation but are never written
to ontology or SHACL source files. Standalone review-only findings cannot enter
an ontology proposal.

## Permanent Two-PDF Benchmark

The benchmark uses these existing files in place:

- `examples/simple-ontology/documents/consumer-lending-servicing-compliance-standard.pdf`;
- `examples/simple-ontology/documents/commercial-account-and-payment-authorization-policy.pdf`;
- `examples/simple-ontology/ontology/simple.ttl`.

### Approved Positive Meanings

| Meaning | Approved labels or aliases | Required structural expectation |
| --- | --- | --- |
| Payment | `Payment`, `Outgoing Payment`, `Payment Instruction` | A payment is modeled independently of `Account` and can reference its funding account, destination, support, and approval record. |
| Payment approval record | `Payment Approval Record`, `Payment Review Record`, `Payment Decision Record` | A payment links to an approval/review record; the record links to reviewer or approver identity and decision facts. |
| High-value payment approval | `High-Value Payment Approval`, `Separate Approval for High-Value Payment` | Evidence records the USD 25,000 threshold and separate final approval. Linked-payment aggregation remains review-only. |
| Consumer-loan servicing control | `Consumer Loan Servicing Control`, `Loan Servicing Control`, `Servicing Control` | Controls apply to servicing activity or controlled records and trace to control IDs and evidence; they are not a `Customer`-to-`Loan` relationship. |
| Payment suspense control | `Payment-to-Loan Validation`, `Payment Suspense Investigation` | Accepted payments reference the intended loan or remain in suspense for investigation. |

The commercial-policy example individuals and facts include:

- Harbor Point Dental Group LLC as the illustrative customer or organization;
- Harbor Point Operating Account `ACCT-884210` as an illustrative account owned
  by that organization;
- Elena Ruiz as an illustrative authorized initiator who is not the owner;
- Summit Medical Supply as an illustrative payment destination;
- Invoice `INV-44719` as illustrative support for the payment;
- the illustrative USD 28,460 payment on 2026-09-14;
- Marcus Lee as the illustrative analyst who verified authorization and invoice;
- Priya Nair as the illustrative manager who gave final approval.

The required relationship pattern keeps ownership, initiation, verification,
destination, invoice support, and final approval distinct. It must not infer
that an initiator owns the account or that an invoice approves a payment.

Exact provenance must retain the document, PDF page, located block, offsets,
excerpt, extraction method, and OCR confidence where applicable. The
high-value threshold and scope occur on pages 1–2 of the commercial policy.
Servicing controls and requirements occur on pages 1–3 of the consumer
standard.

### Approved Negative Meanings

The benchmark fails if the final executable plan:

- creates `Compliance Status` from the document-control value `Status:
  Approved`;
- turns either document's effective date, review date, version, owner, or
  approval authority into a business property without independent body evidence;
- creates a `Customer → Loan` property meaning “has servicing compliance”;
- attaches the high-value payment rule to `Account` because `Payment` is absent;
- models `Account → Invoice` as payment approval;
- treats Elena Ruiz as the account owner;
- treats illustrative people, organizations, accounts, payments, or invoices as
  production records without explicit confirmation;
- makes linked-payment aggregation, separation of initiation and final approval,
  temporal sequencing, or conditional service applicability a simple datatype
  property or unsupported constraint.

Linked-payment aggregation, role separation, and the context-specific USD
15,000 Accounts Payable threshold remain review-only unless an approved current
typed SHACL operation can express the complete meaning without loss. The
benchmark must preserve them even when they are non-executable.

## Acceptance Traceability

| Spec criterion | Implementation slice | Required proof |
| ---: | --- | --- |
| 1–2 | 2, 8 | Separate discovery call with no ontology/edit context; orchestrator stage-order tests |
| 3 | 3, 11 | Connected-model fixtures introduce missing support concepts |
| 4–5 | 1, 7, 9 | Grouped operation, dependency, temporary-reference, and atomic translation tests |
| 6 | 7, 9 | Reference, duplicate, source, freshness, and unsupported-operation failures |
| 7 | 2, 3, 6, 10, 11 | Metadata classification, critic, presentation, and negative benchmark |
| 8 | 1, 9, 10, 11 | Individual classification, explicit confirmation, UI, and benchmark |
| 9 | 3, 7, 9, 10, 11 | Review-only contract, verification, provenance, presentation, benchmark |
| 10 | 1, 7, 11 | One deterministic coverage disposition per verified discovery |
| 11–12 | 6, 7, 10 | Critic actions, final dispositions, blockers, and grouped review |
| 13 | 1, 6, 10 | Three dimensions and Kotlin minimum calculation |
| 14 | 7, 10, 11 | Exact grouped preview, provenance, impact, accessibility, and E2E |
| 15 | 2–8, 10, 11 | Stage versions, timing, bounds, cancellation, status, and security |
| 16–17 | 9, 11 | Existing staging/proposal path and no-source-write-before-approval tests |
| 18 | 0, 11 | Approved aliases, structural patterns, positive and negative expectations |
| 19 | 4, 8, 9, 11 | Legacy provenance decoding, reconciliation, apply retention, restart test |
| 20 | 11 | Assistant, non-document, CLI, VS Code, proposal, reasoning, SHACL, map, apply, reload, and rollback regressions |
| 21 | 1, 7, 9, 11 | Expanded edit counts, atomic batch boundary, split or block behavior |
| 22 | 8, 11 | New-pipeline-only activation and absence of the old single-stage production path |

## Files Modified

- `docs/architecture/phase-11.5-scope.md`;
- `docs/specs/0021-phase-11.5-multi-stage-ai-modeling-and-connected-ontology-change-sets.md`;
- `docs/execplans/0021-phase-11.5-multi-stage-ai-modeling-and-connected-ontology-change-sets.md`;
- `docs/decisions/phase-11.5-slice-0-contract-audit.md`.

## Tests And Verification

No test code changes in Slice 0. Verification is documentation and source-contract
review only.

Required commands:

```bash
git diff --check
git status --short
```

Both commands must pass before commit and again after the local merge.

## Commit

This completion record is included in the focused Slice 0 commit
`Approve Phase 11.5 pipeline contracts` on
`feature/phase-11-5-slice-0-contract-audit`. The commit hash is recorded in Git
history.

## Assumptions And Limitations

- Phase 11.5 remains an in-memory analysis workflow until human-approved applied
  provenance is written.
- The task-selected model is used for every stage; separate prompt and schema
  contracts provide role separation, not a second model.
- Deterministic checks prove structure, evidence, bounds, and freshness but do
  not prove that a modeling judgment is conceptually correct.
- The ontology snapshot is bounded. Truncation is visible and blocks unsupported
  certainty.
- Current typed SHACL cannot express aggregation, temporal sequence, separation
  of duties, or several conditional multi-fact rules.
- A controlled real-provider smoke is separate from credential-free
  deterministic CI and never records credentials, full prompts, provider
  payloads, or documents.
