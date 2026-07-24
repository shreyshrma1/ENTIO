import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { WebStagedEntry } from "../web/projectApi";
import StagingPanel from "./StagingPanel";

describe("staging panel", () => {
  afterEach(() => cleanup());

  it("keeps individual edits behind proposal details while preview is running", async () => {
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
    expect(screen.queryByRole("button", { name: "Remove" })).not.toBeInTheDocument();
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

  it("recovers from a failed proposal preview and restores proposal decisions", async () => {
    const entry = {
      ...stagedEntry(),
      id: "stage-shacl",
      summary: "shacl-create-property-shape",
      editType: "shacl-create-property-shape",
      normalizedValues: {
        shapeLabel: "Minimum Accounts",
        targetClassIri: "https://example.com/Checking",
        propertyIri: "https://example.com/ownsAccount",
      },
    };
    let previewAttempts = 0;
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (init?.method === "POST" && path.endsWith("/proposal/preview")) {
        previewAttempts += 1;
        if (previewAttempts === 1) {
          return new Response(JSON.stringify({ message: "The preview could not be prepared." }), {
            status: 400,
            headers: { "Content-Type": "application/json" },
          });
        }
        return json({
          ...stagingResponse([entry]),
          proposal: {
            id: "proposal-1",
            status: "READYFORREVIEW",
            stagedChangeIds: [entry.id],
            baselineProjectFingerprint: "baseline",
            validationMessages: [],
            validationIssues: [],
            diff: [{ kind: "Added", subject: "https://example.com/MinimumAccounts", predicate: null, objectValue: null, description: "Added Minimum Accounts." }],
            targetSourceIds: ["shapes"],
            shaclImpact: null,
            message: null,
          },
        });
      }
      if (path.endsWith("/staged")) return json(stagingResponse([entry]));
      if (path.endsWith("/summary")) return json({});
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });

    render(<QueryClientProvider client={client}><StagingPanel projectId="simple" /></QueryClientProvider>);

    expect(await screen.findByRole("button", { name: "Retry proposal" })).toBeEnabled();
    expect(screen.queryByText("Updating staged changes...")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Retry proposal" }));

    expect(await screen.findByRole("button", { name: "Accept" })).toBeEnabled();
    fireEvent.click(screen.getByRole("button", { name: "View Details" }));
    expect(await screen.findByText("Minimum Accounts")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Reject" }).some((button) => !(button as HTMLButtonElement).disabled)).toBe(true);
    expect(screen.getByRole("heading", { name: "Ready for review" })).toBeInTheDocument();
  });

  it("removes rejected proposal entries from the active review queue", async () => {
    const entry = stagedEntry();
    const proposal = {
      id: "proposal-1",
      status: "READYFORREVIEW",
      stagedChangeIds: [entry.id],
      baselineProjectFingerprint: "baseline",
      validationMessages: [],
      validationIssues: [],
      diff: [],
      targetSourceIds: ["simple"],
      shaclImpact: null,
      message: null,
    };
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (init?.method === "POST" && path.endsWith("/proposal/preview")) return json({ ...stagingResponse([entry]), proposal });
      if (init?.method === "POST" && path.endsWith("/proposal/reject")) return json(stagingResponse([]));
      if (path.endsWith("/staged")) return json(stagingResponse([entry]));
      if (path.endsWith("/summary")) return json({});
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });

    render(<QueryClientProvider client={client}><StagingPanel projectId="simple" /></QueryClientProvider>);

    fireEvent.click(await screen.findByRole("button", { name: "Reject" }));
    expect(await screen.findByText("The shared review queue is empty.")).toBeInTheDocument();
    expect(screen.queryByText("Customer")).not.toBeInTheDocument();
    expect(screen.getByText("Proposal rejected. Its source files were not changed.")).toBeInTheDocument();
  });

  it("shows each AI edit in proposal details across multiple sources", async () => {
    const aiTriple = { aiRunId: "run-1", operation: "remove", subjectIri: "https://example.com/Customer", subjectLabel: "Customer", predicateIri: "https://example.com/ownsAccount", predicateLabel: "owns account", objectIri: "https://example.com/Account101", objectLabel: "Account 101" };
    const ontologyEntry = { ...stagedEntry(), id: "stage-ontology", summary: "AI proposal: Remove Customer associations", sourceId: "simple", normalizedValues: aiTriple };
    const shapesEntry = { ...stagedEntry(), id: "stage-shapes", summary: "AI proposal: Remove Customer associations", sourceId: "shapes", normalizedValues: aiTriple };
    const proposal = { id: "proposal-1", status: "READYFORREVIEW", stagedChangeIds: [ontologyEntry.id, shapesEntry.id], baselineProjectFingerprint: "baseline", validationMessages: [], validationIssues: [], diff: [], targetSourceIds: ["simple", "shapes"], shaclImpact: null, message: null };
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (init?.method === "POST" && path.endsWith("/proposal/preview")) return json({ ...stagingResponse([ontologyEntry, shapesEntry]), proposal });
      if (path.endsWith("/staged")) return json({ ...stagingResponse([ontologyEntry, shapesEntry]), proposal });
      if (path.endsWith("/summary")) return json({});
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });

    render(<QueryClientProvider client={client}><StagingPanel projectId="simple" /></QueryClientProvider>);

    expect(await screen.findByRole("button", { name: "View Details" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "View Details" }));
    expect((await screen.findAllByText("Remove Customer associations"))).toHaveLength(2);
    expect(screen.getAllByText("Customer — owns account — Account 101")).toHaveLength(2);
    expect(screen.getAllByRole("button", { name: "Remove" })).toHaveLength(2);
  });

  it("shows server-owned reasoning provenance for each supported materialization type", async () => {
    const entries = [
      materializedEntry("stage-subclass", "SubclassRelationship", "simple", "LocalOnly"),
      materializedEntry("stage-type", "IndividualType", "simple", "Imported"),
      materializedEntry("stage-assertion", "ObjectPropertyAssertion", "secondary", "Unknown"),
    ];
    const proposal = {
      id: "proposal-1", status: "READYFORREVIEW", stagedChangeIds: entries.map((entry) => entry.id),
      baselineProjectFingerprint: "baseline", validationMessages: [], validationIssues: [],
      diff: [{ kind: "Added", subject: "https://example.com/Subject", predicate: "https://example.com/predicate", objectValue: "https://example.com/Object", description: "Added assertion." }],
      targetSourceIds: ["simple", "secondary"], shaclImpact: null, message: null,
    };
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (init?.method === "POST" && path.endsWith("/proposal/preview")) return json({ ...stagingResponse(entries), proposal });
      if (path.endsWith("/staged")) return json({ ...stagingResponse(entries), proposal });
      if (path.endsWith("/summary")) return json({});
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    render(<QueryClientProvider client={client}><StagingPanel projectId="simple" /></QueryClientProvider>);

    fireEvent.click(await screen.findByRole("button", { name: "View Details" }));
    expect(screen.getAllByText("Materialized from reasoning")).toHaveLength(3);
    expect(screen.getByText("Subclass Relationship")).toBeInTheDocument();
    expect(screen.getByText("Individual Type")).toBeInTheDocument();
    expect(screen.getByText("Object Property Assertion")).toBeInTheDocument();
    expect(screen.getAllByText("job-reasoning-1")).toHaveLength(3);
    expect(screen.getAllByText("Yes")).toHaveLength(4);
    expect(screen.getAllByText("No")).toHaveLength(1);
    expect(screen.getByText("Unknown")).toBeInTheDocument();
    expect(screen.queryByText("private-import-source")).not.toBeInTheDocument();
    expect(screen.getByText("Added · Subject · predicate · Object")).toBeInTheDocument();
  });

  it("shows document recommendation provenance on the staged typed edit", async () => {
    const entry: WebStagedEntry = {
      ...stagedEntry(),
      documentDraftProvenance: {
        taskId: "task-1",
        recommendationId: "recommendation-1",
        decisionId: "decision-1",
        evidenceIds: ["evidence-1", "evidence-2"],
        modelId: "gpt-test",
        promptVersion: "prompt-v1",
        extractionMethods: ["Text"],
        confidence: 92,
        targetSourceId: "simple",
        normalizedTypedOperationKey: "create-class-customer",
      },
    };
    const proposal = {
      id: "proposal-1", status: "READYFORREVIEW", stagedChangeIds: [entry.id],
      baselineProjectFingerprint: "baseline", validationMessages: [], validationIssues: [],
      diff: [], targetSourceIds: ["simple"], shaclImpact: null, message: null,
    };
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (init?.method === "POST" && path.endsWith("/proposal/preview")) {
        return json({ ...stagingResponse([entry]), proposal });
      }
      if (path.endsWith("/staged")) return json({ ...stagingResponse([entry]), proposal });
      if (path.endsWith("/summary")) return json({});
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    }));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    render(<QueryClientProvider client={client}><StagingPanel projectId="simple" /></QueryClientProvider>);

    fireEvent.click(await screen.findByRole("button", { name: "View Details" }));
    const provenance = screen.getByLabelText("Document recommendation provenance");
    expect(within(provenance).getByText("Accepted document recommendation")).toBeInTheDocument();
    expect(within(provenance).getByText("task-1")).toBeInTheDocument();
    expect(within(provenance).getByText("recommendation-1")).toBeInTheDocument();
    expect(within(provenance).getByText("2 verified references")).toBeInTheDocument();
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
    normalizedValues: { resourceIri: "https://example.com/Customer", label: "Client" },
    generatedIris: [],
    validationMessages: [],
  };
}

function materializedEntry(
  id: string,
  inferenceKind: "SubclassRelationship" | "IndividualType" | "ObjectPropertyAssertion",
  sourceId: string,
  importDependence: "LocalOnly" | "Imported" | "Unknown",
): WebStagedEntry {
  return {
    ...stagedEntry(),
    id,
    sourceId,
    summary: `materialize · ${inferenceKind}`,
    normalizedValues: {
      subjectIri: "https://example.com/Subject",
      subjectLabel: "Subject",
      predicateIri: "https://example.com/predicate",
      predicateLabel: "predicate",
      objectIri: "https://example.com/Object",
      objectLabel: "Object",
    },
    materializationProvenance: {
      origin: "MaterializedFromReasoning",
      inferenceKind,
      reasoningJobId: "job-reasoning-1",
      graphFingerprint: "fingerprint",
      factId: `fact-${id}`,
      stagedByUserId: "alice",
      stagedAt: "2026-07-23T15:00:00Z",
      targetSourceId: sourceId,
      entailedBeforeAssertion: true,
      importDependence,
      importSourceIds: ["private-import-source"],
    },
  };
}

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
