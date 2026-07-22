import { describe, expect, it } from "vitest";
import { loadEntityDetails, loadHierarchy, loadOntologyGraph, loadOntologyGraphNeighborhood, loadProjectOutline, loadShaclShapes, searchProject, stageChange, previewStagedChanges, loadAiProviderSettings, discoverAiModels, selectAiModel, retestAiModel, clearAiModelSelection, WebApiError } from "./projectApi";

function response(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}

describe("read-only project API", () => {
  it("requests hierarchy pages with encoded technical identifiers", async () => {
    let requestedPath = "";
    const result = await loadHierarchy(
      "simple",
      { parentIri: "https://example.com/entio/simple#Party", limit: 25 },
      async (input) => {
        requestedPath = String(input);
        return response({ page: { items: [], offset: 0, limit: 25, total: 0, nextOffset: null } });
      },
    );

    expect(requestedPath).toContain("parentIri=https%3A%2F%2Fexample.com%2Fentio%2Fsimple%23Party");
    expect(result.page.limit).toBe(25);
  });

  it("loads entity details and search through typed helpers", async () => {
    const paths: string[] = [];
    const fetcher = async (input: RequestInfo | URL) => {
      paths.push(String(input));
      return response({ apiVersion: "v1", page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
    };

    await loadEntityDetails("simple", "https://example.com/entio/simple#Customer", undefined, fetcher);
    await searchProject("simple", "customer", {}, fetcher);

    expect(paths[0]).toContain("entities?iri=https%3A%2F%2Fexample.com%2Fentio%2Fsimple%23Customer");
    expect(paths[1]).toContain("/search?q=customer");
  });

  it("loads a paged semantic outline for the selected source", async () => {
    let requestedPath = "";
    const result = await loadProjectOutline(
      "simple",
      { sourceId: "simple", limit: 100 },
      async (input) => {
        requestedPath = String(input);
        return response({ apiVersion: "v1", sourceId: "simple", page: { items: [], offset: 0, limit: 100, total: 0, nextOffset: null } });
      },
    );

    expect(requestedPath).toContain("/outline?offset=0&limit=100&sourceId=simple");
    expect(result.sourceId).toBe("simple");
  });

  it("encodes abortable graph and neighborhood reads without exposing offsets", async () => {
    const requests: Array<{ path: string; signal?: AbortSignal | null }> = [];
    const fetcher = async (input: RequestInfo | URL, init?: RequestInit) => {
      requests.push({ path: String(input), signal: init?.signal });
      return response({ apiVersion: "v1", nodes: [], edges: [] });
    };
    const controller = new AbortController();
    await loadOntologyGraph("project / one", { sourceIds: ["source one"], seed: { sourceId: "source one", entityIri: "https://example.com/#Customer" }, signal: controller.signal }, fetcher);
    await loadOntologyGraphNeighborhood("project / one", { sourceIds: ["source one"], entity: { sourceId: "source one", entityIri: "https://example.com/#Customer" }, categories: ["ClassHierarchy"], expectedFingerprint: "finger print", continuation: "next/token", signal: controller.signal }, fetcher);
    expect(requests[0].path).toContain("/projects/project%20%2F%20one/graph?");
    expect(requests[0].path).toContain("seedIri=https%3A%2F%2Fexample.com%2F%23Customer");
    expect(requests[1].path).toContain("expectedFingerprint=finger+print");
    expect(requests[1].path).toContain("continuation=next%2Ftoken");
    expect(requests[1].path).not.toContain("offset");
    expect(requests.every(({ signal }) => signal === controller.signal)).toBe(true);
  });

  it("preserves safe stale error codes without exposing response internals", async () => {
    const request = loadOntologyGraph("simple", { sourceIds: ["simple"], expectedFingerprint: "old" }, async () => new Response(JSON.stringify({ apiVersion: "v1", requestId: "safe", code: "stale-graph-fingerprint", message: "Refresh the graph." }), { status: 409 }));
    await expect(request).rejects.toMatchObject({ name: "WebApiError", code: "stale-graph-fingerprint", message: "Refresh the graph." } satisfies Partial<WebApiError>);
  });

  it("loads the applied SHACL shape graph through a typed helper", async () => {
    let requestedPath = "";
    const result = await loadShaclShapes("simple", async (input) => {
      requestedPath = String(input);
      return response({ apiVersion: "v1", projectId: "simple", shapes: [] });
    });

    expect(requestedPath).toBe("/api/v1/projects/simple/shacl/shapes");
    expect(result.shapes).toEqual([]);
  });

  it("keeps staging and proposal actions behind typed HTTP helpers", async () => {
    const requests: RequestInit[] = [];
    const fetcher = async (_input: RequestInfo | URL, init?: RequestInit) => {
      requests.push(init ?? {});
      return response({ apiVersion: "v1", projectId: "simple", status: "READY", entries: [], proposal: null });
    };

    await stageChange("simple", { sourceId: "simple", editType: "create-class", classIri: "https://example.com/Account", idempotencyKey: "one" }, fetcher);
    await previewStagedChanges("simple", fetcher);

    expect(requests[0].method).toBe("POST");
    expect(requests[0].headers).toEqual({ "Content-Type": "application/json" });
    expect(requests[0].body).toContain("create-class");
    expect(requests[1].method).toBe("POST");
  });

  it("uses the provider settings and explicit model-selection contracts", async () => {
    const requests: Array<{ path: string; init?: RequestInit }> = [];
    const fetcher = async (input: RequestInfo | URL, init?: RequestInit) => {
      requests.push({ path: String(input), init });
      return response({ apiVersion: "v1", models: [] });
    };

    await loadAiProviderSettings(fetcher);
    await discoverAiModels(fetcher);
    await selectAiModel("gpt-5.2", "select-one", fetcher);
    await retestAiModel("test-one", fetcher);
    await clearAiModelSelection(fetcher);

    expect(requests.map(({ path }) => path)).toEqual([
      "/api/v1/ai/provider-settings",
      "/api/v1/ai/models/discover",
      "/api/v1/ai/model-selection",
      "/api/v1/ai/model-selection/test",
      "/api/v1/ai/model-selection",
    ]);
    expect(requests.map(({ init }) => init?.method)).toEqual([undefined, "POST", "PUT", "POST", "DELETE"]);
    expect(requests[2].init?.headers).toMatchObject({ "Idempotency-Key": "select-one" });
    expect(requests[3].init?.headers).toMatchObject({ "Idempotency-Key": "test-one" });
  });
});
