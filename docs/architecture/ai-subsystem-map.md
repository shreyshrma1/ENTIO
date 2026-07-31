# Entio AI Subsystem Map

## Current Status

Entio includes an active native ontology assistant backed by the OpenAI Responses API.

The assistant supports:

- project-scoped conversations and in-memory chat history;
- ontology-aware answers grounded in bounded project context;
- response routing between answers, clarifications, and edit proposals;
- optional bounded FIBO context;
- structured review-only ontology edits;
- deterministic proposal validation and repair attempts;
- status polling and cancellation;
- edit removal, proposal rejection, and staging into the shared review queue.

The assistant does not have arbitrary tools, shell or filesystem access, direct ontology-source writes, approval authority, or an automatic apply path. Staged AI edits use the ordinary proposal review, approval, apply, reload, and rollback workflow.

Phase 11 is implemented. It extends this existing assistant and provider foundation with a separate bounded document-ingestion workflow and evidence-grounded recommendations. It does not replace the conversational assistant.

Phase 11.5 and Phase 11.5+ are implemented. They introduced evidence-grounded
per-document discovery, connected modeling over bounded chunks, optional
consolidation, focused prerequisite completion, and deterministic Kotlin
semantic assembly and compilation. Those provider stages and the legacy
reconciliation, ontology-alignment, critic, and final-planning contracts remain
for compatibility and historical tests. Phase 11 remains the upload,
extraction, evidence, authorization, review, proposal, apply, rollback, and
applied-provenance foundation.

Phase 12 is implemented. The default new-task path now performs deterministic
local candidate extraction and authorized ontology retrieval before one bounded
grounded interpretation stage. Kotlin verifies server-issued selections,
freshness, connected context, and duplicates before reusing the existing
semantic compiler and review path. The earlier Phase 11.5+ provider sequence is
available only through explicit compatibility configuration and is not an
automatic fallback.

## Active Server Ownership

| Concern | Entry points | Boundary |
| --- | --- | --- |
| Credential storage and status | `AiProviderContracts.kt` (`AiCredentialService`, `AiCredentialStore`) | Secrets remain server-only and are exposed only through callback-scoped access. |
| Provider credential verification | `OpenAiCredentialClient.kt`, `AiProviderClient` | Verifies the configured OpenAI credential through a fixed provider boundary. |
| Model discovery and access verification | `provider/openai/OpenAiModelDiscoveryClient.kt` | Reads the provider model inventory and verifies explicit model access. |
| Compatibility and selection state | `ai/models/` | Owns server-side filtering, per-user candidates, explicit selection, verification state, and freshness. |
| Assistant orchestration | `AiProposalService.kt` | Owns in-memory conversations and runs, ontology context, response routing, bounded FIBO context, validation/repair, cancellation, proposal state, and staging handoff. |
| OpenAI generation adapter | `OpenAiProposalClient.kt` | Calls the fixed OpenAI Responses endpoint with the verified selected model and structured response formats. It exposes no tools or direct write capability. |
| AI proposal validation | `AiSemanticProposalValidator.kt` and existing graph/proposal services | Checks generated edits deterministically before they can be staged. |
| Document contracts and semantic checks | `DocumentGroundedAnalysisContracts.kt`, existing `Document*` contracts, `DocumentOntologyRetrievalService.kt`, `DocumentGroundedAnalysisVerifier.kt`, `DocumentSemanticPlanCompiler.kt`, `DocumentChangeSetPlanVerifier.kt`, `DocumentRecommendationDraftTranslator.kt` | Owns bounded neutral candidates and choices, exact evidence and selection verification, complete-scope retrieval, freshness, duplicate checks, deterministic semantic compilation, collision-checked references, and conversion to existing typed edits. |
| Document task orchestration | `web/ingestion/DocumentIngestionOrchestrator.kt` and supporting services | Owns authorized intake, temporary storage, extraction, selective OCR, local NLP, retrieval input assembly, the grounded work key, provider budgets, grouped review state, cancellation, cleanup, typed draft handoff, and durable applied-change provenance. |
| Local NLP candidate extraction | `DocumentCandidateExtractionService.kt` | Uses pinned Apache OpenNLP `2.5.11` and English resources `1.3.0` to produce stable exact-span candidates. It uses no hosted NLP, embedding model, or vector service. |
| Document analysis adapter | `OpenAiDocumentAnalysisClient.kt`, `DocumentGroundedAnalysisService.kt` | Uses the current verified selected compatible model through fixed, strict-schema, no-tools grounded requests over bounded evidence and server-issued choices. It does not ask the model for final IRIs, raw RDF, low-level Entio operations, or write instructions. |
| Redacted HTTP boundary | `Application.kt` and `contract/AiProposalContracts.kt` | Exposes credential/model settings and authorized project-scoped assistant proposal routes. |
| Provider settings UI | `web-app/src/workbench/AiCredentialSettings.tsx` | Collects credentials and renders redacted provider/model status. |
| Assistant UI | `web-app/src/workbench/AiProposalPanel.tsx`, `ProjectWorkspace.tsx` | Provides the AI sidebar, conversations, history, status, proposal review, edit removal, cancellation, rejection, and staging controls. |
| Document review UI | `web-app/src/workbench/document-ingestion/DocumentIngestionWorkspace.tsx` | Provides upload metadata, progress, safe evidence viewing, recommendation decisions, clarification, reconsideration, and typed-draft submission. |

