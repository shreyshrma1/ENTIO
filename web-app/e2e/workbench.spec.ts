import { expect, test } from "@playwright/test";

test("completes the browser workbench journey through reviewable staging", async ({ page }) => {
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    if (path === "/api/v1/projects" && request.method() === "GET") return json(route, { apiVersion: "v1", projects: [{ id: "simple", displayName: "Simple ontology" }] });
    if (path.endsWith("/summary")) return json(route, {
      apiVersion: "v1",
      project: { id: "simple", displayName: "Simple ontology", name: "simple-ontology" },
      sources: [{ id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], tripleCount: 7 }],
      symbolCount: 5,
      graphTripleCount: 7,
    });
    if (path.endsWith("/hierarchy")) return json(route, {
      apiVersion: "v1", sourceId: "simple", parentIri: null,
      page: { items: [{ iri: "https://example.com/entio/simple#Customer", label: "Customer", kind: "Class", sourceId: "simple", childCount: 0 }], offset: 0, limit: 50, total: 1, nextOffset: null },
    });
    if (path.endsWith("/entities")) return json(route, {
      apiVersion: "v1", iri: "https://example.com/entio/simple#Customer", label: "Customer", kind: "Class", sourceId: "simple", sourceOntologyId: "simple", locality: "Local", preferredLabelSource: "RdfsLabel",
      alternateLabels: [], definitions: [{ value: "A customer.", language: null, datatype: null }], annotations: [], directSuperclasses: [], directSubclasses: [], directlyTypedIndividuals: [], assertedTypes: [], domains: [], ranges: [], outgoingRelationships: [], incomingRelationships: [],
    });
    if (path.endsWith("/ai/credential-status")) return json(route, { apiVersion: "v1", configured: true, providerId: "provider-neutral", testStatus: "PASSED" });
    if (path.endsWith("/ai/assistant") && request.method() === "POST") return json(route, {
      apiVersion: "v1", operation: "SUGGEST_SUPERCLASS", answer: "A typed suggestion is ready for review.", evidence: [], assertedFacts: ["type: Customer"], inferredFacts: [], fiboResults: [],
      suggestions: [{ id: "suggest-superclass", suggestionType: "add-superclass", rationale: "Review before staging.", edit: { sourceId: "simple", editType: "add-superclass", classIri: "https://example.com/entio/simple#Customer", superclassIri: "https://example.com/entio/simple#Party", aiGenerated: true } }],
      uncertainty: ["Development response"], warnings: [],
    });
    if (path.endsWith("/staged") && request.method() === "GET") return json(route, { apiVersion: "v1", projectId: "simple", status: "READY", entries: [], proposal: null });
    if (path.endsWith("/staged") && request.method() === "POST") return json(route, {
      apiVersion: "v1", projectId: "simple", status: "READY", entries: [{ id: "stage-1", order: 1, sourceId: "simple", summary: "add superclass", editType: "add-superclass", status: "STAGED", authorId: "alice", latestEditorId: "alice", comment: null, aiGenerated: true, normalizedValues: {}, generatedIris: [], validationMessages: [] }], proposal: null,
    });
    if (path.endsWith("/proposal/preview")) return json(route, { apiVersion: "v1", projectId: "simple", status: "READY", entries: [{ id: "stage-1", order: 1, sourceId: "simple", summary: "add superclass", editType: "add-superclass", status: "STAGED", authorId: "alice", latestEditorId: "alice", comment: null, aiGenerated: true, normalizedValues: {}, generatedIris: [], validationMessages: [] }], proposal: { id: "proposal-1", status: "READYFORREVIEW", stagedChangeIds: ["stage-1"], baselineProjectFingerprint: "base", validationMessages: [], diff: [{ kind: "Added", subject: "Customer", predicate: "subClassOf", objectValue: "Party", description: "Customer is a Party" }], message: "Proposal ready for review." } });
    if (path.endsWith("/proposal/approve")) return json(route, { apiVersion: "v1", projectId: "simple", status: "READY", entries: [], proposal: { id: "proposal-1", status: "APPROVED", stagedChangeIds: ["stage-1"], baselineProjectFingerprint: "base", validationMessages: [], diff: [], message: "Approved." } });
    if (path.endsWith("/proposal/apply")) return json(route, { apiVersion: "v1", projectId: "simple", status: "READY", entries: [], proposal: { id: "proposal-1", status: "APPLIED", stagedChangeIds: [], baselineProjectFingerprint: "base", validationMessages: [], diff: [], message: "Applied and reloaded." } });
    if (path.endsWith("/external/fibo/modules")) return json(route, { sourceId: "fibo", release: "test", page: { items: [{ ontologyIri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/", label: "Agreements", domain: "FND", sourcePath: "source/FND/Agreements/Agreements.rdf", maturity: "Release", curated: true, elementCount: 1 }], offset: 0, limit: 15, total: 1, nextOffset: null } });
    if (path.endsWith("/external/fibo/module-elements")) return json(route, { moduleIri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/", page: { items: [{ iri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/Agreement", label: "agreement", kind: "Class", moduleIri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/", domain: "FND", maturity: "Release", catalogStatus: "Available", sourcePath: "source/FND/Agreements/Agreements.rdf", alternateLabels: [], definitions: ["a mutual understanding"], parents: [], domains: [], ranges: [] }], offset: 0, limit: 15, total: 1, nextOffset: null } });
    if (path.endsWith("/external/fibo/details")) return json(route, { element: { iri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/Agreement", label: "agreement", kind: "Class", moduleIri: "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/", domain: "FND", maturity: "Release", catalogStatus: "Available", sourcePath: "source/FND/Agreements/Agreements.rdf", alternateLabels: [], definitions: ["a mutual understanding"], parents: [], domains: [], ranges: [] }, dependencies: [] });
    return json(route, { apiVersion: "v1", page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
  });

  await page.goto("/");
  await page.getByRole("link", { name: /Simple ontology/ }).click();
  await expect(page.getByRole("heading", { name: "simple-ontology" })).toBeVisible();
  await page.getByRole("button", { name: /Customer/ }).click();
  await expect(page.getByRole("heading", { name: "Customer" })).toBeVisible();
  await expect(page.getByText("A customer.")).toBeVisible();
  await expect(page).toHaveScreenshot("workbench-light.png", { fullPage: true, mask: [page.locator(".collaboration-presence")], maskColor: "#ffffff" });

  await page.getByRole("button", { name: "Expand navigation" }).click();
  await expect(page.getByRole("tab", { name: "Explore", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Account", exact: true }).click();
  await expect(page.getByText("Provider credential", { exact: true })).toBeVisible();
  await page.getByRole("tab", { name: "Settings", exact: true }).click();
  await expect(page.getByRole("tab", { name: "Settings", exact: true })).toHaveAttribute("aria-selected", "true");
  await expect(page.getByRole("heading", { name: "Settings", exact: true })).toBeVisible();
  await page.getByRole("tab", { name: "Explore", exact: true }).click();

  await page.getByLabel("Class IRI").fill("https://example.com/entio/simple#Party");
  await page.getByRole("textbox", { name: "Label", exact: true }).fill("Party");
  await page.getByRole("button", { name: "Stage class" }).click();
  await expect(page.getByText("add superclass")).toBeVisible();
  await page.getByRole("button", { name: "Preview proposal" }).click();
  await expect(page.getByText(/Proposal ready for review/i)).toBeVisible();

  await page.getByRole("tab", { name: "Assistant" }).click();
  await page.getByRole("combobox", { name: "Operation" }).selectOption("SUGGEST_SUPERCLASS");
  await page.getByLabel("Request or IRI").fill("https://example.com/entio/simple#Party");
  await page.getByRole("button", { name: "Ask assistant" }).click();
  await page.getByRole("button", { name: "Stage suggestion" }).click();
  await expect(page.getByRole("button", { name: "Staged for review" })).toBeVisible();

  await page.getByRole("tab", { name: "FIBO" }).click();
  await expect(page.getByRole("heading", { name: "External ontology browser" })).toBeVisible();
  await page.getByRole("button", { name: "Agreements" }).click();
  await page.locator(".external-browser-grid .external-scroll-list").nth(1).getByRole("button").click();
  await expect(page.getByText("a mutual understanding")).toBeVisible();
  await expect(page.getByText(/https:\/\/spec\.edmcouncil\.org\/fibo\/ontology\/FND\/Agreements/)).toBeVisible();
});

async function json(route: import("@playwright/test").Route, body: unknown) {
  await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
}
