import { describe, expect, it } from "vitest";
import { loadEntityDetails, loadHierarchy, searchProject } from "./projectApi";

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
});
