import { describe, expect, it } from "vitest";
import type { WebOntologyGraphEdge, WebOntologyGraphNode } from "../../web/contracts";
import { projectedNodeIds, selectionNeighborhood } from "./OntologyMapShell";
import { layeredGraphLayout } from "./graphLayout";

const classNode = (id: string): WebOntologyGraphNode => ({ identity: { id, sourceId: "s", entityIri: `urn:${id}` }, kind: "Class", label: id, definitionExcerpt: null, summary: { directSuperclassLabels: [], domainLabels: [], rangeLabels: [], assertedTypeLabels: [], datatypeRangeLabels: [], loadedRelationshipCount: 0, availableRelationshipCount: 0 } });
const edge = (id: string, kind: WebOntologyGraphEdge["kind"], sourceNodeId: string, targetNodeId: string): WebOntologyGraphEdge => ({ id, kind, sourceNodeId, targetNodeId, label: kind, predicateIri: null, provenance: "Asserted" });

describe("ontology hierarchy projection", () => {
  it("collapses large branches, expands deterministically, and hides individuals until revealed", () => {
    const root = classNode("root");
    const children = Array.from({ length: 8 }, (_, index) => classNode(`child-${index}`));
    const individual = { ...classNode("person-1"), kind: "Individual" as const };
    const nodes = [root, ...children, individual];
    const edges = [...children.map((child) => edge(`e-${child.identity.id}`, "SubclassOf", child.identity.id, root.identity.id)), edge("type", "Type", individual.identity.id, root.identity.id)];
    const positions = layeredGraphLayout(nodes, edges);
    expect(projectedNodeIds(nodes, edges, "Hierarchy", null, new Set(), new Set(), positions).size).toBe(7);
    expect(projectedNodeIds(nodes, edges, "Hierarchy", null, new Set(["root"]), new Set(), positions).size).toBe(9);
    expect(projectedNodeIds(nodes, edges, "FullMap", null, new Set(), new Set(), positions)).not.toContain("person-1");
    expect(projectedNodeIds(nodes, edges, "FullMap", null, new Set(), new Set(["person-1"]), positions)).toContain("person-1");
  });

  it("focuses only the selected asserted neighborhood", () => {
    const nodes = [classNode("parent"), classNode("selected"), classNode("unrelated")];
    const edges = [edge("hierarchy", "SubclassOf", "selected", "parent")];
    const positions = layeredGraphLayout(nodes, edges);
    expect(projectedNodeIds(nodes, edges, "Focus", null, new Set(), new Set(), positions)).toEqual(new Set(["parent", "selected", "unrelated"]));
    expect(projectedNodeIds(nodes, edges, "Focus", "selected", new Set(), new Set(), positions)).toEqual(new Set(["selected", "parent"]));
    expect(selectionNeighborhood("selected", nodes, edges)).toEqual(new Set(["selected", "parent"]));
  });
});
