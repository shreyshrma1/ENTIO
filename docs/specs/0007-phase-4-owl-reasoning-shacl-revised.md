# Feature Spec: Phase 4 OWL Reasoning And SHACL Constraints

## Status

Draft

## Problem

Entio currently reads and edits explicit RDF facts, but it does not calculate the logical consequences of those facts or validate project data against user-authored SHACL rules.

Users need to be able to:

- See relationships and classifications that follow logically from the ontology.
- Distinguish facts written in source files from facts calculated by a reasoner.
- Detect ontology inconsistencies and classes that cannot have valid instances.
- Create SHACL rules without writing raw SHACL Turtle.
- See whether the current graph or a proposed change violates those rules.
- Review reasoning and SHACL effects before any source file is changed.

Phase 4 should add OWL reasoning and SHACL authoring and validation while preserving Entio’s existing staged, preview, semantic-diff, approval, atomic-save, reload, and rollback workflow.

## Goals

- Use the OWL API with the HermiT reasoner as the approved OWL reasoning boundary.
- Run reasoning when a project is loaded, on valid proposal previews, on combined staged previews, after approved ontology or instance-data changes are reloaded, and when the user requests a fresh run.
- Preserve asserted facts separately from inferred facts and never write inferred facts into source files automatically.
- Support the OWL 2 DL reasoning capabilities provided by the pinned HermiT version for small local projects.
- Guarantee class hierarchy classification, inferred individual types, ontology consistency checking, and unsatisfiable-class detection.
- Support equivalent-class, inverse-property, and transitive-property consequences where they are supported by HermiT and included in Entio’s approved Phase 4 feature set.
- Resolve approved local or bundled `owl:imports` into a complete import closure without silently downloading from the internet.
- Report missing, unsupported, unresolved, or cyclic imports clearly.
- Treat import cycles as graph structure rather than automatic failure: load each ontology once, report the cycle, and continue when all imported sources are available.
- Report OWL features used by the project and clearly identify unsupported or incomplete reasoning coverage.
- Provide simple deterministic explanations for supported inference patterns and reasoner-backed justifications where the selected libraries can provide them reliably.
- Run reasoning in a cancellable worker process so a timeout or user cancellation can safely stop the reasoning task without freezing the workbench.
- Reuse reasoning results when the asserted graph, import closure, and reasoner configuration have not changed.
- Let users create, edit, and delete supported SHACL node shapes, property shapes, and constraints through typed forms.
- Use Apache Jena SHACL as the approved SHACL validation boundary.
- Support common SHACL Core constraints for counts, required properties, datatypes, expected classes, allowed values, numeric limits, string patterns, string lengths, and closed shapes.
- Support SHACL severity and human-readable messages.
- Distinguish the data graph from the shapes graph.
- Run SHACL validation against asserted data by default.
- Offer an explicit asserted-plus-inferred validation mode without silently changing the validation basis.
- Compare current and preview validation results so only newly introduced or worsened violations block unrelated changes.
- Support multi-source atomic application when one approved proposal changes both ontology/data files and SHACL shape files.
- Use RDF graph isomorphism or equivalent blank-node-safe comparison for Turtle round-trip and post-save verification.
- Show reasoning, SHACL, and proposal-impact results through the Kotlin engine, machine-readable CLI, and VS Code workbench.
- Keep the Kotlin semantic engine as the source of truth for reasoning, validation, proposal effects, and source persistence.
- Preserve all existing source-safety guarantees.

## Non-Goals

- Full support for every OWL construct or every SHACL feature.
- SHACL-SPARQL constraints.
- Complex SHACL property paths such as inverse, sequence, alternative, zero-or-more, or one-or-more paths.
- SWRL or another custom rule language.
- LLM-generated constraints, explanations, or repairs.
- Automatic repair of ontology inconsistencies or SHACL violations.
- Automatic materialization of inferred triples into Turtle.
- Large-scale or distributed reasoning.
- Reasoning over the future external FIBO catalog.
- Document ingestion, document-to-KG conversion, Schema RAG, autonomous agents, or external entity resolution.
- Durable reasoning-result, validation-result, staged-session, or proposal persistence.
- Actual Git operations inside Entio.
- Full Protégé parity.
- A custom RDF, OWL, or SHACL framework.

