# Phase 4 Scope

Phase 4 adds OWL reasoning and SHACL constraint authoring and validation to Entio.

Phase 3 already gives Entio a structured semantic description layer for explicit ontology facts, labels, definitions, annotations, classes, properties, and individuals.

Phase 4 should add two new capabilities:

- OWL reasoning, so Entio can calculate relationships and classifications that logically follow from the ontology.
- SHACL constraints, so users can define rules that the ontology and knowledge graph must satisfy.

Reasoning and SHACL validation should run automatically when an Entio project is loaded and whenever an approved change updates the ontology, instance data, or SHACL shapes.

Phase 4 should preserve the existing controlled workflow: no source file changes without preview, semantic diff, deterministic checks, explicit approval, atomic save, reload, and rollback.

## Phase 4 Goals

Phase 4 should support:

- Running OWL reasoning automatically when an Entio project is loaded.
- Running OWL reasoning automatically against every proposed preview graph before approval.
- Running OWL reasoning again after approved changes are saved and the project is reloaded.
- Keeping explicitly written facts separate from inferred facts.
- Showing inferred class relationships.
- Showing inferred individual types.
- Showing supported inferred property relationships.
- Checking ontology consistency.
- Identifying classes that cannot logically have valid instances.
- Reporting reasoning errors and unsupported reasoning cases clearly.
- Letting users create, edit, and delete SHACL node shapes.
- Letting users create, edit, and delete SHACL property shapes.
- Letting users apply SHACL rules to classes, individuals, and properties where supported.
- Supporting common SHACL constraints for required properties, value counts, datatypes, expected classes, allowed values, numeric limits, string patterns, and closed shapes.
- Letting users define a validation severity and human-readable message for a SHACL constraint.
- Running SHACL validation automatically when an Entio project is loaded.
- Running SHACL validation automatically against every proposed preview graph.
- Running SHACL validation again after approved changes are saved and the project is reloaded.
- Showing SHACL violations, warnings, and informational results in the VS Code workbench.
- Linking each SHACL result to the affected ontology item, property, shape, and source where available.
- Showing reasoning and SHACL results through the Kotlin engine and machine-readable CLI.
- Reusing the existing staged-edit, combined-preview, semantic-diff, approval, atomic-save, reload, and rollback workflow for SHACL edits.
- Keeping the Kotlin semantic engine as the single source of truth for reasoning, SHACL interpretation, validation, and source changes.
- Preparing the product for later Schema RAG, document-to-KG, and autonomous agent phases without adding those capabilities in Phase 4.
- Resolving approved `owl:imports` relationships from local or bundled ontology sources before reasoning.
- Running reasoning across the project and its complete locally available import set.
- Reporting missing, unresolved, cyclic, or unsupported imports without silently downloading files from the internet.
- Showing the asserted facts that justify important inferences, inconsistencies, and unsatisfiable classes.
- Detecting which OWL features and profile the project uses and reporting constructs that the selected reasoner does not support.
- Providing reasoning run status, timeouts, cancellation, graph fingerprints, result reuse for unchanged graphs, and safe failure behavior.
- Distinguishing ontology and instance-data sources from SHACL shape sources.
- Identifying which graph supplies the data being validated and which graph supplies the SHACL rules.

Phase 4 should make Entio capable of deriving logical consequences from the ontology and enforcing user-defined graph rules.

## Working Definitions

- asserted fact: a fact explicitly written in an ontology source file.

- inferred fact: a fact calculated by the reasoner because it logically follows from asserted facts.

- reasoning run: one complete execution of the approved reasoner against a loaded or previewed project graph.

- inferred class relationship: a superclass or subclass relationship calculated by the reasoner rather than written directly.

- inferred individual type: a class membership calculated by the reasoner rather than written directly.

- inferred property relationship: a supported property assertion or property relationship calculated by the reasoner.

- consistent ontology: an ontology whose statements do not produce a logical contradiction under the supported reasoning rules.

- unsatisfiable class: a class whose definition makes it impossible for any valid individual to belong to that class.

