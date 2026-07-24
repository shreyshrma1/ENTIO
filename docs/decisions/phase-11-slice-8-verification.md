# Phase 11 Slice 8 Verification

## Status

Complete on 2026-07-24.

## Goal

Verify that the full document-ingestion workflow remains bounded, isolated, accessible, and unable to bypass Entio's existing review and apply boundaries.

## Focused Hardening

- Malformed DOCX packages now fail with the same safe `document-signature-mismatch` result as other invalid document containers.
- Hostile intake coverage includes path traversal, unsafe filenames, macro-enabled packages, external relationships, excessive archive expansion, malformed packages, and cleanup after rejection.
- Analysis coverage includes prompt injection as quoted data, altered or invented evidence, safe provider failures, cancellation, the 200-candidate boundary, and the 20-provider-call task boundary.
- Lifecycle coverage confirms that cancellation prevents later matching and draft-preparation work.
- A single configuration test fixes every approved numeric ceiling in executable coverage.
- Browser verification now covers the complete keyboard-reachable document journey and the current read-only reasoning fact browsers.
- Existing Playwright baselines were refreshed to the current approved workbench and ontology-map presentation. The four resulting images were inspected and contain no clipping, missing content, or unexpected error state.

## Acceptance Traceability

| Acceptance area | Automated proof |
| --- | --- |
| Authorized intake and enumeration resistance | `DocumentIngestionRouteIntegrationTest.intakeStartsProcessingAndTaskReadsResistCrossUserAndProjectEnumeration` |
| Intake formats, signatures, filenames, limits, hostile DOCX, and cleanup | `DocumentIntakeServiceTest.acceptsBoundedSupportedMediaAndMetadata`, `DocumentIntakeServiceTest.rejectsMismatchesLimitsUnsafeNamesAndInvalidContentWithCleanup`, and `DocumentIntakeServiceTest.rejectsHostileAndMalformedDocxPackages` |
| Text, Markdown, DOCX, PDF, selective OCR, page geometry, language, encryption, cancellation, and extraction bounds | `DocumentExtractionServiceTest.extractsTextMarkdownAndDocxInStableReadingOrder`, `DocumentExtractionServiceTest.usesOcrOnlyForUnreliablePdfPagesAndRetainsGeometryAndWarnings`, `DocumentExtractionServiceTest.reliabilityAndBoundariesAreDeterministic`, and `DocumentExtractionServiceTest.rejectsEncryptedPdfAndMixedScriptDocuments` |
| Prompt and provider-payload injection, exact evidence, redaction, retries, current model selection, cancellation, comparisons, candidate shapes, and provider-call limits | All seven named `DocumentAnalysisServiceTest` tests: `verifiesExplicitCandidatesAndTreatsPromptInjectionAsQuotedData`, `rejectsAlteredInventedAndUnsupportedEvidence`, `retriesTransientFailuresRedactsProviderDetailsAndCachesExactWork`, `requiresCurrentVerifiedCompatibleModelAndHonorsCancellation`, `comparesDocumentsWithBoundedSecondStageAndSupportsMultipleEvidencePassages`, `acceptsApprovedEntityRelationshipValueRuleAmbiguityAndMultiPassageShapes`, and `rejectsCandidateOverflowAndEnforcesTheTaskProviderCallLimit`; plus `OpenAiDocumentAnalysisClientTest.sendsStrictBoundedRequestWithoutToolsOrSecretInBody` and `OpenAiDocumentAnalysisClientTest.rejectsUnsupportedResponseFieldsAndClassifiesSafeFailures` |
| Local, import, current-work, prior-provenance, and pinned-FIBO matching; duplicate prevention; ambiguity; conflict; split; merge; supersede; and exact-work reuse | All six named `DocumentOntologyMatcherTest` tests: `searchesApprovedScopesInOrderAndKeepsFiboPinned`, `exactTypedOperationsPreventDuplicatesAcrossCurrentAndDurableWork`, `createsOnNoMatchAndBlocksAmbiguousLabelOnlyMatches`, `emitsReviewOnlyEvolutionActionsAndConflictAlternatives`, `usesAuthorityApplicabilityAndNeverTreatsRecencyAsSupersession`, and `remainsStableAndReprocessesOnlyWhenExactWorkKeyChanges` |
| Cross-user and cross-project isolation, symlink rejection, cancellation before later stages, restart cleanup, and task deletion | `DocumentTaskLifecycleTest.enforcesOwnershipCountsDuplicatesCancellationAndDeletion`, `DocumentTaskLifecycleTest.rejectsSymlinkedTaskStorageAndCleansStaleMarkedDirectoriesOnRestart`, `DocumentTaskLifecycleTest.cancellationPreventsMatchingAndDraftPreparationFromAdvancing`, and `DocumentIngestionRouteIntegrationTest.intakeStartsProcessingAndTaskReadsResistCrossUserAndProjectEnumeration` |
| Durable provenance across restart and project isolation | `AppliedDocumentProvenanceRepositoryTest.recordsSurviveRestartAndTemporaryCleanupWithoutCrossProjectAccess` and `AppliedDocumentProvenanceRepositoryTest.pendingRecordsCommitAtomicallyAndRepositoryCannotOverlapProjects` |
| Bounded authorized review and stale or invalid review transitions | `DocumentReviewWorkspaceTest.keepsReadsBoundedAuthorizedAndFreeOfFullDocumentText` and `DocumentReviewWorkspaceTest.supportsReviewChoicesAndRejectsInvalidOrStaleTransitions` |
| Connected intake, extraction, analysis, matching, and review without an ontology write | `DocumentIngestionOrchestratorTest.connectsIntakeExtractionAnalysisMatchingAndReviewWithoutWritingOntology` and `DocumentIngestionOrchestratorTest.reportsModelBlockWithoutExposingOrDeletingReviewableTaskMetadata` |
| Atomic typed staging, field evidence, no source write before approval, successful apply provenance, and rollback on provenance failure | `DocumentDraftProposalIntegrationTest.stagesOneAtomicBatchWithFieldProvenanceAndNoSourceWrite`, `DocumentDraftProposalIntegrationTest.validatesEveryItemBeforeMutationAndRejectsUnrelatedSharedStaging`, `DocumentDraftProposalIntegrationTest.commitsDurableProvenanceOnlyAfterSuccessfulExistingApply`, and `DocumentDraftProposalIntegrationTest.provenanceFailureUsesExistingRollbackAndClearsPendingEvent` |
| All approved numeric ceilings | `DocumentIngestionBoundsTest.configurationCannotExceedApprovedNumericBounds` plus the focused service boundary tests above |
| Screen-reader labels, untrusted text rendering, and keyboard-reachable review controls | `DocumentIngestionWorkspace` Vitest tests `renders untrusted content as text and exposes evidence and review labels accessibly` and `supports keyboard-reachable task, match, edit, reconsider, cancel, and delete controls` |
| PDF, scanned PDF, mixed PDF, DOCX, TXT, Markdown, conflict, evidence, later-document provenance, proposal, and apply journey | Playwright `completes the accessible document review and proposal workflow` |
| Existing proposal, reasoning, map, settings, and non-ingestion workbench behavior | The full Vitest suite and Playwright tests `browses asserted and inferred reasoning facts without writing from Reasoning`, `ontology map remains bounded, accessible, interactive, and read-only`, and `completes the browser workbench and provider model journey through reviewable staging` |
| Existing Kotlin, CLI, web server, and VS Code behavior | Full Gradle and VS Code extension suites |

