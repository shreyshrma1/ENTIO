import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ContextualEditDialog } from "./ContextualEditing";

describe("contextual individual editing", () => {
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
});

function staging(entries: unknown[]) {
  return { apiVersion: "v1", projectId: "simple", status: "READY", entries, proposal: null };
}

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
