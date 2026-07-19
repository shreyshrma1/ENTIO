import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ContextualEditDialog } from "./ContextualEditing";

describe("contextual individual editing", () => {
  afterEach(() => cleanup());

  it("requires one semantically selected class and submits its resolved IRI", async () => {
    let submitted: Record<string, unknown> | undefined;
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/staged") && init?.method !== "POST") return json(staging([]));
      if (path.includes("/search?")) return json({
        apiVersion: "v1",
        query: "Customer",
        page: {
          items: [{
            iri: "https://example.com/entio/simple#Customer",
            label: "Customer",
            kind: "Class",
            sourceId: "simple",
            reason: "PreferredLabel",
            rank: 1,
            locality: "Local",
          }],
          offset: 0,
          limit: 50,
          total: 1,
          nextOffset: null,
        },
      });
      if (path.endsWith("/staged") && init?.method === "POST") {
        submitted = JSON.parse(String(init.body)) as Record<string, unknown>;
        return json(staging([]));
      }
      if (path.endsWith("/summary")) return json({});
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);
    const onClose = vi.fn();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });

    render(<QueryClientProvider client={client}><ContextualEditDialog
      projectId="simple"
      sourceId="simple"
      editor={{ kind: "typed", editType: "create-individual" }}
      onClose={onClose}
    /></QueryClientProvider>);

    expect(screen.getByText("Create a named individual and assign its required initial class.")).toBeInTheDocument();
    fireEvent.change(screen.getByRole("textbox", { name: "Individual label" }), { target: { value: "Account 101" } });
    const classPicker = screen.getByRole("combobox", { name: "Class" });
    fireEvent.change(classPicker, { target: { value: "Customer" } });
    fireEvent.click(await screen.findByRole("option", { name: /Customer/ }));
    expect(classPicker).toHaveValue("Customer");

    fireEvent.click(screen.getByRole("button", { name: "Add to review queue" }));

    await vi.waitFor(() => expect(submitted).toMatchObject({
      sourceId: "simple",
      editType: "create-individual",
      label: "Account 101",
      classIri: "https://example.com/entio/simple#Customer",
      classLabel: "Customer",
    }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("creates an object property with semantically selected domain and range classes", async () => {
    const submitted: Record<string, unknown>[] = [];
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/staged") && init?.method !== "POST") return json(staging([]));
      if (path.includes("/search?")) {
        const query = new URL(path, "http://localhost").searchParams.get("q") ?? "";
        const label = query === "Account" ? "Account" : "Customer";
        return json({
          apiVersion: "v1",
          query,
          page: {
            items: [{
              iri: `https://example.com/entio/simple#${label}`,
              label,
              kind: "Class",
              sourceId: "simple",
              reason: "PreferredLabel",
              rank: 1,
              locality: "Local",
            }],
            offset: 0,
            limit: 50,
            total: 1,
            nextOffset: null,
          },
        });
      }
      if (path.endsWith("/staged") && init?.method === "POST") {
        const request = JSON.parse(String(init.body)) as Record<string, unknown>;
        submitted.push(request);
        return json(staging([{
          id: "stage-object-property",
          order: 1,
          sourceId: "simple",
          summary: "create-object-property · owns account",
          editType: "create-object-property",
          status: "STAGED",
          authorId: "bob",
          latestEditorId: "bob",
          comment: null,
          aiGenerated: false,
          normalizedValues: { label: "owns account", propertyIri: "https://example.com/entio/simple#ownsAccount" },
          generatedIris: ["https://example.com/entio/simple#ownsAccount"],
          validationMessages: [],
        }]));
      }
      if (path.endsWith("/summary")) return json({});
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);
    const onClose = vi.fn();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });

    render(<QueryClientProvider client={client}><ContextualEditDialog
      projectId="simple"
      sourceId="simple"
      editor={{ kind: "typed", editType: "create-object-property" }}
      onClose={onClose}
    /></QueryClientProvider>);

    fireEvent.change(screen.getByRole("textbox", { name: "Property label" }), { target: { value: "owns account" } });
    const domain = screen.getByRole("combobox", { name: "Domain class" });
    fireEvent.change(domain, { target: { value: "Customer" } });
    fireEvent.click(await screen.findByRole("option", { name: /Customer/ }));
    const range = screen.getByRole("combobox", { name: "Range class" });
    fireEvent.change(range, { target: { value: "Account" } });
    fireEvent.click(await screen.findByRole("option", { name: /Account/ }));
    fireEvent.click(screen.getByRole("button", { name: "Add to review queue" }));

    await vi.waitFor(() => expect(submitted).toHaveLength(3));
    expect(submitted).toEqual([
      expect.objectContaining({ editType: "create-object-property", label: "owns account" }),
      expect.objectContaining({
        editType: "set-property-domain",
        propertyIri: "https://example.com/entio/simple#ownsAccount",
        domainClassIri: "https://example.com/entio/simple#Customer",
      }),
      expect.objectContaining({
        editType: "set-property-range",
        propertyIri: "https://example.com/entio/simple#ownsAccount",
        rangeIri: "https://example.com/entio/simple#Account",
      }),
    ]);
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("closes a datatype-property dialog after the change reaches the review queue", async () => {
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/staged") && init?.method !== "POST") return json(staging([]));
      if (path.endsWith("/staged") && init?.method === "POST") return json(staging([{
        id: "stage-datatype-property",
        order: 1,
        sourceId: "simple",
        summary: "create-datatype-property · date opened",
        editType: "create-datatype-property",
        status: "STAGED",
        authorId: "bob",
        latestEditorId: "bob",
        comment: null,
        aiGenerated: false,
        normalizedValues: { label: "date opened", propertyIri: "https://example.com/dateOpened" },
        generatedIris: ["https://example.com/dateOpened"],
        validationMessages: [],
      }]));
      if (path.endsWith("/summary")) return json({});
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);
    const onClose = vi.fn();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });

    render(<QueryClientProvider client={client}><ContextualEditDialog
      projectId="simple"
      sourceId="simple"
      editor={{ kind: "typed", editType: "create-datatype-property" }}
      onClose={onClose}
    /></QueryClientProvider>);

    fireEvent.change(screen.getByRole("textbox", { name: "Property label" }), { target: { value: "date opened" } });
    fireEvent.click(screen.getByRole("button", { name: "Add to review queue" }));

    await vi.waitFor(() => expect(onClose).toHaveBeenCalledOnce());
  });
});

function staging(entries: unknown[]) {
  return { apiVersion: "v1", projectId: "simple", status: "READY", entries, proposal: null };
}

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
