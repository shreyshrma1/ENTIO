import { describe, expect, it } from "vitest";
import { encodeWebIri, normalizeOntologyGraphResponse, normalizePageRequest } from "./contracts";

describe("web contract helpers", () => {
  it("encodes IRIs before they are placed in a path", () => {
    expect(encodeWebIri("https://example.com/entio/simple#Customer")).toBe(
      "https%3A%2F%2Fexample.com%2Fentio%2Fsimple%23Customer",
    );
  });

  it("normalizes graph contracts and rejects orphan edge references", () => {
    const fixture = {
      apiVersion: "v1", projectId: "simple", graphFingerprint: "abc", sources: [], loadKind: "RootOverview", seed: null,
      nodes: [{ identity: { id: "one", sourceId: "simple", entityIri: "urn:one" }, kind: "Class", label: "One", definitionExcerpt: null, summary: {} }],
      edges: [], limits: { nodeLimit: 75, edgeLimit: 150 }, totalNodeCount: 1, totalEdgeCount: 0, continuation: null,
      ambiguousCrossSourceRelationshipCount: 0,
    };
    expect(normalizeOntologyGraphResponse(fixture).nodes[0].label).toBe("One");
    expect(() => normalizeOntologyGraphResponse({ ...fixture, edges: [{ sourceNodeId: "one", targetNodeId: "missing" }] })).toThrow("malformed-graph-reference");
  });

  it("keeps pagination within the server bounds", () => {
    expect(normalizePageRequest({ offset: 10, limit: 25 })).toEqual({ offset: 10, limit: 25 });
    expect(() => normalizePageRequest({ limit: 101 })).toThrow("limit-must-be-between-1-and-100");
  });
});
