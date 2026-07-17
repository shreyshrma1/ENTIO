import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

describe("web workbench shell", () => {
  beforeEach(() => {
    window.history.pushState({}, "", "/");
  });

  it("loads an approved project and opens a label-first entity tab", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path === "/api/v1/projects") {
        return json({ apiVersion: "v1", projects: [{ id: "simple", displayName: "Simple ontology" }] });
      }
      if (path.includes("/summary")) {
        return json({
          apiVersion: "v1",
          project: { id: "simple", displayName: "Simple ontology", name: "simple-ontology" },
          sources: [{ id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], tripleCount: 4 }],
          symbolCount: 2,
          graphTripleCount: 4,
        });
      }
      if (path.includes("/hierarchy")) {
        return json({ apiVersion: "v1", sourceId: "simple", parentIri: null, page: { items: [{ iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple", childCount: 0 }], offset: 0, limit: 50, total: 1, nextOffset: null } });
      }
      if (path.includes("/outline")) {
        return json({ apiVersion: "v1", sourceId: "simple", page: { items: [
          { iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple" },
          { iri: "https://example.com/Shrey", label: "Shrey", kind: "Individual", sourceId: "simple" },
          { iri: "https://example.com/receivedInvoice", label: "received invoice", kind: "ObjectProperty", sourceId: "simple" },
        ], offset: 0, limit: 100, total: 3, nextOffset: null } });
      }
      if (path.includes("/entities")) {
        return json({ apiVersion: "v1", iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple", sourceOntologyId: "simple", locality: "Local", preferredLabelSource: "RdfsLabel", alternateLabels: [], definitions: [{ value: "A customer.", language: null, datatype: null }], annotations: [], directSuperclasses: [], directSubclasses: [], directlyTypedIndividuals: [], assertedTypes: [], domains: [], ranges: [], outgoingRelationships: [], incomingRelationships: [] });
      }
      return json({ apiVersion: "v1", query: "", page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
    }));

    render(<App />);

    expect(await screen.findByRole("heading", { name: "Approved projects" })).toBeInTheDocument();
    fireEvent.click(await screen.findByRole("link", { name: /Simple ontology/ }));
    expect(await screen.findByRole("heading", { name: "simple-ontology" })).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Shrey, Object" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "received invoice, Object property" })).toBeInTheDocument();
    fireEvent.click(await screen.findByRole("button", { name: /Customer/ }));
    expect(await screen.findByRole("heading", { name: "Customer" })).toBeInTheDocument();
    expect(screen.getByText("A customer.")).toBeInTheDocument();
    expect(screen.getByText("Technical details")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Customer" })).toHaveAttribute("aria-selected", "true");
    fireEvent.click(screen.getByRole("button", { name: "Expand navigation" }));
    fireEvent.click(screen.getByRole("button", { name: "Account" }));
    expect(screen.getByText("Alice Contributor")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open settings" }));
    fireEvent.change(screen.getByRole("textbox", { name: "Display name" }), { target: { value: "Shrey Sharma" } });
    fireEvent.click(screen.getByRole("button", { name: "Save name" }));
    expect(await screen.findByText("Display name updated.")).toHaveAttribute("role", "status");
    fireEvent.click(screen.getByRole("button", { name: "Account" }));
    expect(screen.getByText("Shrey Sharma")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Explore" }));
    fireEvent.click(screen.getByRole("button", { name: "Close Customer" }));
    expect(await screen.findByRole("heading", { name: "Select an entity" })).toBeInTheDocument();
  });

  it("renders a clear project loading error", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("unavailable", { status: 503 })));

    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load projects");
  });
});

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
