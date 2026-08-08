# ExecPlan: Phase 13 Domain-Ontology-First Modeling And Full-Corpus Retrieval

## Status

Approved Phase 13 ExecPlan as revised by the Slice 0 audit on 2026-08-08.
Production implementation begins with Slice 1 only after the completed Slice 0
branch is merged locally.

Phase 13 is the repository's active phase, while product implementation remains
complete only through Phase 12 until Slice 1 lands. Slice 0 resolved the audit
and ADR gates and incorporated them into this approved revision. Production
code, dependencies, model binaries, generated search assets, routes, and UI
work remain ordered by the slice dependencies below.

This plan implements the approved Phase 13 specification in small, reviewable
slices. Slice 0 is an approval gate. It resolves dependency, model, storage,
atomicity, licensing, and benchmark questions before production code or binary
assets are added. Later slices must not begin until their dependencies and stop
conditions are satisfied.

Every completed slice requires:

- focused tests;
- the listed verification commands;
- a completion record under `docs/decisions/` or a clearly named Phase 13 slice
  record;
- review of generated and staged changes;
- confirmation that no unrelated behavior was changed.

## Goal

Replace the always-present, curated-first FIBO browser model with an optional
project domain profile and a full-corpus recommendation capability:

```text
optional project FIBO activation
→ selectable foundation and full-release search
→ lexical + vector + ontology + graph + project retrieval
→ explainable recommendations throughout human authoring
→ explicit reuse, customization, extension, mapping, or local choice
→ existing preview, validation, approval, atomic apply, reload, and rollback
```

Reused entities keep canonical FIBO IRIs. Their project-owned statements live
in a managed Turtle source and remain editable through supported typed
operations. Original source meaning and applied provenance remain separately
traceable.

## Related Spec

- [Phase 13 scope](../architecture/phase-13-scope.md)
- [Phase 13 feature spec](../specs/0024-phase-13-domain-ontology-first-modeling-full-corpus-retrieval.md)
- [Phase 5 external ontology spec](../specs/0008-phase-5-external-ontology-browsing-schema-rag.md)
- [Phase 12 spec](../specs/0023-phase-12-ontology-grounded-document-analysis.md)
- [Phase 12 ExecPlan](0023-phase-12-ontology-grounded-document-analysis.md)
- [Kotlin engine guidelines](../architecture/003-kotlin-engine-guidelines.md)
- [Approved reproducible FIBO package decision](../decisions/phase-5-slice-2-approved-reproducible-fibo-package.md)

## Objective

Deliver a Phase 13 implementation where:

- a project can explicitly activate or leave disabled the approved pinned
  `master_2026Q2` FIBO snapshot;
- activation safely adds project configuration and an empty managed reuse
  source without adding ontology statements;
- the complete eligible FIBO corpus is indexed and verified offline;
- one shared Kotlin service returns bounded, explainable domain
  recommendations;
- every applicable human-driven web authoring workflow uses that service;
- selected entities and dependencies materialize through existing proposals;
- labels, definitions, and all other supported statements can be customized
  while the canonical IRI remains locked;
- source snapshots and project-local provenance remain atomic with ontology
  apply;
- existing document and assistant retrieval contracts remain unchanged;
- no hosted retrieval service, new database, new Gradle module, or new ontology
  write path is introduced.

## Current State

### Approved FIBO package

The repository already contains:

- `external-ontologies/fibo/manifest.yaml`;
- FIBO `master_2026Q2` at commit
  `f59157fe156e3d91b1c045222d0a7dc06b7d78a2`;
- OMG Commons `1.3` dependencies;
- `297` source files;
- a `4,579`-element generated catalog;
- checksum, license, attribution, ontology-IRI-map, curated-foundation, and
  catalog assets;
- offline generation and verification Gradle tasks.

`FiboCatalogGenerator`, `FiboPackageVerifier`, `FiboCatalogLoader`, and
`FiboSchemaSearchService` own the current package and deterministic search.

### Current reuse

`ExternalDependencyReviewer`, `ExternalProposalPreparer`, and
`ExternalProposalIntentTranslator` calculate bounded dependencies and translate
supported class/property reuse or local subclass actions. The translated graph
changes enter `StagingWorkflowService` and the existing proposal/apply path.

The current model preserves the external IRI but does not provide:

- project activation through a durable domain profile;
- a dedicated managed reuse source;
- editable source-versus-project semantics;
- project-local reuse provenance;
- mapping actions;
- shared recommendations across authoring workflows.

### Current web and clients

`FiboWebService` and `/api/v1/projects/{projectId}/external/fibo/*` expose
modules, search, details, dependencies, and proposal staging. React uses
`ExternalOntologyPanel.tsx`, `projectApi.ts`, and `queries.ts`. VS Code uses
machine-readable CLI external-catalog commands.

FIBO is effectively always available when its package loads. Project settings
do not activate or disable it.

### Current project configuration

`EntioProjectConfig` contains name, ontology sources, local IRI namespace, and
import mappings. `ProjectConfigLoader` reads `entio.yaml`; no focused writer
exists. Ontology application is atomic, but project configuration changes are
not currently a product workflow.

### Current retrieval boundaries

- Local semantic search is deterministic and Kotlin owned.
- FIBO search is weighted lexical/structural search over generated JSONL.
- There is no embedding model, local inference runtime, or vector index.
- Phase 12 document analysis calls `FiboSchemaSearchService` through its own
  frozen deterministic retrieval contract.
- The native assistant calls existing bounded `FiboWebService` search.

### Current apply provenance

Document ingestion has focused apply hooks and an applied-document provenance
repository. Generic staging has no domain-reuse provenance coordinator.
Whether that hook boundary can be safely generalized without coupling domain
reuse to document ingestion is a Slice 0 decision.

## Target State

### Project configuration

- `EntioProjectConfig` includes an optional typed domain profile loaded from
  `.entio/domain-profile.yaml`.
- Managed and provenance paths are fixed conventions rather than duplicated
  user-controlled fields: `ontology/fibo-reuse.ttl` and
  `.entio/domain-reuse/events-v1.jsonl`.
- A focused semantic-engine service previews and transactionally applies
  activation or safe deactivation using the profile file as the commit point.
- Inactive projects behave as they do today except that human FIBO authoring is
  not shown as selected.

### Search assets

- The current verified Phase 5 package remains immutable and remains the
  semantic source.
- Phase 13 assets live in a separately fingerprinted package under
  `external-ontologies/domain-search/fibo/master_2026Q2/` so Phase 12's manifest,
  catalog metadata, fingerprint, ordering, and work keys do not change.
- A Phase 13 index manifest binds full semantic descriptors, BM25 documents,
  exact-scan local vectors, graph context, foundation groups, and ranking
  contracts to the unchanged Phase 5 package fingerprint.
- Interactive search requires no network.
- Vector failure produces a visible lexical-structural mode.

### Recommendation service

- One Kotlin service accepts typed modeling intent.
- It combines lexical and vector candidates, applies hard semantic
  eligibility, reranks using graph/project context, and returns stable
  explanations.
- Web, CLI, and compatible VS Code reads adapt this service rather than
  reimplementing it.

### Reuse

- Selected canonical FIBO entities materialize into
  `ontology/fibo-reuse.ttl` through supported typed proposals.
- Project meaning is editable; canonical IRI is immutable.
- Source snapshots and project differences are inspectable.
- Applied provenance is stored under `.entio/domain-reuse/` and commits or
  rolls back with ontology changes.

### Product integration

- FIBO activation and foundation selection live in domain settings.
- Global search and all specified human-driven authoring workflows receive
  contextual recommendations.
- Existing external routes remain as compatibility adapters throughout Phase
  13; their removal is outside this phase.
