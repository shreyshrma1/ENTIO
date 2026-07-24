# Phase 11 Slice 0: Contract, Dependency, Security, And Typed-Operation Audit

## Status

Approved on 2026-07-24.

This decision completes ExecPlan Slice 0. Production implementation may begin with Slice 1, subject to the slice boundaries and stop conditions in the approved ExecPlan.

## Slice And Goal

ExecPlan Slice 0 approves the exact contracts, dependencies, routes, permissions, temporary-storage boundary, OCR and provider behavior, semantic reuse points, provenance repository, and verification gates for Phase 11.

No production code, test code, fixtures, dependencies, lockfiles, or build files change in this slice.

## Contract Files And Ownership

Slice 1 will add exactly:

- `core-types/src/main/kotlin/com/entio/core/DocumentIngestionContracts.kt`
- `core-types/src/main/kotlin/com/entio/core/DocumentRecommendationContracts.kt`
- focused tests with the same base names under `core-types/src/test/kotlin/com/entio/core/`

`DocumentIngestionContracts.kt` owns:

- `DocumentId`, `DocumentTaskId`, `DocumentTextBlockId`, and `DocumentEvidenceId`;
- `DocumentMediaType`, `DocumentAuthorityStatus`, `DocumentProcessingStatus`, and `DocumentExtractionMethod`;
- `DocumentAuthorityMetadata`, `IngestionDocument`, `DocumentPageGeometry`, `DocumentTextRectangle`, and `LocatedDocumentTextBlock`;
- `DocumentEvidenceType`, `DocumentEvidenceReference`, and `DocumentEvidence`;
- `DocumentReviewDecision` and `DocumentDraftProvenance`.

`DocumentRecommendationContracts.kt` owns:

- `DocumentCandidateCategory`, `DocumentRecommendationCategory`, `DocumentRecommendationAction`, `DocumentConfidenceBand`, and `DocumentRecommendationReviewStatus`;
- `DocumentCandidateIdentity`, `DocumentCandidate`, and `DocumentMatchCandidate`;
- `DocumentAmbiguity`, `DocumentConflictAlternative`, `DocumentConflict`, and `DocumentRecommendationDependency`;
- `DocumentRecommendation`, `DocumentSummaryHighlight`, and `DocumentSummary`;
- `AppliedDocumentProvenance` and its document, evidence, decision, typed-operation, and apply-event records.

All records are immutable. They contain no Ktor, Jackson, provider, parser, filesystem, PDFBox, POI, Tesseract, Jena, or React types. Core records use opaque IDs, canonical IRIs, RDF terms, `Instant`, and normalized primitive values only.

### Contract Invariants

- Opaque IDs are nonblank, at most 200 characters, and match `[A-Za-z0-9][A-Za-z0-9._:-]*`.
- A checksum is a lowercase 64-character SHA-256 hexadecimal value.
- Display filenames are trimmed, 1–255 characters, contain no control characters, path separators, `.` or `..` path segments, or bidirectional override/isolate characters.
- Byte size is 1–26,214,400. A task contains 1–10 distinct document IDs.
- Media type is one of `Pdf`, `Docx`, `Text`, or `Markdown`.
- Authority status is one of `Authoritative`, `Supporting`, `Draft`, `Historical`, `Superseded`, or `Amendment`.
- Applicability strings are trimmed and at most 200 characters. Effective date cannot be after expiration date. Amendment and supersession references must name another document in the same task or retained project provenance.
- Page numbers are one-based. Page geometry has positive dimensions. Rectangle coordinates are finite values in `0.0..1.0`, with left not after right and top not after bottom.
- Located-block offsets are zero-based, end-exclusive, nonnegative, ordered, and within the normalized document text. Exact block text is nonblank and at most the remaining per-document text allowance.
- `EmbeddedText`, `Docx`, `Text`, and `Markdown` blocks do not carry OCR confidence, rectangles, or page-image IDs. `Ocr` blocks carry confidence in `0..100`, page geometry, and a server-issued page-image ID.
- Evidence references resolve within the same authorized task and document. Their offsets are within one block, and their excerpt must equal the server-held normalized substring exactly. An excerpt is 1–500 characters.
- A recommendation has 1–8 evidence references when it claims document evidence. `CombinedEvidence` has at least two distinct evidence references. `ExternalOntologyEvidence` and `ReasoningImpact` reference Entio records and never masquerade as document excerpts.
- Confidence is an integer in `0..100`; the derived bands are `High` for 80–100, `Medium` for 60–79, and `Low` for 0–59.
- `OntologyStructure` and `BusinessFact` are distinct recommendation categories. A candidate or action/category pairing that cannot map to an approved meaning is rejected at construction or normalization.
- `Confirm`, `InsufficientEvidence`, and `Unsupported` cannot claim a draft item. Split, merge, conflict, supersede, low-confidence OCR, ambiguous match, replacement of existing meaning, and unresolved target/source remain blocked until a recorded clarification resolves the gate.
- Review decisions are append-only records with actor, time, previous status, new status, and optional clarification. They do not grant approval or apply authority.
- Stable ordering is by document order, page or logical block order, block offset, category, normalized identity key, and opaque ID.

