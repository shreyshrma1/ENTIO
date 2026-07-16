import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

describe("application workbench journey", () => {
  beforeEach(() => {
    window.history.pushState({}, "", "/");
    vi.restoreAllMocks();
  });

  it("navigates from a project to a local entity and a FIBO detail", async () => {
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
      if (path.includes("/entities")) {
        return json({ apiVersion: "v1", iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple", sourceOntologyId: "simple", locality: "Local", preferredLabelSource: "RdfsLabel", alternateLabels: [], definitions: [{ value: "A customer.", language: null, datatype: null }], annotations: [], directSuperclasses: [], directSubclasses: [], directlyTypedIndividuals: [], assertedTypes: [], domains: [], ranges: [], outgoingRelationships: [], incomingRelationships: [] });
      }
      if (path.includes("/staged")) {
        return json({ apiVersion: "v1", projectId: "simple", status: "READY", entries: [], proposal: null });
      }
      if (path.includes("/external/fibo/modules")) {
        return json({ sourceId: "fibo", release: "test", page: { items: [{ ontologyIri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/", label: "Agreements", domain: "FND", sourcePath: "source/FND/Agreements/Agreements.rdf", maturity: "Release", curated: true, elementCount: 1 }], offset: 0, limit: 15, total: 1, nextOffset: null } });
      }
      if (path.includes("/external/fibo/module-elements")) {
        return json({ moduleIri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/", page: { items: [{ iri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/Agreement", label: "agreement", kind: "Class", moduleIri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/", domain: "FND", maturity: "Release", catalogStatus: "Available", sourcePath: "source/FND/Agreements/Agreements.rdf", alternateLabels: [], definitions: ["a mutual understanding"], parents: [], domains: [], ranges: [] }], offset: 0, limit: 15, total: 1, nextOffset: null } });
      }
      if (path.includes("/external/fibo/details")) {
        return json({ element: { iri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/Agreement", label: "agreement", kind: "Class", moduleIri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/", domain: "FND", maturity: "Release", catalogStatus: "Available", sourcePath: "source/FND/Agreements/Agreements.rdf", alternateLabels: [], definitions: ["a mutual understanding"], parents: [], domains: [], ranges: [] }, dependencies: [] });
      }
      return json({ apiVersion: "v1", page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
    }));

    render(<App />);

    fireEvent.click(await screen.findByRole("link", { name: /Simple ontology/ }));
    expect(await screen.findByRole("heading", { name: "simple-ontology" })).toBeInTheDocument();
    fireEvent.click(await screen.findByRole("button", { name: /Customer/ }));
    expect(await screen.findByText("A customer.")).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "External ontology browser" })).toBeInTheDocument();

    fireEvent.click(await screen.findByRole("button", { name: /Agreements/ }));
    fireEvent.click(await screen.findByRole("button", { name: /agreement/ }));
    expect(await screen.findByText("a mutual understanding")).toBeInTheDocument();
    expect(screen.getByText(/https:\/\/spec\.edmcouncil\.org\/fibo\/ontology\/FND\/Agreements/)).toBeInTheDocument();
  });
});

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
