import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { WebEntityDetailResponse, WebStagedEntry } from "../web/projectApi";
import EntityDetails from "./EntityDetails";

describe("entity details editing", () => {
  beforeEach(() => vi.restoreAllMocks());

  it("synchronizes a changed field and removes it when restored to its applied value", async () => {
    let entries: WebStagedEntry[] = [];
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/sources")) return json(page([]));
      if (path.endsWith("/staged") && init?.method === "POST") {
        const request = JSON.parse(String(init.body)) as Record<string, string>;
        entries = [{
          id: request.replacesStagedId ?? "stage-1",
          order: 1,
          sourceId: "simple",
          summary: `${request.editType} · Customer`,
          editType: request.editType,
          status: "STAGED",
          authorId: "bob",
          latestEditorId: "bob",
          comment: null,
          aiGenerated: false,
          normalizedValues: { resourceIri: request.resourceIri, label: request.label },
          generatedIris: [],
          validationMessages: [],
        }];
        return json(staging(entries));
      }
      if (path.endsWith("/staged/stage-1") && init?.method === "DELETE") {
        entries = [];
        return json(staging(entries));
      }
      if (path.endsWith("/staged")) return json(staging(entries));
      if (path.endsWith("/summary")) return json({});
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);

    renderEntity(classEntity());

    const label = screen.getByRole("textbox", { name: "Preferred label" });
    expect(screen.queryByRole("button", { name: /Stage label/i })).not.toBeInTheDocument();
    fireEvent.change(label, { target: { value: "Client" } });
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged",
      expect.objectContaining({ method: "POST", body: expect.stringContaining('"label":"Client"') }),
    ));

    fireEvent.change(label, { target: { value: "Customer" } });
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged/stage-1",
      expect.objectContaining({ method: "DELETE" }),
    ));
  });

  it("does not expose a schema tab for objects and limits relationship targets to individuals", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/sources")) return json(page([]));
      if (path.endsWith("/staged")) return json(staging([]));
      if (path.includes("/search")) return json({
        apiVersion: "v1",
        query: "Invoice",
        page: page([
          { iri: "https://example.com/Invoice", label: "Invoice", kind: "Class", sourceId: "simple", reason: "PreferredLabel", rank: 1, locality: "Local" },
          { iri: "https://example.com/Invoice20874", label: "Invoice 20874", kind: "Individual", sourceId: "simple", reason: "PreferredLabel", rank: 1, locality: "Local" },
          { iri: "https://example.com/receivedInvoice", label: "received invoice", kind: "ObjectProperty", sourceId: "simple", reason: "PreferredLabel", rank: 1, locality: "Local" },
        ]),
      });
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderEntity({ ...classEntity(), iri: "https://example.com/Shrey", label: "Shrey", kind: "Individual", assertedTypes: [{ iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple" }] });

    expect(await screen.findByRole("tab", { name: "Relationships" })).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "Schema" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Relationships" }));
    expect(screen.getByRole("heading", { name: "Outgoing object relationships" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Incoming object relationships" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Datatype values" })).toBeInTheDocument();
    fireEvent.change(screen.getByRole("combobox", { name: "Object" }), { target: { value: "Invoice" } });
    fireEvent.click(await screen.findByRole("option", { name: /Invoice 20874/ }));
    expect(screen.getByRole("combobox", { name: "Object" })).toHaveValue("Invoice 20874");
    expect(screen.queryByRole("button", { name: "Remove Invoice 20874" })).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole("combobox", { name: "Object" }), { target: { value: "Invoice" } });
    expect(await screen.findByRole("option", { name: /Invoice 20874/ })).toBeInTheDocument();
    const options = screen.getByRole("listbox");
    expect(within(options).getAllByRole("option")).toHaveLength(1);
    expect(options).not.toHaveTextContent("received invoice");
  });

  it("removes a staged property domain when the selection is cleared", async () => {
    let entries: WebStagedEntry[] = [];
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/sources")) return json(page([]));
      if (path.includes("/search")) return json({
        apiVersion: "v1",
        query: "Account",
        page: page([
          { iri: "https://example.com/Account", label: "Account", kind: "Class", sourceId: "simple", reason: "PreferredLabel", rank: 1, locality: "Local" },
        ]),
      });
      if (path.endsWith("/staged") && init?.method === "POST") {
        const request = JSON.parse(String(init.body)) as Record<string, string>;
        entries = [{
          id: "stage-domain",
          order: 1,
          sourceId: "simple",
          summary: "set-property-domain · owns account",
          editType: "set-property-domain",
          status: "STAGED",
          authorId: "bob",
          latestEditorId: "bob",
          comment: null,
          aiGenerated: false,
          normalizedValues: {
            propertyIri: request.propertyIri,
            propertyLabel: request.propertyLabel,
            domainClassIri: request.domainClassIri,
            domainClassLabel: request.domainClassLabel,
          },
          generatedIris: [],
          validationMessages: [],
        }];
        return json(staging(entries));
      }
      if (path.endsWith("/staged/stage-domain") && init?.method === "DELETE") {
        entries = [];
        return json(staging(entries));
      }
      if (path.endsWith("/staged")) return json(staging(entries));
      if (path.endsWith("/summary")) return json({});
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);

    renderEntity(propertyEntity());
    fireEvent.click(await screen.findByRole("tab", { name: "Schema" }));
    fireEvent.change(screen.getByRole("combobox", { name: "Set domain" }), { target: { value: "Account" } });
    fireEvent.click(await screen.findByRole("option", { name: /Account/ }));
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged",
      expect.objectContaining({ method: "POST", body: expect.stringContaining('"editType":"set-property-domain"') }),
    ));

    fireEvent.click(screen.getByRole("button", { name: "Remove Account" }));
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged/stage-domain",
      expect.objectContaining({ method: "DELETE" }),
    ));
    expect(entries).toEqual([]);
    expect(await screen.findByText("This field's staged changes were removed. The review queue is now empty.")).toBeInTheDocument();
  });
});

function renderEntity(entity: WebEntityDetailResponse) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(<QueryClientProvider client={client}><EntityDetails projectId="simple" iri={entity.iri} stagedEntity={entity} /></QueryClientProvider>);
}

function classEntity(): WebEntityDetailResponse {
  return {
    apiVersion: "v1",
    iri: "https://example.com/Customer",
    label: "Customer",
    kind: "Class",
    sourceId: "simple",
    sourceOntologyId: "simple",
    locality: "Local",
    preferredLabelSource: "RdfsLabel",
    alternateLabels: [],
    definitions: [],
    annotations: [],
    directSuperclasses: [],
    directSubclasses: [],
    directlyTypedIndividuals: [],
    assertedTypes: [],
    domains: [],
    ranges: [],
    outgoingRelationships: [],
    incomingRelationships: [],
  };
}

function propertyEntity(): WebEntityDetailResponse {
  return {
    ...classEntity(),
    iri: "https://example.com/ownsAccount",
    label: "owns account",
    kind: "ObjectProperty",
  };
}

function staging(entries: WebStagedEntry[]) {
  return { apiVersion: "v1", projectId: "simple", status: "READY", entries, proposal: null };
}

function page<T>(items: T[]) {
  return { items, offset: 0, limit: 50, total: items.length, nextOffset: null };
}

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
