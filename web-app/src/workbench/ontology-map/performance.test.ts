import { describe, expect, it } from "vitest";
import type { WebOntologyGraphEdge, WebOntologyGraphNode } from "../../web/contracts";
import { layeredGraphLayout } from "./graphLayout";
import { mergeGraphPage } from "./graphMerge";

function nodes(count: number, offset = 0): WebOntologyGraphNode[] {
  return Array.from({ length: count }, (_, index) => ({ identity: { id: `n${index + offset}`, sourceId: "scale", entityIri: `urn:n${index + offset}` }, kind: (["Class", "ObjectProperty", "DatatypeProperty", "Individual"] as const)[index % 4], label: `Entity ${String(index + offset).padStart(4, "0")}`, definitionExcerpt: null, summary: { directSuperclassLabels: [], domainLabels: [], rangeLabels: [], assertedTypeLabels: [], datatypeRangeLabels: [], loadedRelationshipCount: 0, availableRelationshipCount: 1 } }));
}
function edges(items: WebOntologyGraphNode[], count: number): WebOntologyGraphEdge[] {
  return Array.from({ length: count }, (_, index) => ({ id: `e${items[0].identity.id}-${index}`, kind: (["SubclassOf", "Domain", "Range", "Type", "ObjectAssertion"] as const)[index % 5], sourceNodeId: items[index % items.length].identity.id, targetNodeId: items[(index + 1) % items.length].identity.id, label: "relationship", predicateIri: index % 5 === 4 ? "urn:property" : null, provenance: "Asserted" }));
}

describe("Phase 9 frontend performance harness", () => {
  it("records five warm layout and expansion runs within the approved gates", () => {
    const initialNodes = nodes(75);
    const initialEdges = edges(initialNodes, 150);
    const nextNodes = [initialNodes[0], ...nodes(49, 75)];
    const nextEdges = edges(nextNodes, 100);
    layeredGraphLayout(initialNodes, initialEdges);
    const renderRuns = Array.from({ length: 5 }, () => { const start = performance.now(); layeredGraphLayout(initialNodes, initialEdges); return performance.now() - start; });
    const expansionRuns = Array.from({ length: 5 }, () => { const start = performance.now(); const merged = mergeGraphPage(initialNodes, initialEdges, nextNodes, nextEdges, 300, 600); layeredGraphLayout(merged.nodes, merged.edges); return performance.now() - start; });
    const median = (values: number[]) => [...values].sort((a, b) => a - b)[2];
    console.info(`phase9-layout-runs-ms=${renderRuns.map((value) => value.toFixed(2)).join(",")} median=${median(renderRuns).toFixed(2)} worst=${Math.max(...renderRuns).toFixed(2)}`);
    console.info(`phase9-expansion-runs-ms=${expansionRuns.map((value) => value.toFixed(2)).join(",")} median=${median(expansionRuns).toFixed(2)} worst=${Math.max(...expansionRuns).toFixed(2)}`);
    expect(median(renderRuns)).toBeLessThanOrEqual(500);
    expect(Math.max(...renderRuns)).toBeLessThanOrEqual(1_000);
    expect(median(expansionRuns)).toBeLessThanOrEqual(750);
    expect(Math.max(...expansionRuns)).toBeLessThanOrEqual(1_500);
  });
});