- Document ingestion and assistant behavior remain on current contracts.

## Scope

This plan is limited to:

- the approved FIBO package and Phase 13 generated assets;
- project domain-profile configuration;
- local embedded lexical and vector retrieval;
- Kotlin-owned recommendation, dependency, reuse, and provenance behavior;
- versioned Ktor adapters;
- React integration throughout specified human-driven workflows;
- minimum CLI and VS Code compatibility;
- migration, quality, performance, security, and regression coverage;
- Phase 13 documentation and ADRs.

## Non-Goals

The implementation must not add:

- another domain source;
- arbitrary remote or uploaded ontology content;
- runtime FIBO downloads;
- a hosted vector or embedding service;
- an external database or search server;
- a new Gradle module;
- a new RDF, OWL, SHACL, reasoning, proposal, or apply engine;
- automatic alignment, equivalence, approval, or application;
- Phase 13 retrieval in document ingestion or the assistant;
- full CLI/VS Code parity with React;
- release upgrade automation;
- unrelated production identity, tenancy, collaboration, or persistence work.

## Architectural Decisions Fixed By This Plan

- FIBO `master_2026Q2` remains the approved source snapshot. Slice 0 must record
  whether it is an official production publication or an Entio-approved master
  snapshot; documentation must not call it a production release without that
  evidence.
- The search implementation stays inside `semantic-engine`.
- Lucene supplies embedded BM25 lexical index mechanics only.
- Dense vectors are stored in canonical-IRI order and searched by exact cosine
  scan. Approximate nearest-neighbor search is not justified for the current
  corpus.
- ONNX Runtime supplies local JVM inference with the Slice 0-selected,
  revision-pinned `all-MiniLM-L6-v2` model and verified tokenizer, mean-pooling,
  L2-normalization, 256-token, and 384-dimension contract.
- No project content crosses an external embedding boundary.
- The active profile is serialized in `.entio/domain-profile.yaml`; existing
  hand-authored `entio.yaml` is not rewritten by activation.
- Reused statements live in a managed project Turtle source.
- Operational provenance lives in a project-relative JSONL sidecar.
- `skos:closeMatch` and `skos:relatedMatch` are the only Phase 13 annotation
  mapping actions. They do not affect OWL reasoning and do not require target
  materialization by themselves.
- The current proposal/apply workflow remains the ontology write path.
- Compatibility adapters isolate document and assistant behavior from hybrid
  ranking.

## Affected Modules And Files

Paths below are the expected maximum production surface. Before creating a new
file, search for an existing class with the same responsibility and extend it
when that remains focused.

### Documentation and decisions

- `docs/specs/0024-phase-13-domain-ontology-first-modeling-full-corpus-retrieval.md`
- `docs/execplans/0024-phase-13-domain-ontology-first-modeling-full-corpus-retrieval.md`
- `docs/decisions/phase-13-local-hybrid-retrieval.md`
- `docs/decisions/phase-13-project-domain-profile-persistence.md`
- `docs/decisions/phase-13-editable-external-iri-reuse.md`
- Phase 13 slice completion records
- `docs/phase-summaries/phase-13-summary.md` only after all acceptance criteria
  pass
- `README.md` and `AGENTS.md` only in the final documentation slice

### FIBO and retrieval assets

- read-only use of `external-ontologies/fibo/` without modifying its manifest,
  catalog metadata, checksum ledger, indexes, source files, or dependencies
- `external-ontologies/domain-search/fibo/master_2026Q2/manifest.yaml`
- `external-ontologies/domain-search/fibo/master_2026Q2/ATTRIBUTION.md`
- `external-ontologies/domain-search/fibo/master_2026Q2/checksums/`
- `external-ontologies/domain-search/fibo/master_2026Q2/descriptors/`
- `external-ontologies/domain-search/fibo/master_2026Q2/lexical-index/`
- `external-ontologies/domain-search/fibo/master_2026Q2/vectors/`
- an audited local model asset directory under
  `external-ontologies/domain-search/models/` only if Slice 0 approves committed
  distribution
- model license and NOTICE files

The complete Phase 5 package is a read-only input and must not be modified.

### `core-types`

- `core-types/src/main/kotlin/com/entio/core/EntioProject.kt`
- `core-types/src/main/kotlin/com/entio/core/Phase5Contracts.kt` only for
  compatible deprecation adapters
- new focused contracts, preferably
  `core-types/src/main/kotlin/com/entio/core/Phase13DomainOntologyContracts.kt`
- existing semantic and typed-edit contracts only where a narrowly missing
  operation is proven
- matching tests under `core-types/src/test/kotlin/com/entio/core/`

### `semantic-engine`

- `semantic-engine/build.gradle.kts`
- `semantic-engine/src/main/kotlin/com/entio/semantic/ProjectConfigLoader.kt`
- a focused `DomainProfileRepository.kt`
- a focused `DomainProfileService.kt`
- read-only adapters over `FiboPackageVerifier.kt` and `FiboCatalogLoader.kt`;
  current Phase 5 generation and verification output must remain unchanged
- a focused `DomainSearchIndexGenerator.kt`
- a focused `DomainSearchIndexVerifier.kt`
- a focused `LocalSentenceEmbeddingService.kt`
- a focused `DomainRecommendationService.kt`
- a focused `DomainFoundationService.kt`
- `ExternalDependencyReviewer.kt`
- a focused `DomainReuseProposalPreparer.kt`
- a focused `DomainReuseProposalTranslator.kt`; the existing
  `ExternalProposalPreparer.kt` and `ExternalProposalIntentTranslator.kt`
  retain their Phase 5 behavior
- a focused `DomainReuseDescriptionService.kt`
- a focused `DomainReuseProvenanceRepository.kt`
- `TypedOntologyEditTranslator.kt` only for proven missing typed removal or
  mapping behavior
- `MultiSourceAtomicApplier.kt` or `ProposalApplier.kt` only if the approved
  atomic provenance ADR requires a narrow reusable hook
- `SemanticDescriptionService.kt` only for unified local/domain result
  adaptation
- matching tests under `semantic-engine/src/test/kotlin/com/entio/semantic/`

No FIBO, recommendation, or embedding logic belongs in `shared`.

### `web-server`

- `web-server/src/main/kotlin/com/entio/web/Application.kt`
- `web-server/src/main/kotlin/com/entio/web/FiboWebService.kt` for compatibility
- `web-server/src/main/kotlin/com/entio/web/ReadOnlyProjectAdapters.kt`
- `web-server/src/main/kotlin/com/entio/web/StagingWorkflowService.kt`
- a focused `DomainOntologyWebService.kt`
- a focused `DomainRecommendationWebService.kt`
- focused web DTOs under
  `web-server/src/main/kotlin/com/entio/web/contract/`
- `LoadedProjectCache.kt` only for configuration-fingerprint invalidation
- project registry/idempotency files only for existing authorization patterns
- applied-domain-provenance coordination in a focused non-ingestion package
- matching tests under `web-server/src/test/kotlin/com/entio/web/`

Document-ingestion and AI files are forbidden except for explicit compatibility
tests or a minimal adapter proven necessary by Slice 10.

### `web-app`

- `web-app/src/web/projectApi.ts`
- `web-app/src/web/queries.ts`
- `web-app/src/web/contracts.ts` when shared contract assertions require it
- `web-app/src/workbench/ExternalOntologyPanel.tsx` and tests, replaced or
  adapted as domain administration
- a focused `DomainOntologySettings.tsx` and tests
- a focused reusable `DomainRecommendationPanel.tsx` and tests
- `ProjectWorkspace.tsx`
- `ContextualEditing.tsx` and tests
- `EntityDetails.tsx` and tests
- `SemanticClassPicker.tsx`
- `SemanticEntityPicker.tsx`
- `StagingPanel.tsx` and tests
- existing SHACL, reasoning, and ontology-map components only in their assigned
  integration slices
