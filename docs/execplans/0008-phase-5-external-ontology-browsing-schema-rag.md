# ExecPlan: Phase 5 External Ontology Browsing And Basic Schema RAG

## Status

Implemented. The approved slices are complete; see `docs/phase-summaries/phase-5-summary.md` for the resulting repository state and documented limitations.

## Source Spec

- `docs/specs/0008-phase-5-external-ontology-browsing-schema-rag.md`
- `docs/architecture/phase-5-scope.md`

## Goal

Add a read-only, reproducible FIBO catalog to Entio and make it available through deterministic browsing, semantic search, explicit dependency review, and controlled local reuse. External ontology selection and search must never mutate the local project. Any approved reuse or local-extension action must enter the existing staged proposal, preview, validation, semantic-diff, approval, the existing Phase 4 multi-source application path, reload, and rollback workflow.

Phase 5 is intentionally limited to one pinned FIBO release, a curated Foundations package, a wider searchable catalog, and basic deterministic Schema RAG. It does not add generative retrieval, embeddings, network downloads, additional ontology sources, or a new persistence layer.

## Related Architecture

Phase 4 is complete and remains the source of the reasoning and SHACL contracts that Phase 5 consumes. Phase 5 must preserve the current module direction:

- `core-types` owns Entio contracts.
- `semantic-engine` owns RDF parsing, catalog loading, external descriptors, search, dependency calculation, and proposal translation.
- `validation-engine` owns deterministic external-package, search, dependency, and proposal validation.
- `graph-diff` owns semantic and proposal-impact diffing.
- `cli` exposes thin machine-readable commands.
- `vscode-extension` renders Kotlin-owned results and delegates semantic work to the CLI.
- `shared` remains limited to generic utilities.

## Current State

- The local semantic engine parses Turtle with Apache Jena and already produces Entio-owned semantic descriptors for local symbols.
- The proposal workflow supports staged changes, combined previews, deterministic validation, semantic diffs, approval, rejection, coordinated multi-source application, reload verification, and rollback.
- The CLI has a machine-readable JSON boundary backed by Jackson.
- The VS Code extension delegates ontology behavior to the Kotlin CLI.
- `semantic-engine` already contains Apache Jena, Jena SHACL, OWL API/HermiT, and SnakeYAML dependencies. Phase 5 should reuse these dependencies where they fit and should not add a new semantic-web framework.
- Phase 4 reasoning fingerprints, import resolution, asserted/inferred separation, SHACL validation, and the existing `MultiSourceAtomicApplier` are available for approved local FIBO references.
- No external ontology package, FIBO catalog, Phase 5 contracts, commands, or workbench views are implemented yet.

## Fixed Phase 5 Package And Reference Decisions

These decisions are approved by this ExecPlan. Implementation must not choose alternatives during a slice. Any change requires an explicit scope revision before implementation continues.

### Pinned FIBO release

- Release tag: `master_2026Q2`.
- Release commit SHA: `f59157fe156e3d91b1c045222d0a7dc06b7d78a2`.
- Release date: `2026-07-14`.
- Upstream repository: `https://github.com/edmcouncil/fibo`.
- Immutable source artifact: the GitHub tag source ZIP for `master_2026Q2`, committed byte-for-byte as `external-ontologies/fibo/source/fibo-master_2026Q2-f59157f.zip`.
- Package identity uses SHA-256. The archive checksum and every committed derived asset checksum must be recorded in `external-ontologies/fibo/checksums/sha256sums.txt`.
- No branch name, `latest` alias, floating tag, or development snapshot may be substituted.

### Required OMG Commons dependency release

- Package the exact OMG Commons `1.3` ontology documents required by the FIBO import closures described below.
- Store them under `external-ontologies/fibo/dependencies/omg-commons-1.3/`.
- For every Commons document, the manifest must record its ontology IRI, version IRI, official source URL, local package path, SHA-256, copyright statement, and license record.
- Package assembly must fail if a required Commons import is unresolved, lacks an exact local file, or lacks approved provenance and license metadata.

### Package format and layout

The package format is `entio-fibo-package-v1` and the catalog schema version is `fibo-catalog-v1`.

```text
external-ontologies/
└── fibo/
    ├── manifest.yaml
    ├── LICENSE-FIBO-MIT.txt
    ├── LICENSE-OMG-COMMONS-MIT.txt
    ├── ATTRIBUTION.md
    ├── source/
    │   └── fibo-master_2026Q2-f59157f.zip
    ├── dependencies/
    │   └── omg-commons-1.3/
    ├── indexes/
    │   ├── catalog-v1.jsonl
    │   ├── catalog-metadata-v1.json
    │   ├── ontology-iri-map-v1.json
    │   └── curated-foundations-v1.json
    └── checksums/
        └── sha256sums.txt
```

Package rules:

- `manifest.yaml` uses schema version `1`.
- `catalog-v1.jsonl` contains one deterministic record per searchable class, object property, or datatype property.
- `ontology-iri-map-v1.json` maps original FIBO and Commons ontology IRIs to immutable local package entries.
- `curated-foundations-v1.json` records the exact curated seed list and its computed package import closure.
- `catalog-metadata-v1.json` records counts, release identity, index schema, generation tool version, generation timestamp excluded from semantic identity, maturity counts, and package fingerprints.
- Generated indexes are committed and are regenerated only by `./gradlew :semantic-engine:generateFiboCatalog`.
- `./gradlew :semantic-engine:verifyFiboCatalog` must regenerate into a temporary directory, compare normalized output and SHA-256 values, and fail on drift.
- Build, tests, and runtime operate offline. The committed archive and dependency files are the only package inputs.

### Curated FIBO Foundations seeds

The curated package shown by default consists of the following exact ontology IRIs from the pinned release:

```text
https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/
https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Contracts/
https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Documents/
https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/IdentifiersAndIndices/
https://spec.edmcouncil.org/fibo/ontology/FND/Parties/Parties/
https://spec.edmcouncil.org/fibo/ontology/FND/AgentsAndPeople/People/
https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/
https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/BusinessDates/
https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/Occurrences/
https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/
https://spec.edmcouncil.org/fibo/ontology/FND/OwnershipAndControl/Ownership/
https://spec.edmcouncil.org/fibo/ontology/FND/OwnershipAndControl/Control/
https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/ProductsAndServices/
https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/PaymentsAndSchedules/
https://spec.edmcouncil.org/fibo/ontology/FND/Places/RealProperty/
```

Curation rules:

- These ontology IRIs are curated seeds and are visible by default.
- Their complete technical `owl:imports` closure is packaged automatically and recorded in the manifest.
- Package-runtime dependencies, such as FIBO Relations, Annotation Vocabulary, and required OMG Commons ontologies, are not automatically shown as curated business modules unless they are also listed as seeds.
- Every curated seed must have FIBO maturity `Release`. Package verification fails if a seed is missing or has another maturity.
- The curated display groups are: Agreements and Contracts; Documents and Identifiers; Parties and People; Dates and Schedules; Monetary Amounts; Ownership and Control; Products, Services, and Payments; Real Property.

### Wider searchable catalog

- Build the wider catalog from ontology documents registered in the pinned release root `catalog-v001.xml`, using the committed local package and import map. Index Release and Provisional schema content across the release rather than limiting the catalog to `AboutFIBOProd-TBoxOnly.rdf`.
- Index named `owl:Class`, `owl:ObjectProperty`, and `owl:DatatypeProperty` resources only.
- Do not index example individuals, reference individuals, anonymous restrictions, or parser-local blank nodes as candidates.
- Exclude the `EXMP` domain from the searchable schema catalog.
- Index `Release` and `Provisional` ontology content.
- Keep `Informative` or deprecated content in package metadata but exclude it from normal results unless `includeInformative=true` is explicitly requested.
- Provisional candidates must display a warning and require explicit acknowledgement before reuse.
- Informative or deprecated candidates require `includeInformative=true` and explicit acknowledgement before reuse.

### Licensing and attribution

- FIBO package assets use the MIT license text carried by the pinned repository and ontology metadata.
- Required OMG Commons 1.3 files must preserve their embedded copyright and MIT license notices.
- `ATTRIBUTION.md` must identify the EDM Association/EDM Council, the Object Management Group, the pinned FIBO release, the FIBO commit SHA, Commons 1.3, source locations, and a no-endorsement statement.
- `manifest.yaml` must contain a per-asset-family license record and a per-file provenance/checksum record.
- Package verification fails if an asset’s embedded or recorded license differs from the approved MIT record, if required notices are missing, or if attribution is incomplete.

### Local reference representation

Local projects reuse original FIBO IRIs; they do not copy FIBO definitions into the local namespace.

The project configuration records package identity and selected ontology modules:

```yaml
externalOntologyReferences:
  - source: fibo
    release: master_2026Q2
    commitSha: f59157fe156e3d91b1c045222d0a7dc06b7d78a2
    packageFingerprint: <sha256-from-manifest>
    modules:
      - <original-fibo-ontology-iri>
```

Project-owned ontology files may add approved `owl:imports` statements using original FIBO ontology IRIs. Local classes and properties may refer directly to original FIBO element IRIs, for example:

```turtle
local:CommercialLoan
    rdfs:subClassOf <https://spec.edmcouncil.org/fibo/ontology/.../Loan> .
```

At load time, Entio resolves approved FIBO and Commons ontology IRIs through `ontology-iri-map-v1.json`. It must not contact the network.

Selecting FIBO for browsing is session-only and read-only. It does not write `entio.yaml`, Turtle, or any project file. A project reference or `owl:imports` change occurs only through an approved proposal.

### External element status

An element is `already-used` only when at least one of these asserted conditions is true:

- Its original external IRI appears in an asserted local project triple.
- Its ontology IRI appears in an asserted local `owl:imports` statement.
- Its ontology module is recorded in the project’s approved `externalOntologyReferences`.

Search display, staging, related inferred facts, or use of a neighboring concept do not make an element `already-used`.

### Phase 4 reasoning integration

- Phase 4 reasoning loads only modules explicitly approved in `externalOntologyReferences` plus their packaged import closure.
- The wider searchable catalog and generated indexes never enter HermiT or SHACL data graphs.
- Shape-only sources remain excluded from HermiT according to Phase 4 rules.
- The FIBO package fingerprint, release commit, selected module IRIs, and resolved package closure fingerprints become part of the Phase 4 reasoning and SHACL fingerprints.
- Changing selected modules or package identity invalidates cached reasoning and SHACL results.
- Phase 5 reuses the existing Phase 4 coordinated multi-source application and rollback path and must not introduce another source-writing mechanism.

## Target State

The completed phase should provide:

- One exact, versioned, reproducible FIBO package bundled as a read-only Entio asset.
- A manifest, curated Foundations package, required imports/dependencies, wider catalog, source metadata, and licensing/attribution metadata.
- Entio-owned external ontology source, manifest, catalog, module, status, descriptor, search, match-reason, dependency, and reuse/local-extension contracts.
- Deterministic external semantic descriptions that preserve original FIBO IRIs and explicit RDF term shape.
- Curated browsing and wider catalog search for classes and properties using the versioned deterministic `fibo-schema-search-v1` scoring model, stable scores, and plain-language match explanations.
- Explicit dependency sets with reasons, availability, selection state, and completeness.
- Reuse and local-extension proposal intents that preserve FIBO IRIs and do not modify bundled FIBO files.
- Thin CLI commands and VS Code views that consume the Kotlin contracts.
- Copied-fixture tests proving browse, search, dependency review, proposal approval/rejection, coordinated Phase 4 application, reload, rollback, and FIBO asset immutability.

## Non-Goals

- Supporting external ontology sources other than FIBO.
- Automatically downloading, updating, or live-synchronizing FIBO.
- Network access at runtime.
- Editing, rewriting, or serializing bundled FIBO assets.
- Copying FIBO concepts into the local namespace by default.
- OWL inference for catalog search, dependency closure, or descriptor assembly beyond the already implemented Phase 4 behavior.
- Embeddings, vector search, LLM retrieval, generative Schema RAG, or AI-generated proposal edits.
- A database, search server, graph store, plugin system, or durable external-catalog cache.
- Git workflow inside Entio.
- Full external ontology management, version migration, licensing automation, or FIBO authoring.

## Dependency Order And Multi-Agent Safety

Implement slices serially in the order below. Later slices consume contracts and behavior established by earlier slices.