### Stable Identity

Stable IDs use SHA-256 over a UTF-8, length-prefixed encoding; delimiters alone are not used.

- A block ID includes document checksum, extraction-version ID, page or logical section, block ordinal, normalized offsets, and normalized text.
- A candidate ID includes document checksum, candidate category, normalized candidate value, and sorted evidence identity keys.
- A recommendation ID includes sorted candidate IDs, category, action, normalized proposal value, canonical match keys, and sorted evidence keys.
- A normalized typed-operation key includes operation kind, target source, canonical IRIs, RDF literal lexical form/datatype/language, and operation-specific values.

Server-issued task and document IDs remain random opaque UUID-based IDs. Stable content identities never contain a server path.

## JSON Mapping

Core records are not transport DTOs. Slice 6 will map them into additive Ktor DTOs under `web-server/contract` and matching TypeScript types under `web-app/src/web`.

JSON uses:

- lower camel-case field names;
- lowercase kebab-case enum values, for example `awaiting-review`, `ontology-structure`, `strongly-implied`, and `reuse-local`;
- ISO-8601 UTC timestamps and ISO dates;
- lowercase SHA-256 strings;
- opaque IDs and canonical IRI strings;
- zero-based, end-exclusive character offsets;
- normalized coordinates in `0.0..1.0`.

List, task-progress, and cancellation DTOs contain IDs, counts, bounded status messages, and safe filenames only. They never contain document bodies, evidence excerpts, provider payloads, credentials, temporary paths, page-image paths, or provenance-repository paths. Evidence and extracted text are returned only from their focused, authorized, paginated endpoints.

## HTTP Routes

All routes are under `/api/v1/projects/{projectId}/document-ingestion`.

| Method | Route | Purpose |
| --- | --- | --- |
| `POST` | `/tasks` | Multipart intake; returns `202` with the task descriptor before extraction/provider work. |
| `GET` | `/tasks` | Paginated owner-only current-session task list without bodies or excerpts. |
| `GET` | `/tasks/{taskId}` | Bounded task detail, stages, counts, warnings, and safe errors. |
| `POST` | `/tasks/{taskId}/cancel` | Cancel future work and start temporary cleanup. |
| `DELETE` | `/tasks/{taskId}` | Delete owner task state and temporary artifacts. |
| `GET` | `/tasks/{taskId}/documents/{documentId}/blocks` | Paginated located extracted-text blocks. |
| `GET` | `/tasks/{taskId}/documents/{documentId}/pages/{pageNumber}/preview` | Server-rendered PNG page preview; never the active document. |
| `GET` | `/tasks/{taskId}/evidence/{evidenceId}` | One exact, bounded evidence record. |
| `GET` | `/tasks/{taskId}/summaries` | Evidence-linked document summaries. |
| `GET` | `/tasks/{taskId}/recommendations` | Paginated/filterable recommendations. |
| `GET` | `/tasks/{taskId}/recommendations/{recommendationId}` | One recommendation and its bounded evidence/matches. |
| `PUT` | `/tasks/{taskId}/recommendations/{recommendationId}/review` | Idempotent accept, reject, edit, rematch, clarify, merge, and target-source decision. |
| `POST` | `/tasks/{taskId}/recommendations/{recommendationId}/reconsider` | One bounded reconsideration through the same provider boundary. |
| `GET` | `/tasks/{taskId}/draft-impact` | Read-only proposed typed mappings and current block reasons. |
| `POST` | `/tasks/{taskId}/draft` | Slice 7 conversion of accepted current recommendations into ordered all-or-nothing shared-staging batches. |

