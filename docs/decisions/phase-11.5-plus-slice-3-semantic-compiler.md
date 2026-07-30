# Phase 11.5+ Slice 3 Semantic Compiler

Status: Complete

Date: 2026-07-30

## ExecPlan Slice Implemented

Slice 3: Build The Typed Semantic Pattern Registry And Compiler.

## Goal

Compile supported semantic meaning into the existing internal typed-operation
boundary without model-planned operations, raw RDF, or source mutation.

## Files Modified

- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentSemanticPlanCompiler.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/DocumentSemanticPlanCompilerTest.kt`
- `docs/decisions/phase-11.5-plus-slice-3-semantic-compiler.md`

## Implementation

The slice adds:

- an explicit registry of executable semantic item kinds, review-only complex
  meaning, and supported SHACL constraints;
- a deterministic compiler context for writable local targets, existing
  entities, exact alignments, and administrative discoveries;
- exact entity reuse or collision-checked local declaration;
- compilation for classes, semantic metadata, object and datatype properties,
  domains, ranges, hierarchy, individuals, types, and assertions;
- supported node shapes, property shapes, required relationships, and
  standalone inclusive numeric thresholds through existing SHACL operations;
- stable blocked codes for missing, unsafe, duplicate, or kind-incompatible
  mappings;
- review-only results for complete complex rules;
- compilation confidence and final temporary-reference mappings.

All emitted work uses the existing `DocumentPlanOperation` boundary consumed by
the existing typed draft translator. External entities may be referenced by
local operations, but no operation targets an external source.

## Tests Added Or Updated

`DocumentSemanticPlanCompilerTest` covers:

- connected class and object-property compilation;
- domain, range, datatype range, and hierarchy;
- individuals, types, object assertions, and datatype values;
- exact existing-entity reuse and duplicate prevention;
- local extension of an approved external class without external writes;
- required-relationship and numeric-threshold SHACL forms;
- review-only separation-of-duty, aggregation, conditional, and temporal
  rules;
- invalid domain and type targets;
- administrative metadata exclusion;
- declaration of a missing supporting concept instead of substitution.

Existing `DocumentRecommendationDraftTranslator` tests confirm that compiled
operation kinds continue to expand through the approved typed-edit boundary.

## Verification

- `./gradlew :semantic-engine:test --tests '*DocumentSemanticPlanCompiler*'` —
  passed.
- `./gradlew :semantic-engine:test --tests '*DocumentRecommendationDraftTranslator*'`
  — passed.
- `./gradlew :semantic-engine:check` — passed.
- `git diff --check` — passed.

During implementation, two untracked files with a ` 2.kt` suffix appeared and
caused duplicate declarations. They were byte-for-byte copies of the canonical
Slice 2 service and test. Work stopped as required; after explicit user
authorization, only those two duplicate files were removed. They were never
committed.

## Git Commit

A focused Git commit will be created on
`feature/phase-11-5-plus-slice-3-semantic-compiler` after this completion
record is reviewed and staged with the implementation.

## Assumptions And Limitations

- Dependency ordering, aggregate limits, stable operation identities, and
  semantic preview enforcement belong to Slice 4.
- Provider request/response migration belongs to Slice 5.
- A property shape is compiled only when it has exactly one complete supported
  constraint. Additional constraint work remains explicit rather than being
  guessed.
- Complex conditional, aggregation, temporal, and separation-of-duty meaning
  is not weakened into an executable approximation.

## Notable Decisions

- Exact alignment IDs resolve only through deterministic compiler context.
- Generated IRIs collide safely instead of silently becoming reuse.
- Administrative metadata cannot become a business entity merely because a
  semantic item claims it is executable.
