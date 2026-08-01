import { expect, test, type Route } from "@playwright/test";

test("document ingestion completes the accessible review and proposal workflow", async ({ page }) => {
  await page.setViewportSize({ width: 1536, height: 864 });
  let uploaded = false;
  let accepted = false;
  let staged = false;
  let proposalStatus: "READYFORREVIEW" | "APPROVED" | "APPLIED" | null = null;
  let multipartBody = "";

  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith("/summary")) return json(route, {
      apiVersion: "v1",
      project: { id: "simple", displayName: "Simple ontology", name: "simple-ontology" },
      sources: [{ id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], tripleCount: 3 }],
      symbolCount: 2,
      graphTripleCount: 3,
    });
    if (path.endsWith("/sources")) return json(route, {
      items: [{ id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], writable: true }],
      offset: 0, limit: 50, total: 1, nextOffset: null,
    });
    if (path.endsWith("/hierarchy")) return json(route, {
      apiVersion: "v1", sourceId: "simple", parentIri: null,
      page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null },
    });
    if (path.endsWith("/outline")) return json(route, {
      apiVersion: "v1", sourceId: "simple",
      page: { items: [], offset: 0, limit: 100, total: 0, nextOffset: null },
    });
    if (path.endsWith("/staged") && request.method() === "GET") return json(route, staging(staged, proposalStatus));
    if (path.endsWith("/proposal/preview")) {
      proposalStatus = "READYFORREVIEW";
      return json(route, staging(staged, proposalStatus));
    }
    if (path.endsWith("/proposal/approve")) {
      proposalStatus = "APPROVED";
      return json(route, staging(staged, proposalStatus));
    }
    if (path.endsWith("/proposal/apply")) {
      proposalStatus = "APPLIED";
      staged = false;
      return json(route, staging(staged, proposalStatus));
    }
    if (path.endsWith("/document-ingestion/tasks") && request.method() === "POST") {
      multipartBody = request.postData() ?? "";
      uploaded = true;
      return json(route, task);
    }
    if (path.endsWith("/document-ingestion/tasks")) {
      return json(route, {
        items: uploaded ? [task] : [],
        offset: 0, limit: 50, total: uploaded ? 1 : 0, nextOffset: null,
      });
    }
    if (path.includes("/evidence/")) return json(route, evidence);
    if (path.includes("/recommendations/") && path.endsWith("/decision")) {
      const body = request.postDataJSON() as { action: string; clarification?: string };
      expect(body.action).toBe("accept");
      expect(body.clarification).toBe("The later amendment governs.");
      accepted = true;
      return json(route, reviewWorkspace(accepted));
    }
    if (path.endsWith("/review")) return json(route, reviewWorkspace(accepted));
    if (path.endsWith("/draft")) {
      staged = true;
      return json(route, {
        apiVersion: "v1",
        staging: staging(staged, proposalStatus),
        batchCount: 1,
        stagedEditCount: 1,
        confirmCount: 0,
      });
    }
    return json(route, { apiVersion: "v1", page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
  });

  await page.goto("/projects/simple");
  await page.getByRole("tab", { name: "Documents" }).click();
  await expect(page.getByRole("tabpanel", { name: "Documents workspace" })).toBeVisible();
  const documentsIcon = page.getByRole("tab", { name: "Documents" }).locator(".ui-icon");
  const activityIcon = page.getByRole("tab", { name: "Activity" }).locator(".ui-icon");
  await expect(documentsIcon).toHaveText("▤");
  await expect(documentsIcon).not.toHaveText(await activityIcon.textContent() ?? "");

  const assistantToggle = page.getByRole("button", { name: /Entio AI/ });
  if (await assistantToggle.getAttribute("aria-expanded") === "false") await assistantToggle.click();
  const assistant = page.getByRole("complementary", { name: "Entio AI assistant" });
  await expect(assistant).toBeVisible();
  await expect.poll(async () => page.locator(".document-workspace").evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true);
  const uploadCardBounds = await page.locator(".document-upload-card").boundingBox();
  const assistantBounds = await assistant.boundingBox();
  expect(uploadCardBounds!.x + uploadCardBounds!.width).toBeLessThanOrEqual(assistantBounds!.x + 1);

  const input = page.locator('input[type="file"]');
  await input.setInputFiles([
    file("text.pdf", "%PDF-1.7 embedded text", "application/pdf"),
    file("scan.pdf", "%PDF-1.7 scanned page", "application/pdf"),
    file("mixed.pdf", "%PDF-1.7 embedded and scanned pages", "application/pdf"),
    file("policy.docx", "PK\u0003\u0004mock-docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    file("policy.txt", "Supplier policy", "text/plain"),
    file("amendment.md", "# Supplier amendment", "text/markdown"),
  ]);
  await page.getByLabel("Authority").selectOption("Authoritative");
  await page.getByLabel("Business area").fill("Procurement");
  await page.getByRole("button", { name: "Upload and analyze" }).press("Enter");

  await expect(page.getByRole("heading", { name: "Ontology structure" })).toBeVisible();
  await expect.poll(async () =>
    page.locator(".document-workspace").evaluate((element) => element.scrollWidth <= element.clientWidth),
  ).toBe(true);
  await expect.poll(async () =>
    page.locator(".document-review-region").evaluate((element) => element.scrollWidth <= element.clientWidth),
  ).toBe(true);
  for (const filename of ["text.pdf", "scan.pdf", "mixed.pdf", "policy.docx", "policy.txt", "amendment.md"]) {
    expect(multipartBody).toContain(filename);
  }
  await page.getByLabel("Supplier recommendation details").click();
  await expect(page.getByText("Conflicting evidence needs a decision")).toBeVisible();
  await expect(page.getByText("Earlier Entio records: applied-document-change-1")).toBeVisible();
  await expect(page.getByRole("region", { name: "Exact proposed changes" })).toContainText("Add definition");

  const evidenceButton = page.getByRole("button", { name: "Open Explicit evidence" });
  await evidenceButton.focus();
  await evidenceButton.press("Enter");
  const dialog = page.getByRole("dialog", { name: "Evidence" });
  await expect(dialog.getByRole("heading", { name: "Evidence" })).toBeFocused();
  await expect(dialog.getByLabel("Extracted evidence text")).toContainText("Supplier");
  await dialog.getByRole("button", { name: "Close evidence viewer" }).press("Enter");

  await page.getByText("Review options and technical details").click();
  await page.getByLabel("Reviewer note").fill("The later amendment governs.");
  const accept = page.getByRole("button", { name: "Approve for proposal" });
  await accept.focus();
  await accept.press("Enter");
  await expect(page.getByRole("status").filter({
    hasText: "Approved for proposal. Use “Add accepted items to proposal” above to continue.",
  })).toBeVisible();
  await expect(page.getByRole("button", { name: "Approve for proposal" })).toHaveCount(0);
  await expect(page.getByText(
    "1 accepted · 1 ready to approve · 0 need input · 0 matched · 0 unsafe",
  )).toBeVisible();
  await page.getByRole("button", { name: "Add accepted items to proposal" }).press("Enter");
  await expect(page.getByRole("status").filter({ hasText: "1 typed edit added" })).toBeVisible();
  await expect(page.getByLabel("Shared staged changes")).toContainText("1 change staged");

  await page.getByRole("button", { name: "Review proposal" }).press("Enter");
  await expect(page.getByRole("region", { name: "Review queue" })).toBeVisible();
  await expect(page.getByText("Proposal ready for review.")).toBeVisible();
  await page.getByRole("button", { name: "View Details" }).press("Enter");
  const proposal = page.getByRole("dialog", { name: "View Details" });
  await expect(proposal.getByLabel("Document recommendation provenance")).toContainText("Accepted document recommendation");
  await expect(proposal.getByLabel("Document recommendation provenance")).toContainText("1 verified reference");
  await proposal.getByRole("button", { name: "Accept" }).press("Enter");
  await expect(page.getByRole("status").filter({ hasText: "Proposal accepted and applied" })).toBeVisible();
  expect(proposalStatus).toBe("APPLIED");
});