Mutating routes require an `Idempotency-Key`, except cancellation and deletion, whose terminal state makes replay idempotent. Draft conversion does not approve or apply. After conversion, the existing `/proposal/preview`, `/proposal/approve`, `/proposal/reject`, and `/proposal/apply` routes remain authoritative.

## Development Identity And Permissions

Phase 11 remains explicitly development-user scoped. The current identity boundary has two known users and no project-membership model. Every configured user can access every registered development project, but task data is additionally keyed by project ID and owner user ID.

- Intake, task read, extraction/evidence read, review, reconsideration, cancellation, and deletion require the task owner and the existing `PREPARE_EDIT` permission.
- Draft conversion additionally requires `STAGE_OWN_CHANGE`.
- A reviewer does not gain access to another user's temporary task merely by holding the reviewer role.
- Once document-derived entries enter shared staging, existing shared-stage visibility and reviewer-only approve/apply rules apply.
- Durable provenance is never exposed through an unscoped listing route. Internal lookup requires a registered project and `BROWSE`; applied proposal review uses the existing reviewer boundary.
- An unknown project, wrong owner, wrong project/task pair, or deleted task returns the same `ingestion-task-not-found` shape without confirming whether the task exists elsewhere.

This additive owner check is sufficient for Phase 11's development boundary. Production authentication, tenancy, and per-project membership remain out of scope.

## Temporary Storage And Lifecycle

The server owns a single configured root. Its default is `${java.io.tmpdir}/entio-document-ingestion-v1`; production-like launchers may set one absolute root at server startup. The root must not be equal to, inside, or a parent of any registered project root.

- The server creates the root and an ownership marker `.entio-ingestion-root-v1`.
- Task paths are `task-<server UUID>` and document files are `document-<server UUID>.bin`; user filenames never form paths.
- Page artifacts use server-generated page IDs and `.png` files.
- Creation uses owner-only permissions where POSIX permissions are available.
- Every access starts from the configured root, normalizes the generated child, verifies containment, rejects symbolic links with `NOFOLLOW_LINKS`, and rechecks the real parent.
- Cleanup deletes only marked Entio task directories immediately below the owned root. It never recursively deletes an unmarked or unresolved path.
- Intake failure, cancellation, deletion, a 24-hour task lifetime, and normal shutdown remove all task files, rendered pages, extracted text, and in-memory review state.
- Startup removes stale marked task directories. Restart never resumes an incomplete task.

Temporary storage never overlaps ontology sources and never falls back to a project directory.

## Intake And DOCX Safety

- Intake streams each part to its generated file while hashing and enforcing the 25-MB limit; it never buffers a whole upload first.
- Extension, declared media type, and detected signature must agree. TXT and Markdown must be valid UTF-8.
- PDF must begin with a valid PDF signature and parse as an unencrypted PDF. DOCX must be a valid OOXML ZIP package and be unencrypted.
- ZIP entry names must be unique, relative, normalized, and free of traversal and control characters.
- DOCX allows at most 1,000 entries, 100 MB total uncompressed bytes, 25 MB per entry, a maximum 100:1 expansion ratio per entry and overall, 100 embedded images, 10 MB per image, and 50 MB total image bytes.
- Apache POI `ZipSecureFile` is configured consistently with the same 0.01 minimum inflate ratio and 25-MB entry limit.
- Macro-enabled content types, `vbaProject.bin`, ActiveX, OLE/package embeddings, executable entries, external relationships, encrypted packages, scripts, and remote template links are rejected.
- Parsers do not resolve external entities or follow document relationships.