Current repository and phase boundaries remain governed by `AGENTS.md`.

## Approved Libraries And Boundaries

### OWL reasoning

Phase 4 should use:

- OWL API as the Java/Kotlin boundary for constructing and querying OWL ontologies.
- HermiT as the reasoner.

HermiT is a Java OWL 2 DL reasoner that implements the OWL API reasoner interface. Kotlin will call it through normal JVM interoperability. The implementation should pin compatible OWL API and HermiT versions in the ExecPlan.

Third-party OWL API and HermiT types must remain inside `semantic-engine` or a narrowly owned reasoning worker. Entio-owned contracts must cross module, CLI, and UI boundaries.

### SHACL validation

Phase 4 should use Apache Jena SHACL because Entio already uses Apache Jena for RDF parsing and graph handling.

Jena SHACL types must remain inside the semantic engine. The CLI and VS Code extension should receive Entio-owned validation result objects.

### Reasoning worker

Reasoning should execute in a separate JVM worker process owned and launched by the Kotlin semantic engine.

The worker should:

- Receive the resolved ontology/import input and reasoner configuration.
- Return structured reasoning output.
- Support timeout.
- Support explicit cancellation by terminating the worker process.
- Leave the main Entio process and source files unaffected on failure.
- Discard late or stale results.
- Report worker startup, timeout, cancellation, crash, and malformed-output failures clearly.

The ExecPlan should define the narrow worker protocol and packaging approach.

## Supported Reasoning Behavior

### Guaranteed Phase 4 behavior

Using HermiT through the OWL API, Entio should guarantee:

- Classification of the asserted class hierarchy.
- Inferred superclass relationships.
- Inferred subclass relationships where represented as the inverse view of classified superclass results.
- Inferred individual types.
- Ontology consistency status.
- Unsatisfiable-class detection.
- Import-closure reasoning.
- Asserted/inferred separation.

### Additional supported consequences

Entio should also expose these consequences where represented in the supported project ontology and returned reliably by the chosen HermiT/OWL API versions:

- `owl:equivalentClass` consequences.
- `owl:inverseOf` consequences.
- `owl:TransitiveProperty` consequences.

The spec should not describe unsupported constructs as successfully reasoned. Each reasoning result must include:

- HermiT version.
- OWL API version.
- Supported feature report.
- Unsupported or partially supported construct report.
- Whether incomplete imports or unsupported constructs may affect completeness.

## Reasoning Inputs And Results

The reasoning service should operate on:

- A loaded Entio project.
- A valid individual preview graph.
- A combined staged preview graph.
- A reloaded graph after approved changes.
- The resolved import closure for the applicable graph.

Every reasoning result should contain:

- Status: running, completed, failed, cancelled, timed out, incomplete, or unavailable.
- Reasoner and OWL API version.
- Graph fingerprint.
- Import-closure fingerprint.
- Reasoner-configuration fingerprint.
- Import completeness.
- Consistency status.
- Inferred class relationships.
- Inferred individual types.
- Supported inferred property relationships.
- Unsatisfiable classes.
- Unsupported-feature findings.
- Warnings and errors.
- Result timing.
- Whether the result was reused from cache.

The same asserted graph, import closure, library versions, and configuration should produce stable normalized result ordering.

## Asserted And Inferred Facts

Entio must preserve a clear distinction between:

- Asserted facts explicitly present in source files.
- Inferred facts calculated by HermiT.

Every displayed or machine-readable relationship should identify its origin.

Example:

```text
Commercial Loan
- Asserted parent: Loan
- Inferred parent: Financial Product
```

Inferred facts should remain in a computed reasoning view. They should not be automatically written to Turtle.

Users must not be able to edit an inferred fact directly. They should instead edit the asserted facts that produce or remove the inference.

