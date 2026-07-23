# Repository Structure And Code Architecture

## Short version

Entio is split into three layers:

```text
React web app → Ktor server ┐
VS Code extension → CLI     ├→ Kotlin semantic engine modules
CLI                         ┘
```

The Kotlin engine owns ontology meaning and validation. The server and CLI expose engine behavior. The React and VS Code clients present it to users but do not decide RDF, OWL, or SHACL semantics.

## Top-level layout

| Path | Purpose |
| --- | --- |
| `core-types/` | Shared Kotlin data contracts: RDF terms, projects, proposals, validation, diffs, semantic descriptions, and ontology-map data. |
| `semantic-engine/` | Loads and interprets ontologies; owns parsing, descriptions, edits, reasoning, SHACL, FIBO, and read-only graph extraction. |
| `validation-engine/` | Deterministic project, proposal, metadata, deletion, namespace, and external-dependency validation. |
| `graph-diff/` | Semantic diffs, combined previews, and proposal impact analysis. |
| `cli/` | Thin command-line adapter over the Kotlin modules. |
| `web-server/` | Ktor HTTP/WebSocket adapter, in-memory sessions, staging, collaboration, jobs, and provider settings. |
| `web-app/` | React workbench and read-only ontology map. |
| `vscode-extension/` | VS Code workbench that invokes the CLI instead of implementing ontology behavior. |
| `shared/` | Small generic Kotlin utilities only. |
| `external-ontologies/fibo/` | Pinned, read-only FIBO package and generated catalog data. |
| `examples/` | Example Entio projects used for learning and regression coverage. |
| `docs/` | Architecture, specs, ExecPlans, decisions, and phase summaries. |

Production Kotlin code is under each module's `src/main`; tests are under `src/test`. Web code is under `web-app/src`, browser tests under `web-app/e2e`, and VS Code code under `vscode-extension/src`.

## Kotlin module dependencies

```text
core-types ← semantic-engine ← validation-engine ← graph-diff
     ↑             ↑                 ↑                ↑
     └──────────── CLI and web-server consume these modules

shared → semantic-engine, validation-engine, graph-diff, CLI
```

More exactly:

| Module | Internal dependencies | Important external dependencies |
| --- | --- | --- |
| `core-types` | None | Kotlin/JVM only |
| `shared` | None | Kotlin/JVM only |
| `semantic-engine` | `core-types`, `shared` | Apache Jena, Jena SHACL, OWL API, HermiT, SnakeYAML |
| `validation-engine` | `core-types`, `semantic-engine`, `shared` | No direct external library |
| `graph-diff` | `core-types`, `semantic-engine`, `validation-engine`, `shared` | No direct external library |
| `cli` | All engine modules | Picocli, Jackson |
| `web-server` | `core-types`, `semantic-engine`, `validation-engine`, `graph-diff` | Ktor, Jackson, coroutines |

`core-types` never calls higher modules. Engine modules never depend on the server, React app, or VS Code extension.

## Feature ownership

