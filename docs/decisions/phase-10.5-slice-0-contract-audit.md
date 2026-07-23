# Phase 10.5 Slice 0 Contract Audit

## ExecPlan Slice Implemented

Slice 0: Planning Approval And Contract Audit.

## Goal

Align the Phase 10.5 spec with the newer ExecPlan, approve both planning documents, and pin the smallest additive contracts and reuse points before production implementation.

## Approval

- The Phase 10.5 spec and ExecPlan are approved for implementation on 2026-07-23.
- The ExecPlan's newer product decisions are authoritative.
- Phase 10.5 remains read-only: automatic reasoning refresh updates only in-memory reasoning read state and never stages, proposes, materializes, applies, or writes ontology facts.

## Contract Decisions

### Core Contract Ownership

Slice 1 will add `core-types/src/main/kotlin/com/entio/core/InferredFactsReadContracts.kt` with:

- `InferredGraphState`: `Applied`, `Proposal`;
- `InferredReadState`: `Off`, `Current`, `Updating`, `Unavailable`, `Failed`;
- `InferredReadKind`: `SubclassRelationship`, `IndividualType`, `ObjectPropertyAssertion`, `EffectiveDomain`, `EffectiveRange`;
- `InferredFactPlacement`: explicit server/engine-owned presentation locations for hierarchy, class, property, individual, and relationship fields;
- `InferredReadFact`: stable key, named subject/predicate/object IRIs, kind, placement, `FactOrigin`, graph state, reasoning result ID, graph fingerprint, optional proposal fingerprint, and source ID;
- `InferredFactsOverlay`: graph state, read state, bounded facts, total count, truncation state, and fingerprints.

`OntologyGraphProvenance` will add `Inferred`. `OntologyGraphEdge` will add nullable `inferredGraphState`; constructor invariants will require it exactly when provenance is inferred. This preserves the existing asserted default while allowing web and React layers to distinguish applied and proposal inferred edges.

No core contract will depend on Ktor, sessions, React, Jena, OWL API, or HermiT types.

### Stable Identity

For subclass relationships, individual types, and object-property assertions, `InferredFactsReadService` will construct the existing `InferenceMaterializationFact` and call the public `InferenceMaterializationService.semanticFactKey`. This preserves the Phase 10 `entio-semantic-fact-v1` identity exactly.

Effective domain and range facts are not materializable Phase 10 facts. They will use an `entio-inferred-read-v1` SHA-256 key over the same UTF-8 length-prefixed canonical components:

```text
read kind
iri:<subject>
predicate IRI
iri:<object>
```

Labels, graph state, job ID, timestamps, ordering, and display placement are excluded from semantic identity. Graph state remains separate provenance.

### Project-Owned Reasoning State

`SemanticJobManager` already has:

- applied and proposal scopes;
- captured graph/proposal fingerprints;
- coroutine cancellation;
- active-job tracking;
- completion-time fingerprint rechecks;
- retained `ReasoningResult` values.

Slice 3 will replace the applied-only cache with one current state per `(projectId, WebJobScope)`. Each state exposes `Updating`, `Current`, `Unavailable`, or `Failed` plus retained result metadata. Manual semantic jobs remain available. Phase 10 materialization continues to require the submitting user's exact completed applied job; project-owned visibility does not weaken that check.

All authorized users of the same registered project may read its current overlay. Existing browse authorization and selected-source validation remain authoritative.

### Refresh Hooks And Supersession

The exact refresh behavior is:

- the first authorized project read ensures applied reasoning exists for the current applied fingerprint;
- a successful stage, replacement, discard, external stage, graph-change stage, or materialization stage refreshes proposal reasoning;
- proposal preview/rebuild refreshes proposal reasoning;
- reject/clear makes proposal reasoning unavailable and cancels its active work;
- successful apply refreshes applied reasoning and clears/supersedes proposal reasoning;
- failed apply/rollback refreshes applied reasoning against the post-operation fingerprint and clears any result whose captured fingerprint no longer matches;
- repeated refresh requests with the same project, scope, graph fingerprint, and proposal fingerprint coalesce;
- a different fingerprint cancels or marks stale the older active job;
- a completed job is installed only after `StagingWorkflowService.graphSnapshot` still matches its captured identity.

`StagingWorkflowService.graphSnapshot(Proposal)` currently requires a prepared proposal. To cover staged add/update/remove before explicit review, Slice 3 may add a read-only snapshot method in `StagingWorkflowService.kt` that uses the existing normalizer/proposal planner to compute the current staged preview graph without assigning `session.proposal`, changing review status, or writing sources. The ExecPlan now explicitly allows this narrow file change.

