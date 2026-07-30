# Phase 11.5+ Slice 0 Contract Audit

Status: Complete

Date: 2026-07-30

## ExecPlan Slice Implemented

Slice 0: Freeze The Baseline And Audit Current Contracts.

## Goal

Record the reliable Phase 11.5 baseline and settle the exact contract versions,
compatibility rules, supported SHACL patterns, completeness categories,
review-only retention boundary, benchmark inputs, and verification commands
before Phase 11.5+ production contracts are introduced.

This slice changes no production or test code.

## Baseline

The slice started from local `main` commit
`b042ddb48d3251e4346f6a91e50927c3892215f2`. At slice start, local `main` and
`origin/main` matched, and the working tree was clean.

The implemented Phase 11.5 pipeline remains:

```text
verified extracted text
→ per-document discovery
→ connected semantic synthesis and conditional consolidation
→ ontology-aware final planning and modeling review
→ deterministic change-set verification
→ grouped human review
→ existing typed private-draft and proposal workflow
```

Phase 11 upload, extraction, OCR, evidence, authorization, proposal, apply,
reload, rollback, and durable applied-provenance behavior remains unchanged.

## Current Final-Plan Producers And Consumers

### Producer path

1. `DocumentIngestionOrchestrator` assembles the verified upstream task state
   and invokes `DocumentFinalPlanningService`.
2. `DocumentFinalPlanningService` constructs
   `DocumentFinalPlanningRequest`, binds the selected model and frozen work key,
   applies call budgets, and invokes the final-planning provider.
3. `OpenAiDocumentAnalysisClient` implements
   `DocumentFinalPlanningProvider`. It sends the strict final-plan request,
   parses the provider response, canonicalizes evidence and coverage, and
   returns `DocumentFinalPlanningResponse`.
4. `DocumentFinalPlanningService` checks response versions and references,
   applies bounded correction behavior when allowed, and calls
   `DocumentChangeSetPlanVerifier`.
5. `DocumentChangeSetPlanVerifier` verifies the low-level operations, generates
   final IRIs, and returns `DocumentVerifiedFinalPlan` with impact summaries.

### Consumer path

1. `DocumentIngestionOrchestrator` stores the verified plan in the temporary
   review workspace.
2. `DocumentReviewWorkspace` converts the plan to authorized public review DTOs
   and displays resolved IRIs, operation descriptions, evidence, confidence,
   critique, gates, and impact summaries.
3. `DocumentRecommendationDraftTranslator.translateConnected` converts
   accepted executable or mixed recommendations into existing typed ontology,
   semantic, SHACL, or external-reuse operations.
4. Existing staging and proposal services preview, validate, review, approve,
   apply, reload, and roll back the resulting typed operations.
5. `DocumentApplyProvenanceCoordinator` records successfully applied
   document-derived changes and their related review-only findings through
   `AppliedDocumentProvenanceRepository`.

The current low-level provider contract is also referenced by focused contract,
provider-adapter, orchestration, review, translation, proposal-integration, and
provenance tests. Those consumers must migrate before the legacy final-plan
provider contract is retired.

## Current Operation-To-Typed-Edit Mapping

`DocumentRecommendationDraftTranslator` currently maps verified operation kinds
as follows:

| Current operation | Existing typed result |
| --- | --- |
| `CreateClass` | `CreateClassEdit` |
| `CreateObjectProperty` | `CreateObjectPropertyEdit` |
| `CreateDatatypeProperty` | `CreateDatatypePropertyEdit` |
| `CreateAnnotationProperty` | `SemanticEditRequest.CreateAnnotationProperty` |
| `CreateIndividual` | `CreateIndividualEdit` |
| `SetEntityLabel` | `SetEntityLabelEdit` |
| `AddSuperclass` / `RemoveSuperclass` | `AddSuperclassEdit` / `RemoveSuperclassEdit` |
| `SetPropertyDomain` / `RemovePropertyDomain` | `SetPropertyDomainEdit` / `RemovePropertyDomainEdit` |
| `SetPropertyRange` / `RemovePropertyRange` | `SetPropertyRangeEdit` / `RemovePropertyRangeEdit` |
| `AssignType` | `AssignTypeEdit` |
| `AddObjectPropertyAssertion` | `AddObjectPropertyAssertionEdit` |
| `AddDatatypePropertyAssertion` | `AddDatatypePropertyAssertionEdit` |
| `AddDefinition` / `ReplaceDefinition` / `RemoveDefinition` | matching `SemanticEditRequest` |
| `AddAlternateLabel` / `ReplaceAlternateLabel` / `RemoveAlternateLabel` | matching `SemanticEditRequest` |
| `AddAnnotation` / `RemoveAnnotation` | matching `SemanticEditRequest` |
| `ReuseExternal` | existing `ExternalProposalIntent` handoff |
| `CreateNodeShape` | `TypedShaclEdit.CreateNodeShape` |
| `CreatePropertyShape` | `TypedShaclEdit.CreatePropertyShape` |
| `UpdateShaclConstraint` | `TypedShaclEdit.UpdateConstraint` |
| `RemoveShaclConstraint` | `TypedShaclEdit.RemoveConstraint` |
| `UpdateShapeLabel` | `TypedShaclEdit.UpdateShapeLabel` |
| `DeleteShape` | `TypedShaclEdit.DeleteShape` |

Phase 11.5+ compilation must continue to use these typed boundaries. It must not
produce graph changes, RDF, Turtle, SPARQL, or source writes directly.

## Approved Initial SHACL Pattern Registry

The current public typed SHACL API and `TypedShaclEditTranslator` support these
constraint kinds:

| Kind | Required value | Approved Phase 11.5+ use |
| --- | --- | --- |
| `MinCount` | nonnegative integer | required-relationship and minimum-cardinality patterns |
| `MaxCount` | nonnegative integer | maximum-cardinality patterns |
| `Datatype` | datatype IRI | datatype-property value constraints |
| `Class` | class IRI | object-value class constraints |
| `MinInclusive` | valid decimal lexical form | standalone lower numeric thresholds |
| `MaxInclusive` | valid decimal lexical form | standalone upper numeric thresholds |
| `Pattern` | nonblank text | explicit lexical patterns only |

The existing typed operations can create a class-targeted node shape, create a
direct-property shape with one supported constraint, update or remove one
supported constraint, update a shape label, and delete a round-trippable shape.

The initial deterministic semantic registry may therefore compile:

- a required relationship as a direct property shape with `MinCount = 1`;
- a supported datatype or class value restriction;
- a standalone inclusive numeric threshold when it preserves the complete
  intended meaning;
- an explicit lexical pattern.

Conditional thresholds, linked-record aggregation, separation of duties,
temporal sequencing, procedural rules, cross-record comparisons, and any rule
whose meaning cannot be preserved by the typed forms above remain review-only.
This satisfies the Slice 0 SHACL stop condition without adding new SHACL
semantics.

## Public Review DTO Compatibility

`DocumentReviewWorkspaceResponse` and `DocumentReviewRecommendation` already
carry additive optional Phase 11.5 information, including:

- grouped recommendation status;
- evidence summaries;
- three confidence dimensions and overall confidence;
- critic dispositions;
- review-only findings;
- individual gates;
- compiled operation descriptions;
- resolved final IRIs through operation descriptions;
- validation, reasoning, SHACL, and semantic-diff summaries.

They do not yet carry:

- optional compilation confidence;
- separate semantic-coverage numerator, denominator, and percentage;
- separate compilation-success numerator, denominator, percentage, and safe
  failure counts;
- an explicit semantic-intent item list;
- a public final-reference mapping;
- an explicit `Retain as documented rule` decision.

The Phase 11.5+ review boundary will use a versioned additive response. Existing
Phase 11 and 11.5 records remain readable with absent new fields. React will
render server-calculated results and will not compile items, resolve references,
calculate metrics, or decide semantic validity.

## Important-Discovery Contract

