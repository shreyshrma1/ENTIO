# ADR: Provider-led semantic execution and observable AI progress

## Context

The native assistant previously classified requests by matching a small set of phrases. That
made semantically equivalent requests take different paths and caused broad ontology work to be
misread as a request for clarification. Long provider calls also left the workbench without
authoritative progress until the request completed.

## Decision

Every non-empty conversation message is now bound to a `SEMANTIC_REQUEST`. The provider receives
the scoped ontology context, project/workflow state, and the complete approved capability registry
and chooses the bounded read, typed-draft, FIBO, analysis, or SHACL calls needed to satisfy the
user's goal. The server remains the authority for capability decoding, permissions, source scope,
typed staging, deterministic checks, cancellation, and human review; deterministic postcondition
checks may reject an incomplete provider result but do not classify the user's intent.

The web client requests an asynchronous turn. Entio returns the run identity immediately and
publishes safe milestone events such as context inspection, capability execution, deterministic
analysis, draft preparation, retry, cancellation, and completion through the existing private run
event stream. These are observable workflow updates, not hidden chain-of-thought or provider
private reasoning.

## Consequences

- Natural-language variants use the same semantic provider path rather than a phrase allowlist.
- Multi-step ontology tasks can remain active while the user sees what bounded work is occurring.
- Users can stop an active generation while its private draft remains reviewable.
- The UI must treat the run event stream and authoritative conversation/draft routes as the source
  of truth after the immediate asynchronous response.
