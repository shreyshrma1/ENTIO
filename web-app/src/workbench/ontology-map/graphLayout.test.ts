import { describe, expect, it } from "vitest";
import { anchoredScroll, clampZoom, directionalNeighbor, draggedPoint, graphWorldBounds, layeredGraphLayout } from "./graphLayout";

const node = (id: string, label: string) => ({ identity: { id, sourceId: "s", entityIri: `urn:${id}` }, kind: "Class" as const, label, definitionExcerpt: null, summary: { directSuperclassLabels: [], domainLabels: [], rangeLabels: [], assertedTypeLabels: [], datatypeRangeLabels: [], loadedRelationshipCount: 0, availableRelationshipCount: 0 } });

describe("ontology graph geometry", () => {
  it("lays out deterministic left-to-right layers with stable label ties", () => {
    const nodes = [node("b", "Beta"), node("a", "Alpha"), node("c", "Child")];
    const edges = [{ id: "e", kind: "SubclassOf" as const, sourceNodeId: "a", targetNodeId: "c", label: "subclass of", predicateIri: null, provenance: "Asserted" as const }];
    expect(layeredGraphLayout(nodes, edges)).toEqual(layeredGraphLayout([...nodes].reverse(), edges));
    expect(layeredGraphLayout(nodes, edges).c.x).toBeGreaterThan(layeredGraphLayout(nodes, edges).a.x);
  });

  it("keeps bounds reachable at all supported zooms and navigates with stable ties", () => {
    const positions = { a: { x: 80, y: 80 }, b: { x: 384, y: 80 }, c: { x: 384, y: 188 } };
    const bounds = graphWorldBounds(positions);
    expect([0.25, 1, 2].map((zoom) => [bounds.width * zoom, bounds.height * zoom]).every(([w, h]) => w > 0 && h > 0)).toBe(true);
    expect(directionalNeighbor("a", "right", positions)).toBe("b");
    expect(clampZoom(0.1)).toBe(0.25);
    expect(clampZoom(3)).toBe(2);
  });

  it("separates four-pixel drags and preserves the zoom pointer anchor", () => {
    expect(draggedPoint({ x: 10, y: 10 }, 3, 0)).toBeNull();
    expect(draggedPoint({ x: 10, y: 10 }, 4, 0)).toEqual({ x: 14, y: 10 });
    const nextScroll = anchoredScroll(200, 75, 1, 1.5);
    expect(Math.abs((nextScroll + 75) / 1.5 - (200 + 75))).toBeLessThanOrEqual(1);
  });
});
