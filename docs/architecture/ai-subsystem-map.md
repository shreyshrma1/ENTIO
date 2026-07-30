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

Phase 11.5 is implemented and is the current active phase. It replaces Phase 11's single-stage document-analysis contract with bounded discovery, connected modeling, reconciliation, ontology alignment, critic, and final-planning stages. Phase 11 remains the upload, extraction, evidence, authorization, review, proposal, apply, rollback, and applied-provenance foundation.

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
| Document contracts and semantic checks | `DocumentAnalysisPipelineContracts.kt`, `DocumentIngestionContracts.kt`, `DocumentRecommendationContracts.kt`, `DocumentEvidenceVerifier.kt`, `DocumentOntologyMatcher.kt`, `DocumentChangeSetPlanVerifier.kt`, `DocumentRecommendationDraftTranslator.kt` | Owns bounded neutral records, exact evidence verification, canonical matching, coverage and dependency checks, collision-checked temporary-reference resolution, and conversion to existing typed edits. |
| Document task orchestration | `web/ingestion/` | Owns authorized intake, temporary storage, extraction, selective OCR, the fixed multi-stage pipeline, task-wide call and retry budgets, grouped review state, cancellation, cleanup, typed draft handoff, and durable applied-change provenance. |
| Document analysis adapter | `OpenAiDocumentAnalysisClient.kt` | Uses the current verified selected compatible model through separate fixed, strict-schema, no-tools requests for discovery, connected modeling, reconciliation, alignment, critique, and final planning. |
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

## Implemented Phase 11 And 11.5 Document Extension

The implemented extension:

- reuse the active credential, verified-model, and provider boundaries;
- keep ordinary assistant conversations and ontology proposals working;
- analyze supported documents through a separate bounded, evidence-verifying multi-stage workflow;
- discover meaning without ontology context before building a connected model;
- reconcile across documents and retained provenance before current-ontology alignment;
- critique the model separately and verify connected atomic plans in Kotlin;
- show grouped exact changes, evidence, critique, confidence, and review-only meaning;
- treat documents and provider output as untrusted;
- convert accepted recommendations only through supported typed private-draft operations;
- keep temporary uploads, OCR artifacts, and incomplete task state out of ontology sources;
- retain narrowly scoped provenance for successfully applied document-derived changes;
- preserve human staging, review, approval, and apply boundaries.

Uploads, extracted text, OCR images, incomplete task state, and review workspaces are temporary. Applied-change provenance is the only durable document-ingestion record, is authorized by project, and is stored separately from ontology sources. The feature supports English PDF, DOCX, TXT, and Markdown; it does not add production document/task storage, handwritten OCR, broader formats, external indexing, autonomous tools, or a direct apply path.

## Active Phase 11.5 Boundary

Phase 11.5 implements:

```text
verified extracted text
→ per-document discovery
→ connected cross-document semantic synthesis and conditional consolidation
→ ontology-aware recommendation planning and modeling review
→ Kotlin verification
→ grouped human review
→ existing typed private-draft and proposal workflow
```

The implementation keeps the current credential, model-selection, upload, extraction, evidence, authorization, staging, proposal, apply, reload, rollback, and durable applied-provenance boundaries. It adds no automatic approval, direct write path, unrestricted agent loop, raw RDF fallback, or fallback to the retired Phase 11 single-stage analysis path.

The approved and implemented documents are:

- `docs/architecture/phase-11.5-scope.md`;
- `docs/specs/0021-phase-11.5-multi-stage-ai-modeling-and-connected-ontology-change-sets.md`;
- `docs/execplans/0021-phase-11.5-multi-stage-ai-modeling-and-connected-ontology-change-sets.md`;
- `docs/phase-summaries/phase-11.5-summary.md`.

## Historical Records

The Phase 7, Phase 7.5, and Phase 8 specs, ExecPlans, decisions, and summaries remain historical delivery records. They provide context for earlier designs but do not override the current source tree, this subsystem map, or the implemented Phase 11 and 11.5 boundaries.