## Import-Aware Reasoning

Before reasoning, Entio should resolve `owl:imports` from only:

- Ontology files in the current project.
- Bundled local assets.
- Explicitly configured local sources.

Entio should never silently download imports.

Import resolution should:

- Build the complete locally available import closure.
- Load each ontology once.
- Preserve source attribution for asserted facts.
- Detect cycles.
- Report cycles as informational or warning when all members resolve successfully.
- Report missing or unsupported imports.
- Apply a configured incomplete-import policy.

Default incomplete-import policy:

- Missing required imports block a complete reasoning result.
- Entio may return an explicitly incomplete result if the user or project policy allows it.
- An incomplete result must never be shown as complete.

## OWL Feature And Profile Reporting

Before reasoning, Entio should inspect the resolved ontology and report:

- OWL constructs present.
- Whether the ontology fits OWL 2 DL under the selected OWL API checks.
- Constructs supported by the Phase 4 reasoning feature set.
- Constructs ignored, unsupported, or only partially surfaced by Entio.
- Whether those limitations may make results incomplete.

A completed run must not imply that every parsed RDF statement participated in reasoning.

## Reasoning Explanations

Phase 4 should support explanations for:

- A selected inferred superclass relationship.
- A selected inferred individual type.
- An inconsistency.
- An unsatisfiable class.

The first implementation should provide:

- Deterministic path-based explanations for subclass chains.
- Deterministic explanations for type propagation.
- Deterministic explanations for inverse-property and transitive-property consequences that Entio explicitly supports.
- Relevant asserted facts and their source files.
- The named reasoning rule or OWL relationship connecting them.

For inconsistency and unsatisfiable classes:

- Use reasoner-backed justifications if the pinned OWL API/HermiT integration provides them reliably.
- Otherwise return the relevant reported axioms or entities where available and clearly state that a minimal justification is unavailable.

The system should not promise a complete minimal explanation for every possible OWL 2 DL inference.

## Reasoning Lifecycle And Cache Invalidation

Reasoning should run:

1. When an Entio project is loaded.
2. When the user requests a fresh reasoning run.
3. After a valid ontology or instance-data proposal preview.
4. After staged ontology or instance-data edits are combined.
5. After approved ontology or instance-data changes are saved and reloaded.
6. After an import configuration changes.

Reasoning should not rerun when only SHACL shape files change and the data/ontology/import graph is unchanged.

Invalidation rules:

- Ontology/schema change: rerun OWL reasoning and SHACL validation.
- Instance-data change: rerun OWL reasoning when instance reasoning may change, then rerun SHACL validation.
- Import change: rerun OWL reasoning and SHACL validation.
- Shape-only change: reuse the OWL result and rerun SHACL validation only.
- UI-only change: rerun neither.

Reasoning should not run on each form keystroke.

## Reasoning Timeout And Cancellation

The workbench should show:

- Running status.
- Cancel action.
- Elapsed time.
- Timeout status.
- Failure or incomplete state.

Cancellation should terminate the reasoning worker process.

Default timeout and tested project-size limits must be pinned in the ExecPlan and exposed in reasoner metadata.

A timeout, cancellation, or worker failure must:

- Leave the project usable.
- Leave source files unchanged.
- Mark the reasoning result unavailable, failed, cancelled, or timed out.
- Prevent stale or partial output from replacing the last valid result.

## SHACL Graph Roles

Project configuration should assign each ontology source one or more roles:

- `ontology`
- `data`
- `shapes`

A source may have more than one role only when explicitly configured.

Backward-compatible defaults:

- Existing sources without a role default to `ontology` and `data`.
- No source defaults to `shapes`.
- A source supplies shapes only when explicitly configured.

The validation request must identify:

- Data graph sources.
- Shapes graph sources.
- Whether inferred facts are included.

Imported ontology sources are not treated as shapes unless explicitly configured.

## Stable SHACL Identity

User-created node shapes and property shapes must receive stable IRIs generated through Entio’s deterministic IRI-generation rules.

