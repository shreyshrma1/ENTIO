import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

describe("application workbench journey", () => {
  beforeEach(() => {
    window.history.pushState({}, "", "/");
    vi.restoreAllMocks();
  });

  it("navigates from a project to a local entity and a FIBO detail", async () => {
    const requestedPaths: string[] = [];
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      requestedPaths.push(path);
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

    const app = render(<App />);

    fireEvent.click(await screen.findByRole("link", { name: /Simple ontology/ }));
    expect(await screen.findByRole("heading", { name: "simple-ontology" })).toBeInTheDocument();
    await waitFor(() => expect(requestedPaths.some((path) => path.includes("ensure-applied-reasoning"))).toBe(true));
    const reasoningIndex = requestedPaths.findIndex((path) => path.includes("ensure-applied-reasoning"));
    expect(reasoningIndex).toBeGreaterThan(requestedPaths.findIndex((path) => path.includes("/hierarchy")));
    expect(reasoningIndex).toBeGreaterThan(requestedPaths.findIndex((path) => path.includes("/outline")));
    expect(reasoningIndex).toBeGreaterThan(requestedPaths.findIndex((path) => path.includes("/staged")));
    expect(requestedPaths.filter((path) => path.includes("/hierarchy")).length).toBe(1);
    expect(requestedPaths.filter((path) => path.includes("/outline")).length).toBe(1);
    expect(requestedPaths.find((path) => path.includes("/hierarchy"))).toContain("sourceId=simple");
    expect(requestedPaths.find((path) => path.includes("/outline"))).toContain("sourceId=simple");
    fireEvent.click(await screen.findByRole("button", { name: /Customer/ }));
    expect(await screen.findByRole("textbox", { name: "Definition" })).toHaveValue("A customer.");
    app.unmount();
    render(<App />);
    expect(await screen.findByRole("textbox", { name: "Definition" })).toHaveValue("A customer.");
    fireEvent.click(await screen.findByRole("tab", { name: "FIBO" }));
    expect(window.location.search).toBe("?module=fibo");
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
