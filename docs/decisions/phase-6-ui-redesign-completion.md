# Phase 6 UI Redesign Completion

## Scope

The Phase 6 web workbench redesign was implemented as a frontend-only change over the existing HTTP and WebSocket contracts. Kotlin semantic behavior, proposal workflow, reasoning, SHACL, FIBO behavior, collaboration authority, AI policy, the CLI, and the VS Code extension were not changed.

## Implemented

- Replaced the previous dark full-page treatment with the prototype's light visual system: `#f4f6f9` app background, white surfaces, the specified border palette, indigo actions, Inter typography, compact radii, and prototype shadows.
- Preserved the prototype's narrow dark app rail as a navigation accent; the application workspace, header, sidebar, content surfaces, drawers, forms, and review dock are light.
- Added reusable `Icon` and `StatusBadge` primitives without introducing a UI dependency.
- Reworked the application shell around a global header, app rail, resizable ontology sidebar, tab workspace, contextual edit inspector, and shared staged-change dock.
- Made the ontology sidebar resizable with pointer dragging and keyboard arrow controls while preserving minimum and maximum widths.
- Kept labels as primary navigation text while retaining IRIs in entity technical details and external ontology details.
- Organized Explore, Changes, Reasoning, Constraints, FIBO, Activity, Assistant, and Settings as stable rail workspaces.
- Preserved lazy hierarchy loading, search, entity tabs, staging, proposal review, semantic jobs, FIBO browsing, collaboration presence, AI assistant, and credential controls through their existing hooks and clients.
- Added responsive layout behavior, reduced-motion support, visible focus states, a skip link, and accessible module/tab labels.
- Updated existing panel buttons to use the prototype's primary, neutral, danger, and compact button treatments.
- Added a Playwright screenshot checkpoint for the light workbench at the desktop viewport. The live collaboration region is masked in that checkpoint because its server event timing is intentionally nondeterministic.
- Updated browser tests for the new explicit Assistant and FIBO workspace navigation.

## Files Modified

- `web-app/src/components/ui/Icon.tsx`
- `web-app/src/components/ui/StatusBadge.tsx`
- `web-app/src/styles.css`
- `web-app/src/workbench/AiAssistantPanel.tsx`
- `web-app/src/workbench/AiCredentialSettings.tsx`
- `web-app/src/workbench/ProjectWorkspace.tsx`
- `web-app/src/workbench/ProjectListPage.tsx`
- `web-app/src/workbench/EntityDetails.tsx`
- `web-app/src/workbench/ExternalOntologyPanel.tsx`
- `web-app/src/workbench/SemanticJobPanel.tsx`
- `web-app/src/workbench/StagingPanel.tsx`
- `web-app/src/App.e2e.test.tsx`
- `web-app/e2e/workbench.spec.ts`
- `web-app/e2e/workbench.spec.ts-snapshots/workbench-light-darwin.png`

## Verification

- `cd web-app && npm ci && npm test && npm run build` passed: 9 test files and 15 tests passed; production build passed.
- `cd web-app && npm run test:e2e` passed: 1 browser journey and the light workbench screenshot checkpoint passed.
- `./gradlew test --no-daemon --console=plain` passed.
- `cd vscode-extension && npm ci && npm test` passed: 37 tests passed.
- The real Kotlin web server was already serving port 8080 and returned HTTP 200 JSON from `/api/v1/projects`; a second server start was not attempted because the port was occupied.
- `git diff --check` passed.

## Limitations

- No new backend fields or semantic behavior were added.
- The browser E2E run logs expected WebSocket proxy connection warnings when the Kotlin web server is not running; the mocked journey still passes.
- The web-app dependency audit reports two existing high-severity advisories; dependency versions were not changed as part of this redesign.
- The prototype assumes the Inter font family is available to the host; no remote font dependency was added.
