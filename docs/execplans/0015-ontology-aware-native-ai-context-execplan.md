# ExecPlan: Ontology-Aware Native AI Context

## Source Spec

- [Ontology-aware native AI context](../specs/0015-ontology-aware-native-ai-context.md)

## Plan

1. Extend `AiOntologyContextBuilder` with source/namespace context, a general ontology-engineering guide, and deterministic semantic entity descriptions.
2. Strengthen the provider instruction to establish semantic understanding and verify RDF term roles while leaving planning and retrieval decisions to the model.
3. Send the Entio context as a developer message and preserve user/assistant conversation turns as native Responses API input messages.
4. Add a model-led answer/proposal/clarification routing call and specialize the second provider call for prose or strict proposal output.
5. Validate proposal source IDs against the loaded project before preview and return unknown IDs through the existing repair loop.
6. Assemble follow-up semantic context from the effective private draft and require complete replacement proposals that preserve unaffected edits.
7. Add workflow and provider-request coverage for routing, semantic context, conversation messages, draft follow-ups, and invalid-source repair.
8. Run focused and module-level tests plus `git diff --check`.
9. Restore the selected in-memory chat after browser refresh, expose in-memory chat history, and allow starting a new chat without deleting prior conversations.

## Boundaries

- Kotlin remains authoritative for project loading, graph semantics, validation, staging, and application.
- The provider remains authoritative for interpretation, planning, natural-language responses, and proposed graph edits.
- No provider response can directly write ontology or configuration files.
