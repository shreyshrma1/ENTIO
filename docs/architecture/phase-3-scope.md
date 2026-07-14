# Phase 3 Scope

Phase 3 is draft planning only. Phase 2.5+ is the latest completed implementation phase, and no Phase 3 product implementation has begun.

Phase 3 adds the semantic description layer that Entio needs for clearer ontology authoring, stronger search, and future Schema RAG and document-to-KG workflows.

Phases 2, 2.5, and 2.5+ already provide safe ontology editing, semantic previews, approval, atomic saving, rollback, label-first selection, deterministic IRI generation, deletion, and multi-edit staging.

Phase 3 should add richer meaning and human-readable context for ontology elements without introducing full OWL reasoning or AI behavior.

## Phase 3 Goals

Phase 3 should support:

- Building structured semantic descriptions for classes, object properties, datatype properties, annotation properties, and individuals.
- Having the Kotlin semantic engine read existing ontology statements and assemble each element’s IRI, labels, definitions, annotations, entity kind, and source ontology into one structured description for use by the UI, CLI, search, Schema RAG, and future AI features.
- Having the Kotlin semantic engine automatically collect existing explicit structural facts, including superclasses, subclasses, domains, ranges, asserted types, object-property assertions, and datatype-property assertions, into each element’s semantic description.
- Having the Kotlin semantic engine select one preferred label from existing labels through a deterministic policy.
- Having the Kotlin semantic engine collect alternate labels, definitions, and annotations without duplicates and in stable order.
- Preserving language tags and datatypes on labels, definitions, and annotation values.
- Letting users add, edit, and delete natural-language definitions.
- Letting users add, edit, and delete alternate labels.
- Letting users create and delete annotation properties.
- Letting users add, edit, and delete annotation values on ontology elements.
- Letting users search for and select ontology elements by preferred label, alternate label, full IRI, entity kind, and source ontology.
- Having the Kotlin semantic engine report why a search result matched, such as preferred label, alternate label, or IRI.
- Showing semantic details and all definition, alternate-label, and annotation functionality in the VS Code workbench.
- Returning the same semantic descriptions through the Kotlin engine and machine-readable CLI.
- Keeping the Kotlin semantic engine as the single source of truth for labels, definitions, annotations, structural facts, and search behavior.
- Reusing the existing preview, validation, semantic diff, approval, staging, atomic save, reload, and rollback flow for every user-made semantic edit.
- Providing one reusable semantic layer for the VS Code workbench, CLI, future Schema RAG, and future AI agents.
- Limiting the Phase 3 semantic engine to organizing explicit ontology facts, without performing full OWL reasoning, inferring unstated relationships, using embeddings, calling LLMs, or retrieving external ontology concepts.

Phase 3 should create the semantic layer that later Schema RAG and AI features can consume.

## Working Definitions

- preferred label: the main human-readable name for an ontology element.

- alternate label: another name, abbreviation, synonym, or commonly used phrase that refers to the same ontology element.

- definition: a natural-language explanation of what an ontology element means and how it should be used.

- annotation: descriptive information attached to an ontology element that does not by itself define formal logical behavior.

- annotation property: the type of descriptive information being attached, such as label, alternate label, definition, source, example, author, or status.

- semantic descriptor: an Entio-owned object that summarizes the important descriptive and structural information about one ontology element.

- localized text: a text value that may include a language tag, such as English or French.

- semantic layer: the reusable Kotlin services and contracts that turn raw RDF statements into meaningful entity descriptions for the UI, search, and future AI workflows.

## Required Semantic Descriptors

Phase 3 should introduce or complete semantic descriptors for:

- Classes.
- Object properties.
- Datatype properties.
- Annotation properties.
- Individuals.

A descriptor should include the fields that are relevant to its entity kind.

### Common Descriptor Fields

Every descriptor should support:

- IRI.
- Preferred label.
- Alternate labels.
- Definitions.
- General annotations.
- Entity kind.
- Source ontology.
- Whether the entity is local or imported where that information is available.

### Class Descriptor

A class descriptor should support:

- Direct superclasses.
- Direct subclasses where practical.
- Definitions.
- Preferred and alternate labels.
- General annotations.
- Individuals directly typed as the class where practical.

### Object Property Descriptor

An object property descriptor should support:

- Domain.
- Range.
- Preferred and alternate labels.
- Definitions.
- General annotations.
- Direct assertions using the property where practical.

### Datatype Property Descriptor

A datatype property descriptor should support:

- Domain.
- Datatype range.
- Preferred and alternate labels.
- Definitions.
- General annotations.
- Direct literal assertions using the property where practical.

### Annotation Property Descriptor

An annotation property descriptor should support:

- IRI.
- Preferred label.
- Alternate labels.
- Definition.
- General annotations.
- Statements that use the annotation property where practical.

### Individual Descriptor

An individual descriptor should support:

- Asserted types.
- Preferred and alternate labels.
- Definitions or descriptive notes where present.
- General annotations.
- Object-property assertions.
- Datatype-property assertions.

## Annotation Vocabulary

