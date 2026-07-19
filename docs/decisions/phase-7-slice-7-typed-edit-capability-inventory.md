# Phase 7 Slice 7 Typed Edit Capability Inventory

## Purpose

This inventory decides which existing Entio edit lifecycles may be exposed as private AI draft capabilities. `APPROVED` means an operation has a typed request, deterministic preparation, proposal preview, validation, human review, atomic application, reload, and rollback path without raw RDF. `DEFERRED` means at least one required lifecycle stage is not available through a side-effect-free reusable adapter.

The inventory is intentionally conservative. A deferred operation is absent from the model capability registry; there is no raw triples, Turtle, SPARQL, or arbitrary-predicate fallback.

## Approved Ontology Operations

All approved ontology operations use `WebStageChangeRequest`, the side-effect-free `StagingWorkflowService.preparePrivateDraft` adapter, existing label resolution and IRI generation, `StagedChangeOperation`, `WebProposalPlanner`, and the existing multi-source atomic application path.

| Operation | Existing typed operation | Complete lifecycle | Status |
| --- | --- | --- | --- |
| `create-class` | `CreateClassEdit` | Existing typed staging, preview, validation, review, apply, reload, rollback | `APPROVED` |
| `set-entity-label` | `SetEntityLabelEdit` | Existing typed staging, preview, validation, review, apply, reload, rollback | `APPROVED` |
| `add-superclass` | `AddSuperclassEdit` | Existing typed staging, preview, validation, review, apply, reload, rollback | `APPROVED` |
| `remove-superclass` | `RemoveSuperclassEdit` | Existing typed staging, preview, validation, review, apply, reload, rollback | `APPROVED` |
| `create-object-property` | `CreateObjectPropertyEdit` | Existing typed staging, preview, validation, review, apply, reload, rollback | `APPROVED` |
| `create-datatype-property` | `CreateDatatypePropertyEdit` | Existing typed staging, preview, validation, review, apply, reload, rollback | `APPROVED` |
| `set-property-domain` | `SetPropertyDomainEdit` | Existing typed staging, preview, validation, review, apply, reload, rollback | `APPROVED` |
| `set-property-range` | `SetPropertyRangeEdit` | Existing typed staging, preview, validation, review, apply, reload, rollback | `APPROVED` |
| `create-individual` | `CreateIndividualEdit` | Existing typed staging, preview, validation, review, apply, reload, rollback | `APPROVED` |
| `assign-type` | `AssignTypeEdit` | Existing typed staging, preview, validation, review, apply, reload, rollback | `APPROVED` |
| `add-object-property-assertion` | `AddObjectPropertyAssertionEdit` | Existing typed staging, preview, validation, review, apply, reload, rollback | `APPROVED` |
| `add-datatype-property-assertion` | `AddDatatypePropertyAssertionEdit` | Existing typed staging, preview, validation, review, apply, reload, rollback | `APPROVED` |
| `add-definition` | `SemanticEditRequest.AddDefinition` | Side-effect-free typed staging, preview, validation, review, apply, reload, rollback | `APPROVED` |
| `replace-definition` | `SemanticEditRequest.ReplaceDefinition` | Side-effect-free typed staging, preview, validation, review, apply, reload, rollback | `APPROVED` |
| `remove-definition` | `SemanticEditRequest.RemoveDefinition` | Side-effect-free typed staging, preview, validation, review, apply, reload, rollback | `APPROVED` |
| `delete` | `DeletionPlan` in `StagedChangeOperation.Delete` | Existing dependency analysis, typed preview, validation, review, apply, reload, rollback; all dependent statements must be selected before private drafting | `APPROVED` |

## Approved SHACL Operations

Slice 6 proved these operations through `WebShaclStagePreparer`, `TypedShaclEditTranslator`, current and preview SHACL validation, finding deltas, human review, multi-source atomic application, reload, and rollback.

| Operation | Existing typed operation | Complete lifecycle | Status |
| --- | --- | --- | --- |
| `shacl-create-node-shape` | `TypedShaclEdit.CreateNodeShape` | Typed preparation through reviewed shape proposal lifecycle | `APPROVED` |
| `shacl-create-property-shape` | `TypedShaclEdit.CreatePropertyShape` | Typed preparation through reviewed shape proposal lifecycle | `APPROVED` |
| `shacl-update-constraint` | `TypedShaclEdit.UpdateConstraint` | Typed preparation through reviewed shape proposal lifecycle | `APPROVED` |
| `shacl-remove-constraint` | `TypedShaclEdit.RemoveConstraint` | Typed preparation through reviewed shape proposal lifecycle | `APPROVED` |
| `shacl-delete-shape` | `TypedShaclEdit.DeleteShape` | Typed preparation through reviewed shape proposal lifecycle | `APPROVED` |

No other SHACL operation is advertised. Raw shape graphs, SPARQL constraints, complex property paths, qualified value shapes, and arbitrary SHACL predicates remain unsupported.

## Deferred Metadata Operations

Definition edits are now approved through the side-effect-free `StagingWorkflowService.preparePrivateDraft` path. Other semantic metadata contracts remain deferred until their complete lifecycle is explicitly approved and tested.

| Operation | Existing typed contract | Missing lifecycle evidence | Status |
| --- | --- | --- | --- |
| `add-alternate-label` | `SemanticEditRequest.AddAlternateLabel` | Reusable web staging and proposal adapter | `DEFERRED` |
| `replace-alternate-label` | `SemanticEditRequest.ReplaceAlternateLabel` | Reusable web staging and proposal adapter | `DEFERRED` |
| `remove-alternate-label` | `SemanticEditRequest.RemoveAlternateLabel` | Reusable web staging and proposal adapter | `DEFERRED` |
| `add-annotation` | `SemanticEditRequest.AddAnnotation` | Reusable web staging and proposal adapter | `DEFERRED` |
| `remove-annotation` | `SemanticEditRequest.RemoveAnnotation` | Reusable web staging and proposal adapter | `DEFERRED` |

## Deferred External Reuse Operations

FIBO reuse and local subclassing have typed external intents and reviewed proposal behavior, but `FiboWebService.stageProposal` currently enters shared staging directly. Slice 7 forbids using that mutating path from a private AI draft.

| Operation | Existing typed contract | Missing lifecycle evidence | Status |
| --- | --- | --- | --- |
| `reuse-class` | `ExternalProposalIntent.ReuseExternalClass` | Side-effect-free private draft adapter | `DEFERRED` |
| `reuse-object-property` | `ExternalProposalIntent.ReuseExternalObjectProperty` | Side-effect-free private draft adapter | `DEFERRED` |
| `reuse-datatype-property` | `ExternalProposalIntent.ReuseExternalDatatypeProperty` | Side-effect-free private draft adapter | `DEFERRED` |
| `create-local-subclass` | `ExternalProposalIntent.CreateLocalSubclassOfExternalClass` | Side-effect-free private draft adapter | `DEFERRED` |

## Capability And Safety Result

The model registry exposes separate strict tools for approved ontology and bounded SHACL draft additions and updates, plus private remove, reorder, undo, and clear operations. The server revalidates user, project, conversation, source, feature, and permission scope on every invocation.

Private preparation checks source existence, writability, ontology or shapes role, deterministic label resolution, generated IRI collisions, typed translator validity, duplicates, conflicts, and deletion dependencies. Private mutations record rationale, accepting user, conversation, optional run attribution, deterministic order, revisions, and fingerprints. They do not mutate shared staging or project files.