Phase 4 should not rely on blank nodes as the editable identity of user-created shapes.

Supported constraints should be represented as fields or explicit statements owned by a stable shape/property-shape IRI.

This allows reliable edit, delete, diff, and dependency behavior.

## Supported SHACL Targets

Phase 4 should support:

- `sh:targetClass`
- `sh:targetNode`
- `sh:targetSubjectsOf`
- `sh:targetObjectsOf`

The spec should allow supported combinations only when their meaning is unambiguous.

The UI should show each configured target clearly.

## Supported SHACL Property Paths

Phase 4 should support direct property IRI paths only:

```turtle
sh:path ex:hasBorrower
```

The following are deferred:

- Inverse paths.
- Sequence paths.
- Alternative paths.
- Zero-or-more paths.
- One-or-more paths.
- Zero-or-one paths.

## Supported SHACL Constraints

The first implementation should support SHACL Core forms for:

- Minimum count.
- Maximum count.
- Exact count through equal minimum and maximum.
- Required property through minimum count of one.
- Expected datatype.
- Expected class.
- Allowed values with `sh:in`.
- Inclusive minimum and maximum numeric values.
- Exclusive minimum and maximum numeric values when supported by Jena SHACL.
- String pattern.
- Minimum and maximum string length when supported by Jena SHACL.
- Closed shape.
- Ignored properties.
- Severity: violation, warning, or information.
- Human-readable validation message.

For closed shapes:

- `rdf:type` should be ignored by default unless the user explicitly chooses otherwise.
- Additional ignored properties should be explicit and reviewable.
- The validation mode must state whether inferred properties are included.

## SHACL Authoring

The VS Code workbench should let users:

- Create a node shape.
- Create a property shape with a stable IRI.
- Add a direct property constraint to a node shape.
- Edit a supported constraint.
- Delete a constraint.
- Delete a property shape.
- Delete a node shape.
- Change supported targets.
- Set severity.
- Set a human-readable message.
- Configure closed-shape ignored properties.
- View the RDF representation in technical details.

Every SHACL edit must be translated into RDF graph changes by Kotlin and use the existing proposal workflow.

## SHACL Validation Modes

### Default mode

Phase 4 should validate asserted data only by default.

This means SHACL checks the facts explicitly present in configured data graph sources.

### Optional inferred mode

The user may explicitly request validation against:

- Asserted data.
- Asserted data plus the supported inferred reasoning view.

Every report must identify which mode was used.

Entio must not silently change the mode based on available reasoner output.

If inferred mode is requested but reasoning is incomplete, failed, cancelled, or timed out:

- The inferred validation mode should not run as if complete.
- Entio should either block it or return an explicitly incomplete result according to the approved policy.

## SHACL Validation Results

Each validation result should include:

- Stable normalized result identity.
- Severity.
- Message.
- Focus node.
- Direct property path where available.
- Producing shape.
- Constraint component.
- Invalid value where available.
- Source ontology/file attribution where available.
- Data-graph fingerprint.
- Shapes-graph fingerprint.
- Validation mode.
- Whether it belongs to the current graph or preview graph.

## Baseline-Aware Approval Policy

Approval should compare the current graph’s reasoning and SHACL results with the preview graph’s results.

### SHACL approval rules

- A new violation introduced by the proposal blocks approval.
- An existing violation made worse by the proposal blocks approval.
- An unchanged existing violation remains visible but does not block an unrelated proposal.
- A violation resolved by the proposal is shown as an improvement.
- Warnings and informational results remain visible and do not block by default.
- A future project policy may make warnings blocking, but that is not required in Phase 4.

### Reasoning approval rules

- A newly introduced inconsistency blocks approval.
- A newly introduced unsatisfiable class blocks approval.
- An existing inconsistency or unsatisfiable class made worse blocks approval.
- An unchanged existing issue remains visible but does not block an unrelated repair or metadata edit.
- A resolved inconsistency or unsatisfiable class is shown as an improvement.
- Unsupported constructs that make the preview reasoning result materially incomplete block approval unless the approved policy explicitly allows incomplete reasoning.

