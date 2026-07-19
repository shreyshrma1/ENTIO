# Phase 7.5 Scope

> Implementation status: complete. The approved scope was delivered and verified slice by slice; see `docs/phase-summaries/phase-7.5-summary.md`. The remainder of this document preserves the approved planning baseline.

## Phase Name

**Phase 7.5: OpenAI Model Discovery, Selection, And AI Boundary Cleanup**

## Status

Draft

## Purpose

Phase 7.5 replaces Entio's single pinned OpenAI model requirement with a safe, user-specific model discovery and selection workflow.

When a user enters an OpenAI API key, Entio should:

1. Validate the credential through the Kotlin server.
2. Query OpenAI for the models available to that API key and API project.
3. Filter the returned models through an Entio-owned compatibility allowlist and capability policy.
4. Present only approved, compatible models to the user.
5. Require the user to select a model.
6. Verify that the selected model can perform the Phase 7 Entio AI workflow.
7. Store the selected model only in the user's server-side session state.
8. Use that model for the user's subsequent Entio AI requests.
9. Detect when the selected model is no longer available and require reselection without breaking non-AI functionality.

Phase 7.5 also includes a bounded cleanup of the existing Phase 6 and Phase 7 AI implementation tree so that provider transport, model discovery, credential state, model selection, capability definitions, conversations, drafts, and UI contracts are easier to locate and maintain.

The cleanup is architectural organization only. It must not change semantic behavior, proposal behavior, permissions, validation, reasoning, SHACL, FIBO, human review, or source application.

## Problem

The current Phase 7 implementation plan assumes one fixed model:

```text
gpt-5.2
```

That creates a product failure when a valid OpenAI API key belongs to an API project that cannot access that model.

API-key permissions and model availability are separate concerns. A key may be valid and allowed to use API endpoints while its project does not expose a particular model.

Entio must therefore distinguish:

- Invalid credential.
- Valid credential with no Entio-compatible models.
- Valid credential with one compatible model.
- Valid credential with several compatible models.
- Previously selected model no longer available.
- Model listed by OpenAI but incompatible with Entio's required tool workflow.
- Model temporarily unavailable.
- Provider discovery failure.

The browser must never receive the user's API key or unrestricted provider payloads.

## Central Product Promise

> A user can connect their own OpenAI API key, see the models that are both available to their API project and approved for Entio's tool-driven ontology workflow, select one, and safely use it without changing Entio project configuration or exposing credentials to the browser.

## Relationship To Phase 7

Phase 7.5 is an additive refinement of the Phase 7 provider and credential boundary.

It changes:

- Model configuration.
- Credential onboarding.
- Model discovery.
- Model compatibility policy.
- Per-user session state.
- AI settings UI.
- Provider request model selection.
- Failure and reselection behavior.
- Related tests and documentation.

It does not change:

- The AI capability registry.
- Typed ontology-edit semantics.
- Typed SHACL-edit semantics.
- Private AI draft behavior.
- Deterministic validation.
- Semantic diff.
- Reasoning.
- SHACL impact.
- FIBO ranking or reuse.
- Human approval and rejection authority.
- Proposal application and rollback.
- Project configuration files.
- CLI or VS Code behavior.

## Goals

Phase 7.5 should:

- Remove the product requirement that every user must have access to one hard-coded OpenAI model.
- Discover models using the user's own API key through the Kotlin server.
- Use an Entio-owned allowlist and compatibility policy.
- Present a safe, curated model selector in the React application.
- Store model choice per user session, not in ontology files or project configuration.
- Test the selected model before marking AI ready.
- Allow model reselection without requiring the user to re-enter a still-valid key.
- Detect inaccessible, retired, or removed models.
- Preserve non-AI functionality when discovery or model access fails.
- Keep the OpenAI provider behind the existing provider-neutral boundary.
- Preserve all Phase 1 through Phase 7 regression behavior.
- Reorganize the AI implementation tree where needed to make later work safer and easier to navigate.

## Non-Goals

Phase 7.5 must not add:

- Arbitrary user-entered model IDs.
- Automatic use of every model returned by OpenAI.
- An unrestricted model picker.
- Browser-to-OpenAI calls.
- API keys in React state after successful submission.
- API keys in logs, URLs, events, proposals, drafts, ontology files, snapshots, or audit records.
- Model selection in `entio.yaml`.
- Model selection in project registry configuration.
- Shared model selection across users.
- Organization-wide billing or administrator controls.
- Automatic fallback to an unapproved model.
- Automatic silent switching between models during an active AI run.
- Model benchmarking.
- Fine-tuning.
- Embeddings or vector stores.
- Multi-provider UI.
- Provider-specific logic in semantic core modules.
- Changes to ontology-edit, SHACL-edit, proposal, or human-review semantics.
- Broad unrelated refactoring of the web server or web application.

## OpenAI Model Discovery Boundary

Entio should use the user's server-side credential to call the OpenAI model-list endpoint:

```text
GET /v1/models
```

The provider response should be mapped immediately into Entio-owned internal model descriptors.

The raw provider response must not be sent to React.

The discovery service should return only the information Entio needs, such as:

- Model ID.
- Entio display name.
- Entio compatibility status.
- Entio capability tier.
- Selection eligibility.
- Reason unavailable or incompatible.
- Whether it is currently selected.

OpenAI-owned fields that are not required by the product should not cross the server boundary.

## Discovery Does Not Equal Compatibility

A model appearing in `/v1/models` does not by itself prove that it is appropriate for Entio.

Entio's Phase 7 AI workflow requires model support for the approved Responses API behavior and custom function tools used by the application.

The server must therefore calculate:

```text
models visible to this API key
∩ Entio-approved model allowlist
∩ models compatible with the current Entio AI contract
= selectable models
```

The frontend must never perform this intersection independently.

## Entio Model Catalog

Phase 7.5 should add an Entio-owned model catalog.

Each catalog entry should define:

- Exact OpenAI model ID or approved stable family rule.
- Human-readable display name.
- Provider.
- Whether it is enabled.
- Responses API compatibility.
- Custom function-tool compatibility.
- Structured-output compatibility where required.
- Streaming compatibility where required.
- Supported Entio operation tier.
- Default reasoning configuration where supported.
- Default response verbosity where supported.
- Maximum Entio context policy.
- Timeout class.
- Cost/latency description for the user.
- Preference rank.
- Deprecation state.
- Optional replacement recommendation.

The catalog is application configuration, not ontology project configuration.

It must not be editable by the AI.

## Model Eligibility States

An OpenAI model should be mapped into one of these states:

- `AVAILABLE_AND_COMPATIBLE`
- `AVAILABLE_BUT_DISABLED`
- `AVAILABLE_BUT_INCOMPATIBLE`
- `APPROVED_BUT_NOT_AVAILABLE`
- `DEPRECATED`
- `UNKNOWN_PROVIDER_MODEL`

Only `AVAILABLE_AND_COMPATIBLE` models may be selected.

Unknown models returned by OpenAI should not become selectable automatically.

They may be omitted from the browser response or represented only as a count such as:

```text
3 additional provider models are not supported by Entio.
```

Do not expose unrestricted provider inventory by default.

## Credential And Selection Workflow

### Initial Credential Submission

```text
user enters API key
→ React sends key once to Ktor
→ Ktor stores key in user-session memory
→ Ktor calls OpenAI model discovery
→ Ktor intersects provider models with Entio model catalog
→ Ktor returns credential status and selectable Entio model descriptors
→ user selects one model
→ Ktor verifies the selected model
→ Ktor stores the selected model in user-session memory
→ AI status becomes ready
```

The browser may retain:

- Credential state.
- Discovery state.
- Selectable Entio model descriptors.
- Selected model ID.
- Verification state.
- User-facing errors.

The browser must not retain:

- API key.
- Authorization header.
- Raw provider model response.
- Provider request body.
- Hidden compatibility policy.
- Another user's selection.

### Existing Valid Credential

A user with a valid stored session credential should be able to:

- Refresh available models.
- Select a different compatible model.
- Retest the selected model.
- Remove the credential.
- Replace the credential.

A valid credential without a selected model should produce:

```text
CREDENTIAL_VALID_MODEL_REQUIRED
```

AI requests must remain unavailable until selection and verification complete.

## Selected-Model Verification

Selecting a model should not rely only on discovery-list membership.

Entio should perform a bounded provider verification request through the Phase 7 provider interface.

The verification should prove the minimum current Entio contract, including:

- The model can be requested through the Responses API.
- The model accepts the required request structure.
- The model can receive an Entio custom function definition.
- The provider response can be parsed safely.
- The model does not require an unsupported provider feature.

The test must:

- Use a minimal prompt.
- Use a harmless read-only test function or no-op Entio-owned function.
- Never access a project.
- Never create or mutate a draft.
- Never include ontology content.
- Never create a proposal.
- Be clearly disclosed as potentially incurring a small API charge.
- Return only Entio-owned status information.

A model is stored as selected only after verification succeeds.

## Per-User Session State

Recommended server-owned state:

```text
AiUserProviderSettings
- userId
- provider
- credentialStatus
- discoveryStatus
- discoveredAt
- selectableModels
- selectedModelId
- selectedModelVerifiedAt
- selectedModelStatus
- lastProviderErrorCategory
```

The API key remains in the existing secret store and must not be included in this object.

The selected model is:

- Per user.
- Server-side.
- In memory.
- Lost on server restart.
- Removed when the credential is removed.
- Invalidated when the credential is replaced.
- Invalidated when discovery no longer reports access.
- Not written to project files.
- Not shared with collaborators as a secret or user preference.

The selected model ID may appear in the user's own AI run metadata and UI.

## Model Availability Refresh

Entio should refresh model availability:

- Immediately after credential submission.
- After credential replacement.
- When the user chooses Refresh models.
- Before the first AI run after server restart.
- After a provider model-not-found or access-denied response.
- After a configurable session freshness interval.
- When the user attempts to select a model.

Do not call `/v1/models` before every normal tool call.

A reasonable initial session freshness interval may be finalized in the ExecPlan.

## Model Becomes Unavailable

When the selected model is no longer available:

1. Stop before starting a new AI run.
2. Preserve existing conversations and private drafts.
3. Mark the selected model `UNAVAILABLE`.
4. Refresh model discovery.
5. Ask the user to select another compatible model.
6. Do not silently choose a replacement.
7. Do not delete the API key unless the credential itself is invalid.
8. Do not affect non-AI workbench features.

An active run that receives a model-access error should:

- Stop safely.
- Preserve the current draft.
- Mark the run failed or interrupted with a structured model-unavailable status.
- Not retry with a different model automatically.
- Offer model reselection.

## No Compatible Models

A valid credential may return no Entio-compatible models.

The user-facing state should explain:

```text
Your OpenAI API key is valid, but this API project does not currently expose a model supported by Entio.
```

The response may include:

- Number of models returned by OpenAI.
- Entio-supported models that were checked.
- A Refresh models action.
- A Replace API key action.
- General guidance to review the API project.

It must not:

- Display the API key.
- Mark the key invalid.
- Automatically broaden the allowlist.
- Allow free-text model entry.
- Expose internal provider payloads.

## Model Selection User Experience

### Credential Settings States

The AI settings surface should support:

- No key configured.
- Saving key.
- Discovering models.
- Valid key, model selection required.
- Valid key, no compatible models.
- Testing selected model.
- Ready.
- Selected model unavailable.
- Invalid key.
- Rate limited.
- Provider unavailable.
- Discovery failed.
- Credential removed.

### Model Selector

Each selectable model should show concise Entio-owned metadata:

- Display name.
- Exact model ID in secondary text.
- Recommended badge where applicable.
- Relative capability tier.
- Relative speed.
- Relative cost description.
- Short Entio-focused description.

Example:

```text
GPT-5 mini
Fast and cost-conscious
Good for explanations and focused ontology edits

GPT-5
Higher capability
Recommended for broad multi-step ontology design
```

