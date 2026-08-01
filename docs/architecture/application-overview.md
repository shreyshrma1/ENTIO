# Entio Application Overview

Entio is an ontology-first application for turning business knowledge into a
clean, trustworthy knowledge graph. It helps teams understand an existing
ontology, discover important concepts in documents, propose structured
improvements, and review every change before it reaches the ontology.

## Understand And Manage An Ontology

Entio presents RDF and Turtle ontology projects in business-friendly language.
Users can:

- browse classes, properties, individuals, labels, definitions, and
  relationships;
- search by names, alternate labels, definitions, and semantic meaning;
- inspect hierarchy, domains, ranges, types, assertions, and dependencies;
- create and edit supported ontology entities through guided forms;
- review dependencies before deleting an entity; and
- stage several related edits as one coherent change.

An interactive ontology map makes the graph easier to explore. Users can search
for entities, navigate relevant connections, expand nearby relationships, and
inspect details without turning the visualization into an uncontrolled editing
surface.

## Turn Documents Into Ontology Recommendations

Entio analyzes PDF, DOCX, Markdown, and plain-text documents. It can also use
local OCR for scanned or otherwise unreliable PDF pages.

Document analysis separates raw mentions from possible ontology entities. It:

1. records exact evidence and its location in the source document;
2. filters low-value material such as headers, generic fragments, dates,
   amounts, and administrative text;
3. groups safely equivalent mentions into meaningful candidates;
4. searches the existing ontology and authorized reference sources for
   possible matches;
5. uses an AI model to interpret candidates in their document context; and
6. produces connected, evidence-backed ontology recommendations.

Every recommendation links back to its supporting document text. Reviewers can
see what was proposed, why it was proposed, where the evidence appeared, how
confident the analysis was, and whether the meaning matches an existing entity.

Recommendations can propose:

- reusing an existing ontology entity;
- extending an existing entity;
- creating a genuinely new class, property, or individual;
- adding definitions, hierarchy, domains, ranges, datatypes, types, and
  assertions;
- resolving important concepts that remain ambiguous; and
- retaining document-only meaning in the coverage record without creating an
  unnecessary review card.

Related changes stay together. For example, a new property, its domain and
range, and any supporting classes can appear as one understandable
recommendation instead of several disconnected edits.

The review form exposes the structural information needed to complete a
proposal:

- classes can specify a superclass;
- object properties can specify a domain and range;
- datatype properties can specify a domain and datatype; and
- individuals can specify a class type.

## Human-Controlled Review And Application

AI recommendations are drafts, never automatic ontology changes. Before
anything can be applied, Entio:

- verifies document evidence and ontology references;
- detects duplicates, conflicts, stale results, and invalid combinations;
- generates the exact proposed operations;
- shows a semantic diff of what will change;
- runs validation, reasoning, and SHACL impact checks; and
- requires explicit human review and approval.

Reviewers can accept, reject, edit, or resolve recommendations. Approved work is
applied atomically: either the complete connected change succeeds, or the
ontology is restored to its previous state. Reload verification and rollback
support protect the source from partial or invalid writes.

Applied document-derived changes retain provenance, allowing teams to trace
ontology decisions back to their source evidence.

## Native Ontology Assistant

Entio includes an ontology-aware AI assistant that can:

- answer questions about the current project;
- explain entities and relationships;
- help users navigate unfamiliar ontology content;
- prepare structured edit proposals; and
- retain project-scoped conversations and run history.

The assistant uses the same review process as every other edit. It cannot
approve changes, modify ontology files directly, or bypass validation.

## Reasoning And Quality Assurance

Entio helps users understand both what the ontology explicitly states and what
can be logically inferred from it. Users can:

- browse asserted and inferred facts;
- compare reasoning results for the applied ontology and pending proposals;
- display optional inferred relationships in entity details and the ontology
  map;
- select supported inferred facts and stage them as explicit, reviewable edits;
- run SHACL validation and inspect constraint violations; and
- preview how proposed changes affect validation and reasoning before approval.

## Trusted Reference Ontology Reuse

Entio includes a searchable, read-only FIBO catalog for financial-domain
ontology reuse. Users can inspect definitions and dependencies, compare FIBO
concepts with local entities, and propose controlled reuse without changing the
reference catalog or losing the original external identifiers.

## Collaboration

The web workbench supports shared project activity through:

- shared staged edits;
- presence and activity indicators;
- coordinated review state;
- asynchronous document-analysis and reasoning jobs; and
- progress reporting, cancellation, and safe retry behavior.

## Secure AI Configuration

Users can configure their own provider credentials and select a compatible AI
model. Credentials remain outside browser-visible project data, and documents
and provider responses are treated as untrusted content. Document text cannot
instruct Entio to reveal credentials, widen permissions, or bypass review.

## Product Experience

The web workbench provides the complete collaborative experience for ontology
exploration, editing, document analysis, AI assistance, reasoning, validation,
and review. A VS Code workbench supports ontology browsing and controlled
editing alongside source files, while command-line tools support repeatable
project inspection, validation, semantic diffs, and proposal workflows.

Entio combines document understanding, ontology editing, AI assistance,
semantic validation, reasoning, visualization, and human governance in one
workflow. It helps organizations evolve knowledge graphs without sacrificing
accuracy, traceability, or control.