The ExecPlan is authoritative. A verified discovery counts in the semantic
coverage denominator when it is business content and maps to:

- `Concept`;
- `Relationship`;
- `Requirement`;
- `Control`;
- `ConditionalRule`;
- `Attribute` or `Value` when it represents a business fact;
- `Definition`;
- `Individual` when its individual classification is not `Illustrative`.

Administrative metadata and illustrative examples are excluded from the
denominator, but every one still requires exactly one explicit disposition.
`Conflict`, `Ambiguity`, `Role`, and other verified categories also require an
explicit disposition but are not added to the initial denominator unless the
approved ExecPlan is amended.

A discovery cannot be excluded because it is difficult to model or compile.
The initial Phase 11.5+ coverage enum must add explicit `MatchedExisting` and
`Blocked` dispositions while preserving existing disposition readability.

## Review-Only Retention Contract

The existing applied-provenance boundary already stores review-only findings
related to successfully applied executable or mixed recommendations.

Phase 11.5+ will reuse `AppliedDocumentProvenanceRepository` for a second
explicit case: a reviewer chooses **Retain as documented rule** for a pure
review-only finding. The retained record:

- remains non-executable;
- contains verified evidence and project authorization;
- is stored separately from ontology sources;
- is available to later document reconciliation;
- creates no typed edit, proposal, or apply event.

Rejected and unretained review-only findings remain temporary. No new database,
repository abstraction, document store, or general automatic finding
persistence is required.

## Contract Versions

### Versions to retire for new tasks after migration

- prompt: `phase-11-5-final-plan-v1`;
- request: `phase-11-5-final-plan-request-v1`;
- response: `phase-11-5-final-plan-response-v1`.

Legacy records and applied provenance remain readable. The old provider path
must not remain a fallback for new tasks after Slice 5 activation.

### Phase 11.5+ versions

- semantic-plan prompt:
  `phase-11-5-plus-semantic-plan-prompt-v1`;
- semantic-plan request:
  `phase-11-5-plus-semantic-plan-request-v1`;
- semantic-plan response:
  `phase-11-5-plus-semantic-plan-response-v1`;
- semantic pattern registry:
  `phase-11-5-plus-pattern-registry-v1`;
- compiler result:
  `phase-11-5-plus-compiler-result-v1`;
- public document review DTO:
  `phase-11-5-plus-document-review-v1`.

Deterministic completeness and compilation stages make no provider call.
Unchanged upstream Phase 11.5 discovery and connected-model contract versions
remain active.

## Benchmark Baseline

The permanent benchmark reuses these files in place:

- `examples/simple-ontology/documents/consumer-lending-servicing-compliance-standard.pdf`;
- `examples/simple-ontology/documents/commercial-account-and-payment-authorization-policy.pdf`;
- `examples/simple-ontology/ontology/simple.ttl`;
- `web-server/src/test/resources/document-ingestion/phase-11.5-two-pdf-expectations.json`.

No fixture is copied.

The deterministic manifest verifies:

- accepted aliases and required terms for Payment, Payment Approval Record,
  high-value payment approval, consumer-loan servicing control, and payment
  suspense control;
- the approved illustrative individuals;
- prohibited executable patterns;
- linked-payment aggregation, separation of initiation and final approval,
  temporal sequencing, and conditional service applicability as review-only
  meaning;
- absence of metadata-derived `Compliance Status` and `has servicing
  compliance` in the example ontology;
- the current logical-call bounds.

The previous Phase 11.5 completion record confirms the full deterministic
benchmark and regression suite passed. It also records that no controlled live
provider run was possible because no verified server-memory credential was
available.

At this Slice 0 baseline:

- `OPENAI_API_KEY` is absent;
- explicit `ENTIO_DOCUMENT_BENCHMARK` configuration is absent;
- no live provider call was made;
- no credential, prompt, document payload, or provider response was recorded.

The approved Phase 11.5+ ten-run targets remain the Slice 7 release gate. The
absence of credentials does not block implementation or offline verification,
but it blocks declaring Phase 11.5+ complete unless the user explicitly approves
and records a waiver.