Do not claim precise pricing unless Entio has a separately maintained, current pricing source.

The selector must not show models that are not selectable.

### Required User Actions

- Select model.
- Test and use model.
- Change model.
- Refresh available models.
- Replace key.
- Remove key.

The user should not have to re-enter the key merely to switch between models available to the same stored session credential.

## AI Run Binding

Every AI run must capture the selected model ID at run creation.

The run continues with that model for its lifetime.

Changing the user's selected model affects only future runs.

Do not switch a model during:

- A provider tool loop.
- A self-correction cycle.
- A draft analysis sequence.
- Streaming response generation.

The audit record should include the exact selected model ID but never the key.

## Model-Specific Behavior

The provider adapter may apply catalog-defined settings by model, such as:

- Reasoning effort.
- Maximum response token policy.
- Timeout class.
- Streaming mode.
- Structured-output behavior.

These settings must come from the Entio model catalog, not from React and not from the model.

A user selects a model, not arbitrary provider parameters.

Phase 7.5 should avoid exposing expert provider tuning controls unless separately approved.

## Provider-Neutral Architecture

The design should remain provider-neutral:

```text
AiProvider
  discoverModels(credential)
  verifyModel(credential, modelId)
  execute(request, credential, modelId)
```

OpenAI-specific response mapping belongs in the OpenAI adapter.

Entio-owned services should depend on:

- `AiProviderModelDescriptor`
- `AiModelCatalog`
- `AiModelSelectionService`
- `AiModelCompatibilityPolicy`
- `AiProviderSettings`

They should not depend directly on raw OpenAI model objects.

## Recommended Kotlin Components

Potential components:

- `AiProviderModelDiscoveryClient`
- `OpenAiModelDiscoveryClient`
- `AiProviderModelDescriptor`
- `AiModelCatalog`
- `AiModelCatalogEntry`
- `AiModelCompatibilityPolicy`
- `AiModelEligibility`
- `AiModelDiscoveryService`
- `AiModelSelectionService`
- `AiModelVerificationService`
- `AiUserProviderSettings`
- `AiUserProviderSettingsStore`
- `AiModelAvailabilityRefresher`
- `AiModelSelectionError`
- `AiModelSelectionStatus`

Names may vary, but responsibilities must remain separated.

## Recommended Web Contracts

Potential versioned endpoints:

```text
GET    /api/v1/users/me/ai-provider
PUT    /api/v1/users/me/ai-credential
DELETE /api/v1/users/me/ai-credential

POST   /api/v1/users/me/ai-models/discover
GET    /api/v1/users/me/ai-models
PUT    /api/v1/users/me/ai-model-selection
POST   /api/v1/users/me/ai-model-selection/test
DELETE /api/v1/users/me/ai-model-selection
```

The existing credential routes may be extended instead of duplicated if backward compatibility remains clear.

Response DTOs may include:

```text
credentialStatus
discoveryStatus
models
selectedModel
selectionStatus
lastCheckedAt
userFacingError
availableActions
```

They must not include:

- Credential.
- Authorization headers.
- Raw OpenAI response.
- Unrestricted provider metadata.
- Another user's settings.

## Structured Status And Error Model

Recommended statuses:

### Credential

- `NOT_CONFIGURED`
- `SAVING`
- `VALID`
- `INVALID`
- `REMOVED`
- `RATE_LIMITED`
- `PROVIDER_UNAVAILABLE`

### Discovery

- `NOT_STARTED`
- `DISCOVERING`
- `COMPLETED`
- `NO_COMPATIBLE_MODELS`
- `FAILED`
- `STALE`

### Selection

- `NOT_SELECTED`
- `SELECTED_UNVERIFIED`
- `VERIFYING`
- `READY`
- `UNAVAILABLE`
- `INCOMPATIBLE`
- `VERIFICATION_FAILED`

Structured errors:

- `AI_CREDENTIAL_MISSING`
- `AI_CREDENTIAL_INVALID`
- `AI_MODEL_DISCOVERY_FAILED`
- `AI_NO_COMPATIBLE_MODELS`
- `AI_MODEL_SELECTION_REQUIRED`
- `AI_MODEL_NOT_AVAILABLE`
- `AI_MODEL_NOT_APPROVED`
- `AI_MODEL_INCOMPATIBLE`
- `AI_MODEL_VERIFICATION_FAILED`
- `AI_MODEL_BECAME_UNAVAILABLE`
- `AI_PROVIDER_RATE_LIMITED`
- `AI_PROVIDER_UNAVAILABLE`
- `AI_PROVIDER_TIMEOUT`

## Security Requirements

- Model discovery occurs only on the server.
- The browser never calls OpenAI.
- The API key is never returned after submission.
- Discovery and verification use the current user's credential only.
- Cross-user settings access is rejected.
- Cross-session credential reuse is rejected.
- Provider responses are mapped before crossing internal boundaries.
- Raw provider payloads are not logged.
- Authorization headers are redacted.
- Unknown provider fields are ignored or rejected safely.
- Model IDs are validated against the Entio catalog before verification or use.
- The client cannot supply an arbitrary endpoint.
- The client cannot supply an arbitrary model ID outside the returned selectable set.
- The AI cannot modify its model catalog or selected model.
- Model-selection endpoints must be idempotent where retries could duplicate verification.
- Rate limits should prevent repeated discovery or verification abuse.

## AI Tree Cleanup

### Purpose

The existing AI tree has evolved across Phase 6 and Phase 7. Before adding model discovery and selection, Entio should perform a bounded structural cleanup if the current organization makes provider, credential, model, conversation, draft, capability, or transport code difficult to locate.

The cleanup is intended to reduce implementation risk for this and subsequent AI work.

### Recommended Organization

A possible server structure:

```text
web-server/src/main/kotlin/com/entio/web/ai/
  provider/
    AiProvider.kt
    AiProviderClient.kt
    AiProviderRequest.kt
    AiProviderResponse.kt

    openai/
      OpenAiResponsesClient.kt
      OpenAiModelDiscoveryClient.kt
      OpenAiDtos.kt
      OpenAiEventMapper.kt
      OpenAiErrorMapper.kt

  credentials/
    AiCredentialService.kt
    AiCredentialStore.kt
    AiCredentialStatus.kt

  models/
    AiModelCatalog.kt
    AiModelCatalogEntry.kt
    AiModelDiscoveryService.kt
    AiModelSelectionService.kt
    AiModelVerificationService.kt
    AiUserProviderSettings.kt
    AiUserProviderSettingsStore.kt

  capabilities/
    AiCapabilityRegistry.kt
    AiCapabilityDefinition.kt
    AiCapabilityScope.kt
    AiCapabilityExecutor.kt

  conversation/
    AiConversationService.kt
    AiConversation.kt
    AiRun.kt
    AiToolLoop.kt

  draft/
    AiDraftWorkspace.kt
    AiDraft.kt
    AiDraftItem.kt
    AiDraftAnalysisService.kt

  context/
    AiContextBuilder.kt
    AiContextPackage.kt

  audit/
    AiAuditRecord.kt
    AiAuditStore.kt

  web/
    AiRoutes.kt
    AiContractMapper.kt
    AiSseService.kt
```

A possible frontend structure:

```text
web-app/src/workbench/ai/
  assistant/
  credentials/
  models/
  conversations/
  drafts/
  evidence/
  shared/
```

These exact package names are not mandatory.

The important requirement is that responsibilities are clearly separated.

### Cleanup Rules

The cleanup may:

- Move files into clearer packages.
- Split overly broad files.
- Rename ambiguous internal classes.
- Centralize provider DTO mapping.
- Centralize credential and model-selection state.
- Remove dead Phase 6 production code after compatibility is proven.
- Keep the deterministic provider as a test fake.
- Update imports and tests.
- Add package-level documentation.
- Create a short AI architecture map.

The cleanup must not:

- Change public behavior.
- Change HTTP contract meaning unless included in the approved Phase 7.5 contract revision.
- Modify semantic modules.
- Rewrite capability behavior.
- Alter draft or proposal lifecycle.
- Weaken tests.
- Combine unrelated refactors.
- Delete the deterministic fake provider needed by tests.
- Expose secrets.
- Introduce a new Gradle module without separate approval.

### Cleanup Deliverable

Create:

```text
docs/architecture/ai-subsystem-map.md
```

It should identify:

- Package responsibilities.
- Main entry points.
- State ownership.
- Credential boundary.
- Model discovery and selection flow.
- Provider boundary.
- Capability boundary.
- Conversation and draft boundary.
- Web contract boundary.
- Test doubles.
- Forbidden dependency directions.

### Cleanup Timing

The ExecPlan should make cleanup the first implementation slice when the current tree requires it.

Recommended order:

```text
1. AI subsystem inventory and bounded tree cleanup
2. Entio model catalog and compatibility policy
3. OpenAI model discovery
4. Per-user selection and verification
5. Versioned web contracts
6. React model-selection experience
7. Runtime model binding and unavailable-model recovery
8. Security and regression hardening
9. End-to-end verification and Phase 7.5 summary
```

If the tree is already clear and compliant, Slice 1 may produce the architecture map and minimal or no file movement. It must not perform cosmetic churn merely to satisfy the slice.

## Dirty Worktree Boundary

Phase 7.5 planning does not authorize implementation over an extensively dirty worktree.

Before implementation:

1. Read `AGENTS.md`.
2. Identify all modified, staged, and untracked files.
3. Determine whether they belong to completed Phase 7 slices, active unfinished work, generated files, or unrelated work.
4. Do not discard, reset, stash, overwrite, or commit existing changes without explicit authorization.
5. Stop if the intended Phase 7.5 files overlap uncommitted work and ownership is unclear.
6. Begin only from an approved clean accumulated base or from an explicitly authorized continuation branch.

The cleanup slice must not be used as a pretext to absorb unknown uncommitted changes.

## Migration From The Pinned Model

The current fixed-model configuration should be migrated carefully.

### Existing User Session

Because Phase 7 state is in memory, no durable data migration is required.

After Phase 7.5:

- A stored credential with no selected model enters `MODEL_SELECTION_REQUIRED`.
- The previous fixed model may appear as an option only if returned by discovery and approved by the catalog.
- Entio must not assume the previous model is available.
- No AI run begins until a compatible selected model is verified.

### Configuration

Replace a single required model configuration with an Entio model catalog configuration.

The production provider endpoint remains server-controlled.

The browser cannot edit the catalog.

### Backward Compatibility

Existing Phase 7 provider interfaces should be extended additively where practical.

The deterministic fake provider should expose deterministic model-discovery fixtures.

Existing credential endpoints may return additional model-selection state without exposing secrets.

## Testing Requirements

### Model Discovery

- Valid credential returns provider models to the server.
- Raw provider response does not reach React.
- Discovery uses the current user's key.
- Invalid credential maps correctly.
- Rate limit, timeout, malformed response, and provider outage map correctly.
- Unknown models are not selectable.
- Disabled catalog models are not selectable.
- Approved unavailable models are not selectable.
- Stable deterministic ordering is preserved.

### Compatibility Intersection

- Provider models are intersected with the Entio catalog on the server.
- React cannot add a model.
- React cannot select a model absent from the returned eligible list.
- Model compatibility flags are Entio-owned.
- Model discovery does not alter the catalog.
- A newly returned unknown model remains unavailable until an approved catalog change.

### Selection

- User must explicitly select a model.
- Selection is per user.
- Selection is lost on restart.
- Replacing or removing the key clears selection.
- Selecting one model does not affect another user.
- Repeated selection requests are idempotent.
- Unverified selections cannot start AI runs.

### Verification

- Verification uses a minimal non-project request.
- Verification cannot mutate a draft or proposal.
- Verification never includes ontology data.
- Verification failures preserve the key when the key remains valid.
- Verification charge disclosure appears in the UI.
- Fake provider verification is deterministic in CI.

### Runtime