- `web-app/e2e/workbench.spec.ts` and approved snapshots

The React surface must not acquire RDF, ranking, fingerprint, or dependency
policy.

### `cli`

- `cli/src/main/kotlin/com/entio/cli/ExternalCatalogCommands.kt`
- a focused `DomainOntologyCommands.kt` if extending the existing file would
  mix unrelated parsing
- command routing and machine-readable serializers
- matching tests under `cli/src/test/kotlin/com/entio/cli/`

### `vscode-extension`

- `vscode-extension/src/externalWorkbench.ts`
- `vscode-extension/src/workbenchModel.ts`
- matching tests under `vscode-extension/src/test/`
- package metadata only if command labels or contributions change

## Implementation Slices

## Slice 0: Contract, Dependency, License, Atomicity, And Benchmark Audit

### Goal

Resolve every blocking open question before production code, dependencies,
model binaries, or generated indexes are added.

### Allowed files/modules

- `docs/specs/0024-phase-13-domain-ontology-first-modeling-full-corpus-retrieval.md`
- this ExecPlan
- new Phase 13 ADRs under `docs/decisions/`
- temporary untracked benchmark/audit work outside committed source
- read-only inspection of all repository files

### Forbidden actions/modules

- no production Kotlin or TypeScript changes;
- no Gradle dependency changes;
- no committed model or generated binary assets;
- no FIBO source modifications;
- no web routes or UI work;
- no implementation branch reported as feature progress.

### Expected changes or output

Approve and record:

1. Whether `master_2026Q2` is an official production publication or an
   Entio-approved master snapshot, plus the exact terminology used in the UI.
2. Exact FIBO-authored and OMG Commons entity counts, `sourceFamily` rules, and
   whether Commons results appear by default or only through FIBO context.
3. Exact Lucene Core and Analysis Common versions for BM25 only.
4. Exact ONNX Runtime CPU version and supported development platforms,
   including an explicit Apple Silicon decision.
5. A measured comparison of `all-MiniLM-L6-v2` and at least one other locally
   runnable, permissively licensed candidate when one passes the initial audit.
6. The selected model revision, ONNX artifact, tokenizer files, token limit,
   pooling, normalization, vector dimension, checksums, license, and NOTICE
   requirements.
7. A proof-of-contract local Java 21 inference result compared with the selected
   model's approved reference vector within a documented tolerance.
8. Exact-scan vector storage format, canonical-IRI ordering, numerical
   normalization, and stable tie-breaking.
9. Model and Phase 13 asset packaging choice, separate manifest, and measured
   size without modifying Phase 5 manifest or catalog metadata.
10. The `.entio/domain-profile.yaml` schema, fixed managed/provenance paths,
    profile commit-point transaction, orphan cleanup, and crash recovery.
11. The JSONL provenance schema, source snapshot bounds, transaction journal,
    and atomic ontology/provenance recovery protocol.
12. The existing typed operation used for statement removal and IRI-valued
    annotation mapping, or the exact narrowly scoped contract addition needed.
13. `CompleteSupportedMaterialization`, `PartialMaterialization`, and
    `UnsupportedForReuse` rules for every copied or omitted OWL construct.
14. Verification that the initial dependency bounds are `20` explicit
    selections, `100` closure entities, `2,000` generated RDF statements, `2
    MiB` of preview data, depth `16`, and `10 seconds`, including measured
    behavior when one selection exceeds a bound. Any change requires measured
    evidence and an approved spec amendment.
15. Benchmark ownership; development, regression, and locked acceptance sets;
    relevance-judgment rules; baseline Phase 5 scores; and confidence-band
    calibration.
16. Verification of the initial `30 minute` TTL, `500` recommendation records
    and `50` frozen plans per project/user scope, expired-first then oldest-
    sequence eviction, restart invalidation, and cleanup behavior. Any change
    requires an approved spec amendment.
17. Concrete supported-machine description for performance acceptance.
18. A complete workflow integration matrix mapping each authoring workflow to
    intent, context, UI component, slice, and required test.
19. A complete current-contract inventory for CLI, VS Code, web, assistant, and
    document ingestion, including byte-for-byte Phase 12 fingerprint baselines.

Required ADRs:

- local hybrid retrieval and model distribution;
- project domain-profile persistence;
- editable external-IRI reuse and atomic provenance.

### Approved Slice 0 resolution

The approved values and evidence are recorded in the three required ADRs and
`docs/decisions/phase-13-slice-0-contract-audit.md`. Those documents are
normative where this slice previously named an open choice. In particular:

- the selected versions are Lucene `10.5.0`, ONNX Runtime CPU `1.28.0`, DJL
  tokenizers/API `0.36.0`, and the pinned unquantized `all-MiniLM-L6-v2` ONNX
  artifact;
- full-mode performance acceptance is macOS ARM64/Apple Silicon on Temurin
  Java 21, with explicit lexical-structural degradation when approved natives
  are unavailable;
- the model and generated Phase 13 assets are committed beneath the separate
  `external-ontologies/domain-search/` manifest and must remain under `250
  MiB`;
- project/profile and ontology/provenance transactions use the fixed journal
  and recovery rules in the ADRs;
- no new typed removal or raw-RDF operation is authorized;
- locked relevance judgments are two-reviewer approved and cannot tune weights
  or confidence thresholds; the completed hybrid service is the quality gate;
- Phase 5 and Phase 12 files and fingerprints are compatibility baselines, not
  inputs to be rewritten.

### Tests

- standalone Java 21 model smoke test;
- tokenizer/reference-vector comparison;
- temporary full-corpus descriptor and size probe;
- FIBO-versus-OMG-Commons source-family inventory;
- Phase 13 separate-asset proof that leaves Phase 5 manifest and metadata
  unchanged;
- exact-scan vector performance probe;
- development/regression/locked benchmark corpus audit;
- current FIBO verifier;
- current FIBO, document retrieval, and assistant compatibility baselines.

### Verification commands

```bash
./gradlew :semantic-engine:verifyFiboCatalog
./gradlew :semantic-engine:test --tests '*Fibo*'
./gradlew :web-server:test --tests '*Document*Retrieval*'
./gradlew :web-server:test \
  --tests 'com.entio.web.ai.OpenAiProposalClientTest.asksTheModelForFocusedExternalContextWhenNeeded' \
  --tests 'com.entio.web.AiProposalWorkflowTest.followUpCanReviseAndRetractPrivateDraftEdits'
git diff --check
```

Temporary audit commands must be copied into the completion record with their
versions and results.

The original planning filter `*Ai*Fibo*` matched no test because the existing
assistant/FIBO compatibility behavior is named by intent rather than by the
word `Fibo`. Slice 0 replaces that invalid filter with the two exact existing
tests above; it does not weaken or add compatibility behavior.

### Stop conditions

Stop and amend the spec before Slice 1 if:

- local inference requires a custom tokenizer or unapproved native build;
- license or NOTICE obligations cannot be met;
- model plus index exceeds `250 MiB` without an approved release-artifact plan;
- Java 21 or required development platforms are unsupported;
- project text would need to leave the local process;
- atomic provenance would require a second ontology apply path;
- supported statement removal cannot be expressed safely;
- Phase 13 assets change Phase 5 manifest, catalog metadata, retrieval ordering,
  or work-key fingerprints;
- exact vector scan cannot meet the approved latency on the measured corpus;
- no locally runnable embedding candidate can participate in a completed
  hybrid service that meets the locked acceptance set;
- the pinned FIBO package is not actually the complete approved source needed
  by the spec;
