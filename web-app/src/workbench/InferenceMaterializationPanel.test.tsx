import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { WebInferenceMaterializationCandidate, WebReasoningFact } from "../web/projectApi";
import InferenceMaterializationPanel from "./InferenceMaterializationPanel";

const asserted: WebReasoningFact = {
  kind: "SubclassRelationship",
  subject: "https://example.com/Checking",
  predicate: "http://www.w3.org/2000/01/rdf-schema#subClassOf",
  objectValue: "https://example.com/Account",
  origin: "Asserted",
  sourceId: "simple",
};

describe("inference materialization panel", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("separates asserted facts and renders all supported inference types and stageability states", () => {
    const states: WebInferenceMaterializationCandidate["stageability"][] = [
      "Stageable", "AlreadyAsserted", "AlreadyStaged", "Stale", "UnsupportedType",
      "UnsupportedTerm", "MissingEntity", "InvalidPredicate", "NoWritableSource",
      "AmbiguousSource", "ImportDependencyUnsafe",
    ];
    const candidates = states.map((state, index) => candidate(`fact-${index}`, state, index % 3));
    renderPanel({ candidates });

    expect(screen.getByText("Asserted facts")).toBeInTheDocument();
    expect(screen.getByText("Inferred facts")).toBeInTheDocument();
    expect(screen.getAllByText("Subclass").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Individual type").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Object property").length).toBeGreaterThan(0);
    for (const state of states) expect(screen.getByText(formatState(state))).toBeInTheDocument();
    expect(screen.getByText("Existing item: staged-existing")).toBeInTheDocument();
    expect(screen.getByText("Depends on imported knowledge")).toBeInTheDocument();
    expect(screen.getAllByRole("checkbox", { name: /^Select /, hidden: true }).filter((box) => !box.hasAttribute("disabled"))).toHaveLength(2);
  });

  it("supports bounded selection, source choice, clear, and a fact-id-only request", async () => {
    let requestBody: Record<string, unknown> | undefined;
    const navigate = vi.fn();
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/materializations")) {
        requestBody = JSON.parse(String(init?.body));
        return json({
          apiVersion: "v1", projectId: "simple", reasoningJobId: "job-1", graphFingerprint: "fingerprint",
          mappings: [], staging: { apiVersion: "v1", projectId: "simple", status: "READY", entries: [], proposal: null },
        });
      }
      if (path.endsWith("/summary")) return json({});
      if (path.endsWith("/details")) return json({});
      throw new Error(`Unexpected request: ${path}`);
    }));
    renderPanel({
      candidates: [candidate("stageable", "Stageable", 0), candidate("ambiguous", "AmbiguousSource", 1)],
      onOpenChanges: navigate,
    });

    fireEvent.click(screen.getByRole("button", { name: "Select all visible" }));
    expect(screen.getByText("2 selected")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Stage as asserted" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Target source for Subject ambiguous"), { target: { value: "source-b" } });
    fireEvent.click(screen.getByRole("button", { name: "Stage as asserted" }));

    await waitFor(() => expect(navigate).toHaveBeenCalledOnce());
    expect(requestBody?.selections).toEqual([
      { factId: "stageable" },
      { factId: "ambiguous", targetSourceId: "source-b" },
    ]);
    expect(requestBody).toHaveProperty("idempotencyKey");
    expect(JSON.stringify(requestBody)).not.toContain("subjectLabel");
    expect(screen.getByText("0 selected")).toBeInTheDocument();
  });

  it("retains selection after a safe server error and can clear it", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ message: "The reasoning result is stale." }), {
      status: 409,
      headers: { "Content-Type": "application/json" },
    })));
    renderPanel({ candidates: [candidate("fact-1", "Stageable", 0)] });
    fireEvent.click(screen.getByLabelText("Select Subject fact-1 subclass of Object fact-1"));
    fireEvent.click(screen.getByRole("button", { name: "Stage as asserted" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("The reasoning result is stale.");
    expect(screen.getByText("1 selected")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Clear selection" }));
    expect(screen.getByText("0 selected")).toBeInTheDocument();
  });
});

function renderPanel({
  candidates,
  onOpenChanges = vi.fn(),
}: {
  candidates: WebInferenceMaterializationCandidate[];
  onOpenChanges?: () => void;
}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}>
    <InferenceMaterializationPanel
      projectId="simple"
      jobId="job-1"
      jobStatus="Completed"
      facts={[asserted]}
      candidates={candidates}
      truncated={false}
      onOpenChanges={onOpenChanges}
    />
  </QueryClientProvider>);
}

function candidate(
  factId: string,
  stageability: WebInferenceMaterializationCandidate["stageability"],
  kindIndex: number,
): WebInferenceMaterializationCandidate {
  const kinds = ["SubclassRelationship", "IndividualType", "ObjectPropertyAssertion"] as const;
  const ambiguous = stageability === "AmbiguousSource";
  return {
    factId,
    kind: kinds[kindIndex],
    subject: `https://example.com/${factId}`,
    subjectLabel: `Subject ${factId}`,
    predicate: "https://example.com/predicate",
    predicateLabel: kindIndex === 0 ? "subclass of" : kindIndex === 1 ? "type" : "owns account",
    objectValue: `https://example.com/object-${factId}`,
    objectLabel: `Object ${factId}`,
    origin: "Inferred",
    stageability,
    reason: `Reason ${stageability}`,
    sourceCandidates: ambiguous ? [{ sourceId: "source-a", selected: false }, { sourceId: "source-b", selected: false }] : [{ sourceId: "simple", selected: true }],
    selectedSourceId: ambiguous ? null : "simple",
    existingStagedChangeId: stageability === "AlreadyStaged" ? "staged-existing" : null,
    importDependence: stageability === "ImportDependencyUnsafe" ? "Imported" : "LocalOnly",
    importSourceIds: stageability === "ImportDependencyUnsafe" ? ["catalog"] : [],
  };
}

function formatState(value: string): string {
  return value.replace(/([a-z])([A-Z])/g, "$1 $2");
}

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
