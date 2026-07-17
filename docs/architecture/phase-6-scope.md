# Phase 6 Scope

## Phase Name

**Phase 6: Collaborative Web Workbench and Native AI Foundation**

## Status

Complete. The approved collaborative web workbench and native AI foundation is implemented and summarized in `docs/phase-summaries/phase-6-summary.md`. Phase 7 is the active planning boundary.

## Purpose

Phase 6 adds a modern browser-based application as an additional Entio frontend while preserving the existing VS Code extension, Kotlin semantic engine, CLI, proposal workflow, OWL reasoning, SHACL validation, FIBO catalog access, and deterministic semantic behavior implemented in earlier phases.

Phase 6 does not replace the VS Code extension or redesign the existing backend. The web application is another client of the same Entio-owned capabilities.

The goal is not to rebuild the semantic backend or create a disposable visual prototype.

The goal is to introduce a durable web architecture that can support:

- A modern and accessible ontology workbench.
- The technical depth expected from ontology tools such as Protégé.
- A simpler workflow for non-specialist users.
- Native AI assistance.
- Multiple users working in the same ontology workspace.
- Background reasoning while users continue working.
- Continued use of the existing Kotlin/JVM semantic packages.

The phase should prioritize a coherent, extensible product foundation. It does not need to include every production concern, but all implemented behavior should sit on architectural boundaries that can be extended rather than thrown away.

## Product Goals

Phase 6 should provide:

- A polished, modern web application.
- A clear multi-panel ontology workspace.
- Different tabs for different actions and open entities.
- A collapsible ontology hierarchy sidebar.
- Class, property, individual, SHACL, reasoning, FIBO, and proposal views.
- Opening an ontology entity in its own workbench tab.
- Label-first navigation with technical IRIs available when needed.
- Existing Entio edit, stage, preview, review, approve, reject, apply, reload, and rollback behavior.
- Concurrent users connected to the same project session.
- User presence and visible editing activity.
- Safe coordination when multiple users propose changes.
- Reasoning and SHACL work running asynchronously while users continue browsing or editing.
- Native AI assistance through a provider-neutral interface.
- Reuse of the existing Kotlin semantic engine rather than reimplementation in JavaScript or TypeScript.
- A browser experience that is approachable for new users without removing technical ontology details.

## Additive Compatibility Requirement

Phase 6 is strictly additive.

The following existing behavior must remain available and compatible:

- The VS Code extension must continue to compile, run, and pass its existing tests.
- Existing CLI commands and machine-readable responses must continue to work.
- Existing Kotlin module responsibilities must remain intact.
- Existing Phase 1 through Phase 5 workflows must continue to behave as implemented.
- Existing ontology project files and `entio.yaml` projects must continue to load.
- Existing proposal, validation, diff, reasoning, SHACL, FIBO, apply, reload, and rollback services must not be replaced.
- Existing public Entio-owned contracts must remain backward compatible unless an additive extension is explicitly required.
- Existing backend services must remain directly testable without the web server.
- The web application must not become a required dependency for the CLI or VS Code extension.
- The Ktor server must act as an adapter over reusable existing services rather than moving semantic logic into web-specific code.

Any backend change required for the web application should be:

- additive;
- backward compatible;
- covered by regression tests;
- reusable by more than one frontend where practical;
- isolated from existing behavior unless the change is an approved bug fix.

A Phase 6 slice must stop if it would require removing, renaming, weakening, or changing existing functionality beyond an explicitly approved additive contract.

## Recommended Technology Direction

### Frontend

Use:

- React.
- TypeScript.
- Vite.
- React Router.
- A reusable component system and design tokens.
- A client-side data-fetching and cache layer.
- WebSocket support for live collaboration and job updates.

React is appropriate for the tabbed, component-heavy ontology workbench. Vite is preferable to a full server-rendered React framework for this phase because Entio is primarily an authenticated, highly interactive application rather than a public content website.

Node.js should support:

- Frontend development tooling.
- Vite builds.
- TypeScript compilation.
- Frontend tests.
- Package management.