- acceptance thresholds are infeasible on measured baseline hardware.

## Slice 1: Domain Contracts, Profile Loading, And Transaction Primitives

### Goal

Add typed domain-profile contracts, load the optional
`.entio/domain-profile.yaml`, and implement tested transaction primitives. Do
not expose or apply activation yet because the verified Phase 13 asset package
does not exist until Slices 2 and 3.

### Allowed files/modules

- `core-types` domain-profile contracts and `EntioProject.kt`;
- `semantic-engine` focused domain-profile repository, loader integration,
  transaction journal, and domain profile service;
- small copied project fixtures;
- matching unit tests;
- Slice 1 completion record.

### Forbidden actions/modules

- no web routes or React changes;
- no search dependency or vector work;
- no reuse materialization;
- no modification of existing ontology sources during activation;
- no assistant or document changes;
- no arbitrary project-file writer.

### Expected changes or output

- `DomainOntologyProfile`, availability, activation preview, deactivation
  preview, migration status, and structured issue types;
- loader support for an absent or exact approved domain-profile sidecar;
- safe project-relative managed and provenance path validation;
- deterministic profile serialization without rewriting `entio.yaml`;
- fixed managed/provenance path derivation;
- transaction primitives using temporary files, verified hashes, a journal,
  the profile file as commit point, orphan-empty-source cleanup, and recovery;
- activation/deactivation preview data without public apply wiring;
- blocked deactivation reasons;
- no change to inactive-project semantic graphs.

### Tests

- absent, valid, malformed, unsupported, stale, and unsafe profiles;
- serialization round trip and stable output;
- preview makes no changes;
- profile repository round trip;
- prepared activation creates only temporary artifacts;
- simulated transaction failure restores or recovers all files;
- orphan empty-source cleanup;
- deactivation eligibility calculation without public mutation;
- copied fixture preservation.

### Verification commands

```bash
./gradlew :core-types:test
./gradlew :semantic-engine:test --tests '*ProjectConfig*'
./gradlew :semantic-engine:test --tests '*DomainProfile*'
./gradlew :semantic-engine:build
git diff --check
```

### Stop conditions

Stop if activation requires rewriting `entio.yaml`, if transaction recovery is
ambiguous, if managed paths can escape the project, or if project loading
changes when the profile is absent.

## Slice 2: Full-Corpus Descriptor And Foundation Assets

### Goal

Generate and verify Phase 13 semantic records and the reviewed foundation
profile from the complete pinned package without adding vector retrieval yet.

### Allowed files/modules

- `semantic-engine` read-only FIBO loader adapters and focused Phase 13
  descriptor/foundation generator and verifier;
- `external-ontologies/domain-search/fibo/master_2026Q2/` text manifest,
  attribution, descriptors, foundation profile, and checksums;
- semantic-engine tests and benchmark fixtures;
- Slice 2 completion record.

### Forbidden actions/modules

- no changes to FIBO or Commons source files;
- no changes to the Phase 5 manifest, catalog metadata, checksum ledger, or
  generated indexes;
- no model binaries or ONNX Runtime;
- no runtime web download;
- no UI, web route, reuse, or proposal work;
- no independent RDF parser.

### Expected changes or output

- every eligible class/object/datatype property represented once;
- every record labeled `FIBO` or `OMG_COMMONS` through a verified
  `sourceFamily` contract;
- bounded descriptor text and graph context derived through existing semantic
  contracts;
- reviewed eight-group foundation profile;
- deterministic record and dependency fingerprints;
- release/provisional/deprecated/informative classification;
- generator and verifier extensions;
- exact corpus counts recorded from generated output.

### Tests

- full entity traversal and exact identity deduplication;
- entity-kind correctness;
- descriptor label, definition, hierarchy, domain, range, source, and maturity;
- foundation group membership and order;
- unsupported construct reporting;
- deterministic regeneration;
- checksum and stale-record failure.
- byte-for-byte unchanged Phase 5 manifest, catalog metadata, catalog ordering,
  and Phase 12 FIBO fingerprint.

### Verification commands

```bash
./gradlew :semantic-engine:generateFiboCatalog
./gradlew :semantic-engine:verifyFiboCatalog
./gradlew :semantic-engine:test --tests '*Fibo*'
./gradlew :semantic-engine:test --tests '*DomainFoundation*'
git diff --check
```

### Stop conditions

Stop if eligible entities cannot be traced to verified source statements, if
full-corpus generation relies on the network, if foundation membership is not
reviewed/versioned, or if generated records redefine ontology semantics outside
the existing semantic engine. Also stop if any Phase 5 asset or Phase 12
retrieval fingerprint changes.

## Slice 3: Local Embeddings And Hybrid Index

### Goal

Add the Slice 0-selected local inference dependencies and reproducibly
generate/load the BM25 index plus canonical-IRI-ordered vectors for exact cosine
scan.

### Allowed files/modules

- `semantic-engine/build.gradle.kts` with only Slice 0-approved dependencies;
- focused local embedding and domain-index generator/verifier classes;
- approved model/tokenizer/license assets;
- `external-ontologies/domain-search/fibo/master_2026Q2/` generated search
  assets;
- semantic-engine tests and benchmark harness;
- Slice 3 completion record.

### Forbidden actions/modules

- no hosted model or network runtime;
- no Python production runtime;
- no custom tokenizer, approximate-nearest-neighbor index, vector database, or
  new Gradle module;
- no user/project content in build artifacts;
- no web or UI changes;
- no semantic eligibility or proposal work.

### Expected changes or output

- local bounded text-to-vector service with explicit lifecycle and resource
  cleanup;
- verified tokenizer, pooling, and normalization;
- Lucene BM25 fields keyed by canonical IRI;
- exact-scan vectors stored in canonical-IRI order with verified dimensions and
  normalization;
- index manifest and checksums;
- offline generation and verification tasks;
- safe load failure and lexical-only load mode;
- no core-engine logging of raw input text.

### Tests

- reference embedding tolerance;
- truncation at approved token limit;
- blank/oversized input rejection;
- deterministic same-platform results;
- vector dimension and normalization;
- lexical and vector exact-identity joins;
- exact cosine ordering and stable ties;
- corrupt/missing/wrong-model index rejection;
- resource closure and concurrent query safety;
- offline runtime test.

### Verification commands

```bash
./gradlew :semantic-engine:generateDomainSearchIndex
./gradlew :semantic-engine:verifyDomainSearchIndex
./gradlew :semantic-engine:test --tests '*LocalSentenceEmbedding*'
./gradlew :semantic-engine:test --tests '*DomainSearchIndex*'
./gradlew :semantic-engine:build
git diff --check
```

### Stop conditions

Stop if runtime performs a network call, if native loading fails on an approved
platform, if model identity cannot be verified, if exact scan misses the
approved latency, if vectors are accepted as ontology truth, if asset size
exceeds the approved plan, or if lexical mode cannot operate independently.

## Slice 4: Kotlin Domain Recommendation Service

### Goal

Implement structured intent, hybrid candidate union, hard eligibility,
ontology/graph/project reranking, explanations, recommendation identity, and
degraded behavior in `semantic-engine`.

### Allowed files/modules

- `core-types` Phase 13 intent/result contracts;
- focused semantic-engine recommendation service;
- current semantic description, FIBO loader, and dependency services only for
  narrow reusable adapters;
- benchmark fixtures and semantic-engine tests;
- Slice 4 completion record.

### Forbidden actions/modules

- no Ktor, React, CLI, or VS Code work;
- no proposal staging or writes;
- no AI or document integration;
- no client-supplied trusted scores or vectors;
- no embedding-based validation.

### Expected changes or output

