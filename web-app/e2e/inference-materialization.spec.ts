import { expect, test, type Route } from "@playwright/test";

test("reviews inferred facts before explicit assertion and never applies from Reasoning", async ({ page }) => {
  const materializationBodies: Array<Record<string, unknown>> = [];
  const writePaths: string[] = [];
  let entries: Array<Record<string, unknown>> = [];
  let proposal: Record<string, unknown> | null = null;
  let applied = false;

  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    if (path.endsWith("/summary")) return json(route, {
      apiVersion: "v1",
      project: { id: "simple", displayName: "Simple ontology", name: "simple-ontology" },
      sources: [{ id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], tripleCount: 10 }],
      symbolCount: 5, graphTripleCount: applied ? 11 : 10,
    });
    if (path.endsWith("/sources")) return json(route, {
      items: [{ id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], writable: true }],
      offset: 0, limit: 50, total: 1, nextOffset: null,
    });
    if (path.endsWith("/hierarchy") || path.endsWith("/outline")) return json(route, {
      apiVersion: "v1", sourceId: "simple", parentIri: null,
      page: { items: [], offset: 0, limit: 100, total: 0, nextOffset: null },
    });
    if (path.endsWith("/staged") && method === "GET") return json(route, staging(entries, proposal));
    if (path.endsWith("/semantic-jobs") && method === "POST") return json(route, job());
    if (path.endsWith("/semantic-jobs/job-1") && method === "GET") return json(route, job());
    if (path.endsWith("/semantic-jobs/job-1/details")) return json(route, details());
    if (path.endsWith("/semantic-jobs/job-1/materializations")) {
      const body = request.postDataJSON() as Record<string, unknown>;
      materializationBodies.push(body);
      entries = [materializedEntry()];
      proposal = null;
      return json(route, {
        apiVersion: "v1", projectId: "simple", reasoningJobId: "job-1", graphFingerprint: "graph-current",
        mappings: [{ factId: "fact-subclass", stagedChangeId: "stage-1" }],
        staging: staging(entries, proposal),
      });
    }
    if (path.endsWith("/proposal/preview")) {
      proposal = readyProposal();
      return json(route, staging(entries, proposal));
    }
    if (path.endsWith("/proposal/reject")) {
      writePaths.push(path);
      entries = [];
      proposal = null;
      return json(route, staging(entries, proposal));
    }
    if (path.endsWith("/proposal/approve")) {
      writePaths.push(path);
      proposal = { ...readyProposal(), status: "APPROVED" };
      return json(route, staging(entries, proposal));
    }
    if (path.endsWith("/proposal/apply")) {
      writePaths.push(path);
      applied = true;
      entries = [];
      proposal = { ...readyProposal(), status: "APPLIED" };
      return json(route, staging(entries, proposal));
    }
    return json(route, { apiVersion: "v1", page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
  });

  await page.goto("/projects/simple");
  await page.getByRole("tab", { name: "Reasoning" }).click();
  await page.getByRole("button", { name: "Refresh reasoning" }).click();
  await expect(page.getByRole("heading", { name: "Asserted facts" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Inferred facts" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "Subclass", exact: true })).toBeVisible();
  await page.getByLabel("Select Account subclass of Financial entity").focus();
  await page.keyboard.press("Space");
  await page.getByRole("button", { name: "Stage as asserted" }).click();

  await expect(page.getByRole("heading", { name: "Review queue" })).toBeVisible();
  expect(materializationBodies).toHaveLength(1);
  expect(materializationBodies[0].selections).toEqual([{ factId: "fact-subclass" }]);
  expect(JSON.stringify(materializationBodies[0])).not.toContain("subject");
  expect(writePaths).toEqual([]);

  await page.getByRole("button", { name: "View Details" }).click();
  await expect(page.getByText("Materialized from reasoning", { exact: true })).toBeVisible();
  await expect(page.getByText("Added · Account · superclass · Financial entity")).toBeVisible();
  await page.getByRole("dialog", { name: "View Details" }).getByRole("button", { name: "Reject" }).click();
  await expect(page.getByText("No staged changes")).toBeVisible();
  expect(writePaths).toEqual(["/api/v1/projects/simple/proposal/reject"]);

  await page.getByRole("tab", { name: "Reasoning" }).click();
  await page.getByLabel("Select Account subclass of Financial entity").check();
  await page.getByRole("button", { name: "Stage as asserted" }).click();
  await page.getByRole("button", { name: "Accept" }).click();
  await expect(page.getByText("Proposal accepted and applied. The project has been refreshed.")).toBeVisible();
  expect(writePaths.slice(-2)).toEqual([
    "/api/v1/projects/simple/proposal/approve",
    "/api/v1/projects/simple/proposal/apply",
  ]);
});

function job() {
  return {
    apiVersion: "v1", id: "job-1", projectId: "simple", kind: "Reasoning", scope: "Applied",
    status: "Completed", phase: "completed", message: "Reasoning completed.", graphFingerprint: "graph-current",
    proposalFingerprint: null, resultSummary: { inferredFacts: 1 }, error: null,
  };
}

function details() {
  return {
    apiVersion: "v1",
    job: job(),
    facts: [{
      kind: "SubclassRelationship", subject: "https://example.com/Account",
      predicate: "http://www.w3.org/2000/01/rdf-schema#subClassOf",
      objectValue: "https://example.com/FinancialEntity", origin: "Asserted", sourceId: "simple",
    }],
    materializationCandidates: [{
      factId: "fact-subclass", kind: "SubclassRelationship",
      subject: "https://example.com/Account", subjectLabel: "Account",
      predicate: "http://www.w3.org/2000/01/rdf-schema#subClassOf", predicateLabel: "subclass of",
      objectValue: "https://example.com/FinancialEntity", objectLabel: "Financial entity",
      origin: "Inferred", stageability: "Stageable", reason: "Ready to stage.",
      sourceCandidates: [{ sourceId: "simple", selected: true }], selectedSourceId: "simple",
      existingStagedChangeId: null, importDependence: "LocalOnly", importSourceIds: [],
    }],
    shaclFindings: [], warnings: [], errors: [], truncated: false,
  };
}

function materializedEntry() {
  return {
    id: "stage-1", order: 1, sourceId: "simple", summary: "add-superclass · Account",
    editType: "add-superclass", status: "STAGED", authorId: "alice", latestEditorId: "alice",
    comment: "Materialized from reasoning; review before approval.",
    normalizedValues: {
      subjectIri: "https://example.com/Account", subjectLabel: "Account",
      predicateIri: "http://www.w3.org/2000/01/rdf-schema#subClassOf", predicateLabel: "superclass",
      objectIri: "https://example.com/FinancialEntity", objectLabel: "Financial entity",
    },
    generatedIris: [], validationMessages: [],
    materializationProvenance: {
      origin: "MaterializedFromReasoning", inferenceKind: "SubclassRelationship", reasoningJobId: "job-1",
      graphFingerprint: "graph-current", factId: "fact-subclass", stagedByUserId: "alice",
      stagedAt: "2026-07-23T15:00:00Z", targetSourceId: "simple", entailedBeforeAssertion: true,
      importDependence: "LocalOnly", importSourceIds: [],
    },
  };
}

function readyProposal() {
  return {
    id: "proposal-1", status: "READYFORREVIEW", stagedChangeIds: ["stage-1"],
    baselineProjectFingerprint: "baseline", validationMessages: [], validationIssues: [],
    diff: [{
      kind: "Added", subject: "https://example.com/Account",
      predicate: "http://www.w3.org/2000/01/rdf-schema#subClassOf",
      objectValue: "https://example.com/FinancialEntity", description: "Added asserted superclass.",
    }],
    targetSourceIds: ["simple"], shaclImpact: null, message: "Ready for review.",
  };
}

function staging(entries: Array<Record<string, unknown>>, proposal: Record<string, unknown> | null) {
  return { apiVersion: "v1", projectId: "simple", status: entries.length ? "READY" : "EMPTY", entries, proposal };
}

async function json(route: Route, body: unknown) {
  await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
}
