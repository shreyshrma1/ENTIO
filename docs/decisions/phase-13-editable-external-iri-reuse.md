# Phase 13 Editable External-IRI Reuse

Date: 2026-08-08

Status: Approved by the Phase 13 Slice 0 audit

## Context

`owl:imports` exposes source meaning but does not provide the project-owned,
editable representation required by Phase 13. Users must be able to change
labels, definitions, annotations, and supported structure while retaining the
canonical FIBO IRI for traceability. The implementation must continue to use
the existing typed proposal and apply workflow.

## Decision

Selected entities are materialized as project-owned statements in
`ontology/fibo-reuse.ttl`. The canonical subject IRI remains the exact pinned
FIBO or required OMG Commons IRI. Entio never edits the source package and does
not add module-wide `owl:imports` for this workflow.

The Phase 13 preparer emits existing typed edits and graph changes into the
normal staged proposal. It is separate from, and does not change, the Phase 5
`ExternalProposalIntentTranslator` compatibility path.

### Existing typed-operation mapping

- declaration and initial label: existing create-class, create-object-property,
  create-datatype-property, and set-label edits;
- preferred-label replacement: `SetEntityLabelEdit`;
- alternate labels and definitions: existing add, replace, and remove semantic
  edit requests;
- supported literal or IRI annotations: existing `AddAnnotation` and
  `RemoveAnnotation` with `AnnotationValue.Literal` or
  `AnnotationValue.Resource`;
- hierarchy, domain, and range: existing add/set and
  `RemoveSuperclassEdit`, `RemovePropertyDomainEdit`, and
  `RemovePropertyRangeEdit` operations;
- whole-entity removal: the existing deletion dependency review and graph
  removal workflow.

No new low-level removal or mapping operation is needed. A mapping to another
source IRI is an approved IRI-valued annotation whose predicate is selected
from the supported mapping vocabulary. Removing that mapping uses the matching
typed annotation removal. Clients never submit raw RDF.

### Materialization classifications

`CompleteSupportedMaterialization` means every source statement selected by
the versioned materialization policy is representable and no logical source
axiom attached to the entity is omitted.

`PartialMaterialization` means the declaration and all supported selected
statements are safe to materialize, but the source also contains one or more
explicitly listed axioms outside Entio's typed-edit surface. The user must
acknowledge those omissions. Examples include anonymous restrictions,
equivalent or disjoint class expressions, inverse/equivalent properties,
property chains, keys, property characteristics, rules, and reference
individual assertions.

`UnsupportedForReuse` means a safe named class/object-property/datatype-property
declaration cannot be established, a required named structural dependency is
missing or unsupported, a required copied statement depends on an anonymous
resource, the record is malformed/informative, or bounded dependency closure
cannot preserve the supported meaning. It blocks that selection.

The copied allowlist is declaration, bounded preferred/alternate labels,
definition, approved annotations, direct named superclass or superproperty,
named domain, named class or datatype range, and required named declarations.
Administrative source metadata remains provenance rather than project RDF.
Every other construct is omitted and reported; it is never silently converted.

### Provenance bounds and schema

Each JSONL event has schema `entio-domain-reuse-provenance-v1` and the fields
listed in the Phase 13 specification. Records are UTF-8 JSON with stable key
ordering and one terminal newline. IDs and fingerprints use SHA-256.

A source snapshot is canonical N-Quads-like statement data bounded to `128`
statements, `64 KiB` encoded bytes, `4 KiB` per literal lexical form, `20`
alternate labels, `10` definitions, and `50` other annotations per entity.
Exceeding a bound blocks materialization; it never truncates provenance or
semantic meaning. Historical events are append-only in Phase 13.

### Apply ownership

Phase 13 extends the existing `MultiSourceAtomicApplier` with a narrow optional
sidecar transaction participant. The participant receives already prepared
source bytes, journals original and intended managed-source/provenance hashes,
prepares the complete next provenance bytes, participates in verification, and
restores its sidecar on rollback or recovery. Its default is a no-op, preserving
all existing proposal behavior.

`StagingWorkflowService` enables this participant only for a current approved
proposal containing server-prepared domain-reuse metadata. Existing document
provenance hooks remain ingestion-owned and unchanged. The domain participant
lives outside the ingestion package. This is one apply route with an additional
transaction participant, not a domain-specific apply route.

## Consequences

- Users can customize supported project meaning without losing source identity.
- Canonical IRIs cannot be renamed or regenerated.
- Unsupported OWL meaning is visible and conservatively classified.
- Ontology and domain provenance recover together.
- Existing validation, reasoning, SHACL, approval, reload, and rollback remain
  authoritative.