- fixed operation-kind and availability enums;
- explicit FIBO/OMG Commons source-family presentation;
- bounded `DomainModelingIntent`;
- lexical top 100, vector top 100, canonical union, and stable deduplication;
- hard eligibility for source, kind, domain/range, dependency, profile, and
  freshness;
- `domain-ranking-v1` normalized weights and tie-breaking;
- structured match reasons, warnings, confidence bands, and permitted actions;
- stable recommendation IDs bound to frozen fingerprints;
- bounded in-memory recommendation and plan state using Slice 0-approved TTL,
  capacity, deterministic eviction, restart invalidation, and cleanup rules;
- default 10 and broad 50 bounds;
- full and lexical-structural behavior;
- separate development-set tuning report, regression-set results, and untouched
  locked acceptance-set report with Phase 5 baseline comparison.

### Tests

- every retrieval and quality case in the spec;
- hard kind and structural incompatibility;
- graph and already-reused reranking;
- deprecated/informative behavior;
- exact explanation-to-feature correspondence;
- no-result and low-confidence behavior;
- stable ordering and IDs;
- TTL, capacity, eviction, restart, and cleanup behavior;
- stale fingerprints;
- full/degraded equivalence where vector signals are not needed;
- performance microbenchmarks separated from unit-test pass/fail timing.

### Verification commands

```bash
./gradlew :semantic-engine:test --tests '*DomainRecommendation*'
./gradlew :semantic-engine:test --tests '*DomainRetrievalBenchmark*'
./gradlew :semantic-engine:verifyDomainSearchIndex
./gradlew :semantic-engine:build
git diff --check
```

### Stop conditions

Stop if hard incompatibility can be overcome by score, explanations contain
unverified claims, repeated frozen inputs reorder, ranking is tuned against the
locked acceptance set, benchmark quality misses the approved gates, or
recommendation policy leaks into clients.

## Slice 5: Web Profile, Foundation, And Recommendation Read Contracts

### Goal

Expose authorized profile, foundation, search, and recommendation reads plus
previewed activation/deactivation through Ktor. This slice is the first point
at which profile activation may be applied because verified Phase 13 assets now
exist. Semantic logic remains in Kotlin.

### Allowed files/modules

- `web-server` domain services, DTOs, routes, cache invalidation, idempotency,
  and tests;
- semantic-engine profile and recommendation services only for narrow adapter
  fixes;
- `core-types` only for proven contract omissions;
- Slice 5 completion record.

### Forbidden actions/modules

- no React or VS Code changes;
- no ontology reuse staging;
- no assistant/document route changes;
- no raw vector endpoints;
- no arbitrary source/release/path mutation;
- no second search implementation in Ktor.

### Expected changes or output

- spec-defined `/domain-ontologies`, profile, activation, foundation plan,
  recommendation, detail, and migration read routes needed by later slices;
- server-issued activation, plan, selection, and recommendation IDs;
- project/user authorization and idempotency;
- request/result/concurrency bounds and cancellation;
- configuration reload and cache invalidation after activation;
- transactional activation using empty managed source preparation followed by
  atomic profile replacement as the commit point;
- transactional deactivation, orphan cleanup, and startup recovery;
- degraded-mode and structured error mapping;
- no raw project text in default logs.

### Tests

- route contracts and version fields;
- authorization and unknown project;
- activation preview versus apply;
- activation adds no RDF statements and does not rewrite `entio.yaml`;
- crash/failure recovery at every transaction step;
- stale activation token;
- foundation plan paging and batching;
- recommendation bounds and cancellation;
- cross-project/user ID rejection;
- unavailable/degraded mapping;
- no mutation from read routes;
- safe errors without filesystem/model leakage.

### Verification commands

```bash
./gradlew :web-server:test --tests '*DomainOntology*'
./gradlew :web-server:test --tests '*DomainRecommendation*'
./gradlew :web-server:test --tests '*WebContract*'
./gradlew :web-server:build
git diff --check
```

### Stop conditions

Stop if Ktor calculates semantic rank/dependencies, if clients can select an
unapproved release/path/IRI, if activation lacks the approved observable-
atomic transaction and recovery behavior, if `entio.yaml` is rewritten, or if
route state is not invalidated after project reload.

## Slice 6: Managed Reuse, Customization, Mapping, And Provenance

### Goal

Materialize selected entities into the managed project source, support
customization/extension/mapping, lock canonical IRIs, and commit provenance
atomically through the existing proposal path.

### Allowed files/modules

- `core-types` focused action, snapshot, difference, and provenance contracts;
- semantic-engine dependency, proposal, typed edit, source description,
  provenance, and approved apply-hook files;
- web-server staging adapter and focused provenance coordinator;
- copied project fixtures and tests;
- Slice 6 completion record.

### Forbidden actions/modules

- no direct FIBO asset write;
- no second proposal/apply route;
- no raw RDF from clients;
- no automatic equivalence;
- no unsupported anonymous OWL materialization;
- no UI integration;
- no document/assistant changes.

### Expected changes or output

- reuse, reuse-and-customize, local extension, close/related mapping, and local
  continuation actions;
- a new Phase 13 reuse translator that leaves existing Phase 5 external
  translator behavior unchanged;
- deterministic supported statement materialization classified as complete,
  partial, or unsupported;
- explicit display and approval of omitted source axioms for partial
  materialization;
- explicit dependency classification and stable batch planning;
- limits of `20` explicit selections, `100` closure entities, `2,000` generated
  statements, `2 MiB` of preview data, depth `16`, and `10 seconds`, with no
  silent truncation;
- managed-source target enforcement;
- canonical IRI lock;
- source-versus-project difference service;
- annotation-only versus logical customization classification;
- JSONL provenance store with checksums and bounded snapshots;
- atomic ontology/provenance prepare, commit, rollback, and recovery behavior;
- removal event and dependency-safe reuse deletion;
- normal validation, diff, round-trip, stale baseline, reload, and rollback.

### Tests

- class/object/datatype reuse;
- labels, definitions, hierarchy, domain, and range customization;
- canonical IRI mutation rejection;
- unsupported source construct blocking;
- recursive dependency cycles and deterministic order;
- 20-selection batch limit with dependency expansion;
- local subclass/subproperty;
- only two approved SKOS IRI-valued annotation mappings, with no reasoning
  effect and no mandatory target materialization;
- source/project differences;
- provenance append, corruption, prepare failure, commit failure, rollback, and
  crash-recovery contract;
- source package immutability;
- existing proposal regression suite;
- exact unchanged output from current `ExternalProposalIntentTranslator` cases.

### Verification commands

```bash
./gradlew :core-types:test --tests '*Phase13*'
./gradlew :semantic-engine:test --tests '*DomainReuse*'
./gradlew :semantic-engine:test --tests '*External*Proposal*'
./gradlew :semantic-engine:test --tests '*Atomic*Applier*'
./gradlew :web-server:test --tests '*DomainReuse*'
./gradlew :web-server:test --tests '*Staging*'
./gradlew :semantic-engine:verifyFiboCatalog
git diff --check
```

### Stop conditions

Stop if canonical IRIs can change, source and project meaning are silently
merged, partial materialization is presented as complete, provenance can diverge
from applied ontology, unsupported OWL is copied, dependency work is silently
truncated, Phase 5 translator output changes, or implementation requires a
parallel apply workflow.

## Slice 7: Domain Settings, Foundation, And Unified Explore Search

### Goal

Replace the human-facing always-present external panel with optional domain
settings, foundation selection, source/project details, and unified Explore
search.

### Allowed files/modules

- `web-app` API/query types and focused domain components;
- `ExternalOntologyPanel.tsx` only for migration or removal;
- `ProjectWorkspace.tsx`, Explore/search components, and tests;
- Ktor DTO/adapter corrections only when client contract tests reveal a spec
  mismatch;