Visibility controls never call these refresh methods; graph lifecycle hooks do.

### Effective Domain And Range

`ReasoningResult` directly provides class relationships, individual types, and object-property assertions. It does not provide domain/range facts.

The semantic read service may deterministically project an effective domain or range only when:

1. the property and asserted domain/range target already exist as named entities in the relevant graph;
2. current reasoning contains an inferred superclass relationship from that asserted target to another existing named class;
3. the projected fact is `property rdfs:domain/range inferred-superclass`;
4. the canonical triple is not asserted already.

No new entity, anonymous expression, datatype inference, property characteristic, or reasoner rule is created. These effective facts are read-only and never become Phase 10 materialization candidates.

### Field Placement Matrix

| Inferred fact | Existing Explore placement | Map edge |
| --- | --- | --- |
| `child subClassOf parent` | child superclasses; parent subclasses; additive hierarchy child under parent | `SubclassOf` |
| `individual rdf:type class` | individual types; class typed-individual membership; outline direct-type placement when it is the most specific visible type | `Type` |
| `subject property object` | subject outgoing; object incoming; property usage projection | `ObjectAssertion` |
| `property rdfs:domain class` | property domains; class related-property projection | `Domain` |
| `property rdfs:range class` | property ranges; class related-property projection | `Range` |

`InferredFactPlacement` is computed in Kotlin so React does not infer how a fact maps to a field. Asserted placement always wins. Inferred hierarchy placements are additive, deduplicated per parent, and cycle guarded.

### Read Extensions

The smallest compatible extension is:

- add `includeAppliedInferred=false` and `includeProposalInferred=false` to hierarchy, outline, entity, graph-initial, and graph-neighborhood GET queries;
- extend `WebHierarchyItem`, `WebOutlineItem`, entity-detail relationship/reference values, and graph DTOs with optional provenance/graph-state data and asserted-compatible defaults;
- include separate applied/proposal overlay state metadata in each relevant response;
- keep search result ranking asserted-only in Phase 10.5; search-visible outline/entity navigation still receives overlays after opening the result.

No standalone reasoning sidebar or mutation route is added.

### Ordering, Limits, And Continuations

All limits are aggregate after canonical duplicate suppression:

- hierarchy/outline/entity page: existing maximum 100;
- inferred facts per entity: maximum 100;
- initial map page: 75 nodes and 150 edges;
- neighborhood expansion: 50 nodes and 100 edges;
- one open map tab: 300 nodes and 600 edges.

Capacity order is asserted, applied inferred, then proposal inferred. Within an inferred graph state, sort by read-kind ordinal, subject label/IRI, predicate label/IRI, object label/IRI, then semantic key.

An inferred edge is emitted only when both endpoints fit the same bounded page. Continuation signatures include user ID, project ID, source IDs, both visibility flags, applied fingerprint, proposal fingerprint when applicable, query type, seed/entity, and categories. Changing either graph state or visibility flag invalidates the continuation.

### Layout Isolation

Only edges with `OntologyGraphProvenance.Asserted` may enter root selection, class-tree construction, depth, sibling ordering, connected-component scoring, or initial automatic placement. Inferred edges are merged after primary positions exist. Inferred-only nodes are placed near an already loaded endpoint and count against the normal node limit.

## Files Modified

- `docs/specs/0019-phase-10.5-inferred-facts-in-explore-and-ontology-map.md`
- `docs/execplans/0019-phase-10.5-inferred-facts-in-explore-and-ontology-map.md`
- `docs/decisions/phase-10.5-slice-0-contract-audit.md`

## Tests Added Or Updated

No test code changed. Slice 0 used documentation traceability and read-only contract inspection.

Every acceptance criterion maps to Slices 1 through 6 and final evidence is required in Slice 6.

## Verification Commands

- `git diff --check` — required before commit.
- `git status --short` — required before commit and after merge.

## Git Commit

Yes. This completion artifact is included in the focused Slice 0 commit.

## Assumptions And Limitations

- Reasoning results remain in-memory.
- Automatic refresh begins on first authorized project access because the registry has no project-registration event stream.
- Search ranking does not become inference-aware.
- Effective domains/ranges are the bounded deterministic projection defined above, not new reasoner output.
- Phase 10 materialization authorization and supported fact kinds remain unchanged.

## Notable Decisions

- Project-owned visibility and user-owned materialization coexist without weakening either boundary.
- A read-only staged preview snapshot is the only approved addition to staging internals.
- Applied/proposal provenance is separate from semantic identity.
- The asserted layout remains stable regardless of inferred visibility.
