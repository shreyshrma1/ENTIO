# Entio AI Subsystem Map

## Current status

Native AI execution has been removed from Entio. The active provider surface is deliberately limited to credential entry, credential verification, model discovery, model access verification, model selection, and the surrounding settings UI. The former assistant, conversation, task, draft, capability, SSE, and review-handoff architecture is retained only in the Phase 7/7.5/8 historical documents.

## Active server ownership

| Concern | Entry points | Boundary |
| --- | --- | --- |
| Credential storage and status | `AiProviderContracts.kt` (`AiCredentialService`, `AiCredentialStore`) | Secrets remain server-only and are exposed only through callback-scoped access. |
| Provider credential verification | `OpenAiCredentialClient.kt`, `AiProviderClient` | Performs only the provider credential check; it does not call a model or execute a prompt. |
| Model discovery and access verification | `provider/openai/OpenAiModelDiscoveryClient.kt` | Reads the provider model inventory and checks a selected model through the provider model-metadata endpoint. It does not send generation or tool requests. |
| Compatibility and selection state | `ai/models/` | Owns server-side filtering, per-user candidates, explicit selection, verification state, and freshness. |
| Redacted HTTP boundary | `AiModelWebBoundary.kt`, `Application.kt` | Exposes only credential and model settings routes. No project-scoped AI routes are registered. |
| Provider settings UI | `web-app/src/workbench/AiCredentialSettings.tsx` | Collects the key, renders redacted provider/model status, and lets the user discover, select, verify, replace, or remove settings. |

## Active routes

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

No assistant, conversation, run, task, draft, analysis, SSE, capability, or AI review-submission route is available. The ordinary ontology staging and human-review workflow remains independent of provider settings.

## Security boundary

- API keys stay in server memory and never appear in browser DTOs, logs, model descriptors, or errors.
- Provider adapters are fixed-host, provider-specific clients and cannot access ontology services, files, shell commands, project configuration, staging, or review controls.
- Model verification uses provider metadata access only; it does not invoke a model, submit a prompt, or execute a tool.
- The React client owns presentation only. Server-owned compatibility policy determines which discovered models can be selected.

## Historical records

The Phase 7, Phase 7.5, and Phase 8 specs, ExecPlans, decisions, and summaries describe the former native AI implementation and remain available as historical records. They do not authorize reintroducing native AI execution or override this current boundary.
