# Feature Spec: Ontology-Aware Native AI Context

## Status

Approved by the user for implementation.

## Problem

The native AI receives asserted triples and a small vocabulary glossary, but the context does not consistently present the loaded project as a semantic model. This can cause the provider to conflate declarations, schema axioms, assertions, annotations, and inferences. Model-generated source identifiers can also reach proposal preview and terminate a run instead of becoming repair feedback.

## Goal

Improve the existing model-led proposal workflow by giving the provider a concise ontology-engineering guide, explicit editable source identities, and structured descriptions of relevant project entities. Preserve the provider's authority to interpret requests, choose its plan, and decide whether external ontology context is useful.

## Requirements

- Do not add a tool loop, keyword intent router, fixed planner, module, or dependency.
- Describe classes, properties, and individuals using their asserted semantic roles and relationships alongside exact triples.
- Distinguish property declarations from domain and range axioms, and asserted facts from inferred consequences.
- List the real project source IDs, roles, and project IRI namespace in provider context.
- Require proposal edits to target a listed editable source.
- Convert unknown model-generated source IDs into deterministic repair feedback.
- Send conversation history as native user and assistant messages rather than flattening it into prompt text.
- Keep the provider instruction short and general-purpose; supply Entio context separately as trusted developer context.
- Allow ordinary conversational answers, with structured edits present only when the model chooses to propose a change.
- Use a separate model-led semantic routing decision with `answer`, `proposal`, and `clarification` outcomes; do not route with keyword matching.
- Require structured graph edits only on the proposal route. Answer and clarification routes return ordinary prose.
- Constrain only proposal presentation, not model reasoning: one JSON envelope, one RDF triple per edit, Entio operation/object-kind values, and absolute IRIs without compact prefixes.
- For follow-ups, build provider context from the effective private-draft graph (applied graph plus proposal additions minus removals), include the exact existing proposal as authoritative draft state, and preserve/merge existing private edits when the model returns only the requested additions or revisions.
- Present the assistant as a persistent, collapsible workbench sidebar rather than a full navigation module so users can converse while traversing Entio.
- Render assistant prose as readable Markdown and report only status updates that match actual processing.
- Prefer asserted preferred labels for user-facing entity references; reserve stable IRIs for disambiguation, evidence, and structured proposal fields.
- Reset status updates for every new prompt while preserving its conversation and private proposal context.
- Hide a private proposal after it is staged; if the shared staged proposal is rejected, restore that private proposal for further edits.
- Preserve validation findings as expandable details on status updates, and require concrete edits for explicitly numbered proposal requests.
- Let the model request focused read-only FIBO context when it determines that external ontology grounding is useful; Entio performs the catalog lookup and supplies the results to the generation step.
- Preserve the existing private proposal, validation, staging, approval, and source-write boundaries.
- Add focused tests and run relevant verification.

## Non-Goals

- Direct `.ttl` or project-configuration writes by the provider.
- Hard-coded task steps or mandatory FIBO retrieval.
- Deterministic generation of the provider's plan or answer.
- Changes outside documentation and `web-server` native-AI files.
