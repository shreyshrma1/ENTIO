# Phase 7 Slice 6: Typed SHACL Proposal Integration

## ExecPlan Slice

Slice 6: Typed SHACL Proposal Integration.

## Goal

Connect bounded, typed SHACL edits to Entio's ordinary staged-change, preview, finding-impact, human-review, atomic-apply, reload-verification, and rollback workflow without exposing AI mutation tools or raw RDF mutation.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/Phase25PlusContracts.kt`
- `core-types/src/main/kotlin/com/entio/core/Phase7ShaclEditingContracts.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/ProposalCreator.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/StagedChangeSetNormalizer.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/TypedShaclEditTranslator.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/TypedShaclEditTranslatorTest.kt`
- `graph-diff/src/main/kotlin/com/entio/diff/GraphDiffer.kt`
- `graph-diff/src/test/kotlin/com/entio/diff/GraphDifferTest.kt`
- `web-server/src/main/kotlin/com/entio/web/StagingWorkflowService.kt`
- `web-server/src/main/kotlin/com/entio/web/WebProposalPlanner.kt`
- `web-server/src/main/kotlin/com/entio/web/WebShaclStagePreparer.kt`
- `web-server/src/main/kotlin/com/entio/web/contract/StagingContracts.kt`
- `web-server/src/test/kotlin/com/entio/web/TypedShaclStagingWorkflowTest.kt`
- `web-app/src/web/projectApi.ts`
- `web-app/src/workbench/StagingPanel.tsx`
- `web-app/src/workbench/stagingEditTypes.ts`
- `web-app/src/workbench/stagingEditTypes.test.ts`

## Implemented Behavior

- Added strict typed contracts for node-shape creation, property-shape creation, constraint replacement, constraint removal, and shape deletion.
- Limited constraints to direct property paths and the approved count, datatype, class, inclusive-bound, and pattern forms.
- Required registered writable sources with the correct ontology or shapes role.
- Stored typed SHACL intent in staging and translated it against the freshly loaded shapes graph during proposal preparation. This avoids relying on parser-assigned blank-node identifiers across reloads.
- Prepared source-specific previews and atomic apply targets while retaining one combined review proposal and semantic diff.
- Validated the current data graph against current shapes and the preview data graph against preview shapes.
- Reported new, worsened, unchanged, and resolved findings with graph fingerprints.
- Blocked approval for new or worsened SHACL violations while retaining the invalid proposal for review.
- Applied combined ontology and SHACL proposals with the existing multi-source atomic applier.
- Reloaded and reproduced the reviewed SHACL finding set after save; any failed post-save verification restores every affected source.
- Kept rejected proposals staged for correction.
- Added readable SHACL vocabulary descriptions to semantic diffs.
- Added minimal non-AI web forms and review rendering for the supported typed operations.

## Tests Added Or Updated

- Translator tests cover property-shape creation, exact blank-node constraint replacement/removal, and bounded shape deletion.
- Graph-diff tests cover human-readable SHACL descriptions.
- Web workflow tests cover blocking finding impact, rejection without mutation, correct-source application, reload validation, update/remove/delete, wrong source roles, unknown sources, unsupported paths, stale baselines, combined ontology-and-SHACL application, and rollback of every affected file.
- Web form tests cover the supported SHACL operation inventory and typed request construction.
- Existing ontology-only web workflow and repository tests remain passing.

## Verification

- `./gradlew :semantic-engine:test` - passed.
- `./gradlew :graph-diff:test` - passed.
- `./gradlew :web-server:test` - passed.
- `./gradlew :cli:test` - passed.
- `./gradlew test` - passed.
- `(cd web-app && npm ci && npm test && npm run build)` - passed. `npm ci` reported two pre-existing high-severity audit findings; installation, tests, and production build completed successfully.
- `git diff --check` - passed.

## Git

The implementation and this completion record are committed together on `feature/phase-7-slice-6-typed-shacl-proposals`.

## Assumptions And Limitations

- No CLI mapping was added because the existing public CLI proposal contract does not stage typed SHACL requests. Adding a second CLI-only staging model would not be parity.
- Registered local source files are the current mutability boundary. SHACL staging additionally verifies the source is writable and has the shapes role.
- SHACL-SPARQL, JavaScript constraints, custom components, complex paths, arbitrary triples, raw Turtle, and raw SPARQL remain unsupported.
- Finding reload verification compares stable semantic finding content because parser-assigned blank-node identifiers are not durable identities.

## Implementation Decisions

- Typed SHACL intent remains in the staged entry until proposal preparation. Translating earlier would capture ephemeral blank-node identifiers from one parse and make a later preview incorrectly stale.
- Proposal baselines continue to use exact source-file fingerprints. The aggregate graph fingerprint now ignores blank-node labels so unchanged Turtle with newly assigned parser identifiers does not appear stale.
- Blocking SHACL impact produces a reviewable `VerificationFailed` proposal rather than a transport failure, allowing the user to inspect findings and diff before correcting staged edits.