1. Define core contracts that encode the fixed package, catalog, search, dependency, and local-reference decisions in this ExecPlan.
2. Add the approved reproducible read-only FIBO package, manifest, curated package metadata, dependency assets, checksums, and generated catalog indexes.
3. Load the package and assemble external semantic descriptors and catalog browsing results.
4. Implement deterministic catalog search, filtering, ranking, and match explanations after the mandatory package-and-catalog checkpoint passes.
5. Calculate explicit external dependencies and validate dependency sets.
6. Translate external reuse and local-extension intents into the existing proposal workflow and Phase 4 reasoning/import behavior.
7. Expose read-only source selection, browsing, descriptors, search, dependencies, and proposal preparation through the CLI.
8. Add the VS Code external ontology browsing, search, details, dependency review, and proposal states.
9. Add copied-fixture end-to-end regression coverage and the Phase 5 implementation summary.

All slices must be implemented serially because they share contracts, package metadata, proposal state, CLI JSON schemas, and workbench message models. After a contract slice is complete, narrowly scoped tests may be parallelized only if each agent owns separate test files and does not change shared contracts, package assets, build files, CLI schemas, UI messages, specs, ExecPlans, or `AGENTS.md`.

Do not parallelize edits to:

- `core-types` or `shared`;
- FIBO manifests, package metadata, generated indexes, or checksums;
- root or module build files and dependency versions;
- CLI request/response contracts;
- VS Code message contracts;
- specs, ExecPlans, ADRs, summaries, or `AGENTS.md`.

## Recommended Dependencies

- Reuse the existing Apache Jena dependency for parsing bundled Turtle and constructing RDF-term-aware descriptors. Keep Jena types inside `semantic-engine`.
- Reuse the existing SnakeYAML engine to parse the approved YAML `manifest.yaml`. Keep manifest parsing behind an Entio-owned loader and validate the parsed result deterministically.
- Reuse the existing Jackson Kotlin module for machine-readable CLI responses. Do not expose Jackson types from core or engine contracts.
- Reuse the existing Phase 3 semantic-description contracts and Phase 4 proposal/reasoning/SHACL contracts where applicable.
- Do not add a database, search engine, embedding model, HTTP client, network downloader, LLM SDK, or new ontology framework.
- Do not add a new dependency unless the approved libraries cannot implement a concrete requirement and the user explicitly approves the scope change before implementation.

## Affected Modules And Files

Expected areas, subject to the slice boundaries below:

- `core-types/src/main/kotlin/com/entio/core/`
- `core-types/src/test/kotlin/com/entio/core/`
- `semantic-engine/src/main/kotlin/com/entio/semantic/`
- `semantic-engine/src/test/kotlin/com/entio/semantic/`
- `validation-engine/src/main/kotlin/com/entio/validation/`
- `validation-engine/src/test/kotlin/com/entio/validation/`
- `graph-diff/src/main/kotlin/com/entio/diff/`
- `graph-diff/src/test/kotlin/com/entio/diff/`
- `cli/src/main/kotlin/com/entio/cli/`
- `cli/src/test/kotlin/com/entio/cli/`
- `vscode-extension/src/`
- `vscode-extension/src/test/`
- `external-ontologies/fibo/`
- `examples/` and temporary copied fixtures
- module build files only when an approved dependency or asset-packaging change requires them
- `docs/decisions/`, `docs/phase-summaries/`, and this ExecPlan only for focused planning or completion artifacts

## Implementation Slices

### Slice 1: Fixed Package And External Ontology Contracts

#### Goal

Define immutable Entio-owned contracts that encode the fixed FIBO release, package layout, curated seeds, catalog format, licensing metadata, local-reference representation, search model, dependencies, and reuse/local-extension intents approved by this ExecPlan.

No package or reference decision is made in this slice.

#### Allowed files/modules

- `core-types/src/main/kotlin/com/entio/core/`
- Matching `core-types/src/test/kotlin/com/entio/core/`
- `docs/decisions/` completion artifact only

#### Forbidden actions/modules

- Do not modify `semantic-engine`, `validation-engine`, `graph-diff`, `cli`, or `vscode-extension` implementation.
- Do not change the release, commit SHA, seed list, package format, checksum algorithm, maturity policy, license policy, local-reference representation, dependency categories, or search weights in this ExecPlan.
- Do not add FIBO files, generated indexes, or network download logic.
- Do not expose Apache Jena, SnakeYAML, Jackson, or other third-party types in public contracts.
- Do not implement parsing, search, ranking, dependency calculation, proposal application, or UI behavior.
- Do not add dependencies or new modules.

#### Expected changes or output

- Immutable contracts for `ExternalOntologySource`, `ExternalOntologyManifest`, `ExternalOntologyCatalog`, `ExternalOntologyModule`, external element status, external semantic descriptors, search queries/candidates/match reasons, score-model version, dependencies, dependency sets, and external reuse/local-extension intents.
- Manifest contracts that require `master_2026Q2`, the full pinned commit SHA, `entio-fibo-package-v1`, `fibo-catalog-v1`, SHA-256, Commons 1.3, and the exact curated seed list.
- Explicit enums/sealed states for availability, maturity, selection, entity kind, dependency category, dependency requirement, visibility, proposal intent, match reason, confidence band, and ambiguity group.
- External-reference contracts matching the approved `entio.yaml` representation.
- Stable ordering, RDF-term preservation, and JSON-safe value shapes.

#### Tests

- Contract construction, equality, default values, ordering, and absent-value behavior.
- Reject any manifest with a different release tag, commit SHA, package schema, catalog schema, checksum algorithm, Commons version, or curated seed list.
- Preserve original IRIs and RDF term shapes.
- Distinguish local/external and available/curated/selected/already-used states.
- Verify `already-used` is based only on approved asserted local conditions.
- Verify dependency categories and required/optional, direct/package-transitive, already-available/newly-selected, and user-visible/implementation-only flags.

#### Verification commands

```bash
./gradlew :core-types:test
./gradlew test
```

#### Stop conditions

- Implementation requires changing an approved package or reference decision.
- A contract requires a third-party semantic-web type.
- A contract requires implementing catalog behavior or proposal mutation.
- An existing Phase 3 or Phase 4 contract would need an incompatible change without explicit user approval.

### Slice 2: Approved Reproducible Read-Only FIBO Package

#### Goal

Add the exact fixed FIBO and Commons assets, manifest, curated seed metadata, full packaged import closure, wider catalog source inventory, generated indexes, checksums, licensing, attribution, and offline verification tasks defined by this ExecPlan.

#### Allowed files/modules

- `external-ontologies/fibo/`
- `semantic-engine/src/main/kotlin/com/entio/semantic/` only for package generation and verification services
- Matching `semantic-engine` tests
- Build/resource configuration required for `generateFiboCatalog` and `verifyFiboCatalog`
- `docs/decisions/` completion artifact only

