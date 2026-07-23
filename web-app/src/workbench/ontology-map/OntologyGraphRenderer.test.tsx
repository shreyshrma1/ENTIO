import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { OntologyGraphNodeKind, WebOntologyGraphNode } from "../../web/contracts";
import OntologyGraphRenderer from "./OntologyGraphRenderer";

const kinds: OntologyGraphNodeKind[] = ["Class", "ObjectProperty", "DatatypeProperty", "Individual"];
const nodes: WebOntologyGraphNode[] = kinds.map((kind, index) => ({
  identity: { id: `node-${index}`, sourceId: "simple", entityIri: `urn:${kind}` }, kind, label: `${kind} label`, definitionExcerpt: null,
  summary: { directSuperclassLabels: [], domainLabels: [], rangeLabels: [], assertedTypeLabels: [], datatypeRangeLabels: [], loadedRelationshipCount: 0, availableRelationshipCount: 0 },
}));

describe("accessible ontology graph renderer", () => {
  it("names every supported node kind, labels directed edges, and does not navigate on double click", () => {
    const onStateChange = vi.fn();
    const onViewDetails = vi.fn();
    render(<OntologyGraphRenderer nodes={nodes} edges={[{ id: "edge", kind: "Domain", sourceNodeId: "node-1", targetNodeId: "node-0", label: "domain", predicateIri: null, provenance: "Asserted" }]} state={{ selectedNodeId: null }} onStateChange={onStateChange} />);
    kinds.forEach((kind) => expect(screen.getByRole("button", { name: `${kind}: ${kind} label` })).toBeVisible());
    expect(screen.getByText("domain")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Class: Class label" }));
    expect(onStateChange).toHaveBeenCalledWith(expect.objectContaining({ selectedNodeId: "node-0" }));
    fireEvent.doubleClick(screen.getByRole("button", { name: "Individual: Individual label" }));
    expect(onViewDetails).not.toHaveBeenCalled();
  });

  it("moves a node without selecting it when the pointer action is a drag", () => {
    const onStateChange = vi.fn();
    const renderer = render(<OntologyGraphRenderer nodes={nodes} edges={[]} state={{ selectedNodeId: null }} onStateChange={onStateChange} />);
    const classNode = renderer.container.querySelector<HTMLButtonElement>('[aria-label="Class: Class label"]')!;
    classNode.setPointerCapture = vi.fn();
    fireEvent.pointerDown(classNode, { pointerId: 1, clientX: 100, clientY: 100 });
    fireEvent.pointerMove(classNode, { pointerId: 1, clientX: 120, clientY: 120 });
    fireEvent.pointerUp(classNode, { pointerId: 1, clientX: 120, clientY: 120 });
    fireEvent.click(classNode);
    expect(onStateChange).toHaveBeenCalled();
    expect(onStateChange.mock.calls.some(([next]) => next.selectedNodeId === "node-0")).toBe(false);
  });

  it("captures wheel and trackpad pinch gestures as map-local zoom", () => {
    const onStateChange = vi.fn();
    const renderer = render(<OntologyGraphRenderer nodes={nodes} edges={[]} state={{ selectedNodeId: null, zoom: 1 }} onStateChange={onStateChange} />);
    const viewport = renderer.container.querySelector<HTMLElement>(".ontology-graph-viewport")!;
    expect(fireEvent.wheel(viewport, { ctrlKey: false, deltaY: 100, clientX: 20, clientY: 20 })).toBe(false);
    expect(fireEvent.wheel(viewport, { ctrlKey: true, deltaY: -100, clientX: 20, clientY: 20 })).toBe(false);
    expect(onStateChange).toHaveBeenCalledWith(expect.objectContaining({ zoom: expect.any(Number) }));
  });

  it("labels inferred edges by graph state and provides a non-color legend", () => {
    const view = render(<OntologyGraphRenderer nodes={nodes} edges={[
      { id: "applied", kind: "Type", sourceNodeId: "node-3", targetNodeId: "node-0", label: "type", predicateIri: null, provenance: "Inferred", inferredGraphState: "Applied" },
      { id: "proposal", kind: "Domain", sourceNodeId: "node-1", targetNodeId: "node-0", label: "domain", predicateIri: null, provenance: "Inferred", inferredGraphState: "Proposal" },
    ]} state={{ selectedNodeId: null }} onStateChange={vi.fn()} />);
    const rendered = within(view.container);
    expect(rendered.getByText("type · Inferred · Applied")).toBeVisible();
    expect(rendered.getByText("domain · Inferred · Proposal")).toBeVisible();
    const legend = rendered.getByLabelText("Relationship legend");
    expect(legend).toHaveTextContent("Asserted");
    expect(legend).toHaveTextContent("Inferred · Applied");
    expect(legend).toHaveTextContent("Inferred · Proposal");
  });

  it("pans with left-drag on empty space and right-drag over a node", () => {
    const renderer = render(<OntologyGraphRenderer nodes={nodes} edges={[]} state={{ selectedNodeId: null, zoom: 1 }} onStateChange={vi.fn()} />);
    const viewport = renderer.container.querySelector<HTMLElement>(".ontology-graph-viewport")!;
    const classNode = renderer.container.querySelector<HTMLButtonElement>('[aria-label="Class: Class label"]')!;
    viewport.setPointerCapture = vi.fn();
    const initialLeft = viewport.scrollLeft;
    const initialTop = viewport.scrollTop;
    fireEvent.pointerDown(viewport, { pointerId: 1, button: 0, clientX: 100, clientY: 100 });
    fireEvent.pointerMove(viewport, { pointerId: 1, clientX: 60, clientY: 70 });
    fireEvent.pointerUp(viewport, { pointerId: 1 });
    expect(viewport.scrollLeft).toBe(initialLeft + 40);
    expect(viewport.scrollTop).toBe(initialTop + 30);
    fireEvent.pointerDown(classNode, { pointerId: 2, button: 2, clientX: 100, clientY: 100 });
    fireEvent.pointerMove(viewport, { pointerId: 2, clientX: 80, clientY: 60 });
    fireEvent.pointerUp(viewport, { pointerId: 2 });
    expect(viewport.scrollLeft).toBe(initialLeft + 60);
    expect(viewport.scrollTop).toBe(initialTop + 70);
    expect(fireEvent.contextMenu(viewport)).toBe(false);
  });

  it("places selected entity information inside the pannable graph world", () => {
    const renderer = render(<OntologyGraphRenderer
      nodes={nodes}
      edges={[]}
      state={{ selectedNodeId: "node-0" }}
      worldOverlay={{ nodeId: "node-0", content: <aside>Entity information</aside> }}
      onStateChange={vi.fn()}
    />);
    expect(renderer.container.querySelector(".ontology-graph-world .ontology-graph-world-overlay")).toContainElement(screen.getByText("Entity information"));
  });
});