- SHACL shape: a collection of rules that describes what valid graph data should look like.

- node shape: a SHACL shape that validates a class, individual, or selected set of nodes.

- property shape: a SHACL shape that validates values reached through a selected property.

- SHACL constraint: one rule inside a shape, such as a required datatype, minimum count, maximum count, allowed class, or allowed value.

- validation result: one SHACL violation, warning, or informational finding produced when the graph is checked against its shapes.

- reasoning view: a computed project view that includes inferred facts without automatically writing those facts into the ontology source.

- materialized inference: an inferred fact that is explicitly written back into the source graph. Materialization is not required in Phase 4.

- import closure: the project ontology plus all approved local or bundled ontologies reached through resolved `owl:imports` relationships.

- reasoning explanation: the asserted facts that justify an important inferred fact, inconsistency, or unsatisfiable class.

- OWL profile report: a structured summary of the OWL features used by the project and whether they are supported by the selected reasoner.

- reasoning fingerprint: a stable identifier for the exact graph and import set used by a reasoning run.

- data graph: the ontology and instance facts being checked by SHACL.

- shapes graph: the SHACL node shapes, property shapes, and constraints used to validate the data graph.

## OWL Reasoning

Phase 4 should introduce a reusable reasoning service in the Kotlin semantic engine.

The reasoning service should operate on:

- A loaded Entio project.
- An in-memory preview graph.
- A reloaded graph after approved changes are saved.

The reasoning service should return structured results rather than writing inferred facts directly into source files.

### Required Reasoning Capabilities

Phase 4 should support, at minimum:

- Transitive class hierarchy reasoning.
- Inferred superclass and subclass relationships.
- Inferred individual types from class hierarchy.
- Equivalent-class consequences where supported by the selected reasoner and current ontology model.
- Inverse-property consequences where supported.
- Transitive-property consequences where supported.
- Consistency checking.
- Unsatisfiable-class detection.
- Structured reporting of unsupported constructs or reasoner failures.

The exact supported OWL profile and reasoner behavior should be defined in the Phase 4 specification.

Phase 4 does not need to support every OWL feature.

The first implementation should favor predictable behavior on small local ontology projects.

### Asserted And Inferred Separation

Entio must preserve a clear distinction between facts written by the user and facts calculated by the reasoner.

Every semantic relationship shown in the UI or returned through the CLI should indicate its origin, such as:

- Asserted.
- Inferred.

For example:

```text
Commercial Loan
- Asserted parent: Loan
- Inferred parent: Financial Product
```

Inferred facts should not be added to Turtle files automatically.

The reasoning view should be rebuilt whenever the project or preview changes.

### Reasoning Lifecycle

Reasoning should run:

1. When an Entio project is loaded.
2. When a user requests a fresh reasoning run.
3. When an edit is previewed.
4. When several staged edits are combined into one final preview.
5. After an approved proposal is applied and the project is reloaded.
6. After SHACL shapes or ontology structure are changed.

Reasoning should not run on every keystroke in a workbench form.

It should run after a valid preview or explicit refresh action.

### Reasoning Results

A reasoning result should include:

- Run status.
- Reasoner name and version where available.
- Consistency status.
- Inferred class relationships.
- Inferred individual types.
- Supported inferred property relationships.
- Unsatisfiable classes.
- Warnings.
- Errors.
- Unsupported constructs or limitations.
- Baseline or graph fingerprint used for the run.

Reasoning results should be deterministic for the same graph and reasoner configuration.


### Import-Aware Reasoning

Entio should resolve approved `owl:imports` relationships before running the reasoner.

Import resolution should:

- Follow imports available from the project, bundled assets, or explicitly configured local sources.
- Build one complete import closure for the reasoning run.
- Preserve the source ontology for asserted facts.
- Detect missing imports.
- Detect import cycles without repeatedly loading the same ontology.
- Report unresolved or unsupported imports clearly.
- Never download an ontology from the internet silently.
- Allow reasoning to continue only when the configured policy permits incomplete imports.

Reasoning results should identify the import closure and graph fingerprint used for the run.

