import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { WebDomainRecommendation, WebDomainRecommendationDetailResponse } from "../web/projectApi";
import DomainRecommendationPanel from "./DomainRecommendationPanel";

const intent = { operationKind: "CreateClass" as const, requestedKind: "Class" as const, targetSourceId: "simple" };

describe("contextual domain recommendation panel", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("stays hidden and never searches when the domain profile is inactive", async () => {
    const fetcher = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/domain-ontology")) return json(status("Inactive"));
      throw new Error(`Unexpected request: ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);

    renderPanel("Agreement");

    await waitFor(() => expect(fetcher).toHaveBeenCalledOnce());
    await new Promise((resolve) => window.setTimeout(resolve, 350));
    expect(fetcher).toHaveBeenCalledOnce();
    expect(screen.queryByLabelText("Domain ontology recommendations")).not.toBeInTheDocument();
  });

  it("debounces strict context, preserves the host form, and stages only an explicit action", async () => {
    const requests: Array<{ path: string; body?: Record<string, unknown> }> = [];
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      const body = init?.body ? JSON.parse(String(init.body)) as Record<string, unknown> : undefined;
      requests.push({ path, body });
      if (path.endsWith("/domain-ontology")) return json(status("Active"));
      if (path.endsWith("/domain-recommendations")) return json(searchResult("Possible"));
      if (path.endsWith("/domain-recommendations/rec-1")) return json(detail());
      if (path.endsWith("/dependency-preview")) return json({ apiVersion: "v1", projectId: "simple", recommendationId: "rec-1", dependencyIris: ["https://example.com/dependency"] });
      if (path.endsWith("/stage")) return json({ apiVersion: "v1", projectId: "simple", entries: [], proposal: null });
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);

    render(<QueryClientProvider client={client()}><label>Local label<input defaultValue="Agreement draft" /></label><DomainRecommendationPanel projectId="simple" draftLabel="Agreement" intent={intent} /></QueryClientProvider>);

    expect(requests.some((request) => request.path.endsWith("/domain-recommendations"))).toBe(false);
    const recommendation = await screen.findByRole("button", { name: /Agreement/ });
    const searchRequest = requests.find((request) => request.path.endsWith("/domain-recommendations"));
    expect(searchRequest?.body).toMatchObject({ operationKind: "CreateClass", requestedKind: "Class", draftLabel: "Agreement", targetSourceId: "simple", broadSearch: false });

    fireEvent.click(recommendation);
    await screen.findByText("Canonical IRI:");
    expect(screen.getByRole("textbox", { name: "Local label" })).toHaveValue("Agreement draft");
    expect(requests.some((request) => request.path.endsWith("/stage"))).toBe(false);

    expect(screen.getByRole("button", { name: "Reuse" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Extend" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continue locally" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Reuse" }));
    await waitFor(() => expect(requests.filter((request) => request.path.endsWith("/dependency-preview"))).toHaveLength(1));
    fireEvent.click(screen.getByRole("button", { name: "Customize" }));
    await waitFor(() => expect(requests.filter((request) => request.path.endsWith("/dependency-preview"))).toHaveLength(2));
    await screen.findByText(/1 required domain dependencies/);
    fireEvent.change(screen.getByRole("textbox", { name: "Project label" }), { target: { value: "Local agreement" } });
    fireEvent.click(screen.getByRole("button", { name: "Stage domain action" }));

    await waitFor(() => expect(requests.find((request) => request.path.endsWith("/stage"))?.body).toMatchObject({
      action: "ReuseAndCustomize",
      customization: { preferredLabel: "Local agreement" },
    }));
    expect(screen.getByRole("textbox", { name: "Local label" })).toHaveValue("Agreement draft");
  });

  it("collapses low-confidence results and keeps local continuation available during degraded retrieval", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/domain-ontology")) return json(status("Active"));
      if (path.endsWith("/domain-recommendations")) return json(searchResult("Low", "LexicalStructural"));
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderPanel("Agreement");

    expect(await screen.findByText("Lexical and structural retrieval")).toBeInTheDocument();
    expect(screen.getByText("Possible low-confidence matches (1)")).toBeInTheDocument();
    expect(screen.getByText("No confident domain match. The local action remains available.")).toBeInTheDocument();
  });

  it("cancels a superseded request and uses the latest domain context", async () => {
    const requestSignals: AbortSignal[] = [];
    const bodies: Record<string, unknown>[] = [];
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/domain-ontology")) return json(status("Active"));
      if (path.endsWith("/domain-recommendations")) {
        const body = JSON.parse(String(init?.body)) as Record<string, unknown>;
        bodies.push(body);
        if (body.draftLabel === "Agreement") {
          if (init?.signal) requestSignals.push(init.signal);
          return new Promise<Response>((_resolve, reject) => init?.signal?.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError"))));
        }
        return json({ ...searchResult("Possible"), result: { ...searchResult("Possible").result, recommendations: [{ ...recommendation("Possible"), preferredLabel: "Account" }] } });
      }
      throw new Error(`Unexpected request: ${path}`);
    }));

    const rendered = renderPanel("Agreement");
    await waitFor(() => expect(bodies).toHaveLength(1));
    rendered.rerender(<QueryClientProvider client={renderedClient}><DomainRecommendationPanel projectId="simple" draftLabel="Account" intent={{ ...intent, requiredParentIri: "https://example.com/Parent" }} /></QueryClientProvider>);

    expect(await screen.findByRole("button", { name: /Account/ })).toBeInTheDocument();
    expect(requestSignals[0]?.aborted).toBe(true);
    expect(bodies.at(-1)).toMatchObject({ draftLabel: "Account", requiredParentIri: "https://example.com/Parent" });
  });

  it("shows unavailable retrieval and source customization without blocking local work", async () => {
    let unavailable = true;
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/domain-ontology")) return json(status("Active"));
      if (path.endsWith("/domain-recommendations")) {
        if (unavailable) return json({ ...searchResult("Low"), result: { ...searchResult("Low").result, availability: "Unavailable", recommendations: [] } });
        return json(searchResult("Possible"));
      }
      if (path.endsWith("/domain-recommendations/rec-1")) {
        const value = detail();
        value.recommendation.warnings = ["CustomizedFromSource"];
        return json(value);
      }
      throw new Error(`Unexpected request: ${path}`);
    }));

    const rendered = renderPanel("Agreement");
    expect(await screen.findByText("Domain retrieval unavailable. Continue with the local edit.")).toBeInTheDocument();
    unavailable = false;
    rendered.rerender(<QueryClientProvider client={renderedClient}><DomainRecommendationPanel projectId="simple" draftLabel="Account" intent={intent} /></QueryClientProvider>);
    fireEvent.click(await screen.findByRole("button", { name: /Agreement/ }));
    expect(await screen.findByText("The project meaning has been customized from the current FIBO source.")).toBeInTheDocument();
  });
});

let renderedClient: QueryClient;

function renderPanel(draftLabel: string) {
  renderedClient = client();
  return render(<QueryClientProvider client={renderedClient}><DomainRecommendationPanel projectId="simple" draftLabel={draftLabel} intent={intent} /></QueryClientProvider>);
}

function client() {
  return new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } } });
}

function status(availability: "Active" | "Inactive") {
  return { apiVersion: "v1", projectId: "simple", status: { availability, profile: availability === "Active" ? {} : null, migrationStatus: "Current", issues: [] } };
}

function recommendation(confidence: "Possible" | "Low"): WebDomainRecommendation {
  return {
    recommendationId: "rec-1",
    iri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/Agreement",
    preferredLabel: "Agreement",
    kind: "Class",
    sourceFamily: "FIBO",
    sourceModuleIri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/",
    maturity: "Release",
    confidence,
    permittedActions: ["Reuse", "Extend", "MapAnnotation"],
    reasons: [{ type: "PreferredLabelMatch", relatedIri: null }],
    warnings: [],
    rankingContract: "server-ranked",
  };
}

function searchResult(confidence: "Possible" | "Low", availability: "Full" | "LexicalStructural" = "Full") {
  return { apiVersion: "v1", projectId: "simple", result: { availability, recommendations: [recommendation(confidence)], noConfidentMatch: confidence === "Low", normalizedIntentFingerprint: "intent-1" } };
}

function detail(): WebDomainRecommendationDetailResponse {
  return {
    apiVersion: "v1",
    projectId: "simple",
    recommendation: recommendation("Possible"),
    difference: {
      entityId: "entity-1",
      canonicalIri: recommendation("Possible").iri,
      sourceSnapshot: { canonicalIri: recommendation("Possible").iri, kind: "Class", sourceFamily: "FIBO", sourceOntologyIri: "module", sourcePath: "source.ttl", recordFingerprint: "record", statementFingerprint: "statements", statements: [{ subjectResource: recommendation("Possible").iri, predicate: "type", objectTerm: "Class" }], omittedSourceAxioms: [], classification: "FullMaterialization" },
      projectStatements: [],
      addedProjectStatements: [],
      removedSourceStatements: [],
      classification: "Absent",
    },
  };
}

function json(value: unknown) {
  return Promise.resolve(new Response(JSON.stringify(value), { status: 200, headers: { "Content-Type": "application/json" } }));
}