Node.js should not replace the Kotlin semantic backend.

### Backend

Use Kotlin/JVM for the application server.

The preferred web-server framework is **Ktor**, because it:

- Runs natively with the existing Kotlin/JVM code.
- Can call the existing Entio modules directly.
- Supports HTTP APIs and WebSockets.
- Avoids maintaining a second semantic implementation.
- Is lightweight enough for Phase 6.
- Can grow into a more complete service architecture later.

The Kotlin server should expose Entio-owned request and response contracts over HTTP and WebSockets.

The Ktor server should call the same reusable services already used by the CLI. It must not:

- replace the CLI;
- require the VS Code extension to change protocols;
- duplicate proposal or semantic policy;
- move ontology logic into route handlers;
- make web-session state a prerequisite for backend operations.

Where the web application needs a new capability, prefer adding a reusable Kotlin service or additive Entio-owned contract that can also be consumed by the CLI or VS Code extension later.

### Collaboration

Use a server-authoritative collaboration model for ontology changes.

For Phase 6:

- Users join a shared project session.
- Presence, cursors, current selections, open entities, and draft-form activity can update live.
- Proposed ontology operations remain typed Entio edits.
- The server assigns ordering and current baselines.
- Each staged change records its author and session metadata.
- Conflicts are detected against proposal baselines.
- No user can silently overwrite an approved change from another user.

Yjs may be used for low-risk shared interface state, collaborative notes, or text fields, but it should not become the authoritative ontology mutation engine.

Entio graph changes, proposal validation, reasoning impact, SHACL impact, approval, application, and rollback must remain Kotlin-owned.

### Native AI

Add an AI service boundary rather than hard-coding one model provider into the UI.

The Phase 6 AI foundation should support:

- A docked AI assistant panel.
- Current-project and current-entity context.
- Questions about classes, properties, definitions, relationships, reasoning results, SHACL findings, and FIBO candidates.
- Suggested typed Entio changes.
- Explanations of proposed changes in plain language.
- AI suggestions entering the same staged proposal workflow as human edits.
- No direct AI write access to ontology files.
- Clear distinction between ontology facts, inferred facts, search results, and AI suggestions.

The initial implementation should use OpenAI through a user-supplied API key, while the Kotlin boundary remains provider-neutral so additional model providers can be supported later.

Native AI assistance is optional. Entio must remain fully usable without an API key.

Each user who wants to use native AI assistance must supply their own OpenAI API key. Entio must not bundle, share, or provide a platform-wide OpenAI credential.

## Native AI Credential Requirements

Native AI assistance requires the user to provide their own OpenAI API key.

The application should:

- Clearly explain that an OpenAI API key is required before AI features can be used.
- Keep all non-AI Entio capabilities available when no key is configured.
- Provide a settings flow for adding, replacing, testing, and removing the key.
- Treat the key as a secret and never display it after entry.
- Never include the key in URLs, logs, analytics, collaboration events, proposal records, or frontend state that is visible to other users.
- Never commit the key to the repository or store it in ontology project files.
- Never send one user’s key to another user’s browser session.
- Use the key only for requests explicitly initiated through the native AI feature.
- Allow users to disable AI access and delete the stored credential.
- Return a clear unavailable state when the key is missing, invalid, revoked, or rate-limited.
- Make clear that model usage and billing are associated with the user’s own OpenAI API account.

For the initial implementation, secure server-side storage may be session-scoped or development-only, but the credential boundary must be isolated behind a dedicated secret-management interface so production secret storage can replace it later.

The React frontend should never call OpenAI directly. It should send AI requests to the Kotlin server, and the server should use the configured user credential through the provider adapter.

## Architecture Principle

The browser is a new client for the existing Entio semantic engine. The VS Code extension remains a supported client, and the CLI remains a supported machine-readable boundary.

