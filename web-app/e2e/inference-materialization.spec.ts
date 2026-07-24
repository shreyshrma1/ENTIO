import { expect, test, type Route } from "@playwright/test";

test("browses asserted and inferred reasoning facts without writing from Reasoning", async ({ page }) => {
  const writePaths: string[] = [];

  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    if (path.endsWith("/summary")) return json(route, {
      apiVersion: "v1",
      project: { id: "simple", displayName: "Simple ontology", name: "simple-ontology" },
      sources: [{ id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], tripleCount: 10 }],
      symbolCount: 5, graphTripleCount: 10,
    });
    if (path.endsWith("/sources")) return json(route, {
      items: [{ id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], writable: true }],
      offset: 0, limit: 50, total: 1, nextOffset: null,
    });
    if (path.endsWith("/hierarchy") || path.endsWith("/outline")) return json(route, {
      apiVersion: "v1", sourceId: "simple", parentIri: null,
      page: { items: [], offset: 0, limit: 100, total: 0, nextOffset: null },
    });
    if (path.endsWith("/staged") && method === "GET") return json(route, staging([], null));
    if (path.endsWith("/semantic-jobs") && method === "POST") return json(route, job());
    if (path.endsWith("/semantic-jobs/job-1") && method === "GET") return json(route, job());
    if (path.endsWith("/semantic-jobs/job-1/details")) {
      return json(route, details(url.searchParams.get("factOrigin") ?? "Asserted"));
    }
    if (method !== "GET" && (path.includes("/materializations") || path.includes("/proposal/"))) {
      writePaths.push(path);
    }
    return json(route, { apiVersion: "v1", page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
  });

  await page.goto("/projects/simple");
  await page.getByRole("tab", { name: "Reasoning" }).click();
  await page.getByRole("button", { name: "Refresh reasoning" }).click();
  const asserted = page.getByRole("button", { name: /Asserted facts/ });
  await asserted.focus();
  await asserted.press("Enter");
  const assertedDialog = page.getByRole("dialog", { name: "Asserted facts" });
  await expect(assertedDialog).toContainText("Account");
  await expect(assertedDialog).toContainText("Financial entity");
  await assertedDialog.getByRole("button", { name: "Done" }).press("Enter");

  const inferred = page.getByRole("button", { name: /Inferred facts/ });
  await inferred.focus();
  await inferred.press("Enter");
  const inferredDialog = page.getByRole("dialog", { name: "Inferred facts" });
  await expect(inferredDialog).toContainText("Customer");
  await inferredDialog.getByRole("textbox", { name: "Search relationships" }).fill("Customer");
  await inferredDialog.getByRole("button", { name: "Search" }).press("Enter");
  await expect(inferredDialog).toContainText("subclass of");
  await expect(page.getByRole("button", { name: "Stage as asserted" })).toHaveCount(0);
  expect(writePaths).toEqual([]);
});

function job() {
  return {
    apiVersion: "v1", id: "job-1", projectId: "simple", kind: "Reasoning", scope: "Applied",
    status: "Completed", phase: "completed", message: "Reasoning completed.", graphFingerprint: "graph-current",
    proposalFingerprint: null, resultSummary: { inferredFacts: 1 }, error: null,
  };
}

function details(origin: string) {
  const inferred = origin === "Inferred";
  return {
    apiVersion: "v1",
    job: job(),
    facts: [{
      kind: "SubclassRelationship",
      subject: inferred ? "https://example.com/Customer" : "https://example.com/Account",
      subjectLabel: inferred ? "Customer" : "Account",
      predicate: "http://www.w3.org/2000/01/rdf-schema#subClassOf",
      predicateLabel: "subclass of",
      objectValue: "https://example.com/FinancialEntity",
      objectLabel: "Financial entity",
      origin,
      sourceId: "simple",
    }],
    factOffset: 0,
    factLimit: 50,
    totalFactCount: 1,
    nextFactOffset: null,
    materializationCandidates: [],
    shaclFindings: [],
    warnings: [],
    errors: [],
    truncated: false,
  };
}

function staging(entries: Array<Record<string, unknown>>, proposal: Record<string, unknown> | null) {
  return { apiVersion: "v1", projectId: "simple", status: entries.length ? "READY" : "EMPTY", entries, proposal };
}

async function json(route: Route, body: unknown) {
  await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
}
