# Phase 13 Slice 9: Cross-Workflow Domain Integrations

Date: 2026-08-08

Status: Complete

Branch: `feature/phase-13-slice-9-cross-workflow-integrations`

## Scope

Slice 9 completes the approved human-driven domain retrieval integrations for
deletion review, proposal review, SHACL authoring, the ontology map, and the
Reasoning workspace. Every surface delegates to the shared recommendation
panel and server ranking contract introduced in Slice 8. No surface calculates
semantic compatibility, changes deterministic validation or reasoning, or
automatically replaces, applies, materializes, or adds a graph node.

The Slice 9 completeness matrix records all seven fixed operation kinds, their
owning components, and the workflow invariant each integration must preserve.

## Deletion and proposal review

Deletion review retains the existing direct and dependent statement plan,
explicit dependency selection, and stage gate. A schema entity now offers an
opt-in search for a FIBO replacement or broader concept. Opening that search
does not select dependencies or alter the deletion request. Reuse or mapping is
a separate review-queue action.

Proposal details offer one bounded domain reuse check for a selected new class,
new property, changed preferred label, or changed definition. Domain-reuse
entries are excluded to prevent recursive checks. Converting a recommendation
adds the existing supported domain action; the server clears the old prepared
proposal, and the existing staging workflow must generate a fresh preview
before review can continue. The original staged edit is not silently removed.

## SHACL authoring

Target-class, object/datatype property-path, and class-constraint controls now
provide contextual domain recommendations. Property recommendations require an
explicit property kind. XSD datatypes remain explicit choices from the fixed
standard datatype list because they are not FIBO entities. The browser does
not decide SHACL validity, and all SHACL staging and validation continues
through the existing Kotlin contracts.

## Ontology map and reasoning

Selecting a supported schema node on the ontology map exposes an opt-in,
bounded related-FIBO panel using at most 20 loaded neighboring project IRIs.
Results are labeled `Available, not applied`; they are not passed to the graph
renderer, do not become nodes or edges, and do not affect asserted layout.

Completed Reasoning jobs expose one bounded related search over at most 50
server-issued asserted or inferred facts. Subclass/type facts search classes,
and object-property assertions search object properties. The underlying fact
remains read-only. A domain action is separate from inference materialization
and enters the shared review queue.

## Server adapter

The web adapter can derive the entity kind for proposal review from a verified
current project IRI when the client does not know it. All other contextual
operations still require a strict requested kind. The adapter validates fixed
operation kinds and relevant project context before constructing the Kotlin
modeling intent.

## Verification

The required commands passed on 2026-08-08:

```text
(cd web-app && npm test)
(cd web-app && npm run build)
(cd web-app && npm run test:e2e)
./gradlew :web-server:test --tests '*Shacl*'
./gradlew :web-server:test --tests '*OntologyGraph*'
./gradlew :web-server:test --tests '*Reasoning*'
./gradlew :semantic-engine:test --tests '*Shacl*'
./gradlew :semantic-engine:test --tests '*Reasoning*'
git diff --check
```

Results were 26 Vitest files and 118 tests passed, the production web build
passed, all four Playwright journeys passed, and every focused Kotlin suite
passed. Additional focused coverage verifies deletion dependency preservation,
server-resolved proposal kinds, mandatory proposal regeneration after domain
conversion, the Slice 9 operation matrix, asserted/inferred fact mapping, map
node-count invariance, available-versus-applied presentation, inactive and
degraded shared-panel behavior, and continued absence of reasoning writes.

The approved ontology-map popup snapshot was updated only to include the new
collapsed `Related FIBO concepts` row. Its existing asserted summary,
read-only controls, node counts, interaction checks, and performance gates
continue to pass.