## Active Routes

Provider and model settings:

```text
GET    /api/v1/ai/credential-status
GET    /api/v1/ai/provider-settings
PUT    /api/v1/ai/credentials
POST   /api/v1/ai/credentials/test
DELETE /api/v1/ai/credentials
POST   /api/v1/ai/models/discover
GET    /api/v1/ai/models
PUT    /api/v1/ai/model-selection
POST   /api/v1/ai/model-selection/test
DELETE /api/v1/ai/model-selection
```

Project-scoped assistant proposals:

```text
POST   /api/v1/projects/{projectId}/ai/proposals
GET    /api/v1/projects/{projectId}/ai/proposals
GET    /api/v1/projects/{projectId}/ai/proposals/{runId}
POST   /api/v1/projects/{projectId}/ai/proposals/{runId}/edits/{editId}/remove
POST   /api/v1/projects/{projectId}/ai/proposals/{runId}/stage
POST   /api/v1/projects/{projectId}/ai/proposals/{runId}/reject
POST   /api/v1/projects/{projectId}/ai/proposals/{runId}/cancel
```

The browser polls proposal status rather than using SSE. Conversations, runs, and history are process-memory state and are lost on server restart.

Project-scoped document ingestion:

```text
POST   /api/v1/projects/{projectId}/document-ingestion/tasks
GET    /api/v1/projects/{projectId}/document-ingestion/tasks
GET    /api/v1/projects/{projectId}/document-ingestion/tasks/{taskId}
GET    /api/v1/projects/{projectId}/document-ingestion/tasks/{taskId}/review
GET    /api/v1/projects/{projectId}/document-ingestion/tasks/{taskId}/evidence/{evidenceId}
POST   /api/v1/projects/{projectId}/document-ingestion/tasks/{taskId}/recommendations/{recommendationId}/decision
POST   /api/v1/projects/{projectId}/document-ingestion/tasks/{taskId}/draft
POST   /api/v1/projects/{projectId}/document-ingestion/tasks/{taskId}/cancel
DELETE /api/v1/projects/{projectId}/document-ingestion/tasks/{taskId}
```

## Security And Human-Control Boundary

- API keys stay in server memory and never appear in browser DTOs, logs, model descriptors, or errors.
- OpenAI adapters use fixed approved endpoints.
- The assistant receives bounded ontology and optional FIBO context; it cannot access arbitrary files, URLs, shell commands, or project secrets.
- Provider output is parsed into supported structured edits and checked by deterministic validation.
- The React client owns presentation and reviewer actions, not semantic policy.
- A user must explicitly stage a valid AI proposal.
- Staging does not approve or apply a proposal.
- Existing authorization, human review, validation, semantic diff, reasoning, SHACL, atomic apply, reload, and rollback boundaries remain authoritative.

