import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import ExternalOntologyPanel from "./ExternalOntologyPanel";

describe("external ontology browser", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("does not show details loading until an element is selected and opens details in a dialog", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.includes("/external/fibo/modules")) return json({ sourceId: "fibo", release: "2025", page: { items: [], offset: 0, limit: 15, total: 0, nextOffset: null } });
      if (path.includes("/external/fibo/search")) return json({ query: "agreement", page: { items: [element()], offset: 0, limit: 15, total: 1, nextOffset: null } });
      if (path.includes("/external/fibo/details")) return json({ apiVersion: "v1", element: element(), dependencies: [
        { category: "SourceOntology", requirement: "Required", visibility: "UserVisible", selection: "Missing", reason: "Defined by this module.", externalIri: "https://spec.example/module/", label: "Contracts" },
        { category: "SemanticParent", requirement: "Required", visibility: "UserVisible", selection: "Missing", reason: "Explicit superclass.", externalIri: "https://spec.example/situation", label: "situation" },
      ] });
      throw new Error(`Unexpected request: ${path}`);
    }));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });

    render(<QueryClientProvider client={client}><ExternalOntologyPanel projectId="simple" sourceId="simple" /></QueryClientProvider>);

    expect(screen.queryByText("Loading external details...")).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole("textbox", { name: "Search FIBO" }), { target: { value: "agreement" } });
    fireEvent.click(screen.getByRole("button", { name: "Search" }));
    fireEvent.click(await screen.findByRole("button", { name: /bilateral contract/ }));

    expect(await screen.findByRole("dialog", { name: "bilateral contract" })).toBeInTheDocument();
    expect(screen.getByText("https://spec.example/contract")).toBeInTheDocument();
    expect(screen.getByText("Definition:")).toBeInTheDocument();
    expect(screen.getByText(/To import this class/)).toBeInTheDocument();
    expect(screen.getByText("Ontology module · adds owl:imports · Missing")).toBeInTheDocument();
    expect(screen.getByText("Semantic parent · context only · Missing")).toBeInTheDocument();
    expect(screen.getByLabelText("Required ontology import")).toBeInTheDocument();
    expect(screen.queryByLabelText("Intent")).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Stage reuse proposal" })).not.toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Target ontology" })).toBeInTheDocument();
  });

  it("loads the full module page and highlights the selected module", async () => {
    let moduleRequest = "";
    const moduleItems = Array.from({ length: 70 }, (_, index) => ({
      ...element(),
      iri: `https://spec.example/contract-${index + 1}`,
      label: `contract ${index + 1}`,
    }));
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.includes("/external/fibo/modules")) return json({ sourceId: "fibo", release: "2025", page: { items: [{ ontologyIri: "https://spec.example/contracts", label: "Contracts", domain: "FND", sourcePath: "contracts.ttl", maturity: "Release", curated: true, elementCount: 70 }], offset: 0, limit: 15, total: 1, nextOffset: null } });
      if (path.includes("/external/fibo/module-elements")) {
        moduleRequest = path;
        return json({ moduleIri: "https://spec.example/contracts", page: { items: moduleItems, offset: 0, limit: 100, total: 70, nextOffset: null } });
      }
      throw new Error(`Unexpected request: ${path}`);
    }));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });

    render(<QueryClientProvider client={client}><ExternalOntologyPanel projectId="simple" sourceId="simple" /></QueryClientProvider>);

    fireEvent.click(await screen.findByRole("button", { name: /Contracts/ }));

    expect(await screen.findByRole("button", { name: /contract 70/ })).toBeInTheDocument();
    expect(moduleRequest).toContain("limit=100");
    expect(screen.getByRole("button", { name: /Contracts/ })).toHaveClass("selected-list-item");
  });

  it("switches from module browsing to search results", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.includes("/external/fibo/modules")) return json({ sourceId: "fibo", release: "2025", page: { items: [{ ontologyIri: "https://spec.example/contracts", label: "Contracts", domain: "FND", sourcePath: "contracts.ttl", maturity: "Release", curated: true, elementCount: 1 }], offset: 0, limit: 15, total: 1, nextOffset: null } });
      if (path.includes("/external/fibo/module-elements")) return json({ moduleIri: "https://spec.example/contracts", page: { items: [element()], offset: 0, limit: 100, total: 1, nextOffset: null } });
      if (path.includes("/external/fibo/search")) return json({ query: "situation", page: { items: [{ ...element(), iri: "https://spec.example/situation", label: "situation" }], offset: 0, limit: 15, total: 1, nextOffset: null } });
      throw new Error(`Unexpected request: ${path}`);
    }));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });

    render(<QueryClientProvider client={client}><ExternalOntologyPanel projectId="simple" sourceId="simple" /></QueryClientProvider>);
    fireEvent.click(await screen.findByRole("button", { name: /Contracts/ }));
    expect(await screen.findByRole("button", { name: /bilateral contract/ })).toBeInTheDocument();
    fireEvent.change(screen.getByRole("textbox", { name: "Search FIBO" }), { target: { value: "situation" } });
    fireEvent.click(screen.getByRole("button", { name: "Search" }));

    expect(await screen.findByRole("button", { name: /situation/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /bilateral contract/ })).not.toBeInTheDocument();
  });
});

function element() {
  return {
    iri: "https://spec.example/contract",
    label: "bilateral contract",
    kind: "Class",
    moduleIri: "https://spec.example/module",
    domain: "Contracts",
    maturity: "Release",
    catalogStatus: "Wider",
    sourcePath: "fibo.ttl",
    alternateLabels: [],
    definitions: ["A contract between two parties."],
    parents: [],
    domains: [],
    ranges: [],
  };
}

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
