import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { WebReasoningFact } from "../web/projectApi";
import ReasoningFactsPanel from "./InferenceMaterializationPanel";

describe("reasoning fact browsers", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("opens separate read-only asserted and inferred fact dialogs", async () => {
    const paths: string[] = [];
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      paths.push(path);
      const origin = new URL(path, "http://entio.test").searchParams.get("factOrigin") ?? "Asserted";
      return json(details([fact(origin as "Asserted" | "Inferred", `${origin}Subject`)], 1, null));
    }));
    renderPanel();

    fireEvent.click(screen.getByRole("button", { name: /Asserted facts/ }));
    expect(await screen.findByRole("dialog", { name: "Asserted facts" })).toBeInTheDocument();
    expect(await screen.findByText("AssertedSubject")).toBeInTheDocument();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Stage as asserted" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Done" }));

    fireEvent.click(screen.getByRole("button", { name: /Inferred facts/ }));
    expect(await screen.findByRole("dialog", { name: "Inferred facts" })).toBeInTheDocument();
    expect(await screen.findByText("InferredSubject")).toBeInTheDocument();
    expect(paths.some((path) => path.includes("factOrigin=Asserted"))).toBe(true);
    expect(paths.some((path) => path.includes("factOrigin=Inferred"))).toBe(true);
  });

  it("searches relationships and loads facts in pages of fifty", async () => {
    const paths: string[] = [];
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      paths.push(path);
      const params = new URL(path, "http://entio.test").searchParams;
      const offset = Number(params.get("factOffset") ?? 0);
      const query = params.get("factQuery");
      if (query) return json(details([fact("Inferred", "MatchingAccount")], 1, null, offset));
      const page = Array.from({ length: offset ? 1 : 50 }, (_, index) => fact("Inferred", `Subject${offset + index}`));
      return json(details(page, 51, offset ? null : 50, offset));
    }));
    renderPanel();

    fireEvent.click(screen.getByRole("button", { name: /Inferred facts/ }));
    expect(await screen.findByText("50 of 51")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Load more" }));
    expect(await screen.findByText("51 of 51")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Load more" })).not.toBeInTheDocument();

    fireEvent.change(screen.getByRole("textbox", { name: "Search relationships" }), { target: { value: "owns account" } });
    fireEvent.click(screen.getByRole("button", { name: "Search" }));
    expect(await screen.findByText("MatchingAccount")).toBeInTheDocument();
    await waitFor(() => expect(paths.some((path) => path.includes("factQuery=owns+account"))).toBe(true));
  });
});

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>
    <ReasoningFactsPanel projectId="simple" jobId="job-1" jobStatus="Completed" />
  </QueryClientProvider>);
}

function fact(origin: "Asserted" | "Inferred", subject: string): WebReasoningFact {
  return {
    kind: "property-relationship",
    subject: `https://example.com/${subject}`,
    predicate: "https://example.com/ownsAccount",
    objectValue: "https://example.com/Account101",
    origin,
    sourceId: "simple",
  };
}

function details(facts: WebReasoningFact[], total: number, next: number | null, offset = 0) {
  return {
    apiVersion: "v1",
    job: { id: "job-1", status: "Completed" },
    facts,
    factOffset: offset,
    factLimit: 50,
    totalFactCount: total,
    nextFactOffset: next,
    materializationCandidates: [],
    shaclFindings: [],
    warnings: [],
    errors: [],
    truncated: next != null,
  };
}

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
