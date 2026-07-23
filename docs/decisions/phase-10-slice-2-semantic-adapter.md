# Phase 10 Slice 2: Semantic Materialization Analysis And Typed-Edit Conversion

## ExecPlan Slice

Slice 2: Semantic Materialization Analysis And Typed-Edit Conversion.

## Goal

Analyze retained inferred facts deterministically, assign canonical identities, enforce
semantic/source/import safety, and convert the supported facts through existing typed
ontology edits without mutating a graph or source.

## Files Modified

- `semantic-engine/src/main/kotlin/com/entio/semantic/InferenceMaterializationService.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/InferenceMaterializationServiceTest.kt`
- `docs/decisions/phase-10-slice-2-semantic-adapter.md`

## Tests Added Or Updated

- Covered inferred subclass, individual-type, and object-property mappings.
- Covered asserted-row exclusion, applied duplicates, anonymous terms, invalid object
  properties, entity compatibility, and stable ordering.
- Covered semantic-key stability and fact-ID changes across project, user, job, and
  fingerprint contexts.
- Covered one, several, and no writable subject owners.
- Covered safe imported references and unknown import dependence.
- Ran the complete semantic-engine and core contract suites.

## Verification

- `./gradlew :semantic-engine:test`: passed.
- `./gradlew :semantic-engine:check`: passed.
- `./gradlew :core-types:test :semantic-engine:test`: passed.
- `git diff --check`: passed.

## Git Commit

Yes. This record is included in the focused Slice 2 commit.

## Assumptions And Limitations

- Configured import-mapping target source IDs are import/read-only candidates and are
  never assertion targets.
- With imports present, dependence is `Imported` only when retained source identity or
  referenced-entity declarations prove it. Otherwise it is `Unknown` and unsafe.
- Staged/proposal duplicate checks remain in Slice 3 because they require shared
  session state.

## Notable Decisions

- Canonical identities use the approved length-prefixed UTF-8 SHA-256 encodings.
- Subject ownership comes from loaded symbols in writable local sources; the object's
  source never selects the assertion target.
- Object-property assertions require an asserted `owl:ObjectProperty` declaration.
- Conversion is verified against `TypedOntologyEditTranslator`; no raw RDF fallback
  exists.