## Deterministic Benchmark Matching Rules

The initial harness must:

- freeze document checksums, extraction version, ontology/current-work
  fingerprints, retained-provenance snapshot, model ID, prompt versions, and
  limits for every compared run;
- match approved meanings by normalized accepted alias plus required evidence
  terms and required structural roles, not label alone;
- verify exact evidence document, page or block, offsets, excerpt, extraction
  method, and OCR confidence when applicable;
- count required concept and major-relationship occurrence independently;
- reject every prohibited executable pattern;
- require unsupported complex meanings to remain review-only;
- require every verified discovery to have exactly one disposition;
- keep illustrative individuals behind explicit confirmation;
- calculate supported compilation success from compiler-eligible semantic
  items, excluding correctly review-only or unsupported items;
- never apply ontology changes during a benchmark;
- keep deterministic fixture tests offline in normal verification.

## Provider Usage Availability

Current document stage records expose:

- stage start and finish times;
- `durationMillis`;
- selected model ID;
- prompt, request, and response versions;
- provider attempt count;
- input and output hashes;
- safe status codes.

The current document adapter does not retain provider input-token,
output-token, total-token, or actual monetary-cost fields. Initial model
comparison can therefore report model ID, attempts, duration, success,
semantic coverage, compilation success, consistency, and prohibited outcomes.
Token or cost reporting is added only if the existing provider response exposes
those values through an approved bounded contract in a later slice; cost must
not be estimated or invented.

## Existing Verification Commands

Slice-level commands:

```bash
./gradlew :core-types:test
./gradlew :semantic-engine:test
./gradlew :web-server:test --tests '*DocumentAnalysis*' --tests '*DocumentIngestion*'
npm --prefix web-app test -- --run DocumentIngestionWorkspace
```

Full-phase commands remain:

```bash
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm audit --omit=dev && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```

## Tests Added Or Updated

None. Slice 0 is an audit-only implementation unit and forbids production and
test-code changes.

## Verification Results

| Command | Result |
| --- | --- |
| `./gradlew :core-types:test` | Passed; build successful |
| `./gradlew :semantic-engine:test` | Passed; build successful |
| `./gradlew :web-server:test --tests '*DocumentAnalysis*' --tests '*DocumentIngestion*'` | Passed; build successful |
| `npm --prefix web-app test -- --run DocumentIngestionWorkspace` | Passed; 1 file and 7 tests |
| `git diff --check` | Passed |

No controlled provider benchmark was run because neither verified credentials
nor explicit benchmark configuration was available.

## Stop-Condition Review

- The current typed SHACL API supports approved initial constraint patterns.
- Replacing the final-plan contract does not require intake, extraction, OCR, or
  apply-path changes.
- Reviewer-approved pure review-only retention can reuse the existing applied
  document provenance repository and does not require a new persistence layer.
- No scope expansion or planning amendment is required.

No Slice 0 stop condition was triggered.

## Files Modified

- `docs/decisions/phase-11.5-plus-slice-0-contract-audit.md`

## Git

This completion record is included in the focused Slice 0 commit. The completed
slice branch is pushed before local non-fast-forward merge into `main`.

## Assumptions, Limitations, And Follow-Up

- The exact seven typed SHACL constraint kinds above are the complete initial
  executable SHACL registry; other rule meaning remains review-only.
- Provider token and monetary-cost data is unavailable in the current document
  task contracts.
- The live ten-run baseline remains unavailable without a verified credential
  and explicit benchmark configuration.
- Slice 1 may add only neutral contracts and tests. It must not implement
  completeness calculations, compilation, provider parsing, or review UI.

## Notable Decisions

- The newer ExecPlan's important-discovery list is authoritative over the
  broader earlier spec wording.
- Public review evolution is versioned and additive.
- Pure review-only retention reuses existing durable provenance and requires an
  explicit reviewer decision.
- Kotlin owns all new completeness, compilation, reference, IRI, ordering,
  metric, and confidence behavior.