Phase 3 should support common annotation properties, including at minimum:

- `rdfs:label`
- `rdfs:comment`
- `skos:prefLabel`
- `skos:altLabel`
- `skos:definition`
- `dcterms:source`

The specification may approve additional common annotation properties if they fit the existing architecture.

Entio should not hardcode all future annotation behavior around only these properties. The semantic layer should also support user-created annotation properties.

## Annotation Property Editing

Phase 3 should allow the user to:

- Create an annotation property.
- Add a preferred label to an annotation property.
- Add a definition to an annotation property.
- Use an annotation property to attach a value to a class, property, or individual.
- Remove an annotation value.
- Replace an annotation value where explicit replacement behavior is supported.
- Preserve language tags and datatypes when applicable.

The user should not need to construct raw RDF triples manually.

Typed annotation edits should be translated into graph changes by the Kotlin engine.

## Definition Editing

The workbench should allow users to:

- Add a definition to a class, property, annotation property, or individual where supported.
- Edit an existing definition.
- Remove a definition.
- Add a language tag.
- View all definitions when more than one is present.

Definitions should normally be stored through an approved annotation property, preferably `skos:definition` unless the specification approves another project policy.

Replacement must be represented as explicit removal and addition so the semantic diff remains clear.

## Alternate Label Editing

The workbench should allow users to:

- Add one or more alternate labels.
- Edit an alternate label.
- Remove an alternate label.
- Add a language tag.
- View all alternate labels for the selected item.

Alternate labels should normally use `skos:altLabel`.

Entio should prevent exact duplicates for the same entity, language tag, and value.

Alternate labels should be included in:

- Label-first selectors.
- Search results.
- Entity detail views.
- Semantic descriptors.
- Future Schema RAG inputs.

## Preferred Label Behavior

The semantic layer should define a deterministic preferred-label policy.

The exact priority should be approved in the specification, but a reasonable starting policy is:

1. `skos:prefLabel` in the preferred UI language.
2. `rdfs:label` in the preferred UI language.
3. `skos:prefLabel` without a language tag.
4. `rdfs:label` without a language tag.
5. Another available label in deterministic order.
6. A readable local name derived from the IRI.

The policy must be deterministic and should not guess based on AI.

When labels are ambiguous, Entio should continue to disambiguate using entity kind, source ontology, namespace, or full IRI.

## General Annotation Editing

The user should be able to attach an annotation to a selected ontology element by choosing:

- Annotation property.
- Value.
- Optional language tag.
- Optional datatype where supported.

The user should also be able to:

- Edit a staged annotation change.
- Remove an existing annotation.
- Preview and stage several annotation edits.
- Review all annotation changes in the combined semantic diff.

The initial UI may support text-valued annotations first. IRI-valued annotations may be included only if explicitly approved in the specification.

## Semantic Search And Selection

Phase 3 should improve entity lookup so that existing elements can be found by:

- Preferred label.
- Alternate label.
- Full IRI.
- Entity kind.
- Source ontology.

Search results should indicate whether the match came from:

- Preferred label.
- Alternate label.
- IRI.
- Another supported annotation.

The Kotlin engine should perform resolution and ranking. The TypeScript extension should only display the returned results.

Phase 3 should not add fuzzy semantic search, embeddings, or external ontology lookup. Those belong to the Schema RAG phase.

## Existing Workflow Integration

All new Phase 3 edits must reuse the existing controlled editing workflow:

1. The user enters or changes semantic information.
2. Entio converts the action into a typed edit.
3. Entio creates graph changes.
4. Entio previews the result without modifying source files.
5. Entio generates a semantic diff.
6. Entio validates the proposed graph.
7. Entio verifies Turtle serialization and reparsing.
8. The edit enters the staged list.
9. The user may edit or remove the staged change.
10. Entio creates one combined preview for all staged edits.
11. The user approves or rejects the complete set.
12. Approved changes are saved atomically.
13. Entio reloads the project and verifies the result.
14. Entio restores the prior source if save verification fails.

Phase 3 must not create a separate annotation-saving pathway.

## Expected Project Concepts

Phase 3 may introduce or refine:

- `LocalizedText`
- `AnnotationValue`
- `AnnotationStatement`
- `OntologyEntityDescriptor`
- `ClassDescriptor`
- `ObjectPropertyDescriptor`
- `DatatypePropertyDescriptor`
- `AnnotationPropertyDescriptor`
- `IndividualDescriptor`
- `PreferredLabelPolicy`
- `SemanticDescriptorService`
- `SemanticSearchResult`
- `CreateAnnotationPropertyEdit`
- `AddDefinitionEdit`
- `RemoveDefinitionEdit`
- `AddAlternateLabelEdit`
- `RemoveAlternateLabelEdit`
- `AddAnnotationEdit`
- `RemoveAnnotationEdit`

Names may vary in the specification, but responsibilities should remain clear.

These contracts should use Entio-owned RDF terms and should not expose Apache Jena types.

## CLI Requirements

Phase 3 should extend the machine-readable CLI boundary so the VS Code extension can:

- Fetch semantic descriptors.
- Search by preferred label and alternate label.
- Create annotation properties.
- Add, edit, and remove definitions.
- Add, edit, and remove alternate labels.
- Add and remove general annotation values.
- Preview, validate, diff, stage, apply, and reject annotation-related edits.

The CLI should remain thin and delegate all semantic behavior to Kotlin services.

Structured JSON should be used where needed to avoid overly complex command-line flags.

Existing commands should remain backward compatible unless the approved specification explicitly requires a versioned response change.

## VS Code Workbench Requirements

The workbench should add:

- A semantic details section for the selected item.
- Preferred label display.
- Alternate-label list.
- Definition list.
- General annotation list.
- Annotation-property details.
- Forms for creating annotation properties.
- Forms for adding or editing definitions.
- Forms for adding or editing alternate labels.
- Forms for adding general annotations.
- Remove actions for definitions, alternate labels, and annotations.
- Search and selectors that include alternate labels.
- Full IRI display in technical details.
- Existing staged-change edit, remove, combined preview, approval, refresh, and source-opening behavior.

Successful preview should continue to clear the current form and add the verified edit to the staged list.

Editing a staged semantic change should refill the appropriate form.

The extension must not:

- Interpret RDF independently.
- Choose preferred labels independently.
- Build annotation triples independently.
- Write Turtle files directly.
- Bypass preview, validation, approval, or atomic application.

## Validation Expectations

Phase 3 should add deterministic validation for:

- Missing annotation property.
- Missing annotation value.
- Invalid language tag.
- Invalid datatype where supported.
- Duplicate preferred label.
- Duplicate alternate label.
- Duplicate definition.
- Removal of a missing annotation.
- Annotation property kind mismatch.
- Ambiguous target entity.
- Invalid or missing target source.
- Stale baseline.
- Combined staged conflicts.
- Turtle round-trip non-equivalence.

The specification should decide whether multiple preferred labels in the same language are allowed. If disallowed, Entio should return a structured validation issue.

Validation should not use AI judgment or full OWL reasoning.

## Testing Requirements

Phase 3 should include focused tests for:

### Semantic Descriptors

- Descriptor creation for classes, object properties, datatype properties, annotation properties, and individuals.
- Preferred-label selection.
- Alternate-label extraction.
- Definition extraction.
- General annotation extraction.
- Language-tag preservation.
- Deterministic ordering.

### Annotation Properties

- Create annotation property.
- Add label and definition to annotation property.
- Use annotation property on supported entity kinds.
- Remove annotation value.
- Reject invalid property kinds.

### Definitions

- Add definition.
- Replace definition.
- Remove definition.
- Preserve language tags.
- Reject duplicate definitions.

### Alternate Labels

- Add alternate label.
- Edit alternate label.
- Remove alternate label.
- Preserve language tags.
- Reject exact duplicates.
- Search by alternate label.

### General Annotations

- Add text annotation.
- Remove annotation.
- Stage multiple annotation edits.
- Include annotation changes in combined semantic diff.
- Apply atomically.
- Roll back on failed post-save verification.

### Integration

- CLI descriptor and search responses.
- VS Code semantic details rendering.
- Definition, alternate-label, and annotation forms.
- Staged edit restoration.
- Combined preview, approval, rejection, refresh, and source opening.
- Copied-fixture end-to-end tests without modifying committed examples.

## Non-Goals

Phase 3 should not include:

- Schema RAG.
- Embeddings or vector search.
- External ontology retrieval.
- AI-generated definitions or labels.
- Document ingestion.
- Document-to-KG conversion.
- Entity resolution across documents.
- Autonomous agents.
- Full OWL reasoning.
- Full SHACL authoring or validation.
- Equivalent-class editing.
- Disjoint-class editing.
- Property chains.
- Cardinality restrictions.
- Property characteristics.
- Inverse-property editing.
- Full Protégé parity.
- Source-text-preserving Turtle editing.
- Long-term proposal or staged-change persistence.
- Multi-user review.
- Authentication or authorization.
- Actual Git operations inside Entio.
- A separate desktop application.
- A full drag-and-drop graph editor.

## Success Criteria

Phase 3 is successful if a user can:

- Open a class, property, annotation property, or individual and view a semantic descriptor.
- View its preferred label, alternate labels, definitions, annotations, source, and full IRI.
- Create an annotation property.
- Add, edit, and remove a definition.
- Add, edit, and remove alternate labels.
- Add and remove a supported general annotation.
- Preserve language tags.
- Search for an entity by preferred or alternate label.
- Stage several semantic edits.
- Edit or remove staged semantic changes.
- Preview one combined semantic diff.
- View deterministic validation and Turtle round-trip results.
- Reject the complete staged set without changing source files.
- Approve and apply all staged semantic changes atomically.
- Reload the project and see the complete result.
- Recover the prior source automatically if final verification fails.
- Confirm that only intended ontology source files changed.

Phase 3 should complete the semantic description layer needed for future Schema RAG and ontology-construction agents while preserving Entio’s existing safety rule: no source mutation without preview, semantic diff, deterministic verification, and explicit approval.