- E2E fixture handlers and approved snapshots;
- Slice 7 completion record.

### Forbidden actions/modules

- no client ranking, dependency closure, or RDF generation;
- no contextual authoring forms yet;
- no assistant/document changes;
- no unconfirmed activation;
- no direct `entio.yaml` or ontology writes.

### Expected changes or output

- None/FIBO settings and exact release display;
- activation/deactivation preview and confirmation;
- foundation group/member browsing, select all, group, and individual planning;
- stable batch progress and partial completion display;
- full-corpus broad search;
- unified locality/status labels in Explore;
- explicit `FIBO`, `OMG Commons`, local, imported, and project-reuse source
  badges;
- manual domain-catalog browsing while inactive, without contextual
  recommendations or actionable reuse until activation;
- project versus FIBO source description and differences;
- degraded/unavailable status;
- accessible loading, empty, no-match, error, and stale states.

### Tests

- inactive and active project rendering;
- activation confirmation and failure restoration response;
- foundation selection and plan batches;
- unsupported/conflicting member display;
- unified local/imported/reused/customized/available results;
- source/project details;
- degraded and unavailable behavior;
- keyboard/focus/accessibility behavior;
- no API call loops or stale result rendering.

### Verification commands

```bash
cd web-app
npm test -- --runInBand
npm run build
npm run test:e2e
cd ..
./gradlew :web-server:test --tests '*DomainOntology*'
git diff --check
```

If the configured test runner does not support `--runInBand`, use the existing
repository `npm test` command and record the exact command.

### Stop conditions

Stop if activation lacks an exact file-change preview, if Select all silently
drops work, if available FIBO results appear applied, or if React derives
semantic compatibility. Also stop if Commons results are presented as FIBO.

## Slice 8: Core Authoring Recommendations

### Goal

Integrate the shared recommendation panel into class, property, individual,
annotation, hierarchy, domain, range, datatype, assertion, and value authoring.

### Allowed files/modules

- `web-app` contextual editing, entity details, semantic pickers, reusable
  recommendation panel, API/query hooks, and focused tests;
- web-server request-context adapter only for operation-specific server
  resolution;
- semantic-engine intent adapter only for missing operation context;
- E2E tests and Slice 8 completion record.

### Forbidden actions/modules

- no duplicate ranking code per form;
- no browser-owned domain/range compatibility;
- no automatic form replacement or staging;
- no deletion, proposal-review, SHACL, map, or reasoning integration yet;
- no assistant/document changes.

### Expected changes or output

- 300 ms debounced bounded requests;
- create-class recommendations;
- object/datatype property recommendations with strict kind handling;
- individual type and applicable-property recommendations;
- local/reused label and definition comparison;
- superclass/superproperty recommendations;
- domain/range/datatype recommendations;
- assertion property/target recommendations;
- explicit reuse/customize/extend/map/local/dismiss actions;
- regenerated server preview after action change;
- cancelled or ignored superseded requests;
- no recommendations when profile inactive.
- an implementation-completeness matrix checked by tests for every operation
  kind, context field, UI component, slice, and acceptance case.

### Tests

- each operation kind and context mapping;
- property cross-kind suppression;
- domain/range context update;
- debounce, cancellation, and stale response handling;
- low-confidence collapsed state;
- explicit action and form preservation;
- inactive/degraded/unavailable behavior;
- source/project customization advisory;
- local action remains available.

### Verification commands

```bash
cd web-app
npm test
npm run build
cd ..
./gradlew :web-server:test --tests '*DomainRecommendation*'
./gradlew :semantic-engine:test --tests '*DomainRecommendation*'
git diff --check
```

### Stop conditions

Stop if recommendations cause silent edit replacement, typing produces
unbounded requests, form context is trusted without server resolution, or one
form needs a contradictory ranking implementation.

## Slice 9: Deletion, Proposal Review, SHACL, Map, And Reasoning Integration

### Goal

Complete the remaining specified human-driven integrations without changing
deterministic deletion, SHACL, graph, or reasoning semantics.

### Allowed files/modules

- `web-app` deletion/dependency review, staging panel, SHACL authoring,
  ontology-map shell, reasoning workspace, and focused tests;
- web-server adapters for the fixed operation kinds;
- semantic-engine intent context adapters only where necessary;
- E2E tests and Slice 9 completion record.

### Forbidden actions/modules

- no embedding-based deletion validation;
- no inferred-edge creation from retrieval;
- no FIBO nodes in asserted layout before apply;
- no SHACL validity decision in React;
- no assistant/document changes;
- no automatic replacement or materialization.

### Expected changes or output

- optional replacement/broader-parent recommendations in deletion review;
- domain-reuse check for new/substantially changed staged content;
- reviewer conversion to supported domain action with regenerated preview;
- SHACL target/path/class/datatype recommendations;
- bounded related-FIBO result panel from ontology-map nodes;
- bounded related-FIBO search from asserted/inferred reasoning facts;
- clear available-versus-applied visual treatment.

### Tests

- deletion dependency preservation and optional replacement;
- proposal freshness after action conversion;
- SHACL selectors and unchanged validation output;
- map asserted-layout invariance;
- inferred fact remains read-only;
- available domain result never enters project graph before apply;
- inactive and degraded modes;
- existing Phase 9, 10, and 10.5 regressions.

### Verification commands

```bash
cd web-app
npm test
npm run build
npm run test:e2e
cd ..
./gradlew :web-server:test --tests '*Shacl*'
./gradlew :web-server:test --tests '*OntologyGraph*'
./gradlew :web-server:test --tests '*Reasoning*'
./gradlew :semantic-engine:test --tests '*Shacl*'
./gradlew :semantic-engine:test --tests '*Reasoning*'
git diff --check
```

### Stop conditions

Stop if retrieval changes deterministic validation/reasoning results, if map
layout treats available FIBO as asserted, or if review conversion bypasses a new
server preview.

## Slice 10: CLI, VS Code, Assistant, And Document Compatibility

### Goal

Migrate machine-readable external contracts, provide minimum VS Code profile
awareness, and prove assistant/document isolation.

### Allowed files/modules

- CLI external/domain commands and tests;
- VS Code external workbench/model and tests;
- `FiboWebService` compatibility adapters;
- document and assistant files only if a minimal compatibility adapter is
  unavoidable and explicitly documented before editing;
- existing document/assistant tests and Slice 10 completion record.

### Forbidden actions/modules

- no Phase 13 vector or recommendation call from document ingestion;
- no Phase 13 assistant tool or prompt expansion;
- no VS Code semantic ranking;
- no full VS Code contextual parity project;
- no removal of old routes before compatibility tests pass.

### Expected changes or output

- machine-readable profile, foundation, search, recommendation, detail,
  dependency, and proposal commands;
- thin CLI delegation;
- VS Code inactive/active profile status and full-corpus external browsing;
- source/project status where supported;
- old external routes marked deprecated but functional;
- old external routes retained for the complete Phase 13 lifecycle; their
  removal is explicitly outside Phase 13;
- byte/semantic baseline comparison for Phase 12 ordered choices and work keys;
- assistant context response compatibility;
- documented adapter removal point for a future phase.

### Tests

- CLI JSON contracts and exit codes;
- VS Code rendering, selection, and staging delegation;
- no FIBO display as selected when profile inactive;
- Phase 12 exact retrieval ordering and frozen work key;
- assistant bounded context shape and count;
- no model/index objects exposed to either excluded flow;
- old route authorization and bounds.

### Verification commands

```bash
./gradlew :cli:test
./gradlew :web-server:test --tests '*Document*'
./gradlew :web-server:test --tests '*Ai*'
cd vscode-extension
npm test
cd ..
git diff --check
```

