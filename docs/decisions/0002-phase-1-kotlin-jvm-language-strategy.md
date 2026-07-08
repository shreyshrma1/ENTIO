# ADR 0002: Phase 1 Kotlin/JVM Language Strategy

## Status

Accepted

## Context

Phase 1 is the Core Semantic Engine for Entio. It needs a backend foundation for ontology loading, RDF/Turtle parsing, deterministic validation, semantic diffing, and CLI behavior.

Entio may later support VS Code, web, CLI, and API interfaces. The Phase 1 language choice should prioritize the core semantic-engine work without requiring later interface layers to be built now.

## Decision

Phase 1 core engine will use Kotlin/JVM.

## Rationale

Kotlin/JVM is a stronger fit for ontology, RDF, validation, and semantic-web processing.

Kotlin/JVM gives Entio access to mature semantic-web libraries such as Apache Jena, RDF4J, and OWL API.

Kotlin provides a modern developer experience while remaining compatible with Java libraries.

Gradle is the intended build system for the future Kotlin/JVM multi-module scaffold.

TypeScript may still be used later for VS Code and web interfaces.

## Consequences

The future Phase 1 scaffold should be Kotlin/JVM with Gradle.

Future VS Code or web layers should consume the core engine rather than duplicate semantic logic.

This decision does not create the Gradle scaffold yet.

This decision does not add VS Code, web app, document ingestion, autonomous agents, Schema RAG, entity resolution, Stardog integration, full FIBO indexing, or other later-phase infrastructure.
