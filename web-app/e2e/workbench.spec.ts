import { expect, test } from "@playwright/test";

test("completes the browser workbench journey through reviewable staging", async ({ page }) => {
  const stagedEditTypes: string[] = [];
  const stagedEntries: Array<Record<string, unknown>> = [];
  let approvingUser: string | undefined;
  let aiMessageCall = 0;
  let aiDraftAnalyzed = false;
  let proposalApplied = false;
  let hierarchyRequests = 0;
  let currentProposal: Record<string, unknown> | null = null;
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    if (path === "/api/v1/projects" && request.method() === "GET") return json(route, { apiVersion: "v1", projects: [{ id: "simple", displayName: "Simple ontology" }] });
    if (path.endsWith("/summary")) return json(route, {
      apiVersion: "v1",
      project: { id: "simple", displayName: "Simple ontology", name: "simple-ontology" },
      sources: [{ id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], tripleCount: 7 }],
      symbolCount: proposalApplied ? 6 : 5,
      graphTripleCount: proposalApplied ? 10 : 7,
    });
    if (path.endsWith("/sources")) return json(route, {
      items: [
        { id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], writable: true },
        { id: "shapes", path: "ontology/shapes.ttl", format: "turtle", roles: ["shapes"], writable: true },
      ], offset: 0, limit: 50, total: 2, nextOffset: null,
    });
    if (path.endsWith("/hierarchy")) {
      hierarchyRequests += 1;
      const parentIri = url.searchParams.get("parentIri");
      const items = parentIri === "https://example.com/entio/simple#Customer"
        ? proposalApplied ? [{ iri: "https://example.com/entio/simple#CommercialCustomer", label: "Commercial Customer", kind: "Class", sourceId: "simple", childCount: 0 }] : []
        : [{ iri: "https://example.com/entio/simple#Customer", label: "Customer", kind: "Class", sourceId: "simple", childCount: proposalApplied ? 1 : 0 }];
      return json(route, {
        apiVersion: "v1", sourceId: "simple", parentIri,
        page: { items, offset: 0, limit: 50, total: items.length, nextOffset: null },
      });
    }
    if (path.endsWith("/outline")) return json(route, {
      apiVersion: "v1", sourceId: "simple",
      page: { items: [
        { iri: "https://example.com/entio/simple#Customer", label: "Customer", kind: "Class", sourceId: "simple" },
        { iri: "https://example.com/entio/simple#Shrey", label: "Shrey", kind: "Individual", sourceId: "simple" },
        { iri: "https://example.com/entio/simple#receivedInvoice", label: "received invoice", kind: "ObjectProperty", sourceId: "simple" },
      ], offset: 0, limit: 100, total: 3, nextOffset: null },
    });
    if (path.endsWith("/search")) return json(route, {
      apiVersion: "v1", query: url.searchParams.get("q") ?? "",
      page: { items: [
        { iri: "https://example.com/entio/simple#Customer", label: "Customer", kind: "Class", sourceId: "simple", reason: "PreferredLabel", rank: 0, locality: "Local" },
        { iri: "https://example.com/entio/simple#Shrey", label: "Shrey", kind: "Individual", sourceId: "simple", reason: "AssertedType", rank: 4, locality: "Local" },
      ], offset: 0, limit: 50, total: 2, nextOffset: null },
    });
    if (path.endsWith("/entities")) {
      const iri = url.searchParams.get("iri") ?? "";
      const isProperty = iri.endsWith("receivedInvoice");
      return json(route, {
        apiVersion: "v1", iri, label: isProperty ? "received invoice" : "Customer", kind: isProperty ? "ObjectProperty" : "Class", sourceId: "simple", sourceOntologyId: "simple", locality: "Local", preferredLabelSource: "RdfsLabel",
        alternateLabels: [], definitions: isProperty ? [] : [{ value: "A customer.", language: null, datatype: null }], annotations: [], directSuperclasses: [], directSubclasses: [], directlyTypedIndividuals: [], assertedTypes: [], domains: [], ranges: [],
        outgoingRelationships: isProperty ? [{ direction: "outgoing", predicate: { iri: "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", label: "type", kind: null, sourceId: "simple" }, value: { kind: "Iri", value: "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property", label: null, datatype: null, language: null }, sourceId: "simple" }] : [],
        incomingRelationships: [],
      });
    }
    if (path.endsWith("/deletion-dependencies")) return json(route, {
      apiVersion: "v1", projectId: "simple", targetIri: "https://example.com/entio/simple#Customer", targetLabel: "Customer", status: "RequiresExplicitDependencies",
      directStatements: [{ key: "direct-1", kind: "DirectDefinition", subject: "https://example.com/entio/simple#Customer", subjectLabel: "Customer", predicate: "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", predicateLabel: "type", objectValue: "http://www.w3.org/2002/07/owl#Class", objectLabel: "Class" }],
      dependentStatements: [{ key: "dependent-1", kind: "IncomingReference", subject: "https://example.com/entio/simple#Shrey", subjectLabel: "Shrey", predicate: "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", predicateLabel: "type", objectValue: "https://example.com/entio/simple#Customer", objectLabel: "Customer" }],
    });
    if (path.endsWith("/ai/credential-status")) return json(route, { apiVersion: "v1", configured: true, providerId: "openai", testStatus: "PASSED" });
    if (path.endsWith("/ai/conversations") && request.method() === "GET") return json(route, { apiVersion: "v1", conversations: [aiConversation([])] });
    if (path.endsWith("/ai/conversations/conversation-1") && request.method() === "GET") return json(route, { apiVersion: "v1", conversation: aiConversation([]) });
    if (path.endsWith("/ai/conversations/conversation-1/messages") && request.method() === "POST") {
      aiMessageCall += 1;
      if (aiMessageCall === 1) return json(route, aiTurn({
        runId: "run-1",
        answer: "Customer is an asserted class in the selected project.",
        messages: [
          aiMessage("message-1", "USER", "Explain Customer."),
          aiMessage("message-2", "ASSISTANT", "Customer is an asserted class in the selected project.", "EXPLAIN_ENTITY", ["entity:Customer"]),
        ],
      }));
      if (aiMessageCall === 2) return json(route, aiTurn({
        runId: "run-2",
        status: "AWAITING_PLAN_CONFIRMATION",
        answer: "Confirm the bounded plan before draft mutation.",
        plan: { request: "Design an ontology for receivables.", steps: ["Inspect the approved project", "Prepare typed edits", "Run deterministic analysis"], openDecisions: ["Confirm receivables scope"], estimatedEditCount: 1 },
      }));
      return json(route, aiTurn({
        runId: "run-2",
        answer: "I prepared one private typed edit for deterministic analysis.",
        draftId: "draft-1",
        messages: [aiMessage("message-3", "ASSISTANT", "I prepared one private typed edit for deterministic analysis.")],
      }));
    }
    if (path.includes("/ai/runs/") && path.endsWith("/events")) {
      const runId = path.split("/").at(-2) ?? "run-1";
      return route.fulfill({ status: 200, contentType: "text/event-stream", body: `id: ${runId}:1\nevent: run-started\ndata: {"sequence":1,"runId":"${runId}","type":"RUN_STARTED","message":"Run started.","referenceIds":[],"createdAt":"2026-07-17T12:00:00Z"}\n\nid: ${runId}:2\nevent: capability-completed\ndata: {"sequence":2,"runId":"${runId}","type":"CAPABILITY_COMPLETED","message":"Entity inspection completed.","referenceIds":["entity:Customer"],"createdAt":"2026-07-17T12:00:01Z"}\n\n` });
    }
    if (path.endsWith("/ai/drafts/draft-1") && request.method() === "GET") return json(route, { apiVersion: "v1", draft: aiDraft(aiDraftAnalyzed ? "READY_FOR_REVIEW" : "EDITING") });
    if (path.endsWith("/ai/drafts/draft-1/analysis") && request.method() === "POST") {
      aiDraftAnalyzed = true;
      return json(route, { apiVersion: "v1", analysis: aiAnalysis() });
    }
    if (path.endsWith("/ai/drafts/draft-1/submit") && request.method() === "POST") return json(route, {
      apiVersion: "v1", submissionId: "submission-1", proposalId: "proposal-ai-1", reviewState: "READYFORREVIEW", projectId: "simple", draftId: "draft-1", draftRevision: 1, submittingUserId: "alice", conversationId: "conversation-1", runId: "run-2", rationale: "Reviewed AI draft", itemAttributions: [{ itemId: "item-1", aiGenerated: true, acceptingUserId: "alice", conversationId: "conversation-1", runId: "run-2" }], diff: [{ kind: "Added", subject: "Receivable Account", predicate: "type", objectValue: "Class", description: "Added class Receivable Account." }], analysisReferenceIds: ["analysis-ref"], reviewRoute: "/projects/simple/changes?proposalId=proposal-ai-1",
    });
    if (path.endsWith("/staged") && request.method() === "GET") return json(route, { apiVersion: "v1", projectId: "simple", status: "READY", entries: stagedEntries, proposal: currentProposal });
    if (path.endsWith("/staged") && request.method() === "POST") {
      const body = request.postDataJSON() as Record<string, string>;
      const editType = body.editType;
      stagedEditTypes.push(editType);
      const label = body.label ?? body.classLabel ?? body.propertyLabel ?? "Untitled change";
      const classIri = body.classIri ?? `https://example.com/entio/simple#${label.replace(/\s+/g, "")}`;
      const superclassIri = body.superclassLabel ? `https://example.com/entio/simple#${body.superclassLabel.replace(/\s+/g, "")}` : undefined;
      stagedEntries.push({
        id: `stage-${stagedEntries.length + 1}`, order: stagedEntries.length + 1, sourceId: "simple", summary: `${editType} · ${label}`, editType: "TypedEdit", status: "STAGED", authorId: "bob", latestEditorId: "bob", comment: null, aiGenerated: false,
        normalizedValues: { ...body, classIri, ...(superclassIri ? { superclassIri } : {}) }, generatedIris: editType === "create-class" ? [classIri] : [], validationMessages: [],
      });
      currentProposal = null;
      return json(route, {
        apiVersion: "v1", projectId: "simple", status: "READY", entries: stagedEntries, proposal: null,
      });
    }
    if (path.endsWith("/proposal/preview")) {
      currentProposal = { id: "proposal-1", status: "READYFORREVIEW", stagedChangeIds: stagedEntries.map((entry) => entry.id), baselineProjectFingerprint: "base", validationMessages: [], validationIssues: [], diff: [
        { kind: "Added", subject: "https://example.com/entio/simple#CommercialCustomer", predicate: "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", objectValue: "http://www.w3.org/2002/07/owl#Class", description: "Added triple (raw RDF)." },
        { kind: "Added", subject: "https://example.com/entio/simple#CommercialCustomer", predicate: "http://www.w3.org/2000/01/rdf-schema#label", objectValue: "\"Commercial Customer\"^^http://www.w3.org/2001/XMLSchema#string", description: "Added triple (raw RDF)." },
        { kind: "Added", subject: "https://example.com/entio/simple#CommercialCustomer", predicate: "http://www.w3.org/2000/01/rdf-schema#subClassOf", objectValue: "https://example.com/entio/simple#Customer", description: "Added triple (raw RDF)." },
      ], message: "Proposal ready for review." };
      return json(route, { apiVersion: "v1", projectId: "simple", status: "READY", entries: stagedEntries, proposal: currentProposal });
    }
    if (path.endsWith("/proposal/approve")) {
      approvingUser = request.headers()["x-entio-user"];
      currentProposal = { id: "proposal-1", status: "APPROVED", stagedChangeIds: stagedEntries.map((entry) => entry.id), baselineProjectFingerprint: "base", validationMessages: [], validationIssues: [], diff: [], message: "Approved." };
      return json(route, { apiVersion: "v1", projectId: "simple", status: "READY", entries: stagedEntries, proposal: currentProposal });
    }
    if (path.endsWith("/proposal/apply")) {
      proposalApplied = true;
      stagedEntries.length = 0;
      currentProposal = { id: "proposal-1", status: "APPLIED", stagedChangeIds: [], baselineProjectFingerprint: "base", validationMessages: [], validationIssues: [], diff: [], message: "Applied and reloaded." };
      return json(route, { apiVersion: "v1", projectId: "simple", status: "READY", entries: stagedEntries, proposal: currentProposal });
    }
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
  await expect(page.getByRole("textbox", { name: "Definition" })).toHaveValue("A customer.");
  await expect(page).toHaveScreenshot("workbench-light.png", { fullPage: true, mask: [page.locator(".collaboration-presence")], maskColor: "#ffffff" });

  const projectNavigation = page.getByRole("complementary", { name: "Project navigation" });
  await projectNavigation.getByRole("textbox", { name: "Search entities by label" }).fill("Customer");
  await expect(projectNavigation.getByRole("button", { name: /Shrey/ })).toBeVisible();
  await expect(projectNavigation.getByText("Object · Asserted Type")).toBeVisible();
  await expect(projectNavigation.getByRole("tab", { name: /Classes/ })).toHaveCount(0);
  await expect(page).toHaveScreenshot("semantic-search-light.png", { fullPage: true, mask: [page.locator(".collaboration-presence")], maskColor: "#ffffff" });
  await projectNavigation.getByRole("button", { name: "Clear search" }).click();
  await expect(projectNavigation.getByRole("tab", { name: /Classes/ })).toBeVisible();
  await projectNavigation.getByRole("tab", { name: /Properties/ }).click();
  const propertyNode = projectNavigation.getByRole("button", { name: "received invoice, Object property" });
  await propertyNode.click();
  await expect(page.getByRole("heading", { name: "received invoice" })).toBeVisible();
  await page.getByRole("tab", { name: "Schema" }).click();
  await expect(page.getByRole("combobox", { name: "Set domain" })).toHaveAttribute("placeholder", "Search existing or staged classes");
  await expect(page.getByRole("combobox", { name: "Set range" })).toHaveAttribute("placeholder", "Search existing or staged classes");
  await expect(page.getByRole("tab", { name: "Relationships" })).toHaveCount(0);
  await page.getByRole("tab", { name: "Constraint usage" }).click();
  await expect(page.getByRole("button", { name: "Add constraint" })).toBeVisible();
  await page.getByRole("button", { name: "Add constraint" }).click();
  const constraintDialog = page.getByRole("dialog", { name: "Add constraint" });
  await expect(constraintDialog.getByRole("textbox", { name: "Shape label" })).toBeVisible();
  await expect(constraintDialog.getByRole("combobox", { name: "Target class" })).toBeVisible();
  await expect(page.getByText("Property path", { exact: true })).toBeVisible();
  await expect(page.getByText("received invoice", { exact: true }).last()).toBeVisible();
  await constraintDialog.getByRole("button", { name: "Cancel" }).click();
  await propertyNode.click({ button: "right" });
  await expect(page.getByRole("menuitem", { name: "Add subclass" })).toHaveCount(0);
  await page.getByRole("menuitem", { name: "Edit details" }).click();
  await expect(page.getByRole("heading", { name: "received invoice" })).toBeVisible();
  await projectNavigation.getByRole("tab", { name: /Classes/ }).click();

  await page.getByRole("button", { name: "Expand navigation" }).click();
  await expect(page.getByRole("tab", { name: "Explore", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Account", exact: true }).click();
  await expect(page.getByText("Local reviewer", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Open settings" }).click();
  await expect(page.getByRole("tab", { name: "Settings", exact: true })).toHaveAttribute("aria-selected", "true");
  await expect(page.getByRole("heading", { name: "Settings", exact: true })).toBeVisible();
  await page.getByRole("textbox", { name: "Display name" }).fill("Shrey Sharma");
  await page.getByRole("button", { name: "Save name" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Display name updated." })).toBeVisible();
  await expect(page.getByText("Provider credential", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Assistant", exact: true }).click();
  await expect(page.getByRole("complementary", { name: "Entio AI assistant" })).toBeVisible();
  const settingsScroll = page.locator(".workspace-content");
  await expect.poll(() => settingsScroll.evaluate((element) => element.scrollHeight > element.clientHeight)).toBe(true);
  await settingsScroll.evaluate((element) => element.scrollTo({ top: element.scrollHeight }));
  await expect.poll(() => settingsScroll.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
  await expect(page.getByRole("button", { name: "Save credential" })).toBeVisible();
  await page.getByRole("button", { name: "Close assistant" }).click();
  await page.reload();
  await expect(page.getByRole("heading", { name: "simple-ontology" })).toBeVisible();
  await page.getByRole("button", { name: "Expand navigation" }).click();
  await page.getByRole("button", { name: "Account", exact: true }).click();
  await expect(page.getByText("Shrey Sharma", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Account", exact: true }).click();
  await page.getByRole("tab", { name: "Explore", exact: true }).click();

  const customerNode = projectNavigation.getByRole("button", { name: "Customer, Class", exact: true });
  await customerNode.click();
  await customerNode.click({ button: "right" });
  await expect(page.getByRole("menuitem", { name: "Add subclass" })).toBeVisible();
  await expect(page.getByRole("menuitem", { name: "Edit details" })).toBeVisible();
  await expect(page.getByRole("menuitem", { name: "Copy IRI" })).toBeVisible();
  await page.getByRole("menuitem", { name: "Delete" }).click();
  await expect(page.getByRole("heading", { name: "Delete Customer" })).toBeVisible();
  await expect(page.getByText("Shrey · type · Customer", { exact: true })).toBeVisible();
  await page.getByRole("checkbox").check();
  await expect(page.getByRole("button", { name: "Stage deletion" })).toBeEnabled();
  await page.getByRole("button", { name: "Cancel" }).click();
  await customerNode.click({ button: "right" });
  await page.getByRole("menuitem", { name: "Add subclass" }).click();
  await expect(page.getByRole("heading", { name: "Add subclass of Customer" })).toBeVisible();
  await expect(page.getByRole("list", { name: "Selected superclass" })).toContainText("Customer");
  await expect(page.getByRole("combobox", { name: "Superclass" })).toHaveValue("");
  await page.getByRole("textbox", { name: "Class label", exact: true }).fill("Commercial Customer");
  await page.getByRole("button", { name: "Stage class" }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.getByText("2 changes staged", { exact: true })).toBeVisible();
  const expandCustomer = projectNavigation.getByRole("button", { name: "Expand Customer" });
  await expandCustomer.click();
  const stagedSubclass = projectNavigation.getByRole("button", { name: "Commercial Customer, Class" });
  await expect(stagedSubclass).toBeVisible();
  await expect(projectNavigation.getByText("Loading children...", { exact: true })).toHaveCount(0);
  await expect(stagedSubclass.locator("..")).toHaveClass(/entity-staged/);
  const parentBox = await customerNode.boundingBox();
  const childBox = await stagedSubclass.boundingBox();
  expect(parentBox).not.toBeNull();
  expect(childBox).not.toBeNull();
  expect(childBox!.x).toBeGreaterThan(parentBox!.x);
  await projectNavigation.getByRole("button", { name: "Collapse Customer" }).click();
  await expect(stagedSubclass).toBeHidden();
  await projectNavigation.getByRole("button", { name: "Expand Customer" }).click();
  await expect(stagedSubclass).toBeVisible();
  await stagedSubclass.click();
  const stagedDetails = page.locator(".entity-details");
  await expect(stagedDetails.getByRole("heading", { name: "Commercial Customer" })).toBeVisible();
  await expect(stagedDetails.getByText("Staged", { exact: true })).toBeVisible();
  await expect(stagedDetails.getByRole("textbox", { name: "Preferred label" })).toHaveValue("Commercial Customer");
  await stagedDetails.getByRole("tab", { name: "Hierarchy" }).click();
  await expect(stagedDetails.getByRole("heading", { name: "Superclass" }).locator("..")).toContainText("Customer");
  await expect(page.getByRole("tab", { name: "Commercial Customer", exact: true })).toHaveCSS("background-color", "rgb(255, 251, 235)");
  await expect(page.getByRole("tab", { name: "Customer", exact: true }).locator("..")).toHaveClass(/entity-staged/);
  expect(stagedEditTypes).toEqual(["create-class", "add-superclass"]);
  await page.getByRole("tab", { name: "Proposal" }).click();
  await expect(page.getByRole("tab", { name: "Customer", exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Shared staged changes")).toHaveCount(0);
  await expect(page.getByText("Commercial Customer", { exact: true }).first()).toBeVisible();
  await expect(page.getByRole("heading", { name: "Review queue" })).toBeVisible();
  await expect(page.getByText(/Proposal ready for review/i)).toBeVisible();
  await expect(page.getByRole("button", { name: "Remove" }).first()).toBeEnabled();
  await expect(page.getByText("Updating staged changes...", { exact: true })).toHaveCount(0);
  const proposalReview = page.locator(".proposal-summary");
  await expect(proposalReview.getByText("Added · Commercial Customer · type · Class", { exact: true })).toBeVisible();
  await expect(proposalReview.getByText("Added · Commercial Customer · superclass · Customer", { exact: true })).toBeVisible();
  await expect(proposalReview).not.toContainText("https://");
  await page.getByRole("button", { name: "Accept" }).click();
  const appliedNotification = page.getByRole("status").filter({ hasText: "Proposal accepted and applied" });
  await expect(appliedNotification).toBeVisible();
  await expect(page.getByText("Applied", { exact: true })).toHaveCount(0);
  await expect(page.getByText("Updating staged changes...", { exact: true })).toHaveCount(0);
  await expect(page.getByText("No staged changes", { exact: true })).toBeVisible();
  await expect(projectNavigation.getByRole("button", { name: "Commercial Customer, Class" })).toBeVisible();
  await expect.poll(() => hierarchyRequests).toBeGreaterThan(1);
  await expect(appliedNotification).toBeHidden({ timeout: 6_000 });
  expect(approvingUser).toBe("bob");

  await page.getByRole("button", { name: "Assistant" }).click();
  await expect(page.getByRole("complementary", { name: "Entio AI assistant" })).toBeVisible();
  await expect(page.getByRole("tab", { name: "Proposal", exact: true })).toHaveAttribute("aria-selected", "true");
  await expect(page.getByText("Ready when you are")).toBeVisible();
  await page.getByLabel("Ask about this ontology context").fill("Explain Customer.");
  await page.getByRole("button", { name: "Send" }).click();
  await expect(page.getByText("Customer is an asserted class in the selected project.")).toBeVisible();
  await expect(page.getByText("Entity inspection completed.")).toBeVisible();
  await page.getByLabel("Ask about this ontology context").fill("Design an ontology for receivables.");
  await page.getByRole("button", { name: "Send" }).click();
  await expect(page.getByRole("heading", { name: "Confirm the plan" })).toBeVisible();
  await page.getByRole("button", { name: "Confirm plan" }).click();
  await expect(page.getByRole("heading", { name: "Private draft" })).toBeVisible();
  await expect(page.getByText("Create Receivable Account")).toBeVisible();
  await page.getByRole("button", { name: "Run deterministic analysis" }).click();
  await expect(page.getByText("Ready for human review.")).toBeVisible();
  await page.getByRole("button", { name: "Submit for human review" }).click();
  await expect(page.getByRole("link", { name: "Open proposal review" })).toHaveAttribute("href", "/projects/simple/changes?proposalId=proposal-ai-1");

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

function aiConversation(messages: unknown[], currentDraftId: string | null = null) {
  return { id: "conversation-1", projectId: "simple", messages, currentDraftId, modelId: "gpt-5.2", status: "ACTIVE", createdAt: "2026-07-17T12:00:00Z", updatedAt: "2026-07-17T12:00:01Z" };
}

function aiMessage(id: string, role: "USER" | "ASSISTANT", content: string, operation: string | null = null, evidenceReferenceIds: string[] = []) {
  return { id, role, content, operation, evidenceReferenceIds, createdAt: "2026-07-17T12:00:00Z" };
}

function aiTurn({ runId, status = "READY_FOR_REVIEW", answer, messages = [], plan = null, draftId = null }: { runId: string; status?: string; answer: string; messages?: unknown[]; plan?: unknown; draftId?: string | null }) {
  return { apiVersion: "v1", conversation: aiConversation(messages, draftId), run: { id: runId, conversationId: "conversation-1", projectId: "simple", status, capabilityCallCount: 1, draftEditCount: draftId ? 1 : 0, correctionCycleCount: 0, cancellationRequested: false, createdAt: "2026-07-17T12:00:00Z", updatedAt: `${Date.now()}` }, intent: plan ? "BROAD_PLAN" : draftId ? "SMALL_EDIT" : "EXPLANATION", answer, plan, clarificationQuestion: null, draftId, limits: [] };
}

function aiDraft(status: string) {
  return { id: "draft-1", conversationId: "conversation-1", projectId: "simple", baselineFingerprint: "baseline", allowedSourceIds: ["simple"], status, draftFingerprint: "draft-fingerprint", analysisReferenceIds: [], items: [{ id: "item-1", order: 1, capabilityName: "entio_draft_add_ontology_edit", targetSourceId: "simple", summary: "Create Receivable Account", rationale: "Represent the reviewed accounting concept.", dependencyItemIds: [], aiGenerated: true, acceptingUserId: "alice", runId: "run-2" }], revisions: [{ revision: 1, action: "ADD", explanation: "Prepared typed class edit.", itemIds: ["item-1"], undoneRevision: null, createdAt: "2026-07-17T12:00:00Z" }], createdAt: "2026-07-17T12:00:00Z", updatedAt: "2026-07-17T12:00:01Z" };
}

function aiAnalysis() {
  return { id: "analysis-1", draftId: "draft-1", revision: 1, status: "COMPLETED", baselineFingerprint: "baseline", draftFingerprint: "draft-fingerprint", previewGraphFingerprint: "preview-fingerprint", readyForReview: true, validationOk: true, findings: [], diff: [{ kind: "ADDED", subject: "Receivable Account", predicate: "type", objectValue: "Class", description: "Added class Receivable Account." }], references: [{ stage: "VALIDATION", id: "analysis-ref" }], createdAt: "2026-07-17T12:00:00Z" };
}