## Extraction And OCR Decisions

### Libraries And Runtime

- PDF: `org.apache.pdfbox:pdfbox:3.0.8`.
- DOCX: `org.apache.poi:poi-ooxml:5.5.1`.
- OCR: administrator-provided Tesseract `5.5.2` with English trained data.
- Tess4J is not used. The Kotlin adapter invokes the fixed Tesseract executable directly with `ProcessBuilder`; it does not invoke a shell.

The executable and tessdata directory are server-startup configuration only. They must be absolute, existing, non-symlink paths. The executable must be a regular executable and report exactly Tesseract 5.5.2; the configured tessdata directory must contain readable `eng.traineddata`. Client input cannot select the executable, arguments, language, output path, or environment.

The adapter supplies fixed arguments for English LSTM OCR and TSV output, writes only to its task directory, clears inherited environment entries not required by the process, captures bounded output, enforces a deadline, and destroys the process forcibly after the cancellation grace period.

### Page Rules

PDFBox embedded text is usable only when the approved spec's complete rule passes: at least 30 non-whitespace characters; at least 60 percent letter/number characters among non-whitespace characters; no repeated replacement-character pattern; and at least one line with three or more words.

OCR is used only when that rule fails. Rendering is fixed at 150 DPI, maximum 5,000 by 5,000 pixels, maximum 10 MB encoded PNG per page, and maximum 512 MB rendered-image bytes per task. Existing limits of 200 OCR pages and 10 minutes of OCR wall time per document remain.

Page previews are inert server-rendered PNG images. OCR and preview images remain available only while the temporary task exists. Embedded-text pages are rendered on demand under the same dimensions and byte budget. The browser never embeds or executes the original PDF.

The deterministic fixture corpus in Slice 3 must include text, sparse, replacement-character, image-only, mixed, encrypted, oversized-page, and mixed-script PDFs; DOCX headings, tables, relationships, macros, and archive attacks; and UTF-8 text/Markdown boundaries.

### Language

The intake metadata must declare `en`. OCR always uses the `eng` language pack. A deterministic script check runs after extraction: when a document has at least 100 letters and more than 10 percent are non-Latin, it is blocked as unsupported mixed/non-English content. This is a conservative boundary check, not a claim of general language detection.

## Dependency, License, And Vulnerability Decision

PDFBox 3.0.8 and POI 5.5.1 are Apache License 2.0 releases. Tesseract 5.5.2 is Apache License 2.0 and its Leptonica dependency is BSD-2-Clause. Those licenses are acceptable for this repository. PDFBox optional JBIG2/JPEG2000 components with separate distribution terms are not added; unsupported image encodings fail safely.

The official PDFBox security page identifies 3.0.8 as the fixed 3.x release for the 2026 path-traversal advisories, which affected the example extraction utility rather than the core library. Entio does not copy or use that utility. Direct OSV queries on 2026-07-24 returned no advisories for `org.apache.pdfbox:pdfbox:3.0.8` or `org.apache.poi:poi-ooxml:5.5.1`. The Tesseract 5.5.2 NVD keyword query returned no records. These results are a point-in-time gate, not a permanent guarantee.

Official references:

- `https://pdfbox.apache.org/` and `https://pdfbox.apache.org/security.html`
- `https://poi.apache.org/download.cgi`, `https://poi.apache.org/security.html`, and `https://poi.apache.org/legal.html`
- `https://github.com/tesseract-ocr/tesseract/releases/tag/5.5.2`
- `https://tesseract-ocr.github.io/tessdoc/Command-Line-Usage.html`

After Slice 3 resolves the actual graph, run:

```bash
./gradlew :web-server:dependencies --configuration runtimeClasspath
./gradlew :web-server:dependencyInsight --configuration runtimeClasspath --dependency pdfbox
./gradlew :web-server:dependencyInsight --configuration runtimeClasspath --dependency poi-ooxml
osv-scanner scan source --recursive --licenses="Apache-2.0,BSD-2-Clause,MIT,ISC,MPL-2.0" .
```

