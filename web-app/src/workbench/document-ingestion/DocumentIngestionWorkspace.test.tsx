import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import DocumentIngestionWorkspace from "./DocumentIngestionWorkspace";

describe("document ingestion review workspace", () => {
  beforeEach(() => vi.restoreAllMocks());

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
    expect(screen.getByText("High confidence · 92%")).toBeInTheDocument();
    expect(screen.getByText(/OCR 87%/)).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent("Choose the applicable source");
    expect(screen.getByLabelText("Read-only draft impact")).toHaveTextContent("Read only");

    fireEvent.click(screen.getByRole("button", { name: "Open Explicit evidence" }));
    const dialog = await screen.findByRole("dialog", { name: "Evidence" });
    expect(await within(dialog).findByText("OCR confidence 87%", { exact: false })).toBeInTheDocument();
    expect(within(dialog).getByText("Customer", { selector: "mark" })).toBeInTheDocument();
    expect(within(dialog).getByRole("heading", { name: "Evidence" })).toHaveFocus();
    fireEvent.click(within(dialog).getByRole("button", { name: "Close evidence viewer" }));

    fireEvent.change(screen.getByLabelText("Clarification"), { target: { value: "Use the authoritative definition." } });
    fireEvent.click(screen.getByRole("button", { name: "Accept" }));
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

  it("supports keyboard-reachable task, match, edit, reconsider, cancel, and delete controls", async () => {
    const requests: Array<{ path: string; method: string }> = [];
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      requests.push({ path, method: init?.method ?? "GET" });
      if (path.endsWith("/cancel")) return json(tasks.items[0]);
      if (path.includes("/decision")) return json(workspace("Pending"));
      if (path.includes("/review")) return json(workspace("Pending"));
      if (init?.method === "DELETE") return new Response(null, { status: 204 });
      if (path.includes("/document-ingestion/tasks")) return json(tasks);
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderWorkspace();
    expect(await screen.findByRole("heading", { name: "Business facts" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Ontology match"), { target: { value: "https://example.com/Customer" } });
    fireEvent.change(screen.getByLabelText("Supported label edit"), { target: { value: "Customer record" } });
    fireEvent.change(screen.getByLabelText("Target ontology source"), { target: { value: "ontology" } });
    fireEvent.change(screen.getByLabelText("Clarification"), { target: { value: "Confirmed by policy owner." } });
    expect(screen.getByRole("button", { name: "Accept" })).not.toHaveAttribute("tabindex", "-1");
    fireEvent.click(screen.getByRole("button", { name: "Save edits" }));
    fireEvent.click(screen.getByRole("button", { name: "Reconsider" }));
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    fireEvent.click(screen.getByRole("button", { name: "Delete" }));

    await waitFor(() => expect(requests.some((request) => request.path.endsWith("/cancel"))).toBe(true));
    expect(requests.some((request) => request.method === "DELETE")).toBe(true);
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
  }],
  offset: 0,
  limit: 50,
  total: 1,
  nextOffset: null,
};

function workspace(status: string) {
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
      }],
      offset: 0,
      limit: 100,
      total: 1,
      nextOffset: null,
    },
    draftImpact: { acceptedCount: status === "Accepted" ? 1 : 0, pendingCount: status === "Pending" ? 1 : 0, blockedCount: 1, maximumAcceptedEdits: 100, readOnly: true },
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