Deterministic tests use fake OCR and provider adapters and require no credential. The real PDF/OCR/provider smoke suite was not run because this verification environment was not configured with the controlled credential and native-runtime prerequisites. The approved manual procedure remains in `phase-11-slice-0-contract-audit.md`; it verifies one scanned fixture, one bounded `store: false` provider request, log redaction, and temporary cleanup, and never applies a proposal.

## Dependency And Security Review

- The resolved runtime graph contains PDFBox 3.0.8 and POI OOXML 5.5.1.
- `npm audit --omit=dev` reports zero vulnerabilities.
- OSV-Scanner 2.3.8 reports zero known vulnerabilities.
- `web-app/osv-scanner.toml` contains no vulnerability ignore. It records exact-version, time-limited license classifications for seven existing transitive packages whose permissive license identifiers are outside the approved scanner allowlist.

## Verification Results

The complete Slice 8 gate passed:

```text
./gradlew clean test                         PASS
./gradlew build                              PASS
./gradlew check                              PASS
(cd web-app && npm ci)                       PASS
(cd web-app && npm audit --omit=dev)         PASS — 0 vulnerabilities
(cd web-app && npm test)                     PASS — 23 files, 95 tests
(cd web-app && npm run build)                PASS
(cd web-app && npm run test:e2e)             PASS — 4 tests
(cd vscode-extension && npm ci)              PASS
(cd vscode-extension && npm test)            PASS — 37 tests
OSV-Scanner source and license scan           PASS — 0 vulnerabilities
Gradle runtime dependency reports             PASS
git diff --check                              PASS
```

The Playwright development server logs expected connection-refused messages for mocked API and WebSocket traffic because the deterministic browser suite intentionally does not start a live Ktor server. All browser requests under test are fulfilled by Playwright fixtures, and all four tests pass.

## Delivered Boundary

This slice adds verification and focused defensive fixes only. It adds no feature, dependency, module, persistence layer, production test shortcut, or increased limit. Ontology changes still require the existing typed draft, proposal review, human approval, atomic apply, reload verification, and rollback path.