- Every AI run binds one selected model.
- Changing selection affects only later runs.
- No mid-run model switching occurs.
- Model-not-found or access-denied marks the selection unavailable.
- Existing private drafts and conversations remain intact.
- Non-AI features remain healthy.

### Credentials And Privacy

- Key never appears in DTOs, logs, events, audit records, snapshots, or browser storage.
- Raw provider model payload does not appear in browser contracts.
- Cross-user discovery and selection access is rejected.
- Provider errors are redacted.
- Model IDs may appear only where needed and are not treated as secrets.

### Cleanup

- File movement preserves behavior.
- Public tests remain green after each structural move.
- No semantic module acquires provider dependencies.
- Package dependency rules are tested or documented.
- Dead code removal is proven by tests and references.
- Deterministic fake provider remains available.

### UI

- Credential entry transitions to discovery.
- One compatible model may still require explicit confirmation unless the spec later approves auto-selection.
- Multiple models render a clear selector.
- No-compatible-model state is understandable.
- Refresh, select, test, change, replace-key, and remove-key actions work.
- Loading, empty, invalid, rate-limited, unavailable, and stale states are accessible.
- Keyboard and screen-reader behavior works.
- The key is cleared from browser memory after submission.

### Regression

All existing Phase 1 through Phase 7 tests must continue to pass.

## Primary End-To-End Journeys

### Journey 1: Multiple Available Models

```text
enter valid key
→ server discovers models
→ server returns approved compatible subset
→ user selects model
→ server verifies model
→ AI becomes ready
→ user starts AI conversation
→ run uses selected model
```

### Journey 2: No Compatible Models

```text
enter valid key
→ OpenAI returns models
→ no Entio-compatible intersection
→ key remains valid
→ AI remains unavailable
→ user receives clear guidance
→ non-AI workbench remains available
```

### Journey 3: Change Model

```text
valid key and selected model
→ refresh models
→ choose another compatible model
→ verify
→ future runs use new model
→ active run remains on original model
```

### Journey 4: Model Becomes Unavailable

```text
selected model later fails access check
→ current run stops safely
→ draft remains intact
→ selection marked unavailable
→ models refreshed
→ user selects replacement
→ next run resumes normal operation
```

### Journey 5: Replace Credential

```text
replace API key
→ prior model selection cleared
→ rediscover models using new key
→ user selects and verifies model
```

## Expected Documentation

Phase 7.5 should produce or update:

- Phase 7.5 feature spec.
- Phase 7.5 ExecPlan.
- `docs/architecture/ai-subsystem-map.md`.
- Provider/model-selection ADR.
- Model catalog documentation.
- Credential and model-selection user help.
- Phase 7.5 implementation summary.

## Success Criteria

Phase 7.5 is successful when:

- Entio no longer requires every user to access one fixed OpenAI model.
- Entering a valid API key triggers server-side model discovery.
- The browser receives only Entio-approved model descriptors.
- The user selects and verifies one compatible model.
- Model selection is stored per user in server-side session memory.
- Every AI run uses the selected model captured at run creation.
- No AI run starts without a verified selected model.
- A removed or inaccessible model requires explicit reselection.
- Entio never silently chooses an unapproved replacement model.
- The API key and raw provider payload never reach the browser.
- Unknown provider models do not become selectable automatically.
- Non-AI workbench features remain available during provider failure.
- AI subsystem organization is documented and easier to navigate.
- Cleanup does not change semantic or proposal behavior.
- All existing Phase 1 through Phase 7 regressions pass.
- Model discovery, selection, verification, privacy, runtime binding, recovery, cleanup, UI, and end-to-end tests pass.

## Likely Follow-Up Work

Potential later work includes:

- Production-encrypted credential persistence.
- Durable per-user model preferences.
- Administrator-managed model allowlists.
- Organization cost controls.
- Current pricing integration.
- Model capability benchmarking.
- Automatic recommendations based on task complexity.
- Multiple AI providers.
- Provider-region selection.
- Production audit and observability.
- Policy-controlled model retirement and migration.
