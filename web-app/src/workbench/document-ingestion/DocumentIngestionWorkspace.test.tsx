import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { WebDocumentReviewRecommendation, WebDocumentReviewWorkspace } from "../../web/projectApi";
import DocumentIngestionWorkspace from "./DocumentIngestionWorkspace";

describe("document ingestion review workspace", () => {
  beforeEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("renders untrusted content as text and exposes evidence and review labels accessibly", async () => {
    const decisions: unknown[] = [];
    const drafts: unknown[] = [];
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.includes("/evidence/evidence-1")) return json(evidence);
      if (path.endsWith("/draft")) {
        drafts.push(JSON.parse(String(init?.body)));
        return json({
          apiVersion: "v1",
          staging: { apiVersion: "v1", projectId: "simple", status: "READY", entries: [], proposal: null },
          batchCount: 1,
          stagedEditCount: 1,
          confirmCount: 0,
        });
      }
      if (path.includes("/decision")) {
        decisions.push(JSON.parse(String(init?.body)));
        return json(workspace("Accepted"));
      }
      if (path.includes("/review")) return json(workspace("Pending"));
      if (path.includes("/document-ingestion/tasks")) return json(tasks);
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderWorkspace();

    expect(await screen.findByRole("heading", { name: "Ontology structure" })).toBeInTheDocument();
    expect(screen.getByText("<script>alert('unsafe')</script> https://unsafe.example")).toBeInTheDocument();
    expect(document.querySelector("script")).toBeNull();
    expect(screen.queryByRole("link", { name: /unsafe/ })).not.toBeInTheDocument();
    expect(screen.getByText("92% confidence")).toBeInTheDocument();
    const recommendationSummary = screen.getByLabelText("Customer recommendation details");
    expect(recommendationSummary.closest("details")).not.toHaveAttribute("open");
    fireEvent.click(recommendationSummary);
    expect(recommendationSummary.closest("details")).toHaveAttribute("open");
    expect(screen.getByRole("region", { name: "Exact proposed changes" })).toHaveTextContent("Create class");
    expect(screen.getByRole("region", { name: "Exact proposed changes" })).toHaveTextContent("https://example.com/Customer");
    expect(screen.getAllByText("policy.txt")).toHaveLength(2);
    expect(screen.getByText(/OCR 87%/)).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent("Choose the applicable source");
    expect(screen.getByLabelText("Read-only draft impact")).toHaveTextContent("Read only");

    fireEvent.click(screen.getByRole("button", { name: "Status Updates" }));
    const statusDialog = screen.getByRole("dialog", { name: "Ingestion Status Updates" });
    const statusHistory = within(statusDialog).getByRole("region", { name: "Ingestion status history" });
    expect(statusHistory).toHaveClass("document-status-scroll-region");
    expect(statusHistory).toHaveAttribute("tabindex", "0");
    expect(within(statusDialog).getByText("Documents uploaded and awaiting extraction.")).toBeInTheDocument();
    expect(within(statusDialog).getByText("Ready for review.")).toBeInTheDocument();
    expect(within(statusDialog).getByText("Awaiting Review · 100% · 1 of 1 documents")).toBeInTheDocument();
    fireEvent.click(within(statusDialog).getByText("Details"));
    expect(within(statusDialog).getByText("Stage: connected semantic synthesis.")).toBeInTheDocument();
    expect(
      within(statusDialog).getByText("Culprit: connected synthesis item 'item-2' with label 'Effective Date'."),
    ).toBeInTheDocument();
    expect(within(statusDialog).getByText("Unknown discovery IDs: discovery-missing.")).toBeInTheDocument();
    fireEvent.click(within(statusDialog).getByRole("button", { name: "Close ingestion status updates" }));

    fireEvent.click(screen.getByRole("button", { name: "Open Explicit evidence" }));
    const dialog = await screen.findByRole("dialog", { name: "Evidence" });
    expect(await within(dialog).findByText("OCR confidence 87%", { exact: false })).toBeInTheDocument();
    expect(within(dialog).getByText("Customer", { selector: "mark" })).toBeInTheDocument();
    expect(within(dialog).getByRole("heading", { name: "Evidence" })).toHaveFocus();
    fireEvent.click(within(dialog).getByRole("button", { name: "Close evidence viewer" }));

    fireEvent.click(screen.getByText("Review options and technical details"));
    fireEvent.change(screen.getByLabelText("Reviewer note"), { target: { value: "Use the authoritative definition." } });
    fireEvent.click(screen.getByRole("button", { name: "Approve for proposal" }));
    await waitFor(() => expect(decisions).toHaveLength(1));
    expect(decisions[0]).toMatchObject({
      action: "accept",
      clarification: "Use the authoritative definition.",
      expectedWorkKey: "work-key",
      expectedGraphFingerprint: "graph-fingerprint",
    });
    fireEvent.click(await screen.findByRole("button", { name: "Add accepted items to proposal" }));
    await waitFor(() => expect(drafts).toEqual([{
      expectedWorkKey: "work-key",
      expectedGraphFingerprint: "graph-fingerprint",
    }]));
    expect(await screen.findByRole("status")).toHaveTextContent("1 typed edit added to the shared proposal.");
  });

  it("warns that visible status is stale when live task polling fails", async () => {
    let taskRequests = 0;
    const activeTasks = {
      ...tasks,
      items: [{
        ...tasks.items[0],
        status: "analyzing",
        progress: {
          stage: "analyzing",
          completedDocuments: 1,
          totalDocuments: 1,
          percent: 40,
          message: "Discovering evidence-grounded meaning in each document.",
        },
      }],
    };
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.includes("/document-ingestion/tasks")) {
        taskRequests += 1;
        if (taskRequests === 1) return json(activeTasks);
        return new Response("", { status: 200, headers: { "Content-Type": "application/json" } });
      }
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderWorkspace();

    expect(await screen.findByText("Discovering evidence-grounded meaning in each document.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Status Updates" }));
    const dialog = screen.getByRole("dialog", { name: "Ingestion Status Updates" });
    expect(await within(dialog).findByRole("alert", {}, { timeout: 2_000 })).toHaveTextContent(
      "Live status updates could not be refreshed. The updates below may be out of date.",
    );
    expect(within(dialog).getByRole("button", { name: "Retry status updates" })).toBeInTheDocument();
    expect(taskRequests).toBeGreaterThanOrEqual(2);
  });

  it("supports keyboard-reachable task, match, edit, reconsider, and delete controls", async () => {
    const requests: Array<{ path: string; method: string }> = [];
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      requests.push({ path, method: init?.method ?? "GET" });
      if (path.includes("/decision")) return json(workspace("Pending"));
      if (path.includes("/review")) return json(workspace("Pending"));
      if (init?.method === "DELETE") return new Response(null, { status: 204 });
      if (path.includes("/document-ingestion/tasks")) return json(tasks);
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderWorkspace();
    expect(await screen.findByRole("heading", { name: "Business facts" })).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Customer recommendation details"));
    fireEvent.click(screen.getByText("Review options and technical details"));
    fireEvent.change(screen.getByLabelText("Matched ontology item"), { target: { value: "https://example.com/Customer" } });
    fireEvent.change(screen.getByLabelText("Proposed label"), { target: { value: "Customer record" } });
    fireEvent.change(screen.getByLabelText("Ontology source"), { target: { value: "ontology" } });
    fireEvent.change(screen.getByLabelText("Reviewer note"), { target: { value: "Confirmed by policy owner." } });
    expect(screen.getByRole("button", { name: "Approve for proposal" })).not.toHaveAttribute("tabindex", "-1");
    fireEvent.click(screen.getByRole("button", { name: "Save review edits" }));
    fireEvent.click(screen.getByRole("button", { name: "Ask Entio to reconsider" }));
    fireEvent.click(screen.getByRole("button", { name: "Delete" }));

    await waitFor(() => expect(requests.some((request) => request.method === "DELETE")).toBe(true));
  });

  it("lets the user stop active model generation", async () => {
    const requests: Array<{ path: string; method: string }> = [];
    const activeTasks = {
      ...tasks,
      items: [{
        ...tasks.items[0],
        status: "analyzing",
        progress: {
          stage: "analyzing",
          completedDocuments: 1,
          totalDocuments: 1,
          percent: 84,
          message: "Preparing grouped recommendations and exact change sets.",
        },
      }],
    };
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      requests.push({ path, method: init?.method ?? "GET" });
      if (path.endsWith("/cancel")) return json({
        ...activeTasks.items[0],
        status: "cancelled",
        progress: {
          ...activeTasks.items[0].progress,
          stage: "cancelled",
          message: "Generation stopped by user.",
        },
      });
      if (path.includes("/document-ingestion/tasks")) return json(activeTasks);
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderWorkspace();

    const stop = await screen.findByRole("button", { name: "Stop generation" });
    expect(stop).not.toHaveAttribute("tabindex", "-1");
    fireEvent.click(stop);
    await waitFor(() => expect(requests.some((request) => request.path.endsWith("/cancel"))).toBe(true));
  });

  it("does not offer approval when a safety failure blocks an exact change", async () => {
    const blocked = workspace("Pending");
    blocked.recommendations.items[0] = {
      ...blocked.recommendations.items[0],
      type: "SafetyFailure",
      connectedStatus: "Blocked",
      proposedLabel: "Account closure definition",
      description: "Entio found possible meaning, but it cannot safely map that meaning to a supported change.",
      changePreview: {
        draftable: false,
        summary: "No ontology change can be created from this recommendation.",
        operations: [],
        blockingReason: "The ontology changed after analysis and the recommendation must be regenerated.",
      },
    };
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.includes("/review")) return json(blocked);
      if (path.includes("/document-ingestion/tasks")) return json(tasks);
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderWorkspace();

    fireEvent.click(await screen.findByLabelText("Account closure definition recommendation details"));
    const preview = await screen.findByRole("region", { name: "Exact proposed changes" });
    expect(preview).toHaveTextContent("No ontology change can be created");
    expect(preview).toHaveTextContent("The ontology changed after analysis");
    expect(screen.queryByRole("button", { name: "Approve for proposal" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reject" })).toBeInTheDocument();
  });

  it("resolves grounded needs-input items with an explicit server-verified ontology decision", async () => {
    const needsInput = workspace("Pending");
    needsInput.draftImpact = {
      acceptedCount: 0,
      pendingCount: 1,
      blockedCount: 0,
      executableCount: 0,
      needsInputCount: 1,
      reviewOnlyCount: 0,
      readOnly: true,
    };
    needsInput.recommendations.items[0] = {
      ...needsInput.recommendations.items[0],
      connectedStatus: "NeedsInput",
      changePreview: {
        draftable: false,
        summary: "This evidence-backed meaning needs reviewer input.",
        operations: [],
        blockingReason: "Complete the grounded ontology decision, then Entio will reverify and recompile it.",
      },
      groundedItems: [{
        itemId: "grounded-item-policy",
        kind: "Class",
        label: "Servicing Policy",
        definition: "A policy governing servicing.",
        disposition: "Unresolved",
        selectedSelectionId: null,
        alternatives: [],
        resolutionAlternatives: [],
        prerequisiteOrigins: [],
        editableFields: [
          { id: "grounded-item-policy:disposition", kind: "Disposition", required: true, compatibleSelectionIds: [], message: "Choose reuse or new." },
          { id: "grounded-item-policy:entitykind", kind: "EntityKind", required: true, compatibleSelectionIds: [], message: "Confirm the kind." },
          { id: "grounded-item-policy:label", kind: "Label", required: true, compatibleSelectionIds: [], message: "Confirm the label." },
        ],
        status: "NeedsInput",
      }],
    };
    const decisions: unknown[] = [];
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.includes("/decision")) {
        decisions.push(JSON.parse(String(init?.body)));
        return json(needsInput);
      }
      if (path.includes("/review")) return json(needsInput);
      if (path.includes("/document-ingestion/tasks")) return json(tasks);
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderWorkspace();

    expect(await screen.findByLabelText("Read-only draft impact")).toHaveTextContent("1 need input");
    expect(screen.getByLabelText("Read-only draft impact")).toHaveTextContent("Pending does not mean blocked");
    fireEvent.click(screen.getByLabelText("Customer recommendation details"));
    const resolution = screen.getByLabelText("Resolve grounded ontology item");
    fireEvent.change(within(resolution).getByLabelText("Resolution"), { target: { value: "ProposeNew" } });
    fireEvent.change(within(resolution).getByLabelText("Label"), { target: { value: "Loan Servicing Policy" } });
    fireEvent.click(within(resolution).getByRole("button", { name: "Verify and compile resolution" }));

    await waitFor(() => expect(decisions[0]).toMatchObject({
      action: "resolve-grounded",
      groundedItemEdit: {
        itemId: "grounded-item-policy",
        disposition: "ProposeNew",
        kind: "Class",
        label: "Loan Servicing Policy",
      },
    }));
  });

  it("explains connected changes, confidence, critique, coverage notes, and individual gates", async () => {
    const connected = workspace("Pending");
    connected.recommendations.items[0] = {
      ...connected.recommendations.items[0],
      connectedStatus: "Mixed",
      type: "CreateClass",
      action: "ConnectedChange",
      confidenceDimensions: { evidence: 94, modeling: 82, ontologyFit: 76, compilation: 100, overall: 76 },
      semanticIntent: "Create the Account closure concept and preserve its connected rule.",
      generatedIris: ["https://example.com/AccountClosure"],
      mandatoryClarificationReasons: ["reviewer-input-required"],
      changePreview: {
        draftable: true,
        summary: "2 ordered typed changes will be added as one atomic recommendation.",
        operations: [
          {
            operation: "Create class",
            description: "Create AccountClosure.",
            targetSourceId: "ontology",
            operationId: "create-account-closure",
            dependsOnOperationIds: [],
            optionalLeaf: false,
            editableLabel: "Account closure",
            semanticRole: "Domain class",
            reviewerInputRequired: true,
          },
          {
            operation: "Add definition",
            description: "Add the verified definition.",
            targetSourceId: "ontology",
            operationId: "define-account-closure",
            dependsOnOperationIds: ["create-account-closure"],
            optionalLeaf: true,
          },
          {
            operation: "Set property range",
            description: "Use the recommended text datatype.",
            targetSourceId: "ontology",
            operationId: "set-reference-range",
            dependsOnOperationIds: ["create-account-closure"],
            optionalLeaf: false,
            editableIri: "http://www.w3.org/2001/XMLSchema#string",
            semanticRole: "Range assignment",
            modelRecommended: true,
          },
        ],
        blockingReason: null,
      },
      reviewOnlyFindings: [{
        id: "finding-separation-of-duties",
        summary: "Separation of duties rule",
        reason: "The current typed SHACL surface cannot represent this complex rule safely.",
        relatedOperationIds: ["create-account-closure"],
      }],
      criticDispositions: [{
        findingId: "critic-1",
        disposition: "AcceptedAndIncorporated",
        rationale: "The property domain was corrected.",
      }],
      individualReviewGates: [{
        operationId: "create-jordan-lee",
        classification: "Illustrative",
        creationConfirmed: false,
      }],
    };
    const decisions: unknown[] = [];
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.includes("/decision")) {
        decisions.push(JSON.parse(String(init?.body)));
        return json(connected);
      }
      if (path.includes("/review")) return json(connected);
      if (path.includes("/document-ingestion/tasks")) return json(tasks);
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderWorkspace();

    expect(await screen.findByText("Mixed")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Customer recommendation details"));
    expect(screen.getByLabelText("Confidence details")).toHaveTextContent("Evidence94%");
    expect(screen.getByLabelText("Confidence details")).toHaveTextContent("Compilation100%");
    expect(screen.getByLabelText("Semantic coverage and compilation metrics")).toHaveTextContent(
      "Semantic coverage: 100%",
    );
    expect(screen.getByLabelText("Document analysis funnel")).toHaveTextContent(
      "30 evidence mentions → 12 grouped candidates → 5 ontology-bearing candidates",
    );
    expect(screen.getByLabelText("Document analysis funnel")).toHaveTextContent(
      "4 document-only mentions and 3 supporting values remain in coverage without creating review cards.",
    );
    expect(screen.getByRole("region", { name: "Exact proposed changes" })).toHaveTextContent(
      "Depends on: create-account-closure",
    );
    expect(screen.getByRole("region", { name: "Exact proposed changes" })).toHaveTextContent(
      "https://example.com/AccountClosure",
    );
    expect(screen.getByRole("region", { name: "Exact proposed changes" })).toHaveTextContent(
      "Model-recommended prerequisite",
    );
    expect(screen.getByRole("region", { name: "Exact proposed changes" })).toHaveTextContent(
      "Reviewer input required",
    );
    expect(screen.getByRole("note")).toHaveTextContent("Reviewer input required.");
    expect(screen.getByLabelText("Coverage notes")).toHaveTextContent("Separation of duties rule");
    expect(screen.getByText("Modeling critique")).toBeInTheDocument();
    expect(screen.getByLabelText("Individual confirmations")).toHaveTextContent("Illustrative");

    fireEvent.click(screen.getByRole("button", { name: "Confirm individual" }));
    await waitFor(() => expect(decisions[0]).toMatchObject({
      action: "confirm-individual",
      operationId: "create-jordan-lee",
    }));
    fireEvent.click(screen.getByRole("button", { name: "Exclude optional change Add definition" }));
    await waitFor(() => expect(decisions[1]).toMatchObject({
      action: "exclude-optional",
      operationIds: ["define-account-closure"],
    }));
    fireEvent.click(screen.getByText("Review options and technical details"));
    fireEvent.change(screen.getByLabelText("Domain class (review needed)"), {
      target: { value: "Account termination" },
    });
    fireEvent.change(screen.getByLabelText("Range assignment (model recommended)"), {
      target: { value: "http://www.w3.org/2001/XMLSchema#token" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save ontology fields" }));
    await waitFor(() => expect(decisions[2]).toMatchObject({
      action: "edit-operations",
      operationEdits: [{
        operationId: "create-account-closure",
        label: "Account termination",
      }, {
        operationId: "set-reference-range",
        entityIri: "http://www.w3.org/2001/XMLSchema#token",
      }],
    }));
  });

  it("confirms an exact ontology reuse without presenting it as an edit", async () => {
    const matched = workspace("Pending");
    matched.recommendations.items[0] = {
      ...matched.recommendations.items[0],
      type: "ExistingOntologyReuse",
      connectedStatus: "Matched",
      changePreview: {
        draftable: false,
        summary: "No ontology edit is needed. Confirm that this evidence maps to the selected existing entity.",
        operations: [],
        blockingReason: null,
      },
      confidenceDimensions: {
        evidence: 90,
        modeling: 85,
        ontologyFit: 80,
        compilation: null,
        overall: 80,
      },
      reviewOnlyFindings: [{
        id: "finding-rule",
        summary: "Customer",
        reason: "The evidence exactly matches the existing Customer class.",
        relatedOperationIds: [],
      }],
    };
    matched.draftImpact = {
      ...matched.draftImpact,
      executableCount: 0,
      matchedCount: 1,
    };
    const decisions: unknown[] = [];
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.includes("/decision")) {
        decisions.push(JSON.parse(String(init?.body)));
        return json(matched);
      }
      if (path.includes("/review")) return json(matched);
      if (path.includes("/document-ingestion/tasks")) return json(tasks);
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderWorkspace();

    fireEvent.click(await screen.findByLabelText("Customer recommendation details"));
    expect(await screen.findByLabelText("Confidence details")).toHaveTextContent("CompilationNot applicable");
    expect(screen.getByRole("region", { name: "Exact proposed changes" })).not.toHaveClass("blocked");
    fireEvent.click(screen.getByRole("button", { name: "Confirm ontology reuse" }));
    await waitFor(() => expect(decisions).toHaveLength(1));
    expect(decisions[0]).toMatchObject({
      action: "retain",
      expectedWorkKey: "work-key",
      expectedGraphFingerprint: "graph-fingerprint",
    });
    expect(decisions[0]).not.toHaveProperty("operations");
  });

  it("keeps unsupported meaning in the document-only coverage ledger without a review card", async () => {
    const documentOnly = workspace("Pending");
    documentOnly.recommendations = { items: [], offset: 0, limit: 100, total: 0, nextOffset: null };
    documentOnly.documentOnlyFindings = [{
      id: "finding-separation-rule",
      label: "Approval separation rule",
      reasons: ["The rule is meaningful but is not a supported typed edit."],
      evidence: documentOnly.recommendations.items[0]?.evidence ?? [{
        evidenceId: "evidence-1", evidenceType: "Explicit", documentId: "document-1", pageNumber: 1,
        extractionMethod: "Ocr", ocrConfidence: 87, excerpt: "Customer", priorRecordId: null,
      }],
    }];
    documentOnly.draftImpact = {
      ...documentOnly.draftImpact,
      pendingCount: 0,
      executableCount: 0,
      matchedCount: 0,
      documentOnlyCount: 1,
    };
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.includes("/review")) return json(documentOnly);
      if (path.includes("/document-ingestion/tasks")) return json(tasks);
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderWorkspace();

    expect(await screen.findByText("Coverage ledger · 1 document-only meaning")).toBeInTheDocument();
    expect(screen.queryByLabelText("Approval separation rule recommendation details")).not.toBeInTheDocument();
    fireEvent.click(screen.getByText("Coverage ledger · 1 document-only meaning"));
    expect(screen.getByText("The rule is meaningful but is not a supported typed edit.")).toBeInTheDocument();
  });

  it("waits for the task to become reviewable before requesting its review workspace", async () => {
    let taskReady = false;
    let reviewRequests = 0;
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.includes("/review")) {
        if (path.includes("/task-processing/review")) reviewRequests += 1;
        return json(workspace("Pending"));
      }
      if (path.includes("/document-ingestion/tasks")) {
        if (!taskReady) {
          return json({
            ...tasks,
            items: [{
              ...tasks.items[0],
              taskId: "task-processing",
              status: "analyzing",
              progress: {
                stage: "analyzing",
                completedDocuments: 1,
                totalDocuments: 1,
                percent: 60,
                message: "Analyzing verified document text.",
              },
            }],
          });
        }
        return json({
          ...tasks,
          items: [{ ...tasks.items[0], taskId: "task-processing" }],
        });
      }
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderWorkspace();

    expect(await screen.findByText("Review results will appear when document analysis is complete.")).toBeInTheDocument();
    expect(reviewRequests).toBe(0);
    taskReady = true;
    await waitFor(() => expect(reviewRequests).toBe(1), { timeout: 2_000 });
  });
});