#### Forbidden actions/modules

- Do not modify `examples/simple-ontology` as a package source.
- Do not substitute another FIBO or Commons version.
- Do not use network access during normal build, verification, tests, or runtime.
- Do not modify bundled FIBO or Commons files during browse, search, proposal, or application flows.
- Do not add a database, remote catalog, HTTP runtime client, updater, or dynamic package resolution.
- Do not implement search, user dependency review, proposal translation, CLI commands, or VS Code UI.

#### Expected changes or output

- The exact `master_2026Q2` source archive at the approved path.
- The exact required OMG Commons 1.3 dependency files under the approved dependency path.
- `manifest.yaml` containing release, commit, archive identity, seed list, full import closure, ontology IRI mappings, maturity, per-file provenance, SHA-256, licensing, and attribution metadata.
- The complete package import closure required by the curated seeds and wider TBox catalog.
- Deterministic `catalog-v1.jsonl`, `catalog-metadata-v1.json`, `ontology-iri-map-v1.json`, and `curated-foundations-v1.json`.
- `LICENSE-FIBO-MIT.txt`, `LICENSE-OMG-COMMONS-MIT.txt`, and `ATTRIBUTION.md`.
- Offline Gradle tasks:
  - `./gradlew :semantic-engine:generateFiboCatalog`
  - `./gradlew :semantic-engine:verifyFiboCatalog`
- Package paths are read-only targets for all normal Entio mutation APIs.

#### Tests

- Validate the exact release tag, full commit SHA, source archive, Commons version, manifest schema, catalog schema, seed list, and SHA-256 records.
- Verify every curated seed exists, has `Release` maturity, and resolves through the local IRI map.
- Verify the complete technical import closure is local and has no unresolved network dependency.
- Verify missing, malformed, mismatched, incomplete, duplicate, or unavailable assets produce structured failures.
- Regenerate indexes twice and compare normalized outputs byte-for-byte.
- Compute SHA-256 for every committed package file before and after package tests; fail on any change.
- Verify serializer, proposal, apply, and rollback APIs reject package paths as mutation targets.
- Verify no-network build, test, package load, and index verification.

#### Verification commands

```bash
./gradlew :semantic-engine:generateFiboCatalog
./gradlew :semantic-engine:verifyFiboCatalog
./gradlew :semantic-engine:test
./gradlew test
```

#### Stop conditions

- The pinned source archive or commit cannot be verified.
- Any curated seed is missing or is not `Release` maturity.
- A required FIBO or Commons import remains unresolved.
- An asset’s provenance, embedded license, approved MIT record, attribution, or checksum cannot be verified.
- Build, tests, verification, or runtime requires network access.
- Generated indexes are not reproducible.
- Tests or application code mutate committed package assets.

### Slice 3: External Catalog Loading And Semantic Descriptors

#### Goal

Load the approved FIBO package and expose curated modules, catalog elements, and Phase 3-compatible external semantic descriptions while preserving explicit source metadata and original IRIs.

#### Allowed files/modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/`
- Matching `semantic-engine/src/test/kotlin/com/entio/semantic/`
- `core-types` only for contract corrections approved by Slice 1
- Package loader/resource adapters from Slice 2

#### Forbidden actions/modules

- Do not alter bundled FIBO assets.
- Do not add OWL inference or infer parents, domains, ranges, imports, or dependencies.
- Do not implement search ranking, proposal application, CLI commands, or VS Code rendering.
- Do not expose Jena model/resource types outside `semantic-engine`.
- Do not add a catalog database or network source.

#### Expected changes or output

- A reusable external catalog loader that reads the committed compact indexes once per workbench/CLI session rather than reparsing the full RDF release for each query.
- Curated package and module browse results using the exact seed list and display groups in this ExecPlan.
- External descriptors for named FIBO classes and object/datatype properties containing labels, definitions, explicit hierarchy, domains, ranges, source ontology, FIBO domain/module, release, maturity, and status.
- Wider catalog records derived from the approved production TBox closure, excluding EXMP, named individuals, anonymous restrictions, and parser-local blank nodes.
- Default page size `25`, maximum page size `100`, total result counts, stable cursors/page numbers, and no silent truncation.
- Session-scoped in-memory catalog reuse.
- Deterministic ordering and explicit handling of blank nodes or unsupported elements.

#### Tests

- Descriptor assembly for classes and properties.
- Preservation of explicit RDF terms and original IRIs.
- Curated versus wider catalog membership.
- Module browse ordering and local-project-use status using only the approved asserted `already-used` definition.
- No inferred relationships or hidden user dependency closure. Package-runtime import closure remains available separately.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew :core-types:test
./gradlew test
```

#### Stop conditions

- Descriptor output depends on inference or unstable traversal order.
- External library types escape the semantic-engine boundary.
- Catalog loading changes local project files or bundled assets.
- Browsing reparses the entire FIBO RDF archive for each page or search request.


## Mandatory Package And Catalog Checkpoint

After Slice 3 is complete and merged into local `main`, stop before beginning Slice 4.

The checkpoint must verify:

- The pinned `master_2026Q2` archive and full commit SHA.
- SHA-256 checks for every committed package file.
- All curated seed ontology IRIs and `Release` maturity.
- The complete packaged FIBO and Commons import closure.
- Per-asset licensing and attribution.
- Deterministic catalog-index regeneration.
- Package immutability before and after tests.
- Curated browse descriptors and wider catalog counts.
- No-network package loading.
- No full RDF reparse per search.
- Catalog cold-load time and warm browse timing recorded in the checkpoint artifact.

Manual performance targets for the committed package are:

- Cold compact-index load: no more than 10 seconds on the documented development machine.
- Warm browse or search page of 25 results: no more than 500 milliseconds on the documented development machine.
- Every result response reports total count and page metadata.

The timing targets are manual checkpoint criteria rather than fragile CI wall-clock assertions. If package identity, import closure, licensing, reproducibility, immutability, or catalog correctness fails, stop and ask before beginning search implementation.

### Slice 4: Deterministic Schema Search And Ranking

#### Goal

Implement deterministic search over external classes and properties using the approved `fibo-schema-search-v1` query normalization, strict filters, optional structural context hints, exact integer scoring weights, adjusted confidence bands, tie groups, pagination, and typed match explanations.

#### Allowed files/modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/`
- Matching `semantic-engine/src/test/kotlin/com/entio/semantic/`
- `core-types` only for approved search-contract corrections
- `validation-engine` only for search-query/result validation if the contract requires it
- Matching validation tests