### Reasoning Explanations

Entio should provide explanations for important results, including:

- Why a superclass or subclass relationship was inferred.
- Why an individual received an inferred type.
- Why the ontology is inconsistent.
- Why a class is unsatisfiable.

An explanation should list the relevant asserted facts used by the reasoner.

The first implementation does not need to explain every inferred fact automatically. It should support explanations for user-selected results and all reported inconsistencies or unsatisfiable classes.

### OWL Feature And Profile Reporting

Before or during reasoning, Entio should inspect the project and report:

- The OWL features used by the ontology.
- The supported OWL profile where one can be determined.
- Features supported by the selected reasoner.
- Features that are unsupported, ignored, or only partially supported.
- Whether incomplete support may affect the reasoning result.

Entio should not report a reasoning run as fully successful if relevant ontology constructs were not understood.

### Reasoning Performance And Failure Safeguards

Reasoning should not make the workbench unusable.

Phase 4 should support:

- Running, completed, failed, cancelled, and timed-out statuses.
- A configurable or implementation-defined timeout suitable for small local projects.
- A user cancellation action.
- Reuse of an existing result when the graph, imports, and reasoner configuration have not changed.
- A graph and import fingerprint attached to each result.
- Clear warnings when a project exceeds the tested reasoning size.
- Safe failure behavior in which the project remains usable and reasoning results are marked unavailable or incomplete.

Reasoning failure must not alter ontology source files.

## SHACL Graph Roles

Entio should distinguish the graph being validated from the graph containing the validation rules.

The project should identify:

- Which ontology sources contribute ontology and instance data to the data graph.
- Which ontology sources contribute SHACL shapes to the shapes graph.
- Whether one source contributes to both roles where explicitly configured.

FIBO or other imported ontologies should not automatically be treated as user-authored SHACL rules.

SHACL validation results should record the data graph and shapes graph fingerprints used for the run.

## SHACL Constraint Authoring

Phase 4 should let users create SHACL constraints through the VS Code workbench without requiring them to write raw SHACL Turtle manually.

All SHACL edits must be translated into RDF graph changes by the Kotlin engine.

### Supported Shape Targets

The first implementation should support shapes targeting:

- A class.
- A specific individual.
- Subjects of a property where practical.
- Objects of a property where practical.

The specification should define the exact target options included in the first release.

### Supported Constraint Types

Phase 4 should support common constraints, including:

- Minimum property count.
- Maximum property count.
- Exact property count through matching minimum and maximum counts.
- Required property.
- Expected datatype.
- Expected class.
- Allowed value list.
- Minimum numeric value.
- Maximum numeric value.
- Minimum exclusive numeric value where practical.
- Maximum exclusive numeric value where practical.
- String pattern.
- Minimum string length where practical.
- Maximum string length where practical.
- Closed shape.
- Ignored properties for a closed shape.
- Custom severity.
- Human-readable validation message.

The specification may reduce or refine this list if required to keep the implementation coherent, but required-property, count, datatype, class, allowed-value, numeric-range, pattern, severity, and message support should remain in scope.

### Shape Editing

The user should be able to:

- Create a node shape.
- Create a property shape.
- Add a property constraint to a node shape.
- Edit an existing supported constraint.
- Delete a constraint.
- Delete a property shape.
- Delete a node shape.
- Change the target of a shape where supported.
- Change severity.
- Change the validation message.
- View the RDF representation in technical details.

Every change must use the existing proposal workflow.

## SHACL Validation

Phase 4 should add a reusable SHACL validation service in the Kotlin semantic engine.

The service should validate:

- The loaded project graph.
- Preview graphs.
- Combined staged previews.
- The reloaded graph after an approved save.

### Validation Result Details

Each SHACL result should include:

- Severity.
- Human-readable message.
- Affected node.
- Affected property path where available.
- Shape that produced the result.
- Constraint type.
- Invalid value where available.
- Source ontology or file where available.
- Whether the issue exists in the current graph or only in a preview.
- Stable result identity for UI refresh and comparison.

### Validation Severities

