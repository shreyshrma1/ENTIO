import { describe, expect, it } from "vitest";
import { anchoredScroll, clampZoom, curvedEdgePath, directionalNeighbor, draggedPoint, edgeLabelPoint, graphWorldBounds, layeredGraphLayout, positionsForExpansion } from "./graphLayout";

const node = (id: string, label: string) => ({ identity: { id, sourceId: "s", entityIri: `urn:${id}` }, kind: "Class" as const, label, definitionExcerpt: null, summary: { directSuperclassLabels: [], domainLabels: [], rangeLabels: [], assertedTypeLabels: [], datatypeRangeLabels: [], loadedRelationshipCount: 0, availableRelationshipCount: 0 } });

describe("ontology graph geometry", () => {
  it("lays out deterministic left-to-right layers with stable label ties", () => {
    const nodes = [node("b", "Beta"), node("a", "Alpha"), node("c", "Child")];
    const edges = [{ id: "e", kind: "SubclassOf" as const, sourceNodeId: "a", targetNodeId: "c", label: "subclass of", predicateIri: null, provenance: "Asserted" as const }];
    expect(layeredGraphLayout(nodes, edges)).toEqual(layeredGraphLayout([...nodes].reverse(), edges));
    expect(layeredGraphLayout(nodes, edges).a.x).toBeGreaterThan(layeredGraphLayout(nodes, edges).c.x);
  });

  it("keeps bounds reachable at all supported zooms and navigates with stable ties", () => {
    const positions = { a: { x: 80, y: 80 }, b: { x: 384, y: 80 }, c: { x: 384, y: 188 } };
    const bounds = graphWorldBounds(positions);
    expect([0.25, 1, 2].map((zoom) => [bounds.width * zoom, bounds.height * zoom]).every(([w, h]) => w > 0 && h > 0)).toBe(true);
    expect(directionalNeighbor("a", "right", positions)).toBe("b");
    expect(clampZoom(0.1)).toBe(0.25);
    expect(clampZoom(3)).toBe(2);
  });

  it("places vertical edge labels beside the arrow in the gap between cards", () => {
    const label = edgeLabelPoint({ x: 80, y: 80 }, { x: 80, y: 186 });
    expect(Math.abs(label.x - (80 + 184 / 2))).toBe(14);
    expect(label.y).toBeGreaterThan(80 + 72);
    expect(label.y).toBeLessThan(186);
  });

  it("separates four-pixel drags and preserves the zoom pointer anchor", () => {
    expect(draggedPoint({ x: 10, y: 10 }, 3, 0)).toBeNull();
    expect(draggedPoint({ x: 10, y: 10 }, 4, 0)).toEqual({ x: 14, y: 10 });
    const nextScroll = anchoredScroll(200, 75, 1, 1.5);
    expect(Math.abs((nextScroll + 75) / 1.5 - (200 + 75))).toBeLessThanOrEqual(1);
  });

  it("keeps cyclic ontologies in compact columns and routes curves to card boundaries", () => {
    const cyclicNodes = [node("a", "Account"), node("b", "Borrower"), node("c", "Customer"), { ...node("p", "has account"), kind: "ObjectProperty" as const }];
    const cyclicEdges = [
      { id: "ab", kind: "SubclassOf" as const, sourceNodeId: "a", targetNodeId: "b", label: "subclass of", predicateIri: null, provenance: "Asserted" as const },
      { id: "ba", kind: "SubclassOf" as const, sourceNodeId: "b", targetNodeId: "a", label: "subclass of", predicateIri: null, provenance: "Asserted" as const },
      { id: "pc", kind: "Domain" as const, sourceNodeId: "p", targetNodeId: "c", label: "domain", predicateIri: null, provenance: "Asserted" as const },
    ];
    const positions = layeredGraphLayout(cyclicNodes, cyclicEdges);
    expect(Math.max(...Object.values(positions).map((point) => point.x))).toBeLessThan(1_000);
    expect(positions.p.x).toBe(positions.c.x);
    expect(positions.p.y).toBeGreaterThan(positions.c.y);
    expect(curvedEdgePath({ x: 80, y: 80 }, { x: 380, y: 180 })).toMatch(/^M 264 /);
  });

  it("orders sibling classes by deterministic ontology-fact density", () => {
    const nodes = [node("root", "Root"), node("plain", "Alpha"), node("dense", "Zulu"), node("grandchild", "Grandchild")];
    const edges = [
      { id: "plain-root", kind: "SubclassOf" as const, sourceNodeId: "plain", targetNodeId: "root", label: "subclass of", predicateIri: null, provenance: "Asserted" as const },
      { id: "dense-root", kind: "SubclassOf" as const, sourceNodeId: "dense", targetNodeId: "root", label: "subclass of", predicateIri: null, provenance: "Asserted" as const },
      { id: "grandchild-dense", kind: "SubclassOf" as const, sourceNodeId: "grandchild", targetNodeId: "dense", label: "subclass of", predicateIri: null, provenance: "Asserted" as const },
    ];
    const positions = layeredGraphLayout(nodes, edges);
    expect(positions.dense.y).toBeLessThan(positions.plain.y);
    expect(positions.root.x).toBeLessThan(positions.dense.x);
    expect(positions.dense.x).toBeLessThan(positions.grandchild.x);
  });

  it("packs property-connected class trees together and separates unrelated trees", () => {
    const domain = node("domain", "Domain");
    const range = node("range", "Range");
    const unrelated = node("unrelated", "Unrelated");
    const property = { ...node("property", "connects"), kind: "ObjectProperty" as const };
    const positions = layeredGraphLayout([domain, range, unrelated, property], [
      { id: "domain-edge", kind: "Domain", sourceNodeId: "property", targetNodeId: "domain", label: "domain", predicateIri: null, provenance: "Asserted" },
      { id: "range-edge", kind: "Range", sourceNodeId: "property", targetNodeId: "range", label: "range", predicateIri: null, provenance: "Asserted" },
    ]);
    const connectedDistance = Math.hypot(positions.domain.x - positions.range.x, positions.domain.y - positions.range.y);
    const unrelatedDistance = Math.hypot(positions.domain.x - positions.unrelated.x, positions.domain.y - positions.unrelated.y);
    expect(connectedDistance).toBeLessThan(unrelatedDistance);
    expect(positions.property.x).toBeGreaterThan(Math.min(positions.domain.x, positions.range.x));
    expect(positions.property.x).toBeLessThan(Math.max(positions.domain.x, positions.range.x));
  });

  it("inserts expanded hierarchy and property nodes beside their asserted anchors", () => {
    const root = node("root", "Root");
    const child = node("child", "Child");
    const property = { ...node("property", "Property"), kind: "DatatypeProperty" as const };
    const positions = positionsForExpansion(
      { root: { x: 80, y: 80 } },
      new Set(["root"]),
      [root, child, property],
      [
        { id: "child-root", kind: "SubclassOf", sourceNodeId: "child", targetNodeId: "root", label: "subclass of", predicateIri: null, provenance: "Asserted" },
        { id: "property-root", kind: "Domain", sourceNodeId: "property", targetNodeId: "root", label: "domain", predicateIri: null, provenance: "Asserted" },
      ],
    );
    expect(positions.root).toEqual({ x: 80, y: 80 });
    expect(positions.child.x).toBeGreaterThan(positions.root.x);
    expect(positions.property.x).toBe(positions.root.x);
    expect(positions.property.y).toBeGreaterThan(positions.root.y);
  });
});