#### Forbidden actions/modules

- Do not add embeddings, vector databases, search servers, LLMs, generative retrieval, or network search.
- Do not mutate local projects or silently select a candidate.
- Do not implement user dependency review, proposal application, CLI, or UI in this slice.
- Do not change existing local semantic-search behavior unless explicitly required to consume a shared approved contract.
- Do not use OWL inferences, SHACL results, clicks, popularity, or user behavior in ranking.

#### Expected changes or output

- Search by preferred label, alternate label, definition, full IRI, local IRI name, entity kind, source module, hierarchy, domain, and range.
- Query normalization that:
  - lowercases;
  - trims whitespace;
  - replaces punctuation, underscores, and hyphens with spaces;
  - collapses repeated spaces;
  - splits camel case;
  - preserves the original query for full-IRI matching;
  - removes duplicate query tokens;
  - does not stem words.
- The versioned `fibo-schema-search-v1` stopword set is exactly:
  - `a`, `an`, `the`, `of`, `for`, `to`, `in`, `on`, `by`, `with`, `from`, `and`, `or`, `as`, `at`.
- A meaningful token is a token of at least three characters that is not in the stopword set, plus the explicitly preserved ontology terms `has` and `is`.
- Strict filters applied before scoring:
  - entity kind;
  - selected FIBO module or domain;
  - curated-only;
  - maturity status.
- Parent, domain, and range are context hints by default and contribute score without excluding a candidate.
- A caller may mark parent, domain, or range context as `required=true`; only then does it act as a strict filter.
- The versioned deterministic score model has a maximum score of 100:
  - name or IRI match: maximum 60 points;
  - definition match: maximum 20 points;
  - explicit semantic-context compatibility: maximum 12 points;
  - catalog status: maximum 5 points;
  - local-project relevance: maximum 3 points.
- Preferred-label scoring:
  - exact normalized match: 50;
  - exact token match in a different order: 44;
  - preferred label starts with the full query: 38;
  - full query appears as a preferred-label phrase: 34;
  - all query tokens appear in the preferred label: 30;
  - partial query-token coverage: `floor(20 * matchedQueryTokens / totalQueryTokens)`.
- Alternate-label scoring:
  - exact normalized alternate-label match: 36;
  - all query tokens appear in one alternate label: 24;
  - partial token coverage: `floor(16 * matchedQueryTokens / totalQueryTokens)`.
- Only the best alternate-label match contributes.
- Name score is the maximum of preferred-label and alternate-label score rather than their sum.
- IRI scoring:
  - exact full IRI match: 60;
  - exact normalized local-name match: 42;
  - query appears in normalized local name: 24.
- Final name-or-IRI score is the maximum of name score and IRI score.
- Definition scoring:
  - exact normalized query phrase: 20;
  - all meaningful query tokens present: 16;
  - at least 75 percent present: 12;
  - at least 50 percent present: 8;
  - at least one meaningful token present: 4.
- Only the best definition contributes.
- Explicit semantic-context scoring for classes:
  - requested direct parent matches an explicit parent: 8;
  - requested broader ancestor appears in the explicit descriptor path: 5;
  - requested module/domain matches: 4;
  - class context is capped at 12.
- Explicit semantic-context scoring for properties:
  - explicit domain matches requested domain: 6;
  - explicit range matches requested range: 6;
  - requested module/domain matches: 3;
  - property context is capped at 12.
- Only explicit catalog facts contribute to context scoring.
- Catalog-status scoring:
  - curated Foundations member: 5;
  - wider packaged catalog member: 1.
- Local-project relevance scoring:
  - exact external IRI satisfies the asserted `already-used` definition: 3;
  - source module is already approved/imported: 2;
  - inferred facts alone do not contribute.
- Integer scores only; proportional scores round down; each category is capped; duplicate values or reasons never add repeated points.
- Confidence bands:
  - exact full-IRI match: `VERY_STRONG` regardless of optional metadata;
  - 60–100: `VERY_STRONG`;
  - 45–59: `STRONG`;
  - 30–44: `POSSIBLE`;
  - 20–29: `WEAK`;
  - below 20: `LOW_CONFIDENCE`.
- Normal search returns candidates scoring at least 20 unless a caller explicitly requests a lower threshold.
- If no candidate reaches 20, return an explicit empty or low-confidence state and never auto-select a result.
- Deterministic tie-break order:
  1. higher name-or-IRI score;
  2. higher preferred-label score;
  3. higher definition score;
  4. higher semantic-context score;
  5. curated before wider catalog;
  6. release before provisional, then informative;
  7. class before object property before datatype property only when no kind filter is supplied;
  8. normalized preferred label ascending;
  9. full IRI ascending.
- An ambiguity/tie group exists when two or more candidates have the same total score and the same category subtotals before lexical tie-breaking.
- Lexical tie-breakers order a tie group for display but do not claim one tied candidate is semantically better and do not permit automatic selection.
- Typed match reasons contain reason type, points, matched field, matched text/tokens, and optional related IRI.
- Candidate responses include score-model version, total score, category subtotals, ordered reasons, confidence band, tie-group ID where applicable, total result count, and page metadata.
- Default page size is 25; maximum is 100; ordering and page boundaries are stable; no silent truncation.
- Search uses the loaded compact index and does not parse the full RDF release per query.

#### Tests

- Query normalization for case, punctuation, underscores, hyphens, repeated whitespace, camel case, duplicate tokens, and the exact stopword set.
- Meaningful-token handling, including preserved `has` and `is`.
- Exact, case-insensitive, normalized, alternate-label, definition, full-IRI, and local-name matches.
- Every preferred-label, alternate-label, IRI, definition, context, catalog, and local-relevance score value.
- Verify scores do not stack across repeated labels/definitions and name/IRI use maximum values.
- Strict entity/module/curated/maturity filters.
- Parent/domain/range as non-excluding hints by default and strict filters only with `required=true`.
- Verify inferred local facts do not produce local-use points.
- Integer-only scores, floor rounding, category caps, adjusted confidence bands, and exact-IRI override.
- Low-confidence/empty behavior below 20.
- Complete tie-break ordering and tie-group reporting before lexical ordering.
- No automatic selection of tied or top candidates.
- Typed reasons, point attribution, subtotals, confidence band, score-model version, counts, and pagination.
- Repeated identical searches produce byte-for-byte equivalent ordered machine-readable responses.
- Warm searches use the session index and do not trigger full RDF parsing.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew test
```

#### Stop conditions

- Any score weight, threshold, normalization rule, stopword, confidence band, ambiguity rule, or tie-breaker is undefined.
- Ranking is nondeterministic or silently resolves semantic ties.
- Floating-point behavior can change ordering.
- Duplicate data inflates a score beyond category caps.
- Structural context is always treated as a hard filter rather than following the approved hint/required model.
- Search requires inference, embeddings, network access, or a new persistence layer.
- Search logic is duplicated in CLI or VS Code.

### Slice 5: Explicit User Dependency Review And Validation

#### Goal

Calculate a bounded, understandable user dependency set for a selected FIBO element while keeping the complete package-runtime import closure separate and already resolved by the fixed package.

#### Allowed files/modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/`
- `validation-engine/src/main/kotlin/com/entio/validation/`
- Matching tests in those modules
- `core-types` only for approved dependency-state corrections

