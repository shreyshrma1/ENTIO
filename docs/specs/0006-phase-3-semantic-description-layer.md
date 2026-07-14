# Feature Spec: Phase 3 Semantic Description Layer

## Status

Draft

## Problem

Entio currently exposes ontology symbols and structural relationships, but users still need to assemble meaning from separate labels, IRIs, annotations, and graph statements. The workbench therefore cannot provide one reliable, human-readable description of a class, property, annotation property, or individual. Search is also limited when an ontology element is known by an alternate label rather than its preferred label or IRI.

Phase 3 should add a reusable semantic description layer. The Kotlin semantic engine must read explicit RDF statements and organize them into Entio-owned descriptors that the CLI and VS Code workbench can consume. The layer must improve authoring and search without adding reasoning, embeddings, external retrieval, or AI behavior.

## Goals

- Build structured descriptors for classes, object properties, datatype properties, annotation properties, and individuals.
- Preserve IRIs, source ontology, local/imported status when available, labels, definitions, annotations, RDF datatypes, and language tags.
- Collect explicit structural facts such as types, hierarchy, domains, ranges, and direct assertions.
- Select a preferred label through a deterministic policy and collect alternate labels and definitions in stable order without exact duplicates.
- Support typed creation, replacement, and removal of definitions, alternate labels, annotation properties, and general annotation values.
- Expose descriptors and label-aware search through the Kotlin engine and machine-readable CLI.
- Show semantic details and edit controls in the VS Code workbench while keeping semantic behavior in Kotlin.
- Reuse the existing preview, validation, semantic diff, staging, approval, atomic apply, reload, and rollback workflow for every semantic edit.
- Provide contracts that later Schema RAG and ontology-construction features can consume without coupling them to Apache Jena types.

## Non-Goals

- Schema RAG, embeddings, vector search, or external ontology retrieval.
- AI-generated labels, definitions, annotations, or semantic judgments.
- Document ingestion, document-to-KG conversion, entity resolution across documents, or autonomous agents.
- Full OWL reasoning, inference of unstated relationships, full SHACL authoring or validation, or Protégé parity.
- Equivalent classes, disjoint classes, property chains, cardinality restrictions, property characteristics, or inverse-property editing.
- Source-text-preserving Turtle editing.
- Long-term proposal or staged-change persistence, multi-user review, authentication, authorization, servers, databases, or actual Git operations inside Entio.
- A second RDF graph model or a custom RDF, OWL, SHACL, or Turtle framework.
- A separate annotation save path that bypasses preview, validation, semantic diff, approval, atomic application, reload, or rollback.

## Proposed Behavior

### Semantic descriptors

The Kotlin semantic engine will expose an `OntologyEntityDescriptor` abstraction with common fields:

- Full entity IRI.
- Preferred label, when one can be selected.
- Alternate labels.
- Definitions.
- General annotation values.
- Entity kind.
- Source ontology identifier.
- Local/imported status when project information makes it available.

Kind-specific descriptors will add:

- Classes: direct superclasses, direct subclasses where available, and directly typed individuals.
- Object properties: domains, ranges, and direct object assertions.
- Datatype properties: domains, datatype ranges, and direct literal assertions.
- Annotation properties: descriptive metadata and statements using the property.
- Individuals: asserted types, object-property assertions, and datatype-property assertions.

All collections must be deterministic and must preserve the RDF term shape of their values. IRI resources, blank nodes, plain literals, datatyped literals, and language-tagged literals must not be flattened into indistinguishable strings.

### Labels and definitions

The semantic layer will recognize these common annotation properties:

- `rdfs:label`
- `rdfs:comment`
- `skos:prefLabel`
- `skos:altLabel`
- `skos:definition`
- `dcterms:source`

The preferred-label policy is deterministic:

1. `skos:prefLabel` in the configured preferred UI language.
2. `rdfs:label` in the configured preferred UI language.
3. `skos:prefLabel` without a language tag.
4. `rdfs:label` without a language tag.
5. Another available label in stable RDF order.
6. A readable local name derived from the IRI.

The policy must return the source of the selected label. Alternate labels will use `skos:altLabel` and definitions will normally use `skos:definition`. `rdfs:comment` and other descriptive values remain general annotations unless an explicit project policy says otherwise.

Exact duplicate labels, definitions, and annotation statements for the same subject, predicate, and RDF value are invalid for newly proposed edits. Multiple distinct `skos:prefLabel` values in one language are also invalid and must produce a structured validation issue; descriptor extraction remains deterministic so an existing invalid ontology can still be inspected. `rdfs:label` values may coexist as fallback labels.

### Annotation editing

Users will not construct raw triples. Typed edits will represent:

- Creating an annotation property.
- Adding, replacing, and removing definitions.
- Adding, replacing, and removing alternate labels.
- Adding and removing general annotation values.
- Adding a label or definition to an annotation property.

Text-valued annotations are required first. IRI-valued annotations may be supported only through the same RDF-term contract and only where the approved implementation slice explicitly includes them. Language tags and datatypes must be retained when present. Replacement must be represented as explicit removal plus addition so the semantic diff is reviewable.

Every typed edit must become ordinary Entio graph changes and enter the existing proposal lifecycle.

### Search and selection

The Kotlin semantic engine will search by preferred label, alternate label, full IRI, entity kind, and source ontology. Results will include:

- The matched entity descriptor or enough stable identity to fetch it.
- Match reason, such as preferred label, alternate label, IRI, or another supported annotation.
- Deterministic ranking and tie-breaking.

The TypeScript extension will display the returned results and will not resolve labels independently. Ambiguous matches must remain explicit and must identify candidates rather than selecting arbitrarily.

### Workflow integration

The existing controlled workflow remains authoritative:

1. The user edits semantic information.
2. The UI sends a typed request to the CLI.
3. Kotlin translates it into graph changes.
4. Entio creates a preview without changing source files.
5. Entio generates a semantic diff and deterministic validation report.
6. Entio verifies Turtle serialization and reparsing.
7. A valid edit enters the in-memory staged list.
8. The user may edit or remove staged edits.
9. Entio creates one combined preview.
10. The user rejects or approves the complete set.
11. Approved changes are applied atomically, reloaded, and verified.
12. Failed post-save verification restores the prior source and graph state.

## Inputs And Outputs

### Inputs

- A loaded Entio project and its ontology sources.
- Existing RDF statements represented by Entio RDF terms.
- Optional preferred UI language and source/kind search filters.
- Structured typed semantic edit requests.
- Existing proposal baseline and staged-change metadata.

### Outputs

- Deterministic semantic descriptors.
- Deterministic search results with match reasons.
- Structured validation issues for semantic metadata and workflow failures.
- Typed graph changes and existing proposal previews/diffs.
- Machine-readable CLI responses for descriptor, search, preview, validate, diff, apply, and reject operations.
- VS Code semantic details, forms, staged entries, and workflow status.

Existing single-edit CLI commands and response fields must remain backward compatible unless a later approved change explicitly versions them.

## Validation And Error Handling

Validation must be deterministic and must report structured issues for:

- Missing annotation property or annotation value.
- Invalid language tag.
- Invalid datatype where datatype validation is supported.
- Exact duplicate values.
- Duplicate alternate labels or definitions.
- Multiple preferred labels in one language.
- Removal of an absent annotation value.
- Annotation property kind mismatch.
- Missing, invalid, or ambiguous target entity.
- Invalid or missing target source.
- Stale proposal baseline.
- Conflicting staged semantic edits.
- Turtle round-trip non-equivalence.

Rejected previews and rejected combined proposals must not mutate source files. Failed application must use the existing rollback behavior. Errors must preserve enough target, source, and staged-entry attribution for the UI and CLI to explain the failure.

## Test Cases

### Descriptor extraction

- Create descriptors for every required entity kind.
- Extract preferred labels, alternate labels, definitions, annotations, and structural facts.
- Select preferred labels according to language and property priority.
- Preserve language tags, datatypes, IRIs, blank nodes, and stable ordering.
- Expose local/imported and source ontology information when available.

### Annotation properties

- Create an annotation property.
- Add a label and definition to it.
- Use it on a class, property, and individual.
- Remove an annotation value.
- Reject a property-kind mismatch.

### Definitions

- Add, replace, and remove a definition.
- Preserve language tags and datatypes.
- Reject duplicate definitions and removal of a missing value.

### Alternate labels

- Add, edit, and remove an alternate label.
- Preserve language tags.
- Reject exact duplicates.
- Search by alternate label and report the match reason.

### General annotations

- Add and remove a text annotation.
- Preserve an optional language tag and datatype.
- Stage several annotation edits.
- Include annotation changes in one combined semantic diff.
- Apply atomically and roll back on failed post-save verification.

### Integration

- Return descriptor and search responses through the CLI.
- Render semantic details and forms in the VS Code workbench.
- Restore staged semantic edit forms after editing and re-preview.
- Exercise combined preview, approval, rejection, refresh, and source opening.
- Run copied-fixture end-to-end tests without mutating committed examples.

## Acceptance Criteria

- A user can inspect a class, object property, datatype property, annotation property, or individual as one semantic descriptor.
- The descriptor shows preferred label, alternate labels, definitions, annotations, source, kind, and full IRI as applicable.
- Explicit structural facts are included without inferred OWL relationships.
- Preferred-label selection and search ranking are deterministic.
- Language tags and datatypes survive extraction, editing, serialization, and reparsing.
- Users can create annotation properties and add, edit, or remove definitions, alternate labels, and supported annotation values through typed forms.
- Duplicate, ambiguous, invalid, stale, and conflicting operations produce structured deterministic validation issues.
- Semantic edits reuse the existing staged, combined-preview, approval, atomic-apply, reload, rejection, and rollback lifecycle.
- Source files remain unchanged on rejection or failed preview.
- The CLI and VS Code workbench consume Kotlin-owned descriptors and search results without duplicating RDF or label semantics.
- Focused module, CLI, extension, and copied-fixture tests pass.
- Phase 3 adds no Schema RAG, AI, inference, persistence, Git workflow, or unrelated infrastructure.

## Open Questions

- Should the preferred UI language be a project setting, a CLI option, an extension setting, or a combination of these?
- Which RDF datatypes should Phase 3 validate beyond the datatypes already preserved by the RDF-term model?
- Should IRI-valued general annotations be included in the first implementation slice or deferred after text-valued annotations?
- Should `rdfs:comment` remain only a general annotation, or should a later project policy allow it as a definition fallback?
- What project metadata is authoritative for determining whether an entity is local or imported?
- Should annotation property creation require an explicit annotation-property type assertion only, or may a configured project policy add additional metadata?
- How should descriptors represent anonymous subjects and blank-node annotation values in the UI?
