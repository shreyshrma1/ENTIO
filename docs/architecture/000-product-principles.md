# Product Principles

Entio exists to help teams build clean, trustworthy knowledge graphs from enterprise information.

## Start With the Rulebook

Entio should start with a rulebook before AI proposes graph changes.

That rulebook is an ontology: the approved business concepts, relationships, and constraints that define what the graph is allowed to contain and how its parts relate to each other.

AI should not freely invent a graph from documents or data. It should work within the ontology and produce changes that can be inspected, validated, and approved.

## Drafts Before Truth

AI-generated graph or ontology changes are drafts.

A draft is not official simply because an AI produced it. Proposed changes should be validated with deterministic checks and reviewed by a human reviewer before they become part of the accepted graph or ontology.

## Human-Reviewable Change

Entio should make changes understandable to people.

Semantic diffs should show what concepts, relationships, constraints, or graph facts changed in terms that a reviewer can inspect. The goal is not just to compute differences, but to support confident human approval.

## Reuse Trusted Ontologies

Entio should reuse trusted existing ontologies where possible, including public standards such as FIBO, Schema.org, Wikidata, and internal enterprise ontologies.

Entio should avoid creating custom ontology machinery when established standards and libraries already exist.

## Deterministic Validation

Validation should not depend on AI judgment. The same input should produce the same validation result every time.

AI may propose changes, explain changes, or assist review, but checks that decide whether a change satisfies structural rules should be deterministic and repeatable.

## Product-Specific Workflow

Entio should focus on the workflows that make enterprise knowledge graph work safer and easier:

- Project configuration.
- Approved ontology loading.
- Draft change representation.
- Validation reports.
- Semantic diffs.
- Human review and approval.

Entio should not become a custom replacement for RDF, OWL, SHACL, or mature semantic-web tooling.
