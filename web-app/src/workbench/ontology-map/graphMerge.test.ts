import { describe, expect, it } from "vitest";
import { mergeGraphPage } from "./graphMerge";

const node = (id: string) => ({ identity: { id, sourceId: "s", entityIri: `urn:${id}` }, kind: "Class" as const, label: id, definitionExcerpt: null, summary: { directSuperclassLabels: [], domainLabels: [], rangeLabels: [], assertedTypeLabels: [], datatypeRangeLabels: [], loadedRelationshipCount: 0, availableRelationshipCount: 0 } });
const edge = (id: string, sourceNodeId: string, targetNodeId: string) => ({ id, sourceNodeId, targetNodeId, kind: "SubclassOf" as const, label: "subclass of", predicateIri: null, provenance: "Asserted" as const });

describe("bounded graph merging", () => {
  it("deduplicates stable identities, omits orphan edges, and honors aggregate limits", () => {
    const merged = mergeGraphPage([node("a")], [edge("old", "a", "a")], [node("a"), node("b"), node("c")], [edge("new", "a", "b"), edge("orphan", "a", "c")], 2, 2);
    expect(merged.nodes.map((item) => item.identity.id)).toEqual(["a", "b"]);
    expect(merged.edges.map((item) => item.id)).toEqual(["old", "new"]);
    expect(merged.limited).toBe(true);
  });
});