At minimum, support:

- Violation.
- Warning.
- Information.

The UI should display them differently.

The specification should define which severities block approval.

A recommended starting policy is:

- Violations block approval by default.
- Warnings do not block approval but require visibility.
- Informational results do not block approval.

Project policy may later make this configurable.

### SHACL Validation Lifecycle

SHACL validation should run:

1. When the project is loaded.
2. After a valid individual edit preview.
3. After all staged edits are combined.
4. After a SHACL shape or constraint is created, edited, or deleted.
5. After an approved proposal is saved and the project is reloaded.
6. When the user requests validation manually.

## Reasoning And SHACL Order

Phase 4 should define a consistent processing order.

The recommended order is:

1. Load or construct the explicit graph.
2. Run OWL reasoning.
3. Build the reasoning view.
4. Run SHACL validation against the approved target graph defined by the specification.
5. Return asserted facts, inferred facts, consistency results, and SHACL results together.

The Phase 4 specification must decide whether SHACL validates:

- Only explicitly asserted facts.
- Asserted facts plus the temporary inferred view.
- Both as separate validation modes.

A recommended first implementation is:

- Use asserted facts plus supported inferred facts for the main validation result.
- Clearly label that validation used the inferred view.
- Preserve an asserted-only mode for troubleshooting if practical.

Entio should not silently change validation behavior.

## Preview And Approval Integration

Every proposed ontology, instance-data, or SHACL change should follow this flow:

1. The user creates or edits a proposal.
2. Entio builds the proposed graph in memory.
3. Entio runs OWL reasoning.
4. Entio checks consistency and unsatisfiable classes.
5. Entio runs SHACL validation.
6. Entio generates a semantic diff.
7. Entio shows:
   - asserted changes;
   - inferred changes caused by the proposal;
   - consistency results;
   - SHACL violations and warnings;
   - affected source files.
8. The user approves or rejects the proposal.
9. Rejection leaves all files unchanged.
10. Approval applies the explicit changes atomically.
11. Entio reloads the project.
12. Entio reruns reasoning and SHACL validation.
13. Entio confirms the expected result.
14. Entio restores the prior source if final verification fails.

Inferred facts remain computed results and are not saved unless a later phase explicitly adds materialization.

## Semantic Diff Requirements

The existing semantic diff should be extended so the user can distinguish:

- Explicit triples added.
- Explicit triples removed.
- Asserted labels or annotations changed.
- Inferences newly produced by the proposal.
- Inferences no longer produced by the proposal.
- SHACL violations newly introduced.
- SHACL violations resolved.
- Existing warnings that remain.

The diff should not present inferred facts as if the user directly edited them.

## VS Code Workbench Requirements

The workbench should add a dedicated reasoning and constraints area.

### Reasoning View

The user should be able to:

- See whether the ontology is consistent.
- See the last reasoning status.
- Refresh reasoning manually.
- View inferred superclass and subclass relationships.
- View inferred individual types.
- View supported inferred property relationships.
- See asserted and inferred facts separately.
- View unsatisfiable classes.
- Open the affected ontology item.
- See clear error or unsupported-feature messages.

### SHACL Authoring View

The user should be able to:

- Browse node shapes.
- Browse property shapes.
- Select the target class, individual, or supported target type.
- Create supported constraints through forms.
- Edit and delete supported constraints.
- Set severity.
- Set a human-readable message.
- Preview the resulting SHACL changes.
- Stage several SHACL edits.
- Review one combined proposal.
- Approve or reject.
- Open the changed Turtle source.

### SHACL Results View

The user should be able to:

- View violations, warnings, and informational findings.
- Filter by severity.
- Filter by shape.
- Filter by class, individual, or property.
- Open the affected entity.
- Open the shape.
- View the invalid value where available.
- See whether an issue is current, newly introduced, or resolved in the preview.

The TypeScript extension should not:

- Perform OWL reasoning.
- Run SHACL validation independently.
- Interpret reasoner output independently.
- Build SHACL triples independently.
- Write Turtle files directly.
- Bypass preview, approval, atomic save, reload, or rollback.