## Processing Order

For each load, preview, combined preview, and post-save reload:

1. Load or construct the asserted graph.
2. Resolve the import closure.
3. Run OWL reasoning when required by the invalidation rules.
4. Build or reuse the asserted-plus-inferred reasoning view.
5. Run SHACL validation in the explicitly selected mode.
6. Compare current and preview reasoning results.
7. Compare current and preview SHACL results.
8. Return graph changes, reasoning impact, and SHACL impact together.

If reasoning fails:

- Asserted-only SHACL validation may still run.
- The result must clearly state that reasoning was unavailable.
- Inferred-mode SHACL validation must not be presented as complete.

## Proposal Impact Report

Reasoning and SHACL findings should not be mixed into the RDF graph diff as if they were graph edits.

A proposal review should contain three separate sections:

```text
Proposal Impact
├── Explicit Semantic Diff
├── Reasoning Impact
└── SHACL Validation Impact
```

### Explicit Semantic Diff

- Explicit triples added.
- Explicit triples removed.
- Explicit labels or annotations changed.

### Reasoning Impact

- New inferred facts.
- Inferred facts no longer produced.
- New or resolved inconsistencies.
- New or resolved unsatisfiable classes.
- Unsupported-feature or incomplete-result changes.

### SHACL Validation Impact

- New violations.
- Worsened violations.
- Resolved violations.
- Unchanged existing violations.
- New, resolved, and retained warnings or informational results.

Expected Entio-owned types may include:

- `ProposalImpactReport`
- `ReasoningDelta`
- `ShaclValidationDelta`

## Multi-Source Atomic Application

Phase 4 should support one approved proposal changing multiple local files, including:

- Ontology/schema files.
- Instance-data files.
- SHACL shapes files.

The save process should:

1. Confirm that every affected source still matches the approved baseline.
2. Build a temporary updated version of every affected file.
3. Parse and verify every temporary file.
4. Compare the combined asserted graph using graph isomorphism.
5. Rerun reasoning and SHACL validation against the temporary combined project.
6. Confirm the normalized post-save impact matches the approved preview.
7. Replace all target files as one controlled operation.
8. Reload the complete project.
9. Rerun required reasoning and SHACL validation.
10. Restore every original file if any replacement or final verification fails.

No partial subset of a multi-source proposal may remain applied.

The proposal baseline must include fingerprints for every affected source.

## Blank Nodes, RDF Lists, And Semantic Equivalence

SHACL features such as `sh:in` and `sh:ignoredProperties` use RDF lists and blank nodes.

Entio must not compare parser-generated blank-node IDs directly.

Round-trip and post-save verification should use:

- RDF graph isomorphism for asserted graphs.
- Stable normalization for inferred fact sets.
- Stable normalization for SHACL validation results.
- Stable normalization for explanation content where compared.

Runtime timestamps, third-party iteration order, parser-local blank-node IDs, and explanation ordering should not affect semantic equivalence.

## Post-Save Verification

Post-save verification should compare normalized semantic content rather than raw runtime output.

It should verify:

- Asserted graph isomorphism.
- Expected explicit semantic diff.
- Normalized inferred fact set.
- Consistency status.
- Unsatisfiable-class set.
- Normalized SHACL validation results.
- Expected reasoning and validation deltas.
- Complete rollback if any required comparison fails.

## VS Code Workbench

The workbench should add a reasoning and constraints area.

### Reasoning View

Users should be able to:

- See consistency status.
- See last reasoning status and fingerprint.
- Refresh reasoning.
- Cancel a running reasoning task.
- See inferred superclass/subclass relationships.
- See inferred individual types.
- See supported inferred property relationships.
- See asserted and inferred facts separately.
- See unsatisfiable classes.
- See import closure and import warnings.
- See supported and unsupported OWL features.
- Request an explanation.
- See timeout, cancellation, incomplete, and failure states.
- Open affected ontology items and source facts.

