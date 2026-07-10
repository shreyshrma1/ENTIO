# Phase 1.5 Slice 3 Completion: Parser RDF Term Fidelity

## ExecPlan Slice Implemented

Slice 3: Parser RDF Term Fidelity.

## Goal

Update Turtle parsing to preserve RDF term distinctions while keeping Jena contained inside `semantic-engine`.

## Files Modified

- `semantic-engine/src/main/kotlin/com/entio/semantic/OntologyParser.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/OntologyParserTest.kt`
- `docs/decisions/phase-1.5-slice-3-parser-rdf-term-fidelity.md`

## Tests Added Or Updated

- Added parser assertions for IRI object terms.
- Added parser assertions for blank-node subjects.
- Added parser assertions for blank-node objects.
- Added parser assertions for plain literal terms.
- Added parser assertions for datatyped literal terms.
- Added parser assertions for language-tagged literal terms.
- Kept the existing repeated-parse determinism test.

## Verification Commands

```bash
./gradlew :semantic-engine:test
./gradlew check
```

## Verification Results

- `./gradlew :semantic-engine:test`: passed.
- `./gradlew check`: passed.

## Git Commit

No Git commit was created.

## Assumptions, Limitations, And Follow-Up Work

- Jena model types remain private to `semantic-engine` and do not appear in public `core-types` contracts.
- Parser output now populates `GraphTriple.subjectResource` and `GraphTriple.objectTerm`.
- Unsupported Jena RDF node shapes are returned as structured `unsupported-rdf-node` parser failures instead of being collapsed into strings.
- Compatibility fields such as `GraphTriple.subject` and `GraphTriple.objectValue` remain available for downstream modules until their later approved migration slices.
- Symbol extraction still reads compatibility values; RDF-term-aware symbol extraction is reserved for the next approved slice.

## Notable Implementation Decisions

- Mapped Jena URI resources to `Iri`.
- Mapped Jena blank nodes to `BlankNodeResource`.
- Mapped Jena literals to `RdfLiteral` with lexical form, datatype IRI, and language tag.
- Did not add parser behavior beyond Turtle/RDF term conversion.