## Kotlin And CLI Requirements

The Kotlin semantic engine should own:

- Reasoner setup and execution.
- Reasoning-view construction.
- Asserted and inferred fact separation.
- Consistency checking.
- Unsatisfiable-class reporting.
- SHACL shape interpretation.
- SHACL validation.
- SHACL typed-edit translation.
- Reasoning and validation result comparison.
- Preview integration.
- Save verification.
- Reload and rollback behavior.

The machine-readable CLI should let the VS Code extension:

- Run reasoning.
- Fetch reasoning status and results.
- Fetch asserted and inferred semantic descriptions.
- List SHACL shapes.
- Fetch shape details.
- Run SHACL validation.
- Fetch validation results.
- Prepare SHACL edit proposals.
- Preview, validate, diff, apply, reject, reload, and roll back SHACL proposals.
- Return combined reasoning, diff, and SHACL results for staged changes.

The CLI should remain thin and should not implement reasoning or SHACL behavior itself.

## Expected Project Concepts

Phase 4 may introduce or refine:

- `ReasoningService`
- `ReasoningRequest`
- `ReasoningResult`
- `ReasoningStatus`
- `ReasonerMetadata`
- `SemanticFactOrigin`
- `InferredRelationship`
- `ConsistencyResult`
- `UnsatisfiableClassResult`
- `ReasoningIssue`
- `ImportResolutionResult`
- `ImportClosure`
- `ReasoningExplanation`
- `OwlProfileReport`
- `ReasoningFingerprint`
- `ReasoningRunControl`
- `ShaclShapeDescriptor`
- `ShaclNodeShapeDescriptor`
- `ShaclPropertyShapeDescriptor`
- `ShaclConstraintDescriptor`
- `ShaclValidationService`
- `ShaclValidationReport`
- `ShaclValidationResult`
- `ShaclSeverity`
- `ShaclGraphRole`
- `DataGraphDescriptor`
- `ShapesGraphDescriptor`
- `CreateNodeShapeEdit`
- `CreatePropertyShapeEdit`
- `AddShaclConstraintEdit`
- `UpdateShaclConstraintEdit`
- `RemoveShaclConstraintEdit`
- `DeleteShaclShapeEdit`

Names may vary in the specification, but responsibilities should remain clear.

These contracts should use Entio-owned types and should not expose third-party reasoner or SHACL-library types outside the semantic engine.

## Validation Expectations

Phase 4 should add deterministic checks for:

- Reasoner initialization failure.
- Missing or unresolved ontology import.
- Import cycle handling failure.
- Unsupported imported ontology format.
- OWL profile or feature unsupported by the selected reasoner.
- Reasoning timeout or cancellation.
- Reasoning result whose fingerprint does not match the current graph.
- Missing or ambiguous data-graph configuration.
- Missing or ambiguous shapes-graph configuration.
- Unsupported ontology construct.
- Inconsistent ontology.
- Unsatisfiable class.
- Missing or invalid SHACL target.
- Missing property path.
- Invalid count range.
- Invalid datatype IRI.
- Missing expected class.
- Invalid allowed-value list.
- Invalid numeric limit.
- Invalid regular-expression pattern.
- Missing validation message where required by policy.
- Invalid severity.
- Duplicate shape IRI.
- Duplicate constraint.
- Removal of a missing shape or constraint.
- Attempt to edit an inferred fact directly.
- Stale preview baseline.
- Round-trip non-equivalence.
- Post-save reasoning or SHACL results that do not match the approved preview.

Validation should not use AI judgment.

## Testing Requirements

Phase 4 should include focused tests for:

### Reasoning

- Transitive class hierarchy inference.
- Inferred individual types.
- Supported inverse-property inference.
- Supported transitive-property inference.
- Equivalent-class consequences where included.
- Consistent ontology.
- Inconsistent ontology.
- Unsatisfiable class.
- Asserted and inferred separation.
- Repeatable results.
- Reasoning on load.
- Reasoning on preview.
- Reasoning after apply and reload.
- Import closure across several local ontology files.
- Missing import reporting.
- Import cycle handling.
- Reasoning explanation for an inferred superclass.
- Explanation for inconsistency or an unsatisfiable class.
- OWL feature and profile reporting.
- Unsupported-feature reporting.
- Timeout, cancellation, safe failure, and unchanged-graph result reuse.

