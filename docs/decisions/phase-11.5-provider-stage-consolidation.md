# Phase 11.5 Provider-Stage Consolidation

## Context

The original Phase 11.5 runtime used separate provider calls for connected
modeling, reconciliation, ontology alignment, modeling critique, and final
planning. Throughput testing showed that later calls repeatedly transmitted much
of the same verified discovery, model, provenance, and ontology context. For two
documents, the ordinary path required about seven calls before semantic repair.

Deterministic validation also retried a rejected connected-model response without
attaching the validation finding, so the retry was not an informed correction.

## Decision

The active production path is:

```text
per-document discovery
→ connected cross-document semantic synthesis
→ ontology-aware recommendation planning and modeling review
→ deterministic verification
→ human review
```

Connected modeling owns cross-document synthesis. Conditional consolidation still
preserves every verified discovery when the synthesis input must be chunked.

One ontology-aware planning call receives the connected model, current ontology
snapshot, document authority, current work, and bounded prior applied provenance.
It owns reconciliation, alignment, modeling review, and grouped final planning.
The earlier standalone provider adapters remain readable for compatibility, but
the production orchestrator does not call them.

Transport failures remain transport retries. A deterministic connected-model
rejection receives one bounded semantic correction call containing the exact safe
error and a bounded copy of the prior invalid output. The corrected complete
response is deterministically verified again. Final planning retains its bounded
finding-specific correction pass.

Provider calls receive the same concise ontology-modeling brief and contrastive
examples. The brief distinguishes reusable classes, properties, particular
individuals, roles, provenance artifacts, and SHACL constraints. It explicitly
allows zero executable edits when no faithful supported change exists.

Deterministic final-plan verification also receives the verified discovery kinds,
content classifications, and individual classifications. It rejects ontology
operations produced only from administrative metadata, requirements or controls
represented only as classes, individuals without particular production evidence,
and newly created individuals without an explicit type. These findings enter the
existing bounded correction pass rather than reaching review as apparently valid
edits.

Connected-model transport identities and verified discovery identities are
separate namespaces. `discoveryIds` trace an item to evidence.
`references[].providerItemId` points to another `item.providerId` in the same
response. The prompt and correction prompt include that rule and a concrete
example. If a provider mistakenly puts a discovery ID in a reference, Kotlin may
resolve it only when exactly one other returned item is grounded in that
discovery. Missing, self-referential, duplicate, or ambiguous targets remain
invalid and their exact target IDs are recorded in bounded status details.

Discovery-stage `relatedProviderIds` are optional descriptive links rather than
evidence. Kotlin retains an otherwise valid evidence-grounded discovery when
one of those links is missing, self-referential, or ambiguous, and drops only
the bad link. Invalid excerpts, unknown blocks, and cross-document claims still
reject the affected discovery. Status updates report retained and rejected
discovery counts plus the exact bounded rejection codes.

Discovery skip accounting preserves one deterministic diagnostic for every
rejected or deduplicated provider item so the status totals reconcile with the
provider response. Discovery, connected synthesis, and final planning prioritize
the reusable operational subjects, transactions, records, values, and
relationships that a requirement constrains. Broad policy, requirement, control,
and role abstractions must not displace that underlying business structure, and
an absent operational concept must not be forced onto a merely nearby
current-ontology class.

Discovery v2 gives the provider bounded, server-issued evidence anchors. The
provider selects anchor IDs instead of calculating character offsets or
reconstructing quotations. Kotlin resolves each selected anchor back to the
server-held block, exact text, and offsets and verifies it through the existing
evidence verifier. Unknown anchors cannot satisfy the strict provider schema,
and this change does not add a provider call.

A valid response envelope is repaired once when business-grounded items violate
the connected-item contract. After that correction, Kotlin retains valid items
and skips invalid items individually. If no connected items survive, the
connected model is explicitly empty and final planning continues from the
complete verified discovery inventory. This is a degraded semantic path, not
acceptance of provider output: skipped items cannot become typed operations, and
final-plan discovery coverage and operation verification still apply.

Chunk consolidation may replace the independently verified per-document models
only when its valid items preserve every discovery those models represented. If
the consolidated response is empty, internally invalid, or loses that coverage,
Kotlin retains and combines the already verified chunk models instead. The
status timeline identifies the rejected consolidation items and states that the
verified per-document models were retained. Final planning still receives the
complete verified discovery inventory. Consolidation can therefore add
cross-document structure, but it cannot erase valid upstream meaning.

The final-planning contract uses complete operation bundles instead of placing
domain or range entities on a declaration operation. A property declaration may
carry only its optional label and writable source. Separate operations set its
domain and range in the same atomic recommendation. Supported simple data
requirements use SHACL shape operations; unsupported compound business rules
remain one review-only finding. Generic roles may be reusable classes when the
evidence supports that category, but never individuals. A correction must not
return both a blocked edit attempt and a review-only duplicate for the same
meaning.

Response capacity is based on the larger of the connected-model inventory and
the verified business-discovery inventory. This keeps the degraded path viable
when connected synthesis retains fewer items than discovery while preserving
the existing output ceiling.

## Consequences

- A normal two-document task uses four provider calls instead of approximately
  seven: two discovery calls, one synthesis call, and one planning call.
- A normal one-document task uses three provider calls.
- Large inventories may use additional synthesis chunks and one consolidation
  call, while remaining inside the existing task call budget.
- No provider result bypasses evidence, identity, dependency, operation, source,
  freshness, semantic-intent, or typed-edit verification.
- One malformed connected item no longer discards unrelated valid items or the
  complete verified discovery inventory.
- A degraded consolidation cannot replace more complete independently verified
  chunk models.
- Malformed declaration operands are rejected before translation, and the
  correction call receives concrete property, shape, role, and deduplication
  guidance.
- Policy and standard titles remain provenance by default; normative clauses are
  modeled as supported shapes and constraints or retained as review-only.
- Generic roles never become ontology individuals.
- No automatic approval, application, ontology write, or alternate proposal path
  is introduced.
- Existing separate-stage records and adapters remain compatible, but new
  production tasks record only the provider stages actually executed.

## Implementation Record

This change amends the implemented Phase 11.5 orchestration and planning
contracts. Production changes are limited to the existing document-ingestion
server files. Focused orchestration and service tests verify:

- standalone reconciliation, alignment, and critic calls are not made;
- connected-model rejection receives the deterministic failure code and prior
  invalid output on its correction attempt;
- the corrected synthesis is revalidated;
- ten-document coverage remains complete within the pinned logical-call budget;
- final recommendations still reach the existing deterministic review workspace.

## Verification

- `./gradlew :web-server:compileKotlin :web-server:compileTestKotlin` — passed.
- Focused `DocumentAnalysisServiceTest`, `DocumentIngestionOrchestratorTest`, and
  `OpenAiDocumentAnalysisClientTest` — passed.
- `./gradlew test` after a clean rebuild — passed.
- `./gradlew build check` — passed.
- `npm test` in `web-app` — passed, 23 files and 100 tests.
- `npm run build` in `web-app` — passed; Vite reported the existing large-chunk
  advisory.
- `npm run test:e2e` in `web-app` — passed, 4 tests.
- `npm test` in `vscode-extension` — passed, 37 tests.
