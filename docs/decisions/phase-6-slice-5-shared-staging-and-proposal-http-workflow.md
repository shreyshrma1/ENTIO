# Phase 6 Slice 5 Completion: Shared Staging And Proposal HTTP Workflow

## Slice

Slice 5: Shared Staging And Proposal HTTP Workflow.

## Goal

Expose the existing controlled-edit proposal lifecycle through a server-owned, single-client web boundary without moving RDF writes or semantic decisions into HTTP or TypeScript code.

## Implemented

- Added web-server staging contracts for typed edit requests, staged entries, proposal state, validation messages, baselines, and semantic diff entries.
- Added an in-memory `StagingWorkflowService` keyed by registered project id.
- Added typed-edit request translation for the existing supported ontology edit operations and deletion planning through the existing semantic-engine services.
- Added staged-change listing, staging, removal, proposal preview, approval, rejection, and application routes.
- Delegated proposal creation, validation, semantic diff generation, baseline checks, source serialization, apply, reload, and rollback to existing Kotlin services.
- Added idempotency handling for stage requests and stable in-session staged ids.
- Added a React staging panel and typed client helpers for staging, proposal review, rejection, and application.

## Allowed Scope And Boundaries

- State is in-memory and single-client, as required for this slice.
- Drafts are private until staged; staged entries carry author, latest editor, comment, AI-origin metadata, normalized values, and generated-IRI metadata.
- Rejection keeps staged entries available for correction.
- No RDF source writes occur in the web server or web app.
- No collaboration session, WebSocket, AI provider, or new core proposal model was added.

## Tests And Verification

- `./gradlew clean :web-server:test test --no-daemon`
- `./gradlew :web-server:test --no-daemon`
- `(cd web-app && npm test && npm run build)`
- `git diff --check`

The server tests cover private staging, proposal preview, reviewer-only approval, apply delegation, source mutation only after approval, rejection preserving staged entries, and existing route behavior. The web tests cover the staging/proposal HTTP helpers and the existing workbench shell.

## Limitations

- The workflow is intentionally in-memory and single-client; restart loses staged entries and proposals.
- The web form currently demonstrates the create-class typed edit. The server request contract supports the existing typed-edit operations, while broader edit-form coverage belongs to the later workbench-edit slice.
- Multi-source proposals are rejected at this boundary for now; no automatic merge is performed.
- Authentication remains the Phase 6 development identity provider.

## Commit

The focused slice commit is `12ae558` (`Expose Phase 6 shared staging workflow`).
