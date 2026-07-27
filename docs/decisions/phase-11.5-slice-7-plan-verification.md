# Phase 11.5 Slice 7: Final Planning And Change-Set Verification

## Status

Completed on 2026-07-27.

## Implemented Boundary

Slice 7 adds one strict final-planning call after the modeling critic. The call receives the complete verified discovery inventory, connected model, reconciliation records, ontology alignments, critic findings, confidence dimensions, and bounded ontology snapshot.

The provider may group supported typed operations into atomic recommendations and may refer to new items only with the pinned `new:<kind>:<localName>` grammar. It cannot provide a final IRI. Kotlin generates collision-checked IRIs from the selected writable source namespace only after the complete recommendation passes verification.

## Deterministic Verification

The final-plan contracts and semantic verifier check:

- exact discovery coverage and exact critic-finding dispositions;
- recommendation, evidence, operation, and expanded-edit limits;
- declaration-before-use temporary references and earlier-operation dependencies;
- supported operation and SHACL constraint kinds;
- operand kind compatibility for classes, properties, individuals, assertions, and shapes;
- writable target sources;
- current ontology and current-work fingerprints; and
- collision-free Kotlin-generated identities.

An invalid operation blocks its whole atomic recommendation. It is not removed, rewritten, or extracted into a smaller recommendation. Other independent recommendations may remain executable. Unresolved critic findings, unconfirmed individual creation, unsupported complex rules, and human conflicts remain blocked or review-only.

## Preview Boundary

Verified recommendations expose deterministic summaries for semantic diff, validation, reasoning, and SHACL impact. These summaries identify whether the complete atomic group is ready for the existing proposal preview services. Slice 7 does not stage, approve, apply, write RDF, or add a second validation engine.

Optional operations may be removed only when they are unreferenced optional leaves. Splitting uses the complete transitive dependency and temporary-declaration closure so a resulting group cannot contain dangling references.

## Verification

Focused tests cover connected class/property/domain/range plans, individual assertions, supported SHACL, temporary-reference and kind errors, unwritable sources, IRI collisions, stale graph fingerprints, per-recommendation atomic blocking, independent recommendation survival, optional-leaf exclusion, split closure, and the strict OpenAI final-plan schema without provider-supplied final IRIs.

The Slice 7 verification commands are:

```bash
./gradlew :semantic-engine:test
./gradlew :semantic-engine:build
./gradlew :web-server:test
./gradlew test
git diff --check
git status --short
```
