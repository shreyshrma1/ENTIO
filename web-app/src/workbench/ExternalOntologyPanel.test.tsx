import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import ExternalOntologyPanel from "./ExternalOntologyPanel";

describe("optional domain ontology workspace", () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("renders inactive FIBO as browse-only with the exact release", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path === "/api/v1/domain-ontologies") return json(catalog());
      if (path.endsWith("/domain-ontology")) return json(status("Inactive"));
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderPanel();

    expect(await screen.findByText("master_2026Q2")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Domain ontology" })).toHaveValue("none");
    expect(screen.getByRole("heading", { name: "Browse FIBO before activation" })).toBeInTheDocument();
    expect(screen.getByText(/Contextual recommendations and reuse actions/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Stage reuse for review" })).not.toBeInTheDocument();
  });

  it("labels degraded retrieval and prevents selection when domain assets are unavailable", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path === "/api/v1/domain-ontologies") return json(catalog("LexicalStructural"));
      if (path.endsWith("/domain-ontology")) return json(status("Inactive"));
      throw new Error(`Unexpected request: ${path}`);
    }));

    const view = renderPanel();
    expect(await screen.findByText("Lexical and structural retrieval")).toBeInTheDocument();
    view.unmount();

    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path === "/api/v1/domain-ontologies") return json(catalog("Unavailable", false));
      if (path.endsWith("/domain-ontology")) return json(status("Inactive"));
      throw new Error(`Unexpected request: ${path}`);
    }));
    renderPanel();
    expect(await screen.findByText("Domain retrieval unavailable")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Select FIBO" })).toBeDisabled();
  });

  it("requires confirmation against an exact activation preview and restores a failure message", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path === "/api/v1/domain-ontologies") return json(catalog());
      if (path.endsWith("/domain-ontology")) return json(status("Inactive"));
      if (path.endsWith("/activation-preview")) return json({ apiVersion: "v1", projectId: "simple", activationToken: "token", preview: { profile: profile(), profilePath: ".entio/domain-profile.yaml", managedSourcePath: "ontology/fibo-reuse.ttl", serializedProfile: "schema: entio-domain-profile-v1\nsourceId: fibo\n", serializedEmptyManagedSource: "# Entio managed FIBO reuse source\n", changesProjectOntology: false } });
      if (path.endsWith("/activate")) return json({ apiVersion: "v1", code: "domain-activation-failed", message: "restored" }, 409);
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderPanel();
    fireEvent.click(await screen.findByRole("button", { name: "Select FIBO" }));

    expect(await screen.findByText(".entio/domain-profile.yaml")).toBeInTheDocument();
    expect(screen.getByText("ontology/fibo-reuse.ttl")).toBeInTheDocument();
    expect(screen.getByText("No")).toBeInTheDocument();
    const activate = screen.getByRole("button", { name: "Activate FIBO" });
    expect(activate).toBeDisabled();
    const confirmation = screen.getByRole("checkbox", { name: /I understand/ });
    confirmation.focus();
    expect(confirmation).toHaveFocus();
    fireEvent.keyDown(confirmation, { key: " " });
    fireEvent.click(confirmation);
    fireEvent.click(activate);

    expect(await screen.findByRole("alert")).toHaveTextContent("prior project state was restored");
  });

  it("plans selected foundations and renders server-ranked full-corpus source and project meaning", async () => {
    const fetcher = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path === "/api/v1/domain-ontologies") return json(catalog());
      if (path.endsWith("/domain-ontology")) return json(status("Active"));
      if (path.endsWith("/domain-ontology/foundation")) return json({ apiVersion: "v1", projectId: "simple", groups: [{ groupId: "agreements", label: "Agreements", members: [{ elementId: "dfe_one", iri: "https://spec.example/Agreement", label: "agreement", kind: "Class", sourceFamily: "FIBO" }] }] });
      if (path.endsWith("/foundation-plans")) return json({ apiVersion: "v1", plan: { planId: "dfp_plan", projectId: "simple", batches: [{ batchNumber: 1, explicitSelectionCount: 1, items: [{ iri: "https://spec.example/Agreement", label: "agreement", kind: "Class", role: "ExplicitSelection" }] }, { batchNumber: 2, explicitSelectionCount: 1, items: [{ iri: "https://www.omg.org/spec/Commons/Party", label: "party", kind: "Class", role: "RequiredDependency" }] }], explicitSelectionCount: 2, dependencyCount: 1, packageFingerprint: "package", indexFingerprint: "index" } });
      if (path.includes("/search?")) return json({ apiVersion: "v1", query: "agreement", page: { items: [{ iri: "https://project.example/Agreement", label: "Local agreement", kind: "Class", sourceId: "simple", reason: "PreferredLabel", rank: 0, locality: "Local" }, { iri: "https://import.example/Agreement", label: "Imported agreement", kind: "Class", sourceId: "imports", reason: "PreferredLabel", rank: 1, locality: "Imported" }], offset: 0, limit: 50, total: 2, nextOffset: null } });
      if (path.endsWith("/domain-recommendations")) return json({ apiVersion: "v1", projectId: "simple", result: { availability: "Full", noConfidentMatch: false, normalizedIntentFingerprint: "intent", recommendations: [recommendation("rec-fibo", "FIBO", "agreement"), recommendation("rec-commons", "OMG_COMMONS", "party"), recommendation("rec-reused", "FIBO", "reused agreement", true), recommendation("rec-custom", "FIBO", "custom agreement", false, true)] } });
      if (path.endsWith("/domain-recommendations/rec-fibo")) return json({ apiVersion: "v1", projectId: "simple", recommendation: recommendation("rec-fibo", "FIBO", "agreement"), difference: { entityId: "dre_one", canonicalIri: "https://spec.example/Agreement", sourceSnapshot: { canonicalIri: "https://spec.example/Agreement", kind: "Class", sourceFamily: "FIBO", sourceOntologyIri: "https://spec.example/", sourcePath: "source/Agreement.rdf", recordFingerprint: "record", statementFingerprint: "statements", statements: [{ subjectResource: "https://spec.example/Agreement", predicate: "type", objectTerm: "Class" }], omittedSourceAxioms: [], classification: "CompleteSupportedMaterialization" }, projectStatements: [], addedProjectStatements: [], removedSourceStatements: [], classification: "LogicalStructureChanged" } });
      if (path.endsWith("/deactivation-preview")) return json({ apiVersion: "v1", projectId: "simple", deactivationToken: null, preview: { active: true, eligible: false, blockers: ["ManagedSourceNotEmpty"], profilePath: ".entio/domain-profile.yaml", removeEmptyManagedSource: false, changesProjectOntology: false } });
      throw new Error(`Unexpected request: ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);

    renderPanel();
    fireEvent.click(await screen.findByRole("checkbox", { name: /Agreements/ }));
    fireEvent.click(screen.getByRole("button", { name: "Plan selected (1)" }));
    expect(await screen.findByText("0 of 2 batches applied")).toBeInTheDocument();

    fireEvent.change(screen.getByRole("textbox", { name: "Search concepts" }), { target: { value: "agreement" } });
    fireEvent.click(screen.getByRole("button", { name: "Search project and FIBO" }));
    expect(await screen.findByText("Local agreement")).toBeInTheDocument();
    expect(screen.getByText("Local")).toBeInTheDocument();
    expect(screen.getByText("Imported")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /reused agreement.*Project reuse/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /custom agreement.*Customized project reuse/i })).toBeInTheDocument();
    expect(screen.getByText("OMG Commons")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /^agreementFIBO/ }));
    expect(await screen.findByRole("heading", { name: "FIBO source meaning" })).toBeInTheDocument();
    expect(screen.getByText("Not currently applied to this project.")).toBeInTheDocument();
    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(7));
  });

  it("keeps a late search response from replacing the latest result", async () => {
    let releaseAlpha!: (response: Response) => void;
    const alphaResponse = new Promise<Response>((resolve) => { releaseAlpha = resolve; });
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path === "/api/v1/domain-ontologies") return json(catalog());
      if (path.endsWith("/domain-ontology")) return json(status("Active"));
      if (path.endsWith("/domain-ontology/foundation")) return json({ apiVersion: "v1", projectId: "simple", groups: [] });
      if (path.includes("/search?")) return json({ apiVersion: "v1", query: "", page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
      if (path.endsWith("/domain-recommendations")) {
        const label = JSON.parse(String(init?.body)).draftLabel as string;
        return label === "alpha" ? alphaResponse : json(recommendationResponse("beta"));
      }
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderPanel();
    const input = await screen.findByRole("textbox", { name: "Search concepts" });
    fireEvent.change(input, { target: { value: "alpha" } });
    fireEvent.submit(screen.getByRole("search"));
    fireEvent.change(input, { target: { value: "beta" } });
    fireEvent.submit(screen.getByRole("search"));
    expect(await screen.findByRole("button", { name: /^betaFIBO/ })).toBeInTheDocument();

    await act(async () => releaseAlpha(json(recommendationResponse("alpha"))));
    await waitFor(() => expect(screen.queryByRole("button", { name: /^alphaFIBO/ })).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: /^betaFIBO/ })).toBeInTheDocument();
  });
});

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><ExternalOntologyPanel projectId="simple" sourceId="simple" /></QueryClientProvider>);
}

function profile() {
  return { sourceId: "fibo", release: "master_2026Q2", packageFingerprint: "package", managedSourceId: "fibo-reuse" };
}

function catalog(retrievalAvailability: "Full" | "LexicalStructural" | "Unavailable" = "Full", selectable = true) {
  return { apiVersion: "v1", domainOntologies: [{ sourceId: "fibo", displayName: "Financial Industry Business Ontology", release: "master_2026Q2", packageFingerprint: "package", retrievalAvailability, selectable }] };
}

function status(availability: "Inactive" | "Active") {
  return { apiVersion: "v1", projectId: "simple", status: { availability, profile: availability === "Active" ? profile() : null, migrationStatus: "NoExistingReuse", issues: [] } };
}

function recommendation(recommendationId: string, sourceFamily: "FIBO" | "OMG_COMMONS", preferredLabel: string, reused = false, customized = false) {
  return { recommendationId, iri: `https://spec.example/${preferredLabel}`, preferredLabel, kind: "Class", sourceFamily, sourceModuleIri: "https://spec.example/module", maturity: "Release", confidence: "Strong", permittedActions: ["Browse", "Reuse"], reasons: [{ type: reused ? "AlreadyReused" : "PreferredLabelMatch", relatedIri: null }], warnings: customized ? ["CustomizedFromSource"] : [], rankingContract: "domain-ranking-v1" };
}

function recommendationResponse(label: string) {
  return { apiVersion: "v1", projectId: "simple", result: { availability: "Full", noConfidentMatch: false, normalizedIntentFingerprint: label, recommendations: [recommendation(`rec-${label}`, "FIBO", label)] } };
}

function json(body: unknown, statusCode = 200): Response {
  return new Response(JSON.stringify(body), { status: statusCode, headers: { "Content-Type": "application/json" } });
}