#### Forbidden actions/modules

- Do not recompute or expose the full package import closure as a list the user must manually approve.
- Do not implement transitive semantic reasoning closure.
- Do not silently select or add user-visible dependencies.
- Do not modify local projects, FIBO assets, or proposal state.
- Do not implement CLI or VS Code behavior.
- Do not add external ontology sources, network access, or persistence.

#### Expected changes or output

- Preserve two separate dependency concepts:
  - package-runtime closure: the complete technical FIBO/Commons import closure already fixed in the package;
  - user dependency review: the bounded concepts, modules, imports, and local references relevant to the selected action.
- Dependency categories:
  - `SEMANTIC_PARENT`;
  - `PROPERTY_DOMAIN`;
  - `PROPERTY_RANGE`;
  - `SOURCE_ONTOLOGY`;
  - `OWL_IMPORT`;
  - `PACKAGE_RUNTIME`;
  - `METADATA`;
  - `LOCAL_REFERENCE`.
- Every dependency records:
  - required or optional;
  - direct or package-transitive;
  - already available or newly selected;
  - user-visible or implementation-only;
  - reason;
  - original IRI;
  - source module;
  - maturity;
  - package availability;
  - selection state.
- User-visible dependency review includes direct parents, explicit property domains/ranges, source ontology/module, local `owl:imports` or external-reference changes, and any required acknowledgement for provisional/informative content.
- `PACKAGE_RUNTIME` dependencies are reported as implementation-only package coverage and do not require individual user selection once the module is approved.
- Required user-visible dependencies block staging until explicitly approved.
- Deterministic validation for missing, conflicting, unsupported, stale, incomplete, or invalid selections.

#### Tests

- Parent, domain, range, source ontology, module import, package-runtime, metadata, and local-reference dependencies.
- Required/optional, direct/package-transitive, available/new, and user-visible/implementation-only states.
- Package-runtime closure does not create hundreds of manual user approvals.
- Already-available and asserted already-used dependencies.
- Missing, rejected, conflicting, duplicate, incomplete, provisional, and informative selections.
- Deterministic ordering and rejection without local mutation.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew test
```

#### Stop conditions

- The user dependency set silently expands beyond approved explicit catalog/package metadata.
- The UI-facing dependency set requires full OWL inference.
- Package completeness is weakened to simplify user review.
- Validation mutates source files or bundled assets.

### Slice 6: External Reuse, Local Extension, And Phase 4 Integration

#### Goal

Translate explicitly selected external elements and reviewed dependencies into existing staged proposal contracts, preserve original FIBO IRIs, update approved local package/module references, and integrate those changes with Phase 4 imports, reasoning fingerprints, SHACL fingerprints, coordinated application, reload, and rollback.

#### Allowed files/modules

- `core-types/src/main/kotlin/com/entio/core/` only for approved proposal-intent contracts
- `semantic-engine/src/main/kotlin/com/entio/semantic/`
- `validation-engine/src/main/kotlin/com/entio/validation/`
- `graph-diff/src/main/kotlin/com/entio/diff/` only for external proposal impact needed by existing diff contracts
- Matching tests in affected modules

#### Forbidden actions/modules

- Do not modify FIBO or Commons assets.
- Do not create local copies of external elements by default.
- Do not apply source or `entio.yaml` changes during selection or staging.
- Do not bypass baseline validation, semantic diff, explicit approval, the existing Phase 4 multi-source apply path, reload, or rollback.
- Do not reason over the wider catalog or generated indexes.
- Do not implement CLI or VS Code presentation.
- Do not add a new proposal, transaction, or persistence system.

#### Expected changes or output

- Direct external class/property reuse intents preserving original FIBO IRIs.
- Local subclass creation with an explicit external superclass.
- Project-owned `owl:imports` changes using original FIBO ontology IRIs where required.
- `externalOntologyReferences` changes using the fixed release, commit SHA, package fingerprint, and selected module IRIs.
- Offline import resolution through the fixed package IRI map.
- Combined previews and diffs that distinguish external references, project configuration changes, ontology imports, and local semantic changes.
- Only approved modules plus their packaged closure enter Phase 4 reasoning.
- Package fingerprint, release commit, selected modules, and closure fingerprints participate in Phase 4 reasoning/SHACL fingerprints and cache invalidation.
- Shape-only, catalog, index, and unselected FIBO content remain excluded from HermiT and SHACL data graphs.
- Validation for duplicate local copies, changed FIBO IRIs, modified package assets, stale baselines, mismatched package identity, unapproved modules, conflicting changes, and non-equivalent serialization.
- Reuse the existing Phase 4 `MultiSourceAtomicApplier`/coordinated rollback semantics; no second writer.

#### Tests

- Stage each supported reuse/local-extension intent without mutation.
- Stage and preview `entio.yaml`, `owl:imports`, and local semantic changes together where required.
- Combined external dependency and local-extension preview.
- Original FIBO element and ontology IRIs remain unchanged.
- Rejection leaves all local files unchanged.
- Approval applies only intended project references/imports/local changes through the Phase 4 path.
- Approved selected modules enter reasoning; the wider catalog does not.
- Package/module changes invalidate reasoning and SHACL fingerprints.
- Failed final verification restores prior local sources and configuration.
- Bundled FIBO and Commons files remain byte-for-byte unchanged.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew :graph-diff:test
./gradlew test
```

#### Stop conditions

- A proposal requires copying or rewriting bundled external content.
- A reuse operation changes an original FIBO IRI.
- Catalog/index content enters reasoning or SHACL validation.
- Application bypasses the existing approval or rollback path.
- Implementation requires another persistence or transaction framework.