| Feature | Main implementation |
| --- | --- |
| Project configuration, source resolution, Turtle parsing | `semantic-engine/ProjectConfigLoader`, `OntologySourceResolver`, `OntologyParser`, `ProjectLoader` |
| RDF and product contracts | `core-types/` |
| Symbol extraction and semantic descriptions | `semantic-engine/SymbolExtractor`, `SemanticDescriptorAssembler`, `SemanticDescriptionService` |
| Labels and deterministic IRI generation | `semantic-engine/LabelResolver`, `SemanticLabelPolicy`, `DeterministicIriGenerator` |
| Typed ontology edits and deletion analysis | `semantic-engine/TypedOntologyEditTranslator`, `DeletionDependencyAnalyzer`, `DeletionChangeGenerator` |
| Proposal preview, apply, rollback | `semantic-engine/GraphChangePreviewer`, `ProposalCreator`, `ProposalApplier`, `MultiSourceAtomicApplier` |
| Validation | `validation-engine/` |
| Diffs and combined proposal previews | `graph-diff/` |
| OWL reasoning and explanations | `semantic-engine/ReasoningService`, `OwlOntologyAdapter`, `ReasoningExplanationService` |
| SHACL authoring and validation | `semantic-engine/ShaclShapeAuthoringService`, `ShaclValidationService` |
| FIBO catalog, search, and reuse | `semantic-engine/Fibo*`, `External*`; server adapter in `FiboWebService`; UI in `ExternalOntologyPanel` |
| CLI commands | `cli/` |
| HTTP routes and web contracts | `web-server/Application.kt`, `web-server/contract/` |
| Shared staging and review | `web-server/StagingWorkflowService`; UI in `StagingPanel` and `ContextualEditing` |
| Collaboration and jobs | `web-server/CollaborationHub`, `SemanticJobManager`; React adapters in `web-app/src/web/` |
| Entity browsing and editing UI | `web-app/src/workbench/ProjectWorkspace`, `EntityDetails`, and picker/editor components |
| Read-only ontology map | `semantic-engine/OntologyGraphService` → `web-server/OntologyGraphWebService` → `web-app/src/workbench/ontology-map/` |
| Provider credentials and model selection | `web-server/ai/`, `AiModelWebBoundary`; UI in `AiCredentialSettings` |
| VS Code workbench | `vscode-extension/`; semantic calls go through `engineCli.ts` to `cli/` |

Native AI proposal/task execution is not active. Historical Phase 7–8 documents and some retained source files describe older work, but only credential and model settings are exposed by the current product.

## Typical call paths

### Open and inspect a project

```text
React query
→ Ktor route in Application.kt
→ read-only web adapter
→ ProjectLoader / semantic description services
→ core data contracts
→ JSON response
```

### Stage and apply an edit

```text
React editor
→ Ktor staging route
→ StagingWorkflowService
→ typed edit translator
→ preview + validation + diff
→ human approval
→ ProposalApplier
→ ontology source file
```

The browser never writes Turtle directly.

### Render the ontology map

```text
OntologyMapShell
→ React Query / projectApi
→ OntologyGraphWebService
→ OntologyGraphService
→ asserted ontology facts
```

Kotlin supplies hierarchy, domain, range, types, assertions, and stable identity. React only filters, lays out, zooms, pans, and remembers temporary positions.

### Use the VS Code extension

```text
VS Code webview
→ engineCli.ts
→ Entio CLI
→ Kotlin engine modules
```

## Frontend organization

`web-app/src/web/` contains transport concerns: DTOs, API calls, React Query hooks, sessions, jobs, and collaboration.

`web-app/src/workbench/` contains product UI:

- `ProjectWorkspace` coordinates navigation, tabs, outline, search, and map state.
- `EntityDetails` renders and stages entity-specific work.
- `StagingPanel` owns proposal review presentation.
- `ontology-map/` isolates graph layout, merging, rendering, and interaction behavior.
- Small reusable visual components live in `components/ui/`.

React uses React Query for server state and React state for temporary UI state. It does not use an additional global state framework.

## Is the code separated or jumbled?

The module boundaries are clear and generally enforced:

- ontology semantics are centralized in `semantic-engine`;
- validation and diffing have dedicated modules;
- interfaces delegate instead of reimplementing semantic behavior;
- the Phase 9 map has a clean engine → server → React path.

The main concentration points are inside presentation/adaptation layers:

- `web-server/Application.kt` registers many routes;
- `ProjectWorkspace.tsx` coordinates many workbench concerns;
- `EntityDetails.tsx` contains many entity-detail workflows;
- `styles.css` is a large shared stylesheet.

These files are dense, but they do not reverse dependency direction or move ontology policy into the UI. If the product grows, they are the clearest candidates for smaller route, controller, component, and stylesheet files.

## Where to start

- To change ontology behavior: start in `semantic-engine`, then update validation/diff adapters as needed.
- To add an HTTP capability: reuse an engine service, add a `web-server/contract` DTO, then register the route.
- To change web presentation: start in `web-app/src/workbench`; keep semantic decisions on the server.
- To change the ontology map: inspect `OntologyGraphService`, `OntologyGraphWebService`, then `web-app/src/workbench/ontology-map`.
- To understand why a design exists: check `docs/decisions/` and the relevant phase spec and ExecPlan.
