import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { WebStagedEntry } from "../web/projectApi";
import StagingPanel from "./StagingPanel";

describe("staging panel", () => {
  it("keeps staged changes removable while proposal preview is running", async () => {
    let resolvePreview: ((response: Response) => void) | undefined;
    const preview = new Promise<Response>((resolve) => { resolvePreview = resolve; });
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (init?.method === "POST" && path.endsWith("/proposal/preview")) return preview;
      if (init?.method === "DELETE" && path.endsWith("/staged/stage-1")) return json(stagingResponse([]));
      if (path.endsWith("/staged")) return json(stagingResponse([stagedEntry()]));
      if (path.endsWith("/summary")) return json({});
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });

    render(<QueryClientProvider client={client}><StagingPanel projectId="simple" /></QueryClientProvider>);

    expect(await screen.findByText("Updating staged changes...")).toBeInTheDocument();
    const remove = screen.getByRole("button", { name: "Remove" });
    expect(remove).toBeEnabled();
    fireEvent.click(remove);

    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged/stage-1",
      expect.objectContaining({ method: "DELETE" }),
    ));
    resolvePreview?.(json(stagingResponse([])));
  });

  it("renders proposal validation findings with labels instead of IRIs", async () => {
    const entry = {
      ...stagedEntry(),
      summary: "add-object-property-assertion · owns account",
      editType: "add-object-property-assertion",
      normalizedValues: {
        subjectIri: "https://example.com/entio/simple#Shrey",
        subjectLabel: "Shrey",
        propertyIri: "https://example.com/entio/simple#ownsAccount",
        propertyLabel: "owns account",
        objectIri: "https://example.com/entio/simple#20874",
        objectLabel: "Invoice 20874",
      },
    };
    const message = "Object 'https://example.com/entio/simple#20874' is not an instance of the declared range 'https://example.com/entio/simple#Account' for property 'https://example.com/entio/simple#ownsAccount'.";
    const fetcher = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/staged")) return json({
        ...stagingResponse([entry]),
        status: "VERIFICATIONFAILED",
        proposal: {
          id: "proposal-1",
          status: "VERIFICATIONFAILED",
          stagedChangeIds: [entry.id],
          baselineProjectFingerprint: "baseline",
          validationMessages: [message],
          validationIssues: [],
          diff: [],
          targetSourceIds: ["simple"],
          shaclImpact: null,
          message: null,
        },
      });
      if (path.endsWith("/summary")) return json({});
      throw new Error(`Unexpected request: ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });

    render(<QueryClientProvider client={client}><StagingPanel projectId="simple" /></QueryClientProvider>);

    expect(await screen.findByText("Object 'Invoice 20874' is not an instance of the declared range 'Account' for property 'owns account'.")).toBeInTheDocument();
    expect(screen.queryByText(/https:\/\/example\.com\/entio\/simple/)).not.toBeInTheDocument();
  });
});

function stagingResponse(entries: WebStagedEntry[]) {
  return { apiVersion: "v1", projectId: "simple", status: "READY", entries, proposal: null };
}

function stagedEntry(): WebStagedEntry {
  return {
    id: "stage-1",
    order: 1,
    sourceId: "simple",
    summary: "set-entity-label · Customer",
    editType: "set-entity-label",
    status: "STAGED",
    authorId: "bob",
    latestEditorId: "bob",
    comment: null,
    aiGenerated: false,
    normalizedValues: { resourceIri: "https://example.com/Customer", label: "Client" },
    generatedIris: [],
    validationMessages: [],
  };
}

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