function file(name: string, contents: string, mimeType: string) {
  return { name, mimeType, buffer: Buffer.from(contents) };
}

const task = {
  taskId: "task-document-e2e",
  projectId: "simple",
  ownerUserId: "alice",
  status: "awaiting-review",
  createdAt: "2026-07-24T12:00:00Z",
  updatedAt: "2026-07-24T12:01:00Z",
  documents: [{ documentId: "document-amendment", safeFilename: "commercial-account-and-payment-authorization-policy.md", mediaType: "markdown", byteSize: 20, checksumSha256: "a".repeat(64), authorityStatus: "authoritative", status: "awaiting-review" }],
  progress: { stage: "awaiting-review", completedDocuments: 6, totalDocuments: 6, percent: 100, message: "Evidence-linked recommendations are ready for review." },
};

function reviewWorkspace(accepted: boolean) {
  return {
    apiVersion: "v1",
    taskId: task.taskId,
    projectId: "simple",
    exactWorkKey: "document-work-key",
    graphFingerprint: "graph-fingerprint",
    documents: [{ documentId: "document-amendment", safeFilename: "commercial-account-and-payment-authorization-policy.md", mediaType: "markdown", authorityStatus: "authoritative", pageCount: null, warningCount: 0 }],
    summaries: [{ documentId: "document-amendment", purpose: "Revises the supplier definition.", highlights: ["Supplier"] }],
    recommendations: {
      items: [{
        id: "recommendation-supplier",
        category: "OntologyStructure",
        type: "Class",
        action: "Extend",
        proposedLabel: "Supplier",
        description: "The document adds a definition to the existing Supplier class.",
        changePreview: {
          draftable: true,
          summary: "1 exact change will be added to the proposal.",
          operations: [{
            operation: "Add definition",
            description: "Add the amendment definition to https://example.com/entio/simple#CommercialAccountPaymentAuthorizationSupplier.",
            targetSourceId: "simple",
          }],
          blockingReason: null,
        },
        confidence: 94,
        confidenceBand: "High",
        rationale: "The amendment explicitly revises Supplier.",
        reviewStatus: accepted ? "Accepted" : "Pending",
        evidence: [{ evidenceId: "evidence-supplier", evidenceType: "Explicit", documentId: "document-amendment", pageNumber: null, extractionMethod: "Markdown", ocrConfidence: null, excerpt: "Supplier", priorRecordId: null }],
        matches: [{ scope: "AppliedLocal", entityIri: "https://example.com/simple#Supplier", sourceId: "simple", preferredLabel: "Supplier", score: 100, reason: "Exact label." }],
        selectedMatchIri: "https://example.com/simple#Supplier",
        conflicts: [{ id: "conflict-amendment", alternatives: ["Earlier policy", "Later amendment"], affectedEntityIris: ["https://example.com/simple#Supplier"], resolutionOptions: ["retain", "revise"] }],
        mandatoryClarificationReasons: ["Choose which document governs."],
        clarification: accepted ? "The later amendment governs." : null,
        targetSourceId: "simple",
        reconsiderationCount: 0,
        priorWorkflowProvenance: ["applied-document-change-1"],
      }],
      offset: 0, limit: 100, total: 1, nextOffset: null,
    },
    draftImpact: {
      acceptedCount: accepted ? 1 : 0,
      pendingCount: accepted ? 0 : 1,
      blockedCount: 0,
      executableCount: 1,
      needsInputCount: 0,
      reviewOnlyCount: 0,
      readOnly: true,
    },
  };
}

