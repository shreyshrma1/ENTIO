import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { OntologyGraphNodeKind, WebOntologyGraphNode } from "../../web/contracts";
import OntologyGraphRenderer from "./OntologyGraphRenderer";

const kinds: OntologyGraphNodeKind[] = ["Class", "ObjectProperty", "DatatypeProperty", "Individual"];
const nodes: WebOntologyGraphNode[] = kinds.map((kind, index) => ({
  identity: { id: `node-${index}`, sourceId: "simple", entityIri: `urn:${kind}` }, kind, label: `${kind} label`, definitionExcerpt: null,
  summary: { directSuperclassLabels: [], domainLabels: [], rangeLabels: [], assertedTypeLabels: [], datatypeRangeLabels: [], loadedRelationshipCount: 0, availableRelationshipCount: 0 },
}));

describe("accessible ontology graph renderer", () => {
  it("names every supported node kind, labels directed edges, and hands off details without mutation", () => {
    const onStateChange = vi.fn();
    const onViewDetails = vi.fn();
    render(<OntologyGraphRenderer nodes={nodes} edges={[{ id: "edge", kind: "Domain", sourceNodeId: "node-1", targetNodeId: "node-0", label: "domain", predicateIri: null, provenance: "Asserted" }]} state={{ selectedNodeId: null }} onStateChange={onStateChange} onViewDetails={onViewDetails} />);
    kinds.forEach((kind) => expect(screen.getByRole("button", { name: `${kind}: ${kind} label` })).toBeVisible());
    expect(screen.getByText("domain")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Class: Class label" }));
    expect(onStateChange).toHaveBeenCalledWith(expect.objectContaining({ selectedNodeId: "node-0" }));
    fireEvent.doubleClick(screen.getByRole("button", { name: "Individual: Individual label" }));
    expect(onViewDetails).toHaveBeenCalledWith(nodes[3]);
  });
});
