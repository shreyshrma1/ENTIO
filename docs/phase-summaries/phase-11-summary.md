# Phase 11 Summary

Phase 11, AI-Powered Document Ingestion and Ontology Evolution, was implemented and verified on 2026-07-24.

## Delivered

- Added authorized, project-scoped web intake for PDF, DOCX, TXT, and Markdown documents with safe filenames, signature checks, checksums, metadata, and temporary task storage.
- Added stable text extraction for TXT, Markdown, and DOCX plus page-aware PDF extraction that prefers reliable embedded text and uses the fixed local Tesseract boundary only for unreliable pages.
- Added bounded OpenAI document analysis through the current user's verified selected compatible model. The adapter uses strict structured responses, no tools, fixed endpoints, safe failures, retry limits, cancellation, and exact-work reuse.
- Added Kotlin-owned exact evidence verification, local/import/current-work/prior-provenance/FIBO matching, canonical duplicate prevention, and deterministic recommendation ordering.
- Added explicit confirm, extend, revise, split, merge, conflict, and supersede review outcomes without allowing recency or AI output to decide ontology truth.
- Added an accessible Documents workspace for upload metadata, task progress, safe evidence viewing, matches, conflicts, clarification, reconsideration, acceptance, rejection, editing, cancellation, deletion, and typed-draft submission.
- Converted accepted recommendations only into existing supported typed ontology edits, in ordered all-or-nothing batches, with field-level document provenance.
- Reused the existing shared staging, preview, semantic diff, validation, reasoning, SHACL, proposal review, human approval, atomic apply, reload verification, and rollback workflow.
- Added a narrowly scoped durable repository for successfully applied document-derived provenance so later workflows can compare new evidence after server restart.

## Bounds

- 10 documents per task and 25 MB per document.
- 500 PDF pages and 5,000,000 normalized extracted characters per document.
- 200 OCR pages and 10 minutes of OCR wall time per document.
- 2,000 candidates per document, with at most 200 in one provider response.
- 8 evidence excerpts per recommendation and 500 characters per excerpt.
- 20 provider calls per task, including retries and reconsideration.
- 30 minutes of processing before review, 100 accepted edits per task, and 20 edits per atomic draft batch.
- Two active ingestion tasks by default; OCR and provider work are serialized within each task.

## Ownership And Trust

`core-types` owns immutable ingestion, evidence, recommendation, and provenance records. `semantic-engine` owns evidence verification, semantic matching, duplicate checks, evolution meaning, and typed-edit conversion. `web-server` owns authorized intake, temporary lifecycle, extraction adapters, OCR, provider orchestration, review state, and applied-change provenance. `web-app` owns presentation and reviewer actions only.

Documents, OCR output, and provider output are untrusted. They cannot widen permissions, choose tools or endpoints, supply raw RDF, write ontology sources, approve a proposal, or trigger automatic apply. Credentials stay server-side. No source changes occur before ordinary human approval.

## Dependencies

- Apache PDFBox 3.0.8 for PDF processing.
- Apache POI OOXML 5.5.1 for DOCX processing.
- A fixed local Tesseract 5.5.2 runtime with approved English data for OCR.

The final resolved dependency graph, production npm tree, and OSV source/license scan passed. OSV reported zero known vulnerabilities.

## Lifecycle And Persistence

Original uploads, extracted text, rendered page images, OCR artifacts, incomplete task state, and review workspaces are temporary. Cancellation and deletion remove task artifacts, and startup cleanup removes stale marked task directories.

Only bounded provenance for successfully applied document-derived changes survives restart. It is authorized by project, stored separately from ontology source files, contains exact supporting excerpts rather than full documents, and is committed atomically with the existing apply outcome. Phase 11 adds no production document store, task database, queue, or durable proposal history.

## Verification

Every ordered slice was implemented on its own branch, verified, committed, pushed, and merged locally with a visible non-fast-forward boundary. The Slice 8 acceptance record maps the approved criteria to exact automated tests.

The final gate passed:

```text
./gradlew clean test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm audit --omit=dev && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```

Results included 95 web unit tests, 4 Playwright journeys, 37 VS Code extension tests, zero npm production vulnerabilities, and zero OSV known vulnerabilities. Deterministic tests use fake OCR and provider adapters. The controlled real-adapter smoke procedure remains optional for a specifically configured credentialed environment and never applies a proposal.

## Known Limitations

- Only English PDF, DOCX, TXT, and Markdown are supported.
- Encrypted or password-protected documents, handwritten OCR, broader office/image formats, and unsupported DOCX active content are rejected.
- Extraction and analysis are bounded development workflows, not a production ingestion service or durable job system.
- There is no external document indexing, embedding store, new ontology catalog, autonomous agent, raw RDF path, or automatic apply.
- The CLI and VS Code extension do not expose document-ingestion commands.
- Durable history is limited to applied document-derived provenance; original documents and incomplete workflows are not retained.

## Planning And Evidence

- [Scope](../architecture/phase-11-scope.md)
- [Spec](../specs/0020-phase-11-ai-powered-document-ingestion-and-ontology-evolution.md)
- [ExecPlan](../execplans/0020-phase-11-ai-powered-document-ingestion-and-ontology-evolution.md)
- [Contract audit](../decisions/phase-11-slice-0-contract-audit.md)
- [Acceptance verification](../decisions/phase-11-slice-8-verification.md)