### Stop conditions

Stop if document work keys change, assistant prompts or tool authority expand,
old clients lose supported behavior without migration, or TypeScript begins
calculating semantic policy.

## Slice 11: Existing-Project Migration

### Goal

Detect existing FIBO use, offer safe explicit migration previews, and preserve
all applied ontology meaning and open-work semantics.

### Allowed files/modules

- semantic-engine migration detector and preview service;
- web-server migration routes/adapters;
- React domain settings migration presentation;
- CLI migration diagnostics;
- copied legacy fixtures and tests;
- Slice 11 completion record.

### Forbidden actions/modules

- no automatic activation;
- no inferred historical release without exact evidence;
- no automatic statement movement;
- no deletion of old provenance or proposals;
- no release upgrade implementation;
- no unrelated project-config migration.

### Expected changes or output

- `NoExistingReuse`, `ExistingReuseRecognized`, `ExistingReuseAmbiguous`, and
  `ExistingReuseUnsupported` detection;
- explicit profile activation/migration preview;
- no RDF move until a separate normal proposal is approved;
- deterministic source/provenance seeding only when evidence is complete;
- stale open work handled through existing baseline behavior;
- inactive historical record retention;
- copied-fixture migration report.

### Tests

- project with no FIBO use;
- direct FIBO class/property assertions;
- local extension of FIBO;
- recognized and ambiguous releases;
- applied, staged, proposed, rejected, and rolled-back historical work;
- no semantic graph change from migration preview or profile activation;
- rollback after migration configuration failure.

### Verification commands

```bash
./gradlew :semantic-engine:test --tests '*DomainMigration*'
./gradlew :web-server:test --tests '*DomainMigration*'
./gradlew :cli:test --tests '*DomainMigration*'
cd web-app
npm test
cd ..
git diff --check
```

### Stop conditions

Stop if migration changes RDF without a normal proposal, invents provenance,
automatically activates existing projects, or invalidates open work without a
clear structured status.

## Slice 12: Quality, Performance, Security, Full Regression, And Documentation

### Goal

Prove every acceptance criterion, complete documentation, and publish the Phase
13 summary only after the implementation is verified.

### Allowed files/modules

- benchmark fixtures and tests;
- security, concurrency, and performance tests;
- focused production fixes only within files already authorized by prior
  slices;
- `README.md`, `AGENTS.md`, Phase 13 ADR completion records, and
  `docs/phase-summaries/phase-13-summary.md` after verification;
- approved E2E snapshots.

### Forbidden actions/modules

- no new feature behavior;
- no threshold weakening without spec amendment;
- no skipped failing checks;
- no summary claiming completion before all required commands pass;
- no removal of compatibility adapters unless already authorized and tested.

### Expected changes or output

- final benchmark corpus and report;
- measured generation, load, inference, warm, broad, memory, size, and
  concurrency results;
- security and privacy review;
- package/model/index license and checksum verification;
- complete regression evidence;
- updated repository status and architecture links;
- Phase 13 implementation summary with exact commands and results.

### Tests

- every spec test category;
- quality thresholds in full and degraded mode;
- supported-platform local inference smoke tests;
- eight concurrent recommendation requests;
- path traversal, cross-project IDs, stale IDs, oversized text, corrupt assets,
  unsafe RDF text rendering, and log redaction;
- activation/apply/provenance fault injection;
- existing CLI, VS Code, web, reasoning, SHACL, graph, assistant, document,
  proposal, apply, reload, and rollback regression suites.

### Verification commands

```bash
./gradlew :semantic-engine:generateFiboCatalog
./gradlew :semantic-engine:verifyFiboCatalog
./gradlew :semantic-engine:generateDomainSearchIndex
./gradlew :semantic-engine:verifyDomainSearchIndex
./gradlew test
./gradlew build
./gradlew check
cd web-app
npm ci
npm test
npm run build
npm run test:e2e
cd ../vscode-extension
npm ci
npm test
cd ..
git diff --check
```

Run the approved benchmark task established in Slice 0 and record its exact
command and machine description in the summary.

### Stop conditions

Do not report Phase 13 complete if any acceptance criterion, quality threshold,
performance threshold, security check, compatibility test, or required command
fails. Stop and fix the owning slice or amend the approved spec rather than
weakening evidence in the summary.

## Integration Completeness Matrix

The implementation must keep this matrix synchronized with the public intent
enum and tests. A row is incomplete until the named UI surface uses the shared
server contract and has the required focused and end-to-end coverage.

Rules applying to every row are part of the matrix: an inactive profile hides
domain recommendations while preserving the valid local action;
`LexicalStructural` mode keeps verified lexical/structural results and labels
the degradation; `Unavailable` preserves local modeling but cannot stage new
reuse; and every reuse/customize/extend/map action resolves a server-issued ID,
revalidates it in Kotlin, and enters the existing staging and proposal path.
Each row's tests must cover its applicable inactive, degraded, stale-ID, and
permitted-action behavior. Mapping is annotation-only and does not require
target materialization; individual creation can select types and properties but
cannot import FIBO reference individuals.

| Workflow | Intent | Required context | Primary surface | Slice | Required verification |
|---|---|---|---|---:|---|
| Global search | `GlobalSemanticSearch` | text, kind, filters | Explore search | 7 | contract + E2E |
| Create class | `CreateClass` | label, definition, parent | Contextual editing | 8 | component + E2E |
| Create object property | `CreateObjectProperty` | label, domain, range, parent, inverse | Contextual editing | 8 | component + E2E |
| Create datatype property | `CreateDatatypeProperty` | label, domain, datatype, parent | Contextual editing | 8 | component + E2E |
| Create individual | `SelectIndividualType` | label, proposed types | Contextual editing | 8 | component + integration |
| Edit annotations | `EditSemanticAnnotation` | entity, label, definition | Entity details | 8 | component + integration |
| Edit class hierarchy | `EditClassHierarchy` | class, current parents | Class picker | 8 | component + integration |
| Edit property hierarchy | `EditPropertyHierarchy` | property, kind, current parents | Entity picker | 8 | component + integration |
| Edit domain | `EditDomain` | property, kind, current range | Class picker | 8 | component + integration |
| Edit range/datatype | `EditRange` | property, kind, current domain | Entity picker | 8 | component + integration |
| Add assertion/value | `AddAssertionOrValue` | subject, types, property direction, target | Contextual editing | 8 | component + E2E |
| Delete/replace | `DeleteOrReplaceEntity` | entity, dependents, current graph | Deletion review | 9 | integration + E2E |
| Proposal review | `ProposalReuseReview` | staged edits, proposal fingerprint | Staging panel | 9 | component + E2E |
| SHACL target | `ShaclTargetClass` | shape, graph state | SHACL authoring | 9 | integration |
| SHACL path | `ShaclPropertyPath` | target class, shape | SHACL authoring | 9 | integration |
| SHACL constraint | `ShaclClassOrDatatypeConstraint` | path, target, constraint kind | SHACL authoring | 9 | integration |
| Ontology map | `OntologyMapRelatedSearch` | selected asserted node | Ontology map | 9 | component + E2E |
| Reasoning | `ReasoningRelatedSearch` | selected asserted/inferred fact | Reasoning workspace | 9 | component + E2E |
| Foundation expansion | `FoundationExpansion` | groups, filters, reuse state | Domain settings | 7 | component + E2E |

## Cross-Slice Test Plan

### Unit tests

- profile contracts, parsing, writing, and path safety;
- descriptor, foundation, embedding, lexical, vector, ranking, explanation,
  eligibility, dependency, snapshot, difference, mapping, and provenance
  behavior;