### SHACL Authoring View

Users should be able to:

- Browse node shapes.
- Browse property shapes.
- Create shapes with stable IRIs.
- Select supported targets.
- Create, edit, and delete supported constraints.
- Configure direct property paths.
- Set severity and message.
- Configure closed-shape ignored properties.
- Preview and stage several SHACL edits.
- Review a combined proposal.
- Approve or reject.
- Open changed sources.

### SHACL Results View

Users should be able to:

- Choose asserted-only or asserted-plus-inferred validation mode.
- View violations, warnings, and information.
- Filter by severity, shape, entity, or property.
- Open affected entities and shapes.
- View invalid values.
- Distinguish new, worsened, resolved, and unchanged results.
- See which data and shapes graphs were validated.

The TypeScript extension must not:

- Perform reasoning.
- Run SHACL validation.
- Choose approval policy.
- Construct SHACL triples.
- Write source files.
- Implement graph isomorphism.
- Bypass the existing proposal lifecycle.

## Kotlin And CLI Boundary

The Kotlin semantic engine should own:

- OWL API/HermiT ontology construction and querying.
- Reasoning worker launch, timeout, cancellation, and result parsing.
- Import closure resolution.
- Asserted/inferred separation.
- Consistency and unsatisfiable-class reporting.
- OWL feature reporting.
- Explanation construction.
- Jena SHACL shape parsing and validation.
- SHACL typed-edit translation.
- Graph-role and validation-mode behavior.
- Baseline-aware approval policy.
- Proposal impact comparison.
- Multi-source atomic application.
- Graph-isomorphic verification.
- Reload and rollback.

The machine-readable CLI should expose thin commands to:

- Run, cancel, and fetch reasoning.
- Fetch asserted and inferred descriptions.
- Fetch import and OWL feature reports.
- Request reasoning explanations.
- List and describe SHACL shapes.
- Run SHACL validation in an explicit mode.
- Fetch validation results and deltas.
- Prepare SHACL edits.
- Preview, validate, apply, reject, reload, and roll back combined multi-source proposals.
- Return `ProposalImpactReport` results.

The CLI should not implement reasoning, SHACL semantics, approval policy, or source writing itself.

## Inputs And Outputs

### Inputs

- Entio project and source-role configuration.
- Asserted ontology and instance-data graphs.
- Approved local or bundled imports.
- Shapes graphs.
- Project or preview graph fingerprints.
- HermiT/OWL API configuration.
- Timeout and cancellation controls.
- SHACL validation mode.
- Supported SHACL edit requests.
- Combined staged edits.
- Existing proposal baselines.

### Outputs

- Reasoning status and metadata.
- Import closure and completeness report.
- OWL feature/profile report.
- Asserted and inferred fact sets.
- Consistency and unsatisfiable-class results.
- Reasoning explanations.
- SHACL shape descriptors.
- SHACL validation reports and normalized results.
- Baseline-aware approval decision.
- Explicit semantic diff.
- Reasoning delta.
- SHACL validation delta.
- Combined proposal impact report.
- Multi-source apply and rollback results.
- Machine-readable CLI output.
- VS Code reasoning, SHACL, and review views.

## Expected Data Contracts

Phase 4 may introduce or refine:

- `ReasoningRequest`
- `ReasoningResult`
- `ReasoningStatus`
- `ReasonerMetadata`
- `OwlFeatureReport`
- `ImportClosure`
- `ImportResolutionResult`
- `ReasoningFingerprint`
- `SemanticFactOrigin`
- `InferredRelationship`
- `ConsistencyResult`
- `UnsatisfiableClassResult`
- `ReasoningExplanation`
- `ReasoningWorkerRequest`
- `ReasoningWorkerResult`
- `ReasoningRunControl`
- `ShaclGraphRole`
- `ShaclValidationMode`
- `ShaclShapeDescriptor`
- `ShaclNodeShapeDescriptor`
- `ShaclPropertyShapeDescriptor`
- `ShaclConstraintDescriptor`
- `ShaclValidationReport`
- `ShaclValidationResult`
- `ShaclSeverity`
- `ProposalImpactReport`
- `ReasoningDelta`
- `ShaclValidationDelta`
- `MultiSourceProposalBaseline`
- `MultiSourceApplyResult`
- `CreateNodeShapeEdit`
- `CreatePropertyShapeEdit`
- `AddShaclConstraintEdit`
- `UpdateShaclConstraintEdit`
- `RemoveShaclConstraintEdit`
- `DeleteShaclShapeEdit`