## Implemented Phase 11 Through Phase 12 Document Extension

The implemented extension:

- reuses the active credential, verified-model, and provider boundaries;
- keeps ordinary assistant conversations and ontology proposals working;
- analyzes supported documents through a separate bounded, evidence-verifying workflow;
- extracts stable evidence-linked candidates locally before semantic model calls;
- searches applied, imported, private-draft, shared-staging, current-proposal, same-task, retained-provenance, and pinned FIBO scopes before grounded interpretation;
- sends compact evidence and up to 20 deterministically ranked choices per candidate to the selected model;
- requires explicit grounded dispositions and server-issued selection IDs for reuse and extension;
- assembles semantic plans and connected recommendation groups deterministically in Kotlin after evidence, selection, freshness, kind, source, structure, and duplicate checks;
- shows collapsed connected recommendations with exact changes, evidence, confidence, editable prerequisites, and retained review-only meaning;
- treats documents and provider output as untrusted;
- converts accepted recommendations only through supported typed private-draft operations;
- keeps temporary uploads, OCR artifacts, and incomplete task state out of ontology sources;
- retains narrowly scoped provenance for successfully applied document-derived changes;
- preserves human staging, review, approval, and apply boundaries.

Uploads, extracted text, OCR images, incomplete task state, and review workspaces are temporary. Applied-change provenance is the only durable document-ingestion record, is authorized by project, and is stored separately from ontology sources. The feature supports English PDF, DOCX, TXT, and Markdown; it does not add production document/task storage, handwritten OCR, broader formats, external indexing, autonomous tools, or a direct apply path.

## Active Phase 12 Boundary

The default production sequence is:

```text
verified extracted text
→ deterministic local candidate extraction
→ deterministic retrieval across authorized ontology scopes
→ frozen grounded work key
→ bounded grounded model interpretation
→ Kotlin evidence, selection, structure, duplicate, and freshness verification
→ deterministic verification and compilation
→ collapsed connected editable human review
→ existing typed private-draft and proposal workflow
```

The implementation keeps the current credential, model-selection, upload,
extraction, evidence, authorization, staging, proposal, apply, reload, rollback,
and durable applied-provenance boundaries. Unsupported complex rules remain
visible and non-executable. Model judgment can vary, but candidate extraction,
retrieval ordering, work keys, verification, and compilation are repeatable for
frozen inputs. There is no embedding dependency, vector database, second
ontology index, external retrieval service, automatic approval, raw RDF
fallback, or additional write path.

Phase 12's approved and implemented documents are:

- `docs/architecture/phase-12-scope.md`;
- `docs/specs/0023-phase-12-ontology-grounded-document-analysis.md`;
- `docs/execplans/0023-phase-12-ontology-grounded-document-analysis.md`.
- `docs/phase-summaries/phase-12-summary.md`.

The approved and implemented documents are:

- `docs/architecture/phase-11.5-scope.md`;
- `docs/specs/0021-phase-11.5-multi-stage-ai-modeling-and-connected-ontology-change-sets.md`;
- `docs/execplans/0021-phase-11.5-multi-stage-ai-modeling-and-connected-ontology-change-sets.md`;
- `docs/phase-summaries/phase-11.5-summary.md`.
- `docs/architecture/phase-11.5-plus-scope.md`;
- `docs/specs/0022-phase-11.5-plus-deterministic-compilation-of-connected-document-models.md`;
- `docs/execplans/0022-phase-11.5-plus-deterministic-compilation-of-connected-document-models.md`;
- `docs/phase-summaries/phase-11.5-plus-summary.md`.

## Historical Records

The Phase 7, Phase 7.5, and Phase 8 specs, ExecPlans, decisions, and summaries
remain historical delivery records. They provide context for earlier designs
but do not override the current source tree, this subsystem map, or the
implemented Phase 11 through Phase 12 boundaries.