function renderWorkspace() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><DocumentIngestionWorkspace projectId="simple" /></QueryClientProvider>);
}

const tasks = {
  items: [{
    taskId: "task-1",
    projectId: "simple",
    ownerUserId: "alice",
    status: "awaiting-review",
    createdAt: "2026-07-24T12:00:00Z",
    updatedAt: "2026-07-24T12:01:00Z",
    documents: [{
      documentId: "document-1",
      safeFilename: "policy <b>unsafe</b>.txt",
      mediaType: "text",
      byteSize: 100,
      checksumSha256: "a".repeat(64),
      authorityStatus: "authoritative",
      status: "awaiting-review",
    }],
    progress: { stage: "awaiting-review", completedDocuments: 1, totalDocuments: 1, percent: 100, message: "Ready for review." },
    updates: [
      { order: 1, stage: "uploaded", completedDocuments: 0, totalDocuments: 1, percent: 0, message: "Documents uploaded and awaiting extraction.", timestamp: "2026-07-24T12:00:00Z" },
      {
        order: 2,
        stage: "awaiting-review",
        completedDocuments: 1,
        totalDocuments: 1,
        percent: 100,
        message: "Ready for review.",
        timestamp: "2026-07-24T12:01:00Z",
        details: [
          "Stage: connected semantic synthesis.",
          "Culprit: connected synthesis item 'item-2' with label 'Effective Date'.",
          "Unknown discovery IDs: discovery-missing.",
        ],
      },
    ],
  }],
  offset: 0,
  limit: 50,
  total: 1,
  nextOffset: null,
};