Names may change during implementation, but responsibilities must remain clear. Third-party OWL API, HermiT, and Jena SHACL types must not cross the semantic-engine boundary.

## Validation And Error Handling

Validation should report structured issues for:

- OWL API or HermiT initialization failure.
- Reasoning worker startup, crash, timeout, cancellation, or malformed output.
- Missing, unresolved, unsupported, or incomplete imports.
- Import cycles.
- Unsupported OWL features.
- Inconsistent ontology.
- Unsatisfiable class.
- Missing or ambiguous graph roles.
- Missing shapes graph.
- Invalid SHACL target.
- Unsupported property path.
- Missing direct property path.
- Invalid count range.
- Invalid datatype IRI.
- Missing expected class.
- Invalid allowed-value list.
- Invalid numeric limit.
- Invalid pattern.
- Invalid severity or required message.
- Duplicate shape IRI.
- Duplicate constraint.
- Missing shape or constraint removal target.
- Attempt to edit an inferred fact directly.
- Invalid or stale multi-source baseline.
- New or worsened blocking issue.
- Unsupported construct causing materially incomplete preview.
- Asserted graph non-isomorphism.
- Post-save normalized reasoning mismatch.
- Post-save normalized SHACL-result mismatch.
- Multi-source replacement or rollback failure.

Failures must not leave partial source changes.

## Test Cases

### HermiT Reasoning

- Class hierarchy classification.
- Inferred superclass relationships.
- Inferred individual types.
- Equivalent-class consequences.
- Inverse-property consequences.
- Transitive-property consequences.
- Consistent ontology.
- Inconsistent ontology.
- Unsatisfiable class.
- Asserted/inferred separation.
- Stable normalized output.
- Reasoning on load, preview, combined preview, and reload.
- Local import closure.
- Bundled import closure.
- Missing import.
- Import cycle with complete closure.
- Incomplete import policy.
- OWL feature report.
- Unsupported-feature report.
- Cache reuse.
- Cache invalidation.
- Worker timeout.
- Worker cancellation.
- Worker crash.
- Safe failure.

### Reasoning Explanations

- Subclass-chain explanation.
- Type-propagation explanation.
- Inverse-property explanation.
- Transitive-property explanation.
- Inconsistency explanation when supported.
- Unsatisfiable-class explanation when supported.
- Explicit “minimal justification unavailable” result when not supported.

### SHACL Authoring

- Stable IRI generation for node and property shapes.
- Create node shape.
- Create property shape.
- Add direct-property-path constraints.
- Edit constraint.
- Delete constraint.
- Delete property shape.
- Delete node shape.
- Count constraints.
- Datatype constraint.
- Expected-class constraint.
- Allowed-value list.
- Numeric limits.
- String pattern and lengths.
- Closed shape.
- Default ignored `rdf:type`.
- Explicit ignored properties.
- Severity and message.
- Preview without mutation.
- Multi-source staged shape changes.
- Reject without mutation.
- Apply atomically.

### SHACL Validation

- Asserted-only default mode.
- Explicit inferred mode.
- Required-property violation.
- Count violations.
- Datatype violation.
- Expected-class violation.
- Allowed-value violation.
- Numeric-range violation.
- Pattern violation.
- Closed-shape violation.
- New violation.
- Worsened violation.
- Unchanged existing violation.
- Resolved violation.
- Warning and information behavior.
- Stable result identity.
- Data/shapes graph fingerprints.
- Inferred-mode failure when reasoning is incomplete.

