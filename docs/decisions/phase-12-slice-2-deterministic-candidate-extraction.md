# Phase 12 Slice 2 Deterministic Candidate Extraction

Status: Complete

Date: 2026-07-31

## ExecPlan Slice Implemented

Slice 2: Implement Deterministic Local Candidate Extraction.

## Goal

Create a stable, evidence-linked inventory of English document candidates from
existing extracted text before any ontology retrieval or provider call.

## Files Modified

- `web-server/build.gradle.kts`
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentCandidateExtractionService.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionConfiguration.kt`
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentCandidateExtractionServiceTest.kt`
- `web-server/src/test/resources/document-ingestion/phase-12-candidate-extraction.txt`
- `docs/decisions/phase-12-slice-2-deterministic-candidate-extraction.md`

## Implementation

- Added only the Slice 0-audited Apache OpenNLP 2.5.11 dependency and the exact
  Maven-hosted English 1.3.0 sentence, tokenizer, POS, and lemmatizer models.
- Loads the immutable resources once through a synchronized lazy adapter. No
  model is copied into the repository and no runtime download or training
  occurs.
- Extracts organizations, people, locations, dates, identifiers, amounts,
  noun/concept phrases, verbs with nearby participants, attribute/value text,
  and rule cues.
- Marks administrative and illustrative blocks as heuristic candidate
  categories without deciding ontology meaning.
- Preserves exact document, block, page/section, and block-relative offsets.
- Derives stable candidate, evidence, and reference IDs from frozen server-held
  inputs and pinned contract/resource versions.
- Removes exact duplicate outputs while retaining merely similar phrases such
  as `Payment` and `Payment Instruction` separately.
- Returns the stable safe code `document-candidate-extraction-failed` for bad
  input or unavailable NLP resources without exposing paths or secrets.

The service consumes already extracted blocks. Upload, parsing, PDF, DOCX, OCR,
temporary storage, provider, ontology, and browser behavior are unchanged.

## Tests Added

`DocumentCandidateExtractionServiceTest` verifies every required category,
relationship hints, exact evidence offsets, deterministic IDs and ordering,
the audited initialization bound, exact duplicate removal, similar-term
separation, administrative and illustrative marking, cross-document and empty
input rejection, and safe resource initialization failure. No provider
interface is present or invoked.

## Verification

- `./gradlew :web-server:test --tests '*DocumentCandidateExtractionServiceTest*'` — passed, 6 tests.
- `./gradlew :web-server:compileKotlin` — passed.
- `./gradlew :web-server:dependencies` — passed and resolved exactly OpenNLP
  tools 2.5.11 plus the four English 1.3.0 model artifacts.
- `git diff --check` — passed.

Initial focused test compilation used authority/status enum names not present
in the existing Phase 11 contracts; the fixture was corrected. The first NLP
fixture run also demonstrated that the pinned UD models return universal tags,
so the narrow adapter was corrected to accept both Penn-style and UD-style
noun, adjective, and verb tags. All required verification then passed.

## Git

The focused commit is created from this completed record on branch
`feature/phase-12-candidate-extraction`, pushed, and then merged locally into
the accumulated `main` with a non-fast-forward merge.

## Assumptions And Limitations

- Traditional local NLP seeds later semantic modeling; it does not establish an
  OWL kind, semantic identity, reuse decision, or write permission.
- Named-entity heuristics are deliberately bounded and English-only. Later
  model supplements remain required for recall under the approved plan.
- A candidate with imperfect categorization remains safe because retrieval and
  grounded modeling treat local kind hints as non-authoritative.
