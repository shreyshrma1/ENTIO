# ADR 0001: Phase 1 Language Strategy

## Status

Superseded

Superseded by [ADR 0002: Phase 1 Kotlin/JVM Language Strategy](0002-phase-1-kotlin-jvm-language-strategy.md).

This decision was superseded because the Phase 1 core engine direction changed from TypeScript to Kotlin/JVM. TypeScript may still be used later for VS Code or web interfaces, but it is no longer the Phase 1 core-engine implementation language.

## Context

Phase 1 is the Core Semantic Engine for Entio. It needs a small backend foundation for project loading, small Turtle/RDF ontology parsing, deterministic validation, semantic diffs, and a simple CLI.

Entio may later support VS Code, web, CLI, and API interfaces. The Phase 1 language choice should support that direction without requiring those later interfaces to be built now.

## Decision

Phase 1 starts with TypeScript.

TypeScript is a good initial fit because it supports future VS Code extension work, CLI tooling, web-facing interfaces, and shared typed packages. It also has access to existing RDF, OWL-adjacent, and SHACL-related libraries that can be evaluated when implementation begins.

## Consequences

The initial repository scaffold is a pnpm TypeScript monorepo with small packages for core types, semantic engine behavior, validation, graph diffing, CLI entry points, and shared utilities.

This decision does not require Entio to use TypeScript for every future capability. Python or other tools may be introduced later where they are the right fit, especially for existing semantic-web or data-processing libraries. Those additions should be justified by a future spec or architecture decision.