```text
React web application
        |
        | HTTP and WebSocket contracts
        v
Kotlin/Ktor application server
        |
        +-----------------------------+
        | Existing reusable Entio     |
        | Kotlin services             |
        +-----------------------------+
          | core-types
          | semantic-engine
          | validation-engine
          | graph-diff
          | OWL API / HermiT
          | Apache Jena / Jena SHACL
          | FIBO catalog services

VS Code extension
        |
        | Existing CLI JSON boundary
        v
Kotlin CLI
        |
        +-----------------------------+
        | The same reusable Entio     |
        | Kotlin services             |
        +-----------------------------+
```

The React application should never become a second RDF, OWL, SHACL, search, reasoning, proposal, or persistence implementation.

## Main Workbench Layout

The Phase 6 application should use a persistent application shell.

### Global Header

Include:

- Entio product identity.
- Current project.
- Current branch or workspace state where applicable.
- Connected-user avatars.
- Reasoning status.
- SHACL status.
- Staged-change count.
- AI assistant control.
- AI credential status.
- User menu.

### Left Sidebar

The sidebar should provide:

- Project sources.
- Ontology hierarchy.
- Classes.
- Object properties.
- Datatype properties.
- Annotation properties.
- Individuals.
- SHACL shapes.
- External FIBO catalog entry point.

The ontology hierarchy should:

- Display labels first.
- Show technical IRIs on demand.
- Use arrows to expand and collapse subclasses.
- Load children incrementally.
- Distinguish asserted and inferred hierarchy links.
- Support search and filtering.
- Allow any entity to open in a tab.
- Show basic status markers for local, external, modified, staged, or invalid entities.

### Main Tab Workspace

Users should be able to open multiple tabs.

Supported tab types should include:

- Entity details.
- Class hierarchy.
- Property details.
- Individual details.
- SHACL shape details.
- Reasoning results.
- SHACL validation results.
- FIBO browser and search.
- Proposal review.
- Project overview.
- AI assistant conversation where useful.

Tabs should support:

- Open.
- Close.
- Reorder.
- Preserve unsaved form state during the session.
- Indicate staged or conflicting changes.
- Open related entities in another tab.

### Bottom Or Right Activity Area

Include one adaptable panel for:

- Staged changes.
- Proposal impact.
- Reasoning activity.
- SHACL results.
- Collaboration activity.
- AI assistant.

This may be implemented as a right drawer or bottom panel rather than several simultaneous panes.

## Entity Detail Experience

Opening a class should show:

- Preferred label.
- IRI.
- Definitions.
- Alternate labels.
- Annotations.
- Asserted parents.
- Inferred parents.
- Direct subclasses.
- Properties with the class as domain or range.
- Related individuals.
- Source ontology and file.
- Local or FIBO origin.
- Reasoning status.
- SHACL constraints and findings.
- Edit actions.
- Delete action with dependency review.
- Open-related-entity actions.

Equivalent tailored details should be provided for properties, individuals, annotation properties, and shapes.

Technical RDF details should be available, but not dominate the default view.

## Tabs For Major Actions

The initial application should provide visible entry points for:

- **Explore**: hierarchy and entity browsing.
- **Edit**: focused typed ontology edits.
- **FIBO**: curated browsing and deterministic schema search.
- **Reasoning**: status, inferred facts, consistency, and explanations.
- **Constraints**: SHACL shapes and validation results.
- **Changes**: staged edits, proposal preview, impact, approval, rejection, apply, and rollback.
- **AI Assistant**: ontology questions and suggested changes.
- **Activity**: collaborators, recent actions, and system jobs.
- **Settings**: user profile, AI credential configuration, and optional client preferences.

These may be implemented as top-level routes, workspace tabs, or a combination.

## Editing And Proposal Workflow

Phase 6 must preserve the existing Entio workflow.

```text
User or AI prepares typed edit
→ edit is validated
→ edit enters staged changes
→ combined preview is built
→ explicit semantic diff is shown
→ reasoning impact is shown
→ SHACL impact is shown
→ collaborators review
→ authorized reviewer approves or rejects
→ Kotlin server applies approved changes
→ project reloads
→ reasoning and SHACL rerun
→ rollback occurs if verification fails
```

Collaboration must not bypass this process.

### Staged Change Metadata

Each staged item should include:

- Author.
- Timestamp.
- Edit type.
- Target entity.
- Target source.
- Current baseline.
- Validation state.
- Conflict state.
- Optional comment.
- AI-generated indicator where applicable.

### Review

The proposal review view should show:

- Explicit graph changes.
- Reasoning changes.
- SHACL changes.
- Affected sources.
- Contributors.
- Conflicts.
- Blocking issues.
- Approval and rejection controls.

One authorized user may perform final application, while multiple users may contribute and review.

## Collaboration Scope

### Required Collaboration Capabilities

- Two or more browser sessions can join the same project workspace.
- Each session shows connected users.
- Users can see when another user opens or edits an entity.
- Staged changes appear to all connected users.
- Each staged change shows its author.
- A newly applied proposal refreshes all connected clients.
- Baseline conflicts are surfaced rather than overwritten.
- Reasoning and SHACL job updates are broadcast to all connected clients.

### Safe Concurrency Model

Phase 6 should not attempt unrestricted simultaneous raw Turtle editing.

Concurrent work should occur through:

- Typed edit forms.
- Shared staged changes.
- Server-assigned proposal order.
- Baseline checks.
- Conflict detection.
- Explicit review and approval.

### Deferred Collaboration Features

The following are not required in Phase 6:

- Offline collaborative editing.
- Peer-to-peer synchronization.
- Full CRDT representation of RDF graphs.
- Fine-grained merge of conflicting ontology axioms.
- Durable comments and review threads.
- Enterprise role management.
- Organization-wide project discovery.
- Audit-retention policies.

The architecture should leave room for these later.

## Asynchronous Reasoning

Reasoning must be able to run while users continue working.

The Kotlin server should manage reasoning as a background job with:

- Job ID.
- Project and graph fingerprint.
- Queued, running, completed, failed, cancelled, incomplete, and stale states.
- Start and completion time.
- Progress/status events where available.
- Cancellation.
- Result invalidation when the graph changes.
- Latest valid result retention.
- WebSocket updates to connected clients.

### Reasoning During Editing

Phase 6 should support:

- Reasoning over the currently applied ontology.
- Reasoning over a combined proposal preview.
- Continued navigation and form editing while reasoning runs.
- A visible distinction between:
  - reasoning for the applied graph;
  - reasoning for a proposal preview;
  - a stale result from an older graph.

Reasoning must not block the main UI thread or the main server request path.

## SHACL Behavior

SHACL should follow the same background-result pattern where practical.

Users should be able to:

- See current validation state.
- Open violations and affected entities.
- See proposal-introduced, worsened, unchanged, improved, and resolved results.
- Continue browsing while validation runs.
- Distinguish asserted-only and asserted-plus-inferred results where supported.

Full SHACL authoring UI is not required in Phase 6 if the existing implemented boundary remains inspection-focused.

## FIBO In The Web Application

Phase 6 should expose the Phase 5 FIBO workbench in the browser.

Users should be able to:

- Browse curated modules.
- Search the wider packaged catalog.
- Inspect external class and property descriptions.
- Review search scores and deterministic match reasons.
- Review dependencies.
- Stage external reuse.
- Create a local subclass of a FIBO class.
- Preserve original FIBO IRIs.
- Keep bundled FIBO assets immutable.

The browser should clarify that FIBO is an immutable source catalog that can be referenced, imported, and extended locally but not modified directly.

## Native AI Capabilities

The AI assistant should support at least these scenarios:

1. Explain a selected class in plain language.
2. Summarize asserted and inferred relationships.
3. Explain a SHACL violation.
4. Search for a relevant FIBO concept.
5. Suggest a definition, superclass, property, constraint, or external reuse action.
6. Convert an accepted suggestion into a typed staged edit.
7. Show that the suggestion still requires normal review and approval.

The AI assistant should receive structured semantic context rather than an uncontrolled dump of source files.

AI requests should only run after the current user has configured a valid OpenAI API key.

The assistant should clearly label:

- Retrieved ontology facts.
- Inferred facts.
- Validation findings.
- FIBO search results.
- AI-generated suggestions.

## API Scope