function workspace(status: WebDocumentReviewRecommendation["reviewStatus"]): WebDocumentReviewWorkspace {
  return {
    apiVersion: "v1",
    taskId: "task-1",
    projectId: "simple",
    exactWorkKey: "work-key",
    graphFingerprint: "graph-fingerprint",
    documents: [{ documentId: "document-1", safeFilename: "policy.txt", mediaType: "text", authorityStatus: "authoritative", pageCount: 1, warningCount: 0 }],
    summaries: [{ documentId: "document-1", purpose: "<script>alert('unsafe')</script> https://unsafe.example", highlights: ["Customer policy"] }],
    recommendations: {
      items: [{
        id: "recommendation-1",
        category: "OntologyStructure",
        type: "Class",
        action: "Extend",
        proposedLabel: "Customer",
        description: "The document adds information about “Customer” to a selected ontology item.",
        changePreview: {
          draftable: true,
          summary: "1 exact change will be added to the proposal.",
          operations: [{
            operation: "Create class",
            description: "Create https://example.com/Customer with label “Customer”.",
            targetSourceId: "ontology",
          }],
          blockingReason: null,
        },
        confidence: 92,
        confidenceBand: "High",
        rationale: "The document explicitly defines the concept.",
        reviewStatus: status,
        evidence: [{ evidenceId: "evidence-1", evidenceType: "Explicit", documentId: "document-1", pageNumber: 1, extractionMethod: "Ocr", ocrConfidence: 87, excerpt: "Customer", priorRecordId: null }],
        matches: [{ scope: "AppliedLocal", entityIri: "https://example.com/Customer", sourceId: "ontology", preferredLabel: "Customer", score: 100, reason: "Exact label and type." }],
        selectedMatchIri: "https://example.com/Customer",
        conflicts: [{ id: "conflict-1", alternatives: ["Current meaning", "Proposed meaning"], affectedEntityIris: ["https://example.com/Customer"], resolutionOptions: ["retain", "revise"] }],
        mandatoryClarificationReasons: ["Choose the applicable source."],
        clarification: null,
        targetSourceId: "ontology",
        reconsiderationCount: 0,
        priorWorkflowProvenance: ["applied-record-1"],
        modelId: "gpt-4o",
        promptVersion: "document-analysis-v2",
      }],
      offset: 0,
      limit: 100,
      total: 1,
      nextOffset: null,
    },
    documentOnlyFindings: [],
    draftImpact: {
      acceptedCount: status === "Accepted" ? 1 : 0,
      pendingCount: status === "Pending" ? 1 : 0,
      blockedCount: 0,
      executableCount: 1,
      needsInputCount: 0,
      reviewOnlyCount: 0,
      matchedCount: 0,
      documentOnlyCount: 0,
      readOnly: true,
    },
    analysisCounts: {
      evidenceBlocks: 8,
      evidenceMentions: 30,
      groupedCandidates: 12,
      ontologyBearingCandidates: 5,
      documentOnlyMentions: 4,
      supportingValueMentions: 3,
      nlpCandidatesRetained: 5,
      nlpCandidatesRejected: 6,
      groundedItemsRetained: 4,
      groundedItemsUnresolved: 1,
      groundedItemsRejected: 0,
      recommendationsExecutable: 1,
      recommendationsMixed: 0,
      recommendationsNeedsInput: 0,
      recommendationsReviewOnly: 0,
      recommendationsBlocked: 0,
      expandedTypedEdits: 1,
    },
    semanticCoverage: { numerator: 1, denominator: 1, percentage: 100, failureCodes: [] },
    compilationSuccess: { numerator: 1, denominator: 1, percentage: 100, failureCodes: [] },
  };
}

const evidence = {
  apiVersion: "v1",
  evidenceId: "evidence-1",
  documentId: "document-1",
  safeFilename: "policy.pdf",
  pageNumber: 1,
  sectionHeading: "Definitions",
  extractionMethod: "Ocr",
  ocrConfidence: 87,
  text: "The Customer definition.",
  highlightStart: 4,
  highlightEnd: 12,
  pageImageAvailable: true,
  truncated: false,
};

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
