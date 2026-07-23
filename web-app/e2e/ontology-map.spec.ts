import { expect, test, type Route } from "@playwright/test";

test("ontology map remains bounded, accessible, interactive, and read-only", async ({ page }) => {
  const graphMethods: string[] = [];
  const fixture = graphFixture(24, 40);
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    if (path.includes("/graph")) graphMethods.push(request.method());
    if (path === "/api/v1/projects/simple/graph") return json(route, { ...fixture, graphFingerprint: "fingerprint-one", totalNodeCount: 1_000, totalEdgeCount: 999, continuation: "opaque-next" });
    if (path.endsWith("/graph/neighborhood")) return route.fulfill({ status: 409, contentType: "application/json", body: JSON.stringify({ apiVersion: "v1", requestId: "safe", code: "stale-graph-fingerprint", message: "Refresh before continuing." }) });
    if (path.endsWith("/summary")) return json(route, { apiVersion: "v1", project: { id: "simple", displayName: "Simple", name: "scale-fixture" }, sources: [{ id: "simple", path: "hidden.ttl", format: "turtle", roles: ["ontology"], tripleCount: 1_000 }], symbolCount: 1_000, graphTripleCount: 1_999 });
    if (path.endsWith("/hierarchy")) return json(route, { apiVersion: "v1", sourceId: "simple", parentIri: null, page: { items: [{ iri: "urn:n0", label: "Entity 0000", kind: "Class", sourceId: "simple", childCount: 1 }], offset: 0, limit: 50, total: 1, nextOffset: null } });
    if (path.endsWith("/outline")) return json(route, { apiVersion: "v1", sourceId: "simple", page: { items: fixture.nodes.slice(0, 4).map((node) => ({ iri: node.identity.entityIri, label: node.label, kind: node.kind, sourceId: "simple" })), offset: 0, limit: 100, total: 4, nextOffset: null } });
    if (path.endsWith("/entities")) return json(route, { apiVersion: "v1", iri: url.searchParams.get("iri"), label: "Entity 0000", kind: "Class", sourceId: "simple", sourceOntologyId: "simple", locality: "Local", preferredLabelSource: "RdfsLabel", alternateLabels: [], definitions: [], annotations: [], directSuperclasses: [], directSubclasses: [], directlyTypedIndividuals: [], assertedTypes: [], domains: [], ranges: [], outgoingRelationships: [], incomingRelationships: [] });
    if (path.endsWith("/staged")) return json(route, { apiVersion: "v1", projectId: "simple", status: "READY", entries: [], proposal: null });
    if (path.endsWith("/activity")) return json(route, { events: [], truncated: false });
    return json(route, { apiVersion: "v1", page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
  });

  await page.goto("/projects/simple");
  await page.getByRole("button", { name: "View Map" }).first().click();
  await expect(page.getByRole("tab", { name: "Ontology map" })).toHaveCount(1);
  await expect(page.getByRole("button", { name: "Class: Entity 0000" })).toBeVisible();
  await expect(page.getByRole("button", { name: "ObjectProperty: Entity 0005" })).toBeVisible();
  await expect(page.getByRole("button", { name: "DatatypeProperty: Entity 0002" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Individual: Entity 0003" })).toHaveCount(0);
  await expect(page.getByLabel("Loaded ontology graph").locator("text=SubclassOf").first()).toBeAttached();
  await expect(page.getByText("24 loaded entities")).toBeVisible();
  await expect(page.getByRole("button", { name: "Class: Entity 0000" })).toBeInViewport();
  await expect(page.locator(".ontology-graph-viewport")).toHaveScreenshot("ontology-map-prototype-layout.png");
  const browserZoomBefore = await page.evaluate(() => ({ scale: window.visualViewport?.scale ?? 1, devicePixelRatio: window.devicePixelRatio }));
  const mapPinchZoomBefore = Number((await page.getByLabel("Zoom percentage").textContent())?.replace("%", ""));
  await page.locator(".ontology-graph-viewport").hover();
  await page.keyboard.down("Control");
  await page.mouse.wheel(0, -120);
  await page.keyboard.up("Control");
  await expect.poll(async () => Number((await page.getByLabel("Zoom percentage").textContent())?.replace("%", ""))).toBeGreaterThan(mapPinchZoomBefore);
  expect(await page.evaluate(() => ({ scale: window.visualViewport?.scale ?? 1, devicePixelRatio: window.devicePixelRatio }))).toEqual(browserZoomBefore);
  await page.getByLabel("Filter project outline and map").click();
  await page.getByRole("checkbox", { name: "Individuals" }).check();
  await expect(page.getByRole("button", { name: "Individual: Entity 0003" })).toBeVisible();
  await expect(page.getByRole("tab", { name: /Objects 1/ })).toBeVisible();
  await page.getByLabel("Filter project outline and map").click();
  const node = page.getByRole("button", { name: "Class: Entity 0000" });
  await node.click();
  const popup = page.getByRole("dialog", { name: "Entity 0000 map summary" });
  await expect(popup.getByRole("heading", { name: "Entity 0000" })).toBeVisible();
  await expect(popup).toContainText("Class · Asserted");
  await expect(popup.getByRole("heading", { name: "Details" })).toBeVisible();
  await expect(popup).toContainText("Direct subclasses: 6");
  await expect(popup).toContainText("Loaded relationships: 1");
  await expect(popup).toContainText("Available relationships: 2");
  await expect(popup.getByRole("button", { name: /edit/i })).toHaveCount(0);
  await expect(popup.getByRole("button")).toHaveCount(2);
  await expect(popup).toHaveScreenshot("ontology-map-popup.png");
  const popupBeforeDrag = await popup.boundingBox();
  const popupHandle = await popup.locator("header").boundingBox();
  await page.mouse.move(popupHandle!.x + 20, popupHandle!.y + 12);
  await page.mouse.down();
  await page.mouse.move(popupHandle!.x - 60, popupHandle!.y + 52, { steps: 4 });
  await page.mouse.up();
  await expect.poll(async () => (await popup.boundingBox())?.x).toBeLessThan(popupBeforeDrag!.x);
  await page.locator(".ontology-graph-controls").click();
  await expect(popup).toHaveCount(0);
  await node.click();
  await expect(popup).toBeVisible();
  await node.focus();
  await page.keyboard.press("ArrowDown");
  await expect(page.locator(".ontology-node:focus")).not.toHaveAttribute("id", "ontology-node-n0");
  const zoomBefore = Number((await page.getByLabel("Zoom percentage").textContent())?.replace("%", ""));
  await page.getByRole("button", { name: "Zoom in" }).click();
  await expect.poll(async () => Number((await page.getByLabel("Zoom percentage").textContent())?.replace("%", ""))).toBe(zoomBefore + 10);
  await page.getByRole("button", { name: "Fit" }).click();
  await expect(popup).toHaveCount(0);
  await page.getByLabel("Filter project outline and map").click();
  await page.getByRole("checkbox", { name: "Individuals" }).uncheck();
  await expect(page.getByRole("button", { name: "Individual: Entity 0003" })).toHaveCount(0);
  await expect(page.getByRole("tab", { name: /Objects 0/ })).toBeVisible();
  await page.getByRole("button", { name: "Reset filters" }).click();
  await expect(page.getByRole("button", { name: "Individual: Entity 0003" })).toHaveCount(0);

  expect(graphMethods.every((method) => method === "GET")).toBe(true);
});

test("@performance production map render and popup meet five-run browser gates", async ({ page }) => {
  const fixture = graphFixture(75, 150);
  await page.route("**/api/v1/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/graph")) return json(route, { ...fixture, graphFingerprint: "performance", totalNodeCount: 1_000, continuation: "bounded" });
    if (path.endsWith("/summary")) return json(route, { apiVersion: "v1", project: { id: "simple", displayName: "Simple", name: "performance" }, sources: [{ id: "simple", path: "hidden", format: "turtle", roles: ["ontology"], tripleCount: 1_999 }], symbolCount: 1_000, graphTripleCount: 1_999 });
    if (path.endsWith("/hierarchy")) return json(route, { apiVersion: "v1", sourceId: "simple", parentIri: null, page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
    if (path.endsWith("/outline")) return json(route, { apiVersion: "v1", sourceId: "simple", page: { items: [], offset: 0, limit: 100, total: 0, nextOffset: null } });
    if (path.endsWith("/staged")) return json(route, { apiVersion: "v1", projectId: "simple", status: "READY", entries: [], proposal: null });
    return json(route, { apiVersion: "v1", page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
  });
  await page.goto("/projects/simple");
  await page.getByRole("button", { name: "View Map" }).first().click();
  await page.getByLabel("Filter project outline and map").click();
  await page.getByRole("checkbox", { name: "Individuals" }).check();
  await page.getByLabel("Filter project outline and map").click();
  await expect(page.locator(".ontology-node")).toHaveCount(75);
  await page.getByRole("button", { name: "Class: Entity 0000" }).click();
  await expect(page.getByRole("dialog", { name: "Entity 0000 map summary" })).toBeVisible();
  await page.getByRole("button", { name: "Close entity summary" }).click();
  await page.getByRole("button", { name: "Close Ontology map" }).click();
  const renderRuns: number[] = [];
  const popupRuns: number[] = [];
  for (let run = 0; run < 5; run += 1) {
    const renderStart = Date.now();
    await page.getByRole("button", { name: "View Map" }).first().click();
    await expect(page.locator(".ontology-node")).toHaveCount(75);
    renderRuns.push(Date.now() - renderStart);
    const popupStart = Date.now();
    await page.getByRole("button", { name: "Class: Entity 0000" }).click();
    await expect(page.getByRole("dialog", { name: "Entity 0000 map summary" })).toBeVisible();
    popupRuns.push(Date.now() - popupStart);
    await page.getByRole("button", { name: "Close entity summary" }).click();
    await page.getByRole("button", { name: "Close Ontology map" }).click();
  }
  const median = (values: number[]) => [...values].sort((a, b) => a - b)[2];
  console.log(`phase9-browser-render-runs-ms=${renderRuns.join(",")} median=${median(renderRuns)} worst=${Math.max(...renderRuns)}`);
  console.log(`phase9-browser-popup-runs-ms=${popupRuns.join(",")} median=${median(popupRuns)} worst=${Math.max(...popupRuns)}`);
  expect(median(renderRuns)).toBeLessThanOrEqual(500);
  expect(Math.max(...renderRuns)).toBeLessThanOrEqual(1_000);
  expect(Math.max(...popupRuns)).toBeLessThanOrEqual(100);
  await page.getByRole("button", { name: "View Map" }).first().click();
  const interactionRuns: Array<{ fps: number; worstLongTask: number }> = [];
  for (let run = 0; run < 6; run += 1) {
    const sample = await page.evaluate(() => new Promise<{ fps: number; worstLongTask: number }>((resolve) => {
      const viewport = document.querySelector<HTMLElement>(".ontology-graph-viewport")!;
      const longTasks: number[] = [];
      const observer = typeof PerformanceObserver === "undefined" ? null : new PerformanceObserver((list) => list.getEntries().forEach((entry) => longTasks.push(entry.duration)));
      observer?.observe({ entryTypes: ["longtask"] });
      let frames = 0;
      let stopped = false;
      const frame = () => {
        if (stopped) return;
        frames += 1;
        viewport.scrollLeft = frames % 2 ? 40 : 0;
        viewport.scrollTop = frames % 2 ? 40 : 0;
        requestAnimationFrame(frame);
      };
      requestAnimationFrame(frame);
      setTimeout(() => { stopped = true; observer?.disconnect(); resolve({ fps: frames, worstLongTask: Math.max(0, ...longTasks) }); }, 1_000);
    }));
    if (run > 0) interactionRuns.push(sample);
  }
  console.log(`phase9-browser-interaction-fps=${interactionRuns.map((run) => run.fps).join(",")} worst-long-task-ms=${Math.max(...interactionRuns.map((run) => run.worstLongTask)).toFixed(1)}`);
  expect(Math.min(...interactionRuns.map((run) => run.fps))).toBeGreaterThanOrEqual(50);
  expect(Math.max(...interactionRuns.map((run) => run.worstLongTask))).toBeLessThanOrEqual(100);
});

function graphFixture(nodeCount: number, edgeCount: number) {
  const kinds = ["Class", "ObjectProperty", "DatatypeProperty", "Individual"] as const;
  const nodes = Array.from({ length: nodeCount }, (_, index) => ({ identity: { id: `n${index}`, sourceId: "simple", entityIri: `urn:n${index}` }, kind: kinds[index % kinds.length], label: `Entity ${String(index).padStart(4, "0")}`, definitionExcerpt: index === 0 ? "Bounded server summary." : null, summary: { directSuperclassLabels: [], domainLabels: [], rangeLabels: [], assertedTypeLabels: [], datatypeRangeLabels: [], loadedRelationshipCount: 1, availableRelationshipCount: 2 } }));
  const classes = nodes.filter((node) => node.kind === "Class");
  const individuals = nodes.filter((node) => node.kind === "Individual");
  const coreEdges: Array<{ kind: "SubclassOf" | "Domain" | "Range" | "Type" | "ObjectAssertion"; sourceNodeId: string; targetNodeId: string; label: string; predicateIri: string | null; provenance: "Asserted" }> = [];
  classes.slice(1).forEach((node, index) => coreEdges.push({ kind: "SubclassOf", sourceNodeId: node.identity.id, targetNodeId: classes[Math.floor(index / 3)].identity.id, label: "SubclassOf", predicateIri: null, provenance: "Asserted" }));
  nodes.filter((node) => node.kind === "ObjectProperty").forEach((node, index) => {
    coreEdges.push({ kind: "Domain", sourceNodeId: node.identity.id, targetNodeId: classes[index % classes.length].identity.id, label: "domain", predicateIri: null, provenance: "Asserted" });
    coreEdges.push({ kind: "Range", sourceNodeId: node.identity.id, targetNodeId: classes[(index + 1) % classes.length].identity.id, label: "range", predicateIri: null, provenance: "Asserted" });
  });
  nodes.filter((node) => node.kind === "DatatypeProperty").forEach((node, index) => coreEdges.push({ kind: "Domain", sourceNodeId: node.identity.id, targetNodeId: classes[index % classes.length].identity.id, label: "domain", predicateIri: null, provenance: "Asserted" }));
  individuals.forEach((node, index) => {
    coreEdges.push({ kind: "Type", sourceNodeId: node.identity.id, targetNodeId: classes[index % classes.length].identity.id, label: "type", predicateIri: null, provenance: "Asserted" });
    if (index) coreEdges.push({ kind: "ObjectAssertion", sourceNodeId: individuals[index - 1].identity.id, targetNodeId: node.identity.id, label: "related to", predicateIri: "urn:predicate", provenance: "Asserted" });
  });
  const edges = Array.from({ length: edgeCount }, (_, index) => ({ ...coreEdges[index % coreEdges.length], id: `e${index}` }));
  return { apiVersion: "v1", projectId: "simple", sources: [{ id: "simple", displayName: "simple" }], loadKind: "RootOverview", seed: null, nodes, edges, limits: { nodeLimit: 75, edgeLimit: 150 }, totalNodeCount: nodeCount, totalEdgeCount: edgeCount, continuation: null, ambiguousCrossSourceRelationshipCount: 0 };
}

async function json(route: Route, body: unknown) { await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) }); }