- typed operation and canonical-IRI safety;
- web/CLI/TypeScript serialization.

### Integration tests

- package to index to recommendation;
- activation to project reload;
- recommendation to dependency preview to staged proposal;
- proposal to apply, provenance, reload, and rollback;
- source/project comparison after customization;
- all React authoring contexts;
- CLI and VS Code delegation;
- migration from copied legacy projects.

### Contract tests

- index and profile schemas;
- Ktor routes and structured errors;
- server-issued IDs and freshness;
- old external route compatibility;
- document work keys and assistant FIBO context;
- disabled/full/degraded/unavailable modes.

### End-to-end tests

At minimum:

1. Activate FIBO on a copied empty project.
2. Select a foundation group and apply one bounded batch.
3. Create a class and choose reuse-and-customize.
4. Verify canonical IRI plus project label and source label.
5. Create a local extension from a property/class recommendation.
6. Add a close mapping.
7. Use domain/range and SHACL recommendations.
8. Find a related concept from the map and reasoning workspace.
9. Remove reuse with dependency review.
10. Force apply/provenance failure and verify full restoration.
11. Repeat local authoring with profile disabled.
12. Run one Phase 12 document task and one assistant FIBO request without Phase
    13 contract changes.

### Performance tests

- clean offline generation;
- verified index load;
- cold inference;
- warm p50/p95;
- broad p95;
- lexical-degraded p95;
- memory and asset size;
- eight concurrent requests;
- cancellation under rapid typing.

### Security tests

- source/model/index checksum tampering;
- path traversal and symlink escape;
- arbitrary source/release/IRI submission;
- cross-project/user token use;
- stale activation/plan/recommendation IDs;
- oversized and adversarial text;
- untrusted labels rendered safely;
- no network call in index/query path;
- no secret/private-text logging;
- no raw vector endpoint;
- no write from read routes.

## Verification Commands

Slice commands are mandatory during implementation. Final verification is:

```bash
./gradlew :semantic-engine:generateFiboCatalog
./gradlew :semantic-engine:verifyFiboCatalog
./gradlew :semantic-engine:generateDomainSearchIndex
./gradlew :semantic-engine:verifyDomainSearchIndex
./gradlew test
./gradlew build
./gradlew check

cd web-app
npm ci
npm test
npm run build
npm run test:e2e

cd ../vscode-extension
npm ci
npm test
```

The Slice 0-approved benchmark command must also pass. Documentation-only
changes before implementation require at minimum `git diff --check` and manual
link review.

## Rollback Notes

### During development

- Each slice should remain a focused commit or small commit series.
- Generated assets must be reproducible from approved source and removable
  without touching source ontologies.
- Do not rewrite or delete the Phase 5 catalog until all compatibility tests
  pass.
- Keep configuration fields additive until migration is proven.
- Feature wiring should remain inactive for projects without a profile.

### Runtime configuration rollback

- Activation journals the original and intended profile and managed-source
  hashes until reload verification succeeds; `entio.yaml` is never rewritten.
- Failure or startup recovery restores or completes the profile-commit
  transaction deterministically.
- Deactivation follows the same transaction protocol and is blocked by reuse.

### Runtime ontology rollback

- Existing proposal baselines and source backups remain authoritative.
- Domain provenance is prepared before apply and committed only with successful
  reload verification.
- Any provenance failure restores ontology and provenance bytes.
- Removing a reused entity appends history; it does not erase source snapshots.

### Asset rollback

- Index/model versions are fingerprinted. Reverting the application and asset
  manifest together restores the earlier search contract.
- A project pinned to an unsupported asset must show an unavailable/migration
  status rather than silently using another release.

## Risks And Assumptions

### Risks

- Local model/tokenizer behavior may not be reproducible across every native
  platform.
- Bundled model/index assets may materially increase repository and release
  size.
- General-English embeddings may confuse directional financial relationships.
- Editing FIBO-IRI semantics can mislead users if source/project differences are
  not prominent.
- Atomic sidecar provenance may expose weaknesses in current apply hooks.
- RAG in many forms may create distracting UI or excessive requests.
- Full-corpus recommendations may surface deprecated or obscure concepts.
- Existing document and assistant code may be coupled to current catalog
  contracts more deeply than expected.
- Multi-file profile, managed-source, and provenance updates require explicit
  observable-atomic recovery rather than an impossible claim of one filesystem
  atomic operation.
- Select-all foundation imports may be too large for one review experience.

### Mitigations

- Slice 0 reference-vector and platform audit;
- checksum-pinned artifacts and lexical fallback;
- hard structural eligibility before ranking;
- clear source/project comparison and extension advisory;
- atomicity ADR and fault injection before UI work;
- 300 ms debounce, cancellation, cache, and bounded results;
- maturity penalties and explicit broad browsing;
- exact compatibility baselines before route migration;
- separate profile sidecar that leaves hand-authored `entio.yaml` untouched;
- journaled transactions, fixed commit points, orphan cleanup, and fault
  injection;
- stable batches of at most 20 explicit selections with visible partial
  progress.

### Assumptions

- The current `master_2026Q2` snapshot remains approved for Phase 13 after its
  publication status and source-family inventory are recorded.
- Its source archive and Commons dependencies contain the complete corpus needed
  for class/property retrieval.
- Java 21 remains the supported Kotlin baseline.
- The existing typed edit system can express most required materialization and
  customization operations.
- Project-local file persistence is acceptable; no production database is
  required.
- Current project identity and authorization are sufficient for Phase 13's
  development boundary.
- Document and assistant compatibility can remain behind current FIBO adapters.

## Definition Of Done

Phase 13 is done only when:

- Slice 0 decisions and all required ADRs are approved;
- Slices 1 through 12 are completed in dependency order or an explicitly
  approved revised order;
- every spec acceptance criterion is demonstrated;
- the exact pinned package, model, and generated indexes verify offline;
- profile activation/deactivation and ontology/provenance writes satisfy the
  approved journaled, recoverable all-or-nothing contract;
- full and degraded retrieval meet approved quality and performance targets;
- every specified human-driven web workflow is integrated;
- canonical IRIs remain locked and project/source meanings remain distinct;
- no retrieval result bypasses human review or deterministic validation;
- existing projects remain semantically unchanged until explicit actions;
- document ingestion and assistant compatibility tests prove isolation;
- CLI, VS Code, React, Ktor, semantic engine, proposal, reasoning, SHACL, map,
  reload, rollback, and migration tests pass;
- all final verification commands pass;
- `AGENTS.md`, `README.md`, ADRs, and the Phase 13 summary accurately describe
  the verified implementation;
- no known required work remains hidden behind an open question.

## Boundary Check

- **Current phase:** Phase 13 is the active phase with an approved matching
  scope, spec, and ExecPlan. It intentionally revises only the listed Phase
  5/12 retrieval boundaries; implementation remains complete through Phase 12.
- **Non-goals:** The plan excludes additional ontologies, hosted retrieval,
  automatic alignment/apply, document/assistant integration, production
  persistence, and unrelated surfaces.
- **Speculative infrastructure:** It adds no server, database, Gradle module,
  provider, or broad framework. New libraries and binary assets are gated by
  Slice 0.
- **Module ownership:** Core contracts remain in `core-types`; source identity,
  indexing, semantic checks, and ranking remain in `semantic-engine`; Ktor owns
  authorization/adaptation; clients own presentation only.
- **Semantic tooling:** Existing Jena, OWL API, reasoning, and SHACL boundaries
  remain. Lucene and ONNX Runtime provide retrieval mechanics only.
- **Human review:** Every ontology mutation continues through the existing
  staged proposal, validation, diff, approval, atomic apply, reload, and
  rollback workflow.