The Ktor server should expose versioned web contracts.

Expected HTTP areas include:

- Project loading and summaries.
- Hierarchy and entity descriptors.
- Search.
- FIBO catalog.
- Reasoning jobs and results.
- SHACL results.
- Typed edit preparation.
- Staged changes.
- Proposal previews.
- Approval, rejection, application, and rollback.
- AI requests and suggested typed edits.
- Collaboration sessions and users.

Expected WebSocket event categories include:

- Presence changes.
- Entity activity.
- Staged-change updates.
- Proposal updates.
- Reasoning-job updates.
- SHACL-job updates.
- Project-applied and project-reloaded events.
- Conflict notifications.

The exact endpoint paths belong in the feature specification and ExecPlan.

## State And Persistence

For Phase 6:

- Ontology projects may remain filesystem-backed.
- FIBO remains a bundled local package.
- Active collaboration sessions may remain in memory.
- Staged changes may remain session-scoped if clearly communicated.
- A lightweight database may be deferred unless authentication or durable collaboration requires it.

The interfaces should separate:

- Project storage.
- Collaboration session state.
- Proposal state.
- User identity.
- Semantic jobs.

This prevents the Phase 6 implementation from tying future persistence to React components or one temporary server object.

## Authentication And Authorization

A production identity system is not required in Phase 6.

Phase 6 should still model:

- User ID.
- Display name.
- Avatar or initials.
- Session membership.
- Contributor versus reviewer capability where practical.

A development login or predefined users is acceptable.

The server, not the UI, should enforce final approval/application permissions.

## Modern UI Requirements

The web application should feel like a modern professional workbench.

It should include:

- Consistent spacing and typography.
- Responsive panes.
- Keyboard navigation.
- Clear empty, loading, error, stale, and conflict states.
- Accessible contrast and focus behavior.
- Search-first interaction.
- Labels before IRIs.
- Technical details available progressively.
- Status badges for asserted, inferred, external, staged, invalid, and stale.
- Avoidance of dense raw-RDF presentation as the default.
- Fast perceived response through skeletons, incremental loading, and background jobs.

The interface should be optimized for desktop browsers. Full mobile support is not required.

## Suggested End-To-End Product Journey

A complete Phase 6 product journey should support:

1. Open an Entio web project.
2. Expand the class hierarchy in the left sidebar.
3. Open several classes in tabs.
4. Inspect labels, definitions, asserted parents, inferred parents, properties, and SHACL status.
5. A second user joins and opens another entity.
6. One user creates a local subclass or edits a definition.
7. Another user sees the staged change appear.
8. Run reasoning over the proposal while continuing to browse.
9. Show inferred impact and SHACL impact.
10. Search FIBO and inspect a candidate with match reasons.
11. Stage reuse of the FIBO concept or create a local subclass.
12. Ask the AI assistant to explain the recommendation.
13. Accept the AI suggestion into the staged set.
14. Review the combined proposal.
15. Approve and apply.
16. Show both users receiving the refreshed ontology and new reasoning status.

## Non-Goals

Phase 6 should not include:

- Rewriting the Kotlin semantic engine in Node.js or TypeScript.
- Replacing Apache Jena, OWL API, HermiT, or Jena SHACL.
- Full production authentication and enterprise authorization.
- Durable multi-organization tenancy.
- Full CRDT-based ontology graph merging.
- Raw simultaneous Turtle editing.
- Offline collaboration.
- Full audit-log retention.
- Full Git integration.
- A graph database migration.
- A new FIBO retrieval system.
- Full Protégé parity.
- Arbitrary OWL class-expression editing.
- Complete SHACL authoring.
- Autonomous AI application of changes.
- AI bypass of validation, proposal review, or approval.
- A bundled, shared, or Entio-funded OpenAI API key.
- Requiring an OpenAI API key for non-AI Entio functionality.
- A mobile-first interface.
- Replacing, deprecating, or weakening the VS Code extension.
- Replacing or altering existing CLI and backend behavior.
- Making the web application a required dependency of existing Entio clients.

## Phase 6 Deliverable Priorities

