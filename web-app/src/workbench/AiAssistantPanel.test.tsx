import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import AiAssistantPanel from "./AiAssistantPanel";

describe("AI assistant panel", () => {
  it("keeps suggestions separate until the user stages one", async () => {
    let stagedCalls = 0;
    let stagedBody = "";
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.includes("/ai/assistant")) {
        return json({
          apiVersion: "v1",
          operation: "SUGGEST_SUPERCLASS",
          answer: "A possible superclass is available for review.",
          evidence: [{ category: "ontology", label: "entity", value: "Customer" }],
          assertedFacts: ["type: Customer"],
          inferredFacts: [],
          fiboResults: [],
          suggestions: [{
            id: "suggest-superclass",
            suggestionType: "add-superclass",
            rationale: "Review this typed edit before staging it.",
            edit: { sourceId: "simple", editType: "add-superclass", classIri: "https://example.com/Customer", superclassIri: "https://example.com/Party", aiGenerated: true },
          }],
          uncertainty: ["Deterministic development response."],
          warnings: [],
        });
      }
      if (path.includes("/staged") && init?.method === "POST") {
        stagedCalls += 1;
        stagedBody = String(init.body);
        return json({ apiVersion: "v1", projectId: "simple", status: "READY", entries: [], proposal: null });
      }
      throw new Error(`Unexpected request: ${path}`);
    }));

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={client}><AiAssistantPanel projectId="simple" entity={{ iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple" }} /></QueryClientProvider>);

    fireEvent.change(screen.getByLabelText("Operation"), { target: { value: "SUGGEST_SUPERCLASS" } });
    fireEvent.change(screen.getByLabelText("Request or IRI"), { target: { value: "https://example.com/Party" } });
    fireEvent.click(screen.getByRole("button", { name: "Ask assistant" }));

    expect(await screen.findByText("Review this typed edit before staging it.")).toBeInTheDocument();
    expect(stagedCalls).toBe(0);
    fireEvent.click(screen.getByRole("button", { name: "Stage suggestion" }));
    expect(await screen.findByRole("button", { name: "Staged for review" })).toBeInTheDocument();
    expect(stagedCalls).toBe(1);
    expect(stagedBody).toContain('"aiGenerated":true');
  });
});

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