### SHACL Authoring

- Create node shape.
- Create property shape.
- Add each supported constraint type.
- Edit a constraint.
- Delete a constraint.
- Delete a property shape.
- Delete a node shape.
- Preserve severity and message.
- Preview without source mutation.
- Stage several shape changes.
- Reject without source mutation.
- Apply atomically.

### SHACL Validation

- Required property violation.
- Minimum count violation.
- Maximum count violation.
- Datatype violation.
- Expected-class violation.
- Allowed-value violation.
- Numeric-range violation.
- Pattern violation.
- Closed-shape violation.
- Severity handling.
- Result attribution.
- Current versus preview results.
- Resolved versus introduced results.
- Separate data-graph and shapes-graph configuration.
- Shapes-source attribution.
- Validation using the configured data and shapes graphs.
- Confirmation that imported FIBO-like ontology sources are not treated as shapes unless configured.

### Integration

- Reasoning and validation on project load.
- Combined ontology and SHACL staged edits.
- Inference changes in semantic diff.
- SHACL issue changes in semantic diff.
- Approval blocking policy.
- Rejection.
- Atomic apply.
- Reload.
- Stale baseline.
- Rollback after failed post-save verification.
- Machine-readable CLI responses.
- VS Code reasoning and SHACL views.

Tests should use temporary copies of committed fixtures.

Tests must not modify committed example projects.

## Non-Goals

Phase 4 should not include:

- Full support for every OWL construct.
- Full support for every SHACL feature.
- SHACL-SPARQL constraints.
- SWRL.
- Custom rule languages.
- LLM-generated constraints.
- LLM-generated repairs.
- Automatic repair of SHACL violations.
- Automatic materialization of inferred triples into source files.
- Large-scale or distributed reasoning.
- Reasoning over the full FIBO catalog.
- Document ingestion.
- Document-to-KG conversion.
- Schema RAG.
- External ontology retrieval.
- Autonomous agents.
- Production graph storage.
- Multi-user collaboration.
- Authentication or authorization.
- Long-term reasoning-result persistence.
- Actual Git operations inside Entio.
- A separate desktop application.
- Full Protégé parity.

## Success Criteria

Phase 4 is successful if a user can:

- Load an Entio project and automatically receive reasoning and SHACL results.
- See whether the ontology is consistent.
- See inferred superclass and subclass relationships.
- See inferred individual types.
- See supported inferred property relationships.
- Clearly distinguish asserted facts from inferred facts.
- See unsatisfiable classes.
- See which local or bundled imports were included in reasoning.
- See clear errors for missing or unsupported imports.
- Request an explanation for an important inference, inconsistency, or unsatisfiable class.
- See which OWL features are supported or unsupported by the selected reasoner.
- Cancel a long-running reasoning run and recover safely from timeout or failure.
- Create a SHACL node shape.
- Identify which project sources provide the data graph and which provide the shapes graph.
- Create a SHACL property shape.
- Add, edit, and delete supported constraints.
- Set severity and a human-readable message.
- Preview SHACL changes without modifying source files.
- See reasoning results for the preview.
- See SHACL violations and warnings for the preview.
- See newly introduced and resolved inferences and SHACL results.
- Stage several ontology and SHACL edits.
- Review one combined semantic diff.
- Reject the proposal with no source changes.
- Approve and apply the complete proposal atomically.
- Reload the project and automatically rerun reasoning and SHACL validation.
- Confirm that inferred facts were not silently written to Turtle.
- Recover the prior source automatically if final verification fails.

Phase 4 should establish Entio’s reasoning and constraint foundation while preserving the existing safety rule: no source mutation without preview, semantic diff, deterministic verification, explicit approval, atomic application, reload, and rollback.