### Slice 7: Machine-Readable CLI Boundary

#### Goal

Expose read-only session source selection, fixed FIBO release status, curated browsing, descriptor lookup, deterministic search, dependency review, and project-changing proposal preparation through thin structured CLI commands.

#### Allowed files/modules

- `cli/src/main/kotlin/com/entio/cli/`
- Matching `cli/src/test/kotlin/com/entio/cli/`
- Kotlin engine files only for missing reusable service adapters discovered during implementation
- Existing JSON request/response types and documentation for command examples

#### Forbidden actions/modules

- Do not parse FIBO or rank candidates in the CLI.
- Do not implement proposal, dependency, catalog, or search logic in CLI files.
- Do not write local ontology files or bundled assets directly from CLI handlers.
- Do not add a server, HTTP API, database, plugin system, or new serialization boundary.
- Do not change VS Code message models in this slice.

#### Expected changes or output

- Machine-readable commands for source listing, read-only session selection/browse, manifest status, curated browse, descriptor lookup, catalog search, dependency inspection, and external proposal preparation.
- Read-only source selection never changes project files. A separate proposal request is required for `externalOntologyReferences`, `owl:imports`, or local semantic changes.
- Structured success, empty, ambiguous, validation-error, and unavailable-package responses.
- Stable JSON schemas backed by Kotlin-owned contracts, including `fibo-schema-search-v1`, total score, category subtotals, adjusted confidence band, tie-group metadata, typed match reasons, result counts, page size, page position/cursor, and no-silent-truncation metadata.
- CLI commands delegate to reusable engine services and existing proposal lifecycle operations.

#### Tests

- Request parsing and response serialization.
- Source, manifest, browse, descriptor, search, dependency, and proposal responses.
- Empty and ambiguous results.
- Invalid filters, unsupported sources, unavailable package, incomplete dependencies, stale baselines, and proposal errors.
- End-to-end CLI tests using copied local fixtures and the bundled read-only package.

#### Verification commands

```bash
./gradlew :cli:test
./gradlew :cli:build
./gradlew test
```

#### Stop conditions

- The CLI owns semantic logic or duplicates ranking/descriptor assembly.
- JSON output exposes third-party library objects.
- A command mutates files outside the existing proposal application path.

### Slice 8: VS Code External Ontology Workbench

#### Goal

Add the external ontology workbench surface for source selection, FIBO release/package status, curated browsing, search, external details, dependency review, and controlled proposal preparation.

#### Allowed files/modules

- `vscode-extension/src/`
- `vscode-extension/src/test/`
- Extension package/configuration files only for the approved commands and views
- Kotlin CLI files only for response-shape fixes required by the stable machine-readable boundary

#### Forbidden actions/modules

- Do not parse FIBO, rank candidates, calculate dependencies, or generate RDF in TypeScript.
- Do not modify local project files or bundled FIBO assets directly from the extension.
- Do not add a web app, remote service, embedded database, or separate semantic implementation.
- Do not bypass staged changes, combined preview, validation, diff, approval, rejection, the existing Phase 4 multi-source application path, reload, or rollback.
- Do not add unrelated editing features or full Protégé parity.

#### Expected changes or output

- Read-only session source selection and exact release/package status, clearly separated from project-changing reference proposals.
- Curated Foundations browse section and wider catalog search.
- Label-first external result display with technical IRI details available on inspection.
- External descriptor details, total score, adjusted confidence band, category breakdown, typed match explanations, tie-group display, local/external status, maturity warnings, and dependency review.
- Result counts, default pages of 25, maximum pages of 100, stable pagination/incremental loading, and a clear `showing X of Y` state.
- Staged external reuse/local-extension changes integrated with the existing workbench workflow.
- Loading, empty, ambiguous, unavailable, blocked, approved, rejected, applied, and rollback states.

#### Tests

- CLI message normalization and rendering for all external response states.
- Source selection, browse, search, candidate details, dependency selection, and staged proposal flows.
- Rejection, refresh, unavailable-package, empty-search, low-confidence, tied-candidate, and ambiguous-search states.
- Verify the extension never invokes direct RDF parsing or file writes.

#### Verification commands

```bash
cd vscode-extension
npm install
npm run compile
npm test
cd ..
./gradlew test
```

#### Stop conditions

- The extension needs semantic logic that is not exposed by the CLI.
- TypeScript starts owning catalog loading, ranking, dependency selection, or RDF mutation.
- The workbench requires a new UI framework or web application.

### Slice 9: End-To-End Phase 5 Regression And Documentation Summary

#### Goal

Prove the complete offline FIBO browse/search/dependency/proposal workflow and document the actual Phase 5 result, limitations, and any deliberate deviations from the spec.

#### Allowed files/modules

- Copied temporary fixtures under test-controlled directories
- `semantic-engine/src/test/`
- `validation-engine/src/test/`
- `graph-diff/src/test/`
- `cli/src/test/`
- `vscode-extension/src/test/`
- `docs/phase-summaries/phase-5-summary.md`
- Focused test fixture documentation only where required

#### Forbidden actions/modules

- Do not change implementation behavior beyond fixes required by the regression tests.
- Do not mutate committed FIBO assets, `examples/simple-ontology`, or committed source fixtures.
- Do not add new features, sources, network access, persistence, or infrastructure.
- Do not update the spec or ExecPlan to hide an implementation gap; document mismatches in the summary and stop for approval when the gap is material.

#### Expected changes or output

- A copied-fixture test that selects FIBO, browses curated modules, searches classes/properties, inspects dependencies, stages an external reuse/local extension, previews and diffs it, rejects or approves it, verifies reload, and exercises rollback.
- SHA-256 verification that every FIBO and Commons package asset remains unchanged.
- Stable CLI and VS Code regression coverage.
- A Phase 5 summary based on the actual repository state, including limitations and follow-up work.

#### Tests

- Full phase regression using temporary project/package copies.
- No-network and asset-immutability checks.
- Repeated-run determinism checks for package generation, catalog, search, dependency, diff, pagination, and response ordering.

#### Verification commands

```bash
./gradlew test
./gradlew build
./gradlew check
cd vscode-extension && npm test
```

#### Stop conditions

- Full-phase verification fails.
- Any test mutates committed package or example assets.
- A required acceptance criterion is unimplemented or behaves differently from the approved spec.
- The completion summary cannot distinguish implemented behavior from planned behavior.

## Test Plan