Phase 6 should prioritize a coherent vertical product experience over breadth.

### Must Have

- React/TypeScript web shell.
- Ktor server that calls existing Kotlin modules.
- Existing VS Code extension and backend workflows continue working unchanged.
- Project load and hierarchy sidebar.
- Entity detail tabs.
- Existing ontology descriptors.
- Staged edits and combined proposal review.
- Approve/reject/apply through Kotlin.
- Reasoning status and background execution.
- SHACL result display.
- FIBO browsing/search/details.
- At least two connected users with presence.
- Shared staged-change updates.
- Baseline conflict visibility.
- AI assistant panel using a user-supplied OpenAI API key.
- AI credential settings and missing/invalid-key states.
- AI suggestion converted into a typed staged edit.

### Should Have

- Proposal reasoning impact.
- Proposal SHACL impact.
- User activity indicators on entities.
- FIBO dependency review and external proposal preparation.
- Reordering and restoring open tabs.
- Reviewer permission distinction.
- Reasoning cancellation.

### Could Have

- Shared comments.
- Collaborative text editing for definitions.
- Rich graph visualization.
- AI-generated SHACL suggestions.
- Saved workspaces.
- Theme switching.
- Command palette.

## Testing Expectations

Phase 6 should include:

- Kotlin server contract tests.
- HTTP endpoint tests.
- WebSocket collaboration tests.
- Existing Kotlin semantic regression tests.
- React component tests.
- Frontend state and routing tests.
- Two-client collaboration integration tests.
- Reasoning background-job tests.
- Stale-result and baseline-conflict tests.
- AI suggestion safety tests.
- Missing, invalid, revoked, and removed API-key tests.
- Tests proving API keys are not exposed in logs, browser events, shared collaboration state, ontology files, or proposal records.
- End-to-end browser tests for the primary product journey where time permits.

Existing Phase 1 through Phase 5 tests must continue to pass.


Phase 6 verification must also include explicit non-regression checks for:

- Existing VS Code extension compilation and tests.
- Existing CLI command behavior and response compatibility.
- Existing example projects.
- Existing proposal preview, apply, reject, reload, and rollback flows.
- Existing reasoning and SHACL commands.
- Existing FIBO browse, search, dependency, and proposal-preparation commands.
- Direct use of backend services without starting the Ktor server.

The web application must be removable or disabled without breaking the CLI, VS Code extension, or semantic engine.

## Success Criteria

Phase 6 is successful when:

- Entio runs as a browser-based workbench.
- The browser is an additional frontend, not a replacement for the VS Code extension.
- The VS Code extension, CLI, Kotlin modules, and existing Phase 1 through Phase 5 workflows continue to function and pass their regression suites.
- The web UI uses existing Kotlin semantic capabilities rather than duplicating them.
- Users can navigate an expandable ontology hierarchy and open entities in tabs.
- Users can view human-readable and technical ontology details.
- Existing staged, preview, review, approve/reject, apply, reload, and rollback behavior remains intact.
- At least two users can connect, see each other, and share staged-change activity.
- Conflicting changes are detected rather than silently overwritten.
- Reasoning can run asynchronously while users continue using the application.
- SHACL results remain visible and linked to ontology entities.
- FIBO browsing and reuse remain available.
- An AI assistant can explain ontology context and propose a typed change when the user supplies a valid OpenAI API key.
- Entio remains fully usable without an OpenAI API key.
- User API keys remain private and are not exposed to collaborators or written into ontology projects.
- AI suggestions cannot bypass validation or approval.
- The application looks and behaves like a credible modern product.
- The architecture can be extended without discarding the Kotlin semantic engine or React workbench.

## Follow-Up Phases

Likely follow-up work includes:

- Durable project and proposal persistence.
- Production authentication and authorization.
- Durable collaboration history and comments.
- Rich conflict resolution.
- More complete SHACL authoring.
- Broader AI workflows.
- Agent orchestration.
- Project versioning.
- Deployment, observability, and administration.
- Additional external ontology sources.
- Production-scale data and graph storage.
