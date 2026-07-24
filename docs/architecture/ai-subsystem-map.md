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

Phase 11 is active and approved for implementation. It extends this existing assistant and provider foundation with bounded document ingestion and evidence-grounded recommendations. It does not replace or reintroduce the assistant.

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
| Redacted HTTP boundary | `Application.kt` and `contract/AiProposalContracts.kt` | Exposes credential/model settings and authorized project-scoped assistant proposal routes. |
| Provider settings UI | `web-app/src/workbench/AiCredentialSettings.tsx` | Collects credentials and renders redacted provider/model status. |
| Assistant UI | `web-app/src/workbench/AiProposalPanel.tsx`, `ProjectWorkspace.tsx` | Provides the AI sidebar, conversations, history, status, proposal review, edit removal, cancellation, rejection, and staging controls. |

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

## Security And Human-Control Boundary

- API keys stay in server memory and never appear in browser DTOs, logs, model descriptors, or errors.
- OpenAI adapters use fixed approved endpoints.
- The assistant receives bounded ontology and optional FIBO context; it cannot access arbitrary files, URLs, shell commands, or project secrets.
- Provider output is parsed into supported structured edits and checked by deterministic validation.
- The React client owns presentation and reviewer actions, not semantic policy.
- A user must explicitly stage a valid AI proposal.
- Staging does not approve or apply a proposal.
- Existing authorization, human review, validation, semantic diff, reasoning, SHACL, atomic apply, reload, and rollback boundaries remain authoritative.

## Approved Phase 11 Extension

Phase 11 may add project-scoped document-ingestion behavior only after the ExecPlan Slice 0 audit is approved. The extension must:

- reuse the active credential, verified-model, and provider boundaries;
- keep ordinary assistant conversations and ontology proposals working;
- analyze supported documents through a separate bounded, evidence-verifying workflow;
- treat documents and provider output as untrusted;
- convert accepted recommendations only through supported typed private-draft operations;
- keep temporary uploads, OCR artifacts, and incomplete task state out of ontology sources;
- retain narrowly scoped provenance for successfully applied document-derived changes;
- preserve human staging, review, approval, and apply boundaries.

## Historical Records

The Phase 7, Phase 7.5, and Phase 8 specs, ExecPlans, decisions, and summaries remain historical delivery records. They provide context for earlier designs but do not override the current source tree, this subsystem map, or the approved Phase 11 boundaries.