### Blank Nodes And Round Trips

- `sh:in` list round trip.
- `sh:ignoredProperties` list round trip.
- Blank-node ID changes with graph-isomorphic equality.
- Stable normalized SHACL results after reparse.

### Multi-Source Atomic Application

- One proposal changes ontology and shapes files.
- All temporary files verify before replacement.
- Failure before replacement changes no files.
- Failure during replacement restores all originals.
- Post-save mismatch restores all originals.
- Stale fingerprint in one affected source blocks the full proposal.

### Baseline-Aware Approval

- New violation blocks.
- Worsened violation blocks.
- Unchanged existing violation does not block.
- Resolved violation is shown as improvement.
- New inconsistency blocks.
- Unchanged existing inconsistency does not block unrelated repair.
- New unsatisfiable class blocks.
- Unsupported construct causing incomplete preview blocks according to policy.

### Integration

- Project load reasoning and asserted-only SHACL validation.
- Shape-only change reuses reasoning.
- Ontology change reruns reasoning and SHACL.
- Combined ontology/data/shape preview.
- Proposal impact report.
- Reject without mutation.
- Atomic apply and reload.
- Stale baseline.
- Rollback.
- CLI machine-readable responses.
- VS Code reasoning, SHACL, cancellation, and impact views.

Tests should use temporary copies of committed fixtures. They must not modify committed examples or bundled assets.

## Acceptance Criteria

- HermiT and the OWL API are pinned and used through a Kotlin/JVM-compatible semantic-engine boundary.
- Loading a project automatically produces explicit reasoning and SHACL statuses.
- Users can see consistency, inferred class relationships, inferred types, supported property consequences, and unsatisfiable classes.
- Asserted and inferred facts remain distinct.
- Import closure, cycles, missing imports, and completeness are visible.
- OWL feature support and limitations are visible.
- Users can request supported explanations.
- Reasoning can time out or be cancelled without freezing the workbench or changing source files.
- Shape-only changes reuse valid reasoning results.
- Existing sources load under backward-compatible graph-role defaults.
- Users can create, edit, and delete supported SHACL shapes and constraints using stable IRIs and direct property paths.
- SHACL validates asserted data by default and inferred data only through an explicit mode.
- Validation results identify data graph, shapes graph, mode, source, focus node, path, shape, severity, and value where available.
- Approval is baseline-aware: new or worsened violations and reasoning failures block, while unchanged existing issues remain visible without blocking unrelated work.
- Proposal review separates explicit graph changes, reasoning impact, and SHACL impact.
- Multi-source proposals apply atomically or restore every original source.
- RDF-list and blank-node round trips use graph-isomorphic comparison.
- Post-save verification compares normalized semantic results rather than runtime-specific output.
- Inferred facts are never silently written into Turtle.
- Kotlin owns all reasoning, SHACL, approval, verification, and source behavior.
- CLI and VS Code remain thin boundaries.
- Focused and end-to-end tests pass.
- Phase 4 introduces no Schema RAG, document processing, AI, automatic repair, materialization, persistence, or Git workflow.

## Open Questions For The ExecPlan

- Which pinned OWL API and HermiT versions are mutually compatible with the repository’s Java version?
- Which exact OWL API checks will be used for OWL 2 DL/profile reporting?
- Which reasoner-backed explanation facility, if any, is reliable with the pinned HermiT version?
- What worker-process request and response format should be used?
- What default timeout and tested project-size threshold should be selected?
- What project setting allows an explicitly incomplete import closure?
- What exact graph-role syntax should be added to `entio.yaml`?
- Should a source with both `ontology` and `data` roles be represented as a list or a combined enum?
- Which additional properties besides `rdf:type` should be ignored by default for closed shapes?
- Which numeric datatypes and lexical forms should the supported numeric constraints accept?
- Which regular-expression syntax and flags should the UI expose?
- How should “worsened” be defined when a SHACL result remains the same but its count or invalid values increase?
- What rollback mechanism provides the safest practical multi-file replacement on supported operating systems?
