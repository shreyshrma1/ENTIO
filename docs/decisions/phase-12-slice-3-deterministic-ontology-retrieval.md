# Phase 12 Slice 3 Deterministic Ontology Retrieval

Status: Complete

Date: 2026-07-31

## ExecPlan Slice Implemented

Slice 3: Implement Deterministic Ontology Retrieval.

## Goal

Coordinate existing Entio semantic description, matcher-record, and pinned FIBO
search behavior into stable Phase 12 retrieval selections without creating a
second ontology index.

## Files Modified

- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentOntologyRetrievalService.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/DocumentOntologyRetrievalServiceTest.kt`
- `docs/decisions/phase-12-slice-3-deterministic-ontology-retrieval.md`

## Implementation

- Added one focused retrieval facade in `semantic-engine`.
- Reuses `SemanticDescriptionService` for loaded applied/imported descriptors,
  the existing `DocumentSemanticRecord` matcher model for explicit current
  work, same-task, and provenance inputs, and `FiboSchemaSearchService` over an
  immutable approved FIBO session.
- Requires explicit project IDs and frozen fingerprints for every web-owned
  record. The semantic engine has no dependency on a web store.
- Searches compatible kinds, incorporates nearby candidate hints, normalizes
  lexical scores to `0..100`, and applies stable score/scope/kind/IRI/source
  ordering.
- Removes only exact scope/IRI/source duplicates.
- Returns bounded labels, definitions, direct hierarchy/domain/range/datatype/
  asserted-type context, sorted reasons, and opaque fingerprint-bound selection
  IDs.
- Returns at most 20 prompt-visible choices per candidate and reports an empty
  list as a successful complete search.
- Performs a separate full-state exact identity/typed-operation check that is
  not limited by the top-20 prompt result boundary.
- Keeps imported and FIBO records read-only and uses only curated FIBO search
  results.

No ontology copy, cache with independent meaning, database, embedding, vector
search, external request, browser policy, or write authority was introduced.

## Tests Added

`DocumentOntologyRetrievalServiceTest` covers applied local, imported, private
draft, shared staging, proposal, same-task, provenance, and pinned FIBO scopes;
stable repeated results and selection IDs; reason ordering; empty results;
compatible kind filtering; top-20 prompt bounds versus complete duplicate
checks; changed-fingerprint IDs; project isolation; wrong-scope rejection; and
import/FIBO read-only rules.

Existing matcher, semantic description, and FIBO search tests remain in the
slice verification command.

## Verification

- Focused retrieval plus existing matcher/search/FIBO tests — passed, 23 tests.
- `./gradlew :semantic-engine:verifyFiboCatalog` — passed.
- `./gradlew :semantic-engine:build` — passed.
- `git diff --check` — passed.

One repeated-result fixture initially omitted the imported record from its
second otherwise identical input. The fixture was corrected, and the complete
verification command then passed without changing production ranking behavior.

## Git

The focused commit is created from this completed record on branch
`feature/phase-12-ontology-retrieval`, pushed, and then merged locally into the
accumulated `main` with a non-fast-forward merge.

## Assumptions And Limitations

- Lexical retrieval narrows choices but does not prove business identity. The
  model or reviewer chooses among verified selections in later slices.
- Imported records can come from the loaded descriptor graph or explicit
  project-bound records prepared by the later web context factory.
- Full-state exact-operation keys are supplied by existing current-work record
  builders; the prompt-visible ranking never replaces that safety check.