Use OSV-Scanner 2.3.8 or the corresponding OSV API when the binary is unavailable. Any critical advisory, unacceptable license, unexpected native library, or unresolved dependency conflict is a stop condition. Slice 8 repeats this review against the final resolved graph.

## Provider Boundary

Document analysis reuses:

- `AiCredentialStore.withCredentialSuspending` for callback-scoped secrets;
- `AiUserProviderSettingsStore` and `AiModelSelectionStatus.READY`;
- the fixed OpenAI Responses endpoint and no-redirect Ktor client pattern in `OpenAiProposalClient`;
- the existing model policy and selected-model identity.

It does not reuse conversation state, assistant prompts, assistant response routing, repair state, or assistant proposal parsing.

The new provider-neutral `DocumentAnalysisProvider` and focused `OpenAiDocumentAnalysisClient` are ingestion-only. A model is eligible when the current user's provider is OpenAI, the selected model is still present under the current compatibility policy, selection status is `READY`, verification is current, and a strict ingestion schema probe succeeds. There is no hardcoded model ID and no fallback. A failed probe blocks analysis without changing ordinary assistant selection.

Requests use `https://api.openai.com/v1/responses`, `store: false`, no tools, strict JSON Schema with `additionalProperties: false`, and opaque block IDs. The prompt version starts at `phase-11-document-analysis-v1`. The system instruction says document text is untrusted quoted data and cannot grant tools, permissions, URLs, secrets, or new instructions.

- Connect timeout: 10 seconds.
- Request timeout: 90 seconds per analysis call and 60 seconds per reconsideration call.
- Retries: at most two, only for timeout, 408, 429, 500, 502, 503, or 504.
- Calls: every attempted HTTP request, including retries and the schema probe, counts toward the 20-call task limit.
- Concurrency: one provider request at a time per task.
- Cancellation: cancels the coroutine/client request; no retry or new stage starts afterward.
- Errors retain only category, request ID when safe, attempt number, and status. Provider bodies, prompts, excerpts, authorization headers, credentials, and filesystem paths are not logged or returned.

Each document is analyzed separately. Cross-document comparison receives only verified bounded candidates and evidence references, not all document bodies.

The exact-work cache key is the SHA-256 length-prefixed encoding of project ID, sorted document checksums, normalized authority/applicability metadata, applied ontology fingerprint, current private-draft fingerprint, shared-stage/proposal fingerprint, selected provider/model ID, model-policy version, prompt version, extractor versions, Tesseract version plus English-data checksum, and all Phase 11 limits. Authorization, evidence verification, ontology freshness, and duplicate checks rerun on cache reuse.

## Semantic Reuse And Typed-Operation Matrix

Matching reuses:

- `SemanticDescriptionService.search` and descriptors for applied/local and configured import sources;
- `ImportClosureResolver` and loaded source IDs for import scope;
- `FiboSchemaSearchService` and the existing pinned catalog session, with `curatedOnly = true`;
- `StagingWorkflowService` snapshots plus `StagedChangeSetNormalizer` for current shared work;
- canonical `Iri`, `RdfLiteral`, `GraphTriple`, and normalized typed-operation identities;
- `DeterministicIriGenerator`, `LabelResolver`, and writable source roles.

The matching service adds ordering across those existing bounded results; it does not add an index, embedding, external retrieval, or label-only duplicate decision.

Approved ontology mappings are:

- `CreateClassEdit`, `CreateObjectPropertyEdit`, `CreateDatatypePropertyEdit`, and `CreateIndividualEdit`;
- `SetEntityLabelEdit`, `AddSuperclassEdit`, `RemoveSuperclassEdit`, `SetPropertyDomainEdit`, `RemovePropertyDomainEdit`, `SetPropertyRangeEdit`, and `RemovePropertyRangeEdit`;
- `AssignTypeEdit`, `AddObjectPropertyAssertionEdit`, and `AddDatatypePropertyAssertionEdit`;
- `SemanticEditRequest.AddDefinition`, `ReplaceDefinition`, `AddAlternateLabel`, and `ReplaceAlternateLabel`;
- existing approved FIBO reuse through `ExternalProposalIntentTranslator`.