Tests should be deterministic, offline, and copy committed fixtures before mutation.

### Package integrity

- Validate `master_2026Q2`, the full commit SHA, archive identity, Commons 1.3 dependency set, exact curated seeds, full package import closure, manifest/index schemas, SHA-256 records, and licensing/attribution metadata.
- Prove missing, malformed, mismatched, and incomplete package states produce structured issues.
- Hash every committed package asset before and after browse, search, preview, approval, rejection, apply, reload, and rollback; require identical SHA-256 values.

### Catalog and descriptor behavior

- Verify classes and properties expose explicit Phase 3 semantic fields, original IRIs, source metadata, and stable ordering.
- Verify curated, available, selected, already-used, local, and external status values.
- Verify no unstated relationships are inferred.

### Search behavior

- Verify the complete `fibo-schema-search-v1` normalization, exact stopword/meaningful-token policy, strict filters, optional/required context hints, exact integer weights, caps, adjusted confidence bands, exact-IRI override, tie groups, tie-breakers, empty/low-confidence behavior, pagination, and typed explanations.
- Repeat identical searches and compare complete ordered responses.

### Dependency and proposal behavior

- Verify separate package-runtime closure and user-visible parent, domain, range, source-module, import, metadata, and local-reference dependencies with all approved category/status flags.
- Verify incomplete or conflicting dependency sets block staging.
- Verify reuse preserves FIBO IRIs, local extension uses local IRIs, and rejection leaves sources unchanged.
- Verify project reference/import updates, Phase 4 reasoning fingerprint invalidation, approval, reload, stale-baseline blocking, and rollback through the existing Phase 4 workflow.

### Boundary behavior

- Verify CLI JSON responses are machine-readable and do not expose Jena or other third-party objects.
- Verify VS Code delegates to the CLI and renders all required states without direct semantic logic.

## Verification Commands

Run the slice-specific commands after each slice. At phase completion run:

```bash
./gradlew test
./gradlew build
./gradlew check
cd vscode-extension && npm install && npm test
```

Also run:

```bash
./gradlew :semantic-engine:generateFiboCatalog
./gradlew :semantic-engine:verifyFiboCatalog
```

Perform the mandatory package-and-catalog checkpoint no-network and performance checks recorded above.

## Rollback Notes

- Each slice must be committed independently and can be reverted without rewriting history.
- Before implementing a later slice, preserve the previous slice's contracts and assets; do not use a later slice to silently repair an earlier public contract.
- If package provenance, licensing, asset identity, or compatibility is invalid, remove only the Phase 5 package/implementation commit and retain the completed Phase 4 base.
- Proposal rejection must leave the local project unchanged.
- Failed final verification must use the existing Phase 4 multi-source application and rollback path to restore all affected local sources and configuration files.
- Bundled FIBO and Commons assets are immutable inputs. Any mutation is a test failure; restore from the pinned package commit rather than accepting changed hashes.

## Risks And Assumptions

- The package decisions are fixed by this ExecPlan. If the pinned release, Commons version, seed list, package format, licenses, or reference model prove infeasible, implementation must stop for a scope revision rather than choosing alternatives.
- FIBO Q2 2026 consolidated and deprecated some ontologies; exact seed identity and maturity checks protect the curated package from silent drift.
- The complete package closure may be substantially larger than the curated display set; compact committed indexes and session-scoped loading are required.
- FIBO and Commons source conventions may include blank nodes and metadata that are not consistent across all files. Preserve explicit information and report unsupported content rather than infer silently.
- Search ranking is deterministic and bounded; it is not OWL inference, fuzzy semantic similarity, or generative RAG.
- Strict filters can eliminate candidates; parent/domain/range are hints unless the caller explicitly marks them required.
- The wider catalog may contain tied candidates. Lexical tie-breaks provide stable display order but never authorize automatic selection.
- The existing proposal workflow remains session scoped. Phase 5 does not add durable catalog or proposal persistence.
- Phase 4 reasoning must consume only approved modules and their package closure, never the wider search index.

## Definition Of Done

- The package uses FIBO `master_2026Q2` at commit `f59157fe156e3d91b1c045222d0a7dc06b7d78a2`, OMG Commons 1.3 dependencies, the exact curated seeds, `entio-fibo-package-v1`, `fibo-catalog-v1`, SHA-256, and the approved local-reference model.
- The committed source archive, dependency assets, manifest, indexes, checksums, licenses, and attribution load offline and remain immutable.
- `generateFiboCatalog` and `verifyFiboCatalog` reproduce and verify committed indexes without network access.
- The complete package import closure is local and distinct from bounded user dependency review.
- External ontology contracts are Entio-owned and third-party semantic-web types remain inside `semantic-engine`.
- Curated browsing and wider deterministic search work for classes and properties using `fibo-schema-search-v1`, exact normalization, stopwords, strict filters, context hints, integer weights, adjusted confidence bands, tie groups, deterministic ordering, counts, pagination, and typed explanations.
- Catalog browsing/search uses the compact session index and does not reparse the full RDF release for each query.
- External descriptors preserve explicit labels, definitions, hierarchy, domains, ranges, source metadata, maturity, and original IRIs.
- `already-used` follows the approved asserted-state definition.
- Dependency review is explicit, categorized, ordered, and blocking when required user-visible selections are incomplete, while package-runtime dependencies do not become individual manual approvals.
- External reuse and local-extension actions preserve original FIBO IRIs and enter the existing staged proposal workflow without mutating local files before approval.
- Approved project references and imports use the fixed package identity and offline IRI map.
- Phase 4 reasoning and SHACL fingerprints include selected FIBO package/module identity; the wider catalog never enters reasoning.
- Phase 5 reuses the existing Phase 4 multi-source apply/reload/rollback path and creates no second writer.
- CLI and VS Code expose approved behavior without duplicating catalog, ranking, dependency, RDF, or proposal logic.
- Rejection, approval, reload, stale-baseline, serialization, reasoning invalidation, and rollback behavior is covered by copied-fixture tests.
- FIBO assets, Commons assets, and committed examples have identical SHA-256 values before and after tests.
- The mandatory package-and-catalog checkpoint passes and records package size and load/search timings.
- `./gradlew test`, `./gradlew build`, `./gradlew check`, `./gradlew :semantic-engine:verifyFiboCatalog`, and the approved VS Code verification commands pass.
- `docs/phase-summaries/phase-5-summary.md` accurately records implemented behavior, deviations, limits, package identity, and follow-up work.
