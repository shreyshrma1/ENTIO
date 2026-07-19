# ADR: Phase 7.5 Provider Model Selection

## Status

Accepted for Phase 7.5.

## Context

Entio previously configured one OpenAI model in the provider instance. A valid API key can belong to a project that cannot access that model, and the provider model-list response proves visibility rather than compatibility with Entio's Responses API custom-tool loop.

OpenAI model families and aliases change over time. Current official OpenAI guidance recommends the Responses API for reasoning and tool-calling workflows and distinguishes the GPT-5.6 Sol, Terra, and Luna variants. It also documents an alias that routes to another model. Entio therefore cannot treat an alias, a static metadata catalog, or provider list membership as proof that a model is usable.

## Decision

- Entio accepts candidate IDs only from the current user's server-side provider discovery result.
- Kotlin applies a versioned compatibility policy before an ID can be offered for verification.
- The policy rejects malformed IDs, unsupported providers, moving `latest`/`current` aliases, and clearly non-conversational resource families such as embedding, moderation, image, audio, transcription, speech, realtime, and video models.
- Unknown-but-discovered conversational candidates remain eligible for live verification. They do not receive invented capability metadata and do not become usable automatically.
- Optional known-model metadata affects display, relative guidance, deterministic ordering, and recommendation only. It never grants availability or compatibility.
- The initial known metadata covers exact GPT-5.6 Sol, Terra, and Luna IDs with qualitative Entio-focused descriptions. It contains no exact pricing claims.
- Live verification through the fixed Responses endpoint and harmless custom function contract is required before readiness. That behavior is implemented in later approved slices.
- React receives only the server projection and never reconstructs the policy, submits an arbitrary endpoint, or expands the discovered candidate set.
- Provider and compatibility policy versions are recorded so later settings and runs can preserve provenance.

## Consequences

- Entio can support a newly discovered conversational model without a code release, but only after the user explicitly selects it and live verification succeeds.
- Known entries provide a better selector experience without becoming a brittle allowlist.
- Models incorrectly named like a supported conversational family may reach verification, but they still cannot become ready merely from their name.
- Category filtering remains intentionally bounded. Broader remote taxonomy, pricing, benchmarking, and automatic routing services are unnecessary and out of scope.
- Provider inventory and raw provider fields remain confined to the provider adapter introduced in Slice 3.

## Sources

- [OpenAI latest-model guidance](https://developers.openai.com/api/docs/guides/latest-model.md)
- [OpenAI tools guide](https://developers.openai.com/api/docs/guides/tools)