Removal operations are emitted only as part of an explicit accepted revision with an exact current value. General deletion, bulk IRI migration, arbitrary annotation properties, raw triples, and unsupported equivalence semantics are not exposed.

Approved ingestion SHACL mappings are limited to:

- `TypedShaclEdit.CreateNodeShape`;
- `TypedShaclEdit.CreatePropertyShape`;
- `TypedShaclEdit.UpdateConstraint`;
- `MinCount`, `MaxCount`, `Datatype`, `Class`, `MinInclusive`, `MaxInclusive`, and `Pattern`.

`UpdateShapeLabel`, `RemoveConstraint`, and `DeleteShape` remain available to the ordinary workbench but are not generated from ingestion. Any other OWL or SHACL request remains review-only and blocks draft conversion.

## Draft, Proposal, And Provenance Flow

`DocumentRecommendationDraftTranslator` produces approved typed operations only. The ingestion service prepares all items first, rechecks the graph and evidence, and then calls one new atomic batch method on `StagingWorkflowService`. A batch contains at most 20 entries; a task contains at most five ordered batches and 100 entries. A failed item mutates none of its batch.

The task owner’s private recommendations are not shared staging. Draft conversion requires the shared staging area to be empty or to contain only entries previously created by the same task; this prevents an ingestion task from silently absorbing unrelated changes into its one final proposal.

Field-level provenance is attached additively to `StagedChange`, projected in `WebStagedEntry`, and retained in the server's prepared proposal session by staged item ID. It is not added to `ChangeProposal`, Turtle, SHACL files, or graph changes. Existing preview, validation, reasoning, SHACL, approval, apply, reload, and rollback behavior remains authoritative.

`Confirm` creates no staged edit. Its accepted evidence is queued as provenance-only work and is committed only with the task's successful existing apply. A task containing only confirms records provenance through an explicit reviewer-confirmed provenance commit and does not create or apply an empty ontology proposal.

## Durable Applied-Provenance Repository

The approved repository is a versioned, server-owned JSON snapshot store outside project and temporary roots. It is not a database or document store.

- Root: startup-configured absolute `entio-data/phase-11-provenance-v1`.
- One directory per SHA-256 project-ID key, with `records-v1.json`, a write-ahead `pending-v1.json`, and an ownership marker.
- Schema version: `1`.
- Record key: project ID plus record ID. Record ID is the stable SHA-256 identity of target source, normalized typed operation or confirm target, document checksum, evidence keys, recommendation ID, decision ID, and apply/provenance event.
- Records contain only the bounded fields defined in `AppliedDocumentProvenance`; no complete document, page image, credential, provider payload, project path, or temporary path is retained.
- Reads require the registered project ID. Public task routes never enumerate the repository.
- Writes use a same-directory temporary file, file flush, and atomic move. Unsupported schema versions block repository use; migrations require a later explicit decision.
- Maximum size is 100,000 records or 512 MB per project. Reaching either limit blocks a new document-derived apply.
- Records supporting ontology content still present in the project are retained. Unreferenced records expire seven years after their apply event. Startup cleanup removes only expired unreferenced records after loading the authorized project. Project deregistration does not silently delete audit records; administrator cleanup is explicit.

Atomicity uses a write-ahead protocol:

1. Before ontology apply, write a pending event containing the baseline fingerprint, expected resulting fingerprint, proposal ID, and complete bounded provenance records.
2. Run the existing atomic source apply and reload verification.
3. As the final post-save verification step, atomically merge the pending records into `records-v1.json`, then remove the pending event.
4. If provenance commit fails, return verification failure so the existing applier restores ontology sources; after confirmed rollback, remove the pending event.
5. On startup, compare a pending event with the authorized project's fingerprint. Finalize it only when the expected resulting fingerprint matches, remove it when the baseline matches, and otherwise quarantine it and block new ingestion applies for that project.

The server never reports a document-derived apply as successful before step 3. This protocol does not change ordinary non-ingestion proposals.

## Iterative Comparison

Comparison order is:

1. applied local ontology;
2. configured imports;
3. private task decisions and current shared staged/proposal work;
4. other recommendations in the task;
5. authorized durable provenance from earlier applied workflows;
6. the pinned curated FIBO catalog.

Documents are extracted and analyzed independently, then their verified candidates are compared. Authority, applicability, effective/expiration dates, explicit amendment/supersession metadata, canonical entity identity, normalized typed-operation identity, and evidence are considered. Recency alone never implies authority or supersession.

## Concurrency And Bounds

- One active ingestion execution per project.
- Two active ingestion executions server-wide.
- One OCR page operation and one provider request at a time per task.
- A task may use one extraction worker and one analysis worker, but stages for that task remain sequential.
- Queued tasks do not consume provider or OCR resources.
- Cancellation, deletion, expiration, or supersession prevents all new work and invalidates late results.

All numeric limits in the approved spec remain unchanged. OCR rendering adds the limits recorded above.

## Acceptance Traceability

| Acceptance area | Implementation slice | Required proof |
| --- | --- | --- |
| Formats, intake bounds, authorization, temporary lifecycle | 2, 3, 8 | Server unit/integration and hostile-intake fixtures |
| Embedded text, OCR, locations, confidence, English/encryption boundary | 3, 8 | Deterministic extraction/OCR fixtures and E2E |
| Selected-model boundary, bounded analysis, summaries, exact evidence | 4, 8 | Fake-provider contract/security tests and controlled smoke |
| Local/import/current-work/prior-provenance/FIBO matching | 5, 8 | Deterministic semantic and restart fixtures |
| Confirm/extend/revise/split/merge/conflict/supersede | 5, 6, 8 | Evolution-service, API, UI, and E2E tests |
| Safe review, editing, clarification, reconsideration, accessibility | 6, 8 | Ktor, Vitest, accessibility, and Playwright tests |
| Typed mappings, batching, provenance, existing proposal/apply path | 7, 8 | Translator, staging, apply/reload/rollback integration |
| Isolation, injection, redaction, limits, cancellation, cleanup | 2–8 | Security and boundary suites |
| Documentation and delivered-boundary consistency | 9 | Link, status, and acceptance review |

Every feature-spec acceptance criterion maps to at least one row. Slice 8 must list the exact test names and results before Slice 9 may mark the phase implemented.

## Controlled Smoke Checks

Deterministic CI uses fake OCR and provider adapters. A real-adapter smoke environment requires the fixed Tesseract runtime, English data, a temporary non-project root, and an explicitly configured current-user OpenAI credential/model.

The controlled smoke checks:

1. verify the fixed Tesseract version and English data;
2. extract one scanned fixture and inspect confidence/coordinates;
3. send one bounded strict-schema analysis request with `store: false`;
4. verify no credential, path, prompt, or excerpt appears in captured logs;
5. delete the task and verify temporary cleanup.

Smoke checks never apply an ontology proposal and are not required in credential-free deterministic CI.

## Files Modified

- `docs/decisions/phase-11-slice-0-contract-audit.md`
- `docs/specs/0020-phase-11-ai-powered-document-ingestion-and-ontology-evolution.md`
- `docs/execplans/0020-phase-11-ai-powered-document-ingestion-and-ontology-evolution.md`

## Tests And Verification

No test code was added or changed. The slice used documentation/contract review, source inspection, the current Gradle runtime dependency report, official dependency/security sources, and point-in-time OSV/NVD queries.

Required commands:

```bash
git diff --check
git status --short
```

Both commands passed before commit.

## Commit

A focused Slice 0 commit was created on `feature/phase-11-slice-0-contract-audit`; its hash is recorded in Git history.

## Assumptions And Limitations

- Identity remains a development boundary with owner-isolated temporary tasks, not production tenancy.
- Tesseract is an administrator prerequisite and is not bundled.
- PDF previews are raster images, not an interactive PDF document viewer.
- Temporary tasks do not survive restart.
- Only applied-change provenance is durable.
- Non-English, encrypted, handwritten, standalone-image, spreadsheet, email, and web ingestion remain unsupported.
- Dependency and vulnerability conclusions must be repeated after the real dependency graph exists and again in Slice 8.