const evidence = {
  apiVersion: "v1",
  evidenceId: "evidence-supplier",
  documentId: "document-amendment",
  safeFilename: "amendment.md",
  pageNumber: null,
  sectionHeading: "Supplier amendment",
  extractionMethod: "Markdown",
  ocrConfidence: null,
  text: "The Supplier definition is revised by this amendment.",
  highlightStart: 4,
  highlightEnd: 12,
  pageImageAvailable: false,
  truncated: false,
};

function staging(staged: boolean, proposalStatus: "READYFORREVIEW" | "APPROVED" | "APPLIED" | null) {
  const entries = staged ? [{
    id: "stage-document-1",
    order: 1,
    sourceId: "simple",
    summary: "edit-label · Supplier",
    editType: "TypedEdit",
    status: "STAGED",
    authorId: "alice",
    latestEditorId: "alice",
    comment: null,
    normalizedValues: { operation: "changed", subjectLabel: "Supplier", predicateLabel: "label", objectLabel: "Supplier" },
    generatedIris: [],
    validationMessages: [],
    documentDraftProvenance: {
      taskId: task.taskId,
      recommendationId: "recommendation-supplier",
      documentIds: ["document-amendment"],
      evidenceIds: ["evidence-supplier"],
      confidence: 94,
      confidenceBand: "High",
      authorityStatuses: ["Authoritative"],
      modelId: "gpt-test",
      promptVersion: "phase-11-document-analysis-v1",
      targetSourceId: "simple",
    },
  }] : [];
  const proposal = proposalStatus ? {
    id: "proposal-document-1",
    status: proposalStatus,
    stagedChangeIds: entries.map((entry) => entry.id),
    baselineProjectFingerprint: "base",
    validationMessages: [],
    validationIssues: [],
    diff: proposalStatus === "APPLIED" ? [] : [{
      kind: "Added",
      subject: "https://example.com/simple#Supplier",
      predicate: "http://www.w3.org/2000/01/rdf-schema#label",
      objectValue: "\"Supplier\"",
      description: "Updated supplier label.",
    }],
    message: proposalStatus === "READYFORREVIEW" ? "Proposal ready for review." : "Applied and reloaded.",
  } : null;
  return { apiVersion: "v1", projectId: "simple", status: "READY", entries, proposal };
}

function json(route: Route, body: unknown) {
  return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
}
