# Phase 11 Slice 3: Deterministic Extraction And Page-Level OCR

## Status

Completed on 2026-07-24.

## ExecPlan Slice

Phase 11 Slice 3: deterministic extraction and page-level OCR.

## Goal

Extract stable, ordered, located text from approved document formats and use bounded English OCR only for PDF pages whose embedded text is not reliable.

## Implementation

- Added Apache PDFBox 3.0.8 and Apache POI 5.5.1 at the versions approved in Slice 0.
- Extracted PDF text one page at a time and applied the approved reliability rule before considering OCR.
- Rendered only unreliable PDF pages at 150 DPI and enforced page count, OCR-page count, elapsed time, dimensions, encoded-image size, and task image-byte limits.
- Added a startup-configured Tesseract 5.5.2 adapter with fixed English TSV arguments, no shell, cleared environment, bounded output, deadline enforcement, and forced termination after a grace period.
- Parsed DOCX paragraphs, headings, and table cells in document order with POI archive protections matching intake limits.
- Extracted UTF-8 TXT and Markdown in stable logical blocks, including safe Markdown heading recognition.
- Added deterministic block identities, normalized offsets, extraction method and version, page number, heading, exact text, OCR confidence, page geometry, normalized rectangles, temporary page-image identity, and confidence warnings.
- Added the deterministic mixed/non-English script boundary and failure cleanup for rendered OCR artifacts.
- Kept extraction inside `web-server`; no provider, semantic matching, frontend, staging, proposal, or ontology source behavior changed.

## Tests Added

- Ordered and repeatable TXT, Markdown, and DOCX paragraph, heading, and table extraction.
- Mixed PDF extraction proving reliable embedded text prevents OCR while a scanned page uses OCR.
- Stable block IDs, offsets, page order, geometry, normalized coordinates, and confidence warnings below 80 and 60.
- Embedded-text reliability boundaries, replacement characters, OCR-page bounds, extracted-character bounds, cancellation, and rendered-page cleanup.
- Encrypted PDF rejection and the mixed/non-English script boundary.
- Existing intake, server, and full Gradle checks continue to pass.

## Dependency And Security Review

- Gradle resolved `org.apache.pdfbox:pdfbox:3.0.8` and its 3.0.8 companion artifacts without a version conflict.
- Gradle resolved `org.apache.poi:poi-ooxml:5.5.1` and `poi-ooxml-lite:5.5.1` without a version conflict.
- OSV-Scanner was unavailable locally, so the approved OSV API fallback was queried directly for both Maven coordinates. Both queries returned no advisories on 2026-07-24.
- Tesseract remains an optional administrator-installed runtime and is not added as a repository dependency. Startup validation requires exactly version 5.5.2 and readable English trained data.

## Verification

```bash
./gradlew :web-server:test
./gradlew :web-server:build
./gradlew check
git diff --check
git status --short
```

Results:

- `:web-server:test`: passed.
- `:web-server:build`: passed.
- `check`: passed from clean generated outputs.
- `git diff --check`: passed.
- `git status --short`: showed only the approved Slice 3 files.

## Decisions, Assumptions, And Limitations

- OCR runs one page at a time. Client data cannot select the executable, language, arguments, environment, output path, or tessdata.
- A PDF page either uses reliable embedded text or OCR; it never silently combines both.
- Page previews are inert temporary PNG files. The original active document is never returned to a browser.
- Unsupported image codecs fail through the bounded PDFBox path; optional JBIG2 and JPEG2000 components were not added.
- Extraction output remains current-session task data. Slice 3 adds no durable document or extracted-text store.
