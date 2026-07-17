# Phase 7 Slice 12 AI Conversation UI

## ExecPlan Slice

Slice 12: React Conversation, Draft Review, And Human Handoff.

## Goal

Replace the Phase 6 operation dropdown with a project-scoped conversational assistant that consumes the versioned Phase 7 APIs, displays safe private run activity, reviews private AI drafts, and submits ready drafts into the existing human proposal workflow.

## Files Modified

- `web-app/src/workbench/AiAssistantPanel.tsx`
- `web-app/src/workbench/AiCredentialSettings.tsx`
- `web-app/src/workbench/ai/AiDraftReview.tsx`
- `web-app/src/workbench/ai/AiRunTimeline.tsx`
- `web-app/src/web/contracts.ts`
- `web-app/src/web/projectApi.ts`
- `web-app/src/web/queries.ts`
- `web-app/src/styles.css`
- `web-app/src/workbench/AiAssistantPanel.test.tsx`
- `web-app/src/workbench/AiCredentialSettings.test.tsx`
- `web-app/src/web/projectApi.test.ts`
- `web-app/e2e/workbench.spec.ts`
- `web-app/e2e/workbench.spec.ts-snapshots/workbench-light-darwin.png`
- `docs/decisions/phase-7-slice-12-ai-conversation-ui.md`

## Implementation Result

- Replaced the one-shot operation selector with private project-scoped conversations, message history, a natural-language composer, follow-up turns, clarification answers, plan confirmation and revision, and explicit cancellation.
- Added typed React contracts and HTTP clients for conversations, messages, runs, cancellation, private drafts, deterministic analysis, review submission, and private SSE events.
- Added ordered SSE activity rendering with reconnect cursors and authoritative query-cache recovery when the server requests resynchronization.
- Added current project and selected-entity context chips with IRIs retained behind technical disclosure.
- Added private draft items, dependencies, revisions, stale/conflicted/invalid states, deterministic findings, semantic diff, provenance, run limits, and submission readiness.
- Kept deterministic analysis and all semantic decisions on the Kotlin server. React renders structured results and does not translate model text into RDF or typed edits.
- Added only an explicit submit-for-human-review action. The assistant surface has no approval or application control.
- Linked successful submission to the authoritative review route returned by the server and invalidated the existing shared staging query.
- Fixed post-analysis cache refresh so server-owned draft status changes become visible before submission.
- Made OpenAI the fixed server-owned provider ID in credential settings and continued clearing the API key from React state after successful save.
- Preserved missing-credential, provider/API failure, disconnected stream, stale, conflict, cancellation, loading, empty, and permission-aware failure surfaces without affecting non-AI workbench features.

## Tests Added Or Updated

- Conversation history, follow-up messages, and safe event ordering.
- Clarification answers and explicit plan confirmation decisions.
- SSE reconnect cursors and authoritative conversation recovery after resynchronization.
- Conflicted draft display, revision history, deterministic analysis, semantic diff, and review submission.
- Review submission navigation and the absence of any apply request.
- Missing-credential behavior and continued availability of the non-AI workbench.
- Fixed OpenAI provider selection and credential removal from browser state after save.
- Updated the end-to-end workbench journey to use the Phase 7 conversation and private SSE contracts.
- Refreshed the stable light-workbench visual snapshot after the Playwright raster baseline changed.

## Verification

- `npm ci && npm test && npm run build && npm run test:e2e` in `web-app` - passed with 27 unit/component tests, a production build, and one browser journey. `npm ci` reported two existing high-severity audit findings; this slice added no dependency or lockfile change.
- `git diff --check` - passed.

## Git Commit

A focused Slice 12 commit will be created on `feature/phase-7-slice-12-ai-conversation-ui` after the final clean-install verification passes.

## Assumptions And Limitations

- Conversation, run, event, draft, analysis, and credential state remains server-memory scoped and is lost on server restart.
- The client renders the server-provided review route after submission; proposal approval and application remain in the existing review UI.
- Provider/model configuration remains server-owned. The browser submits only the approved `openai` provider ID and the user's credential.
- Hidden reasoning, raw provider payloads, credentials, and provider authorization data are never requested or rendered.

## Notable Decisions

- Private AI activity is consumed through the user-scoped SSE route and is not added to collaboration events.
- Event resynchronization refetches authoritative conversation and draft state rather than reconstructing state from missed events.
- Labels remain primary in context and draft review, while IRIs and fingerprints use progressive disclosure.
