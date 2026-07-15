# Phase 4 Slice 5: Reasoning Execution And Asserted/Inferred Results

## ExecPlan Slice Implemented

Slice 5: Reasoning Execution And Asserted/Inferred Results from `docs/execplans/0007-phase-4-owl-reasoning-shacl.md`.

## Goal

Run HermiT through the semantic-engine OWL boundary and return deterministic Entio-owned reasoning results without writing inferred facts back to source or preview files.

## Files Modified

- `semantic-engine/src/main/kotlin/com/entio/semantic/ReasoningService.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/ReasoningServiceTest.kt`

## Implementation

- Added `ReasoningService` for local in-memory `GraphState` reasoning and for already-loaded `OwlOntologyDocument` instances.
- Used Apache Jena only to serialize an in-memory graph into an OWL API document; no source file or preview file is mutated.
- Used HermiT to produce class-superclass relationships, individual types, object-property relationships, consistency status, and unsatisfiable classes.
- Preserved asserted and inferred facts in the existing Entio result types using `FactOrigin` rather than merging them into an untyped collection.
- Added deterministic ordering and SHA-256 fingerprints for graph input, import-closure metadata, and reasoner configuration.
- Included OWL API/HermiT version metadata, feature findings, warnings, and incomplete-import status in the reasoning result.
- When HermiT reports an inconsistent ontology, the service returns `Inconsistent`, preserves asserted facts, and withholds inferred classification that HermiT cannot safely compute.

## Tests Added

`ReasoningServiceTest` verifies:

- Transitive class hierarchy and inferred individual types.
- Asserted versus inferred origin separation and non-mutation of the input graph.
- Consistency and unsatisfiable-class reporting.
- Inverse-property and transitive-property consequences.
- Incomplete import-closure metadata and unknown consistency basis.

## Verification

- `./gradlew :semantic-engine:test` — passed.
- `./gradlew test` — passed.

## Result And Limitations

The Slice 5 reasoning service is complete within the semantic-engine boundary. It does not add caching, cancellation, explanations, SHACL, CLI commands, UI behavior, or durable result storage. Inconsistent ontologies return asserted facts and explicit inconsistency warnings; HermiT-derived classifications are not claimed when the reasoner cannot safely provide them.

No Git commit was created yet when this record was written; commit and remote-branch status are recorded after the implementation is reviewed and committed.
