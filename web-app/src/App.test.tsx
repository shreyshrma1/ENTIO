import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

describe("web workbench shell", () => {
  beforeEach(() => {
    window.history.pushState({}, "", "/");
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("loads an approved project and opens a label-first entity tab", async () => {
    const writeClipboard = vi.fn(async () => undefined);
    Object.defineProperty(window.navigator, "clipboard", { configurable: true, value: { writeText: writeClipboard } });
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path === "/api/v1/projects") {
        return json({ apiVersion: "v1", projects: [{ id: "simple", displayName: "Simple ontology" }] });
      }
      if (path.endsWith("/ai/proposals")) return json([]);
      if (path.includes("/summary")) {
        return json({
          apiVersion: "v1",
          project: { id: "simple", displayName: "Simple ontology", name: "simple-ontology" },
          sources: [
            { id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], tripleCount: 4 },
            { id: "shapes", path: "ontology/shapes.ttl", format: "turtle", roles: ["shapes"], tripleCount: 0 },
          ],
          symbolCount: 2,
          graphTripleCount: 4,
        });
      }
      if (path.includes("/hierarchy")) {
        return json({ apiVersion: "v1", sourceId: "simple", parentIri: null, page: { items: [{ iri: "https://example.com/Customer", label: "Customer", kind: "class", sourceId: "simple", childCount: 0 }], offset: 0, limit: 50, total: 1, nextOffset: null } });
      }
      if (path.includes("/outline")) {
        return json({ apiVersion: "v1", sourceId: "simple", page: { items: [
          { iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple" },
          { iri: "https://example.com/Shrey", label: "Shrey", kind: "Individual", sourceId: "simple", directType: { iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple" } },
          { iri: "https://example.com/receivedInvoice", label: "received invoice", kind: "ObjectProperty", sourceId: "simple" },
        ], offset: 0, limit: 100, total: 3, nextOffset: null } });
      }
      if (path.includes("/search")) {
        return json({ apiVersion: "v1", query: "Customer", page: { items: [
          { iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple", score: 120, reason: "PreferredLabel" },
          { iri: "https://example.com/Shrey", label: "Shrey", kind: "Individual", sourceId: "simple", score: 100, reason: "AssertedType" },
        ], offset: 0, limit: 50, total: 2, nextOffset: null } });
      }
      if (path.includes("/shacl/shapes")) {
        return json({ apiVersion: "v1", projectId: "simple", shapes: [] });
      }
      if (path.includes("/entities")) {
        return json({ apiVersion: "v1", iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple", sourceOntologyId: "simple", locality: "Local", preferredLabelSource: "RdfsLabel", alternateLabels: [], definitions: [{ value: "A customer.", language: null, datatype: null }], annotations: [], directSuperclasses: [], directSubclasses: [], directlyTypedIndividuals: [], assertedTypes: [], domains: [], ranges: [], outgoingRelationships: [], incomingRelationships: [] });
      }
      return json({ apiVersion: "v1", query: "", page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
    }));

    render(<App />);

    expect(await screen.findByRole("heading", { name: "Approved projects" })).toBeInTheDocument();
    fireEvent.click(await screen.findByRole("link", { name: /Simple ontology/ }));
    expect(await screen.findByRole("heading", { name: "simple-ontology" })).toBeInTheDocument();
    expect(screen.getByRole("complementary", { name: "Entio AI assistant" })).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "AI Assistant" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Close Entio AI" }));
    expect(screen.queryByRole("complementary", { name: "Entio AI assistant" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open Entio AI" }));
    expect(screen.getByRole("complementary", { name: "Entio AI assistant" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /^Classes\s*1$/ })).toHaveAttribute("aria-selected", "true");
    fireEvent.click(screen.getByRole("tab", { name: /^Objects\s*1$/ }));
    expect(screen.getByRole("button", { name: "Collapse Customer objects" })).toHaveAttribute("aria-expanded", "true");
    expect(await screen.findByRole("button", { name: "Shrey, Object" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Collapse Customer objects" }));
    expect(screen.queryByRole("button", { name: "Shrey, Object" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: /^Properties\s*1$/ }));
    expect(screen.getByRole("button", { name: "received invoice, Object property" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: /^Objects\s*1$/ }));
    expect(screen.getByRole("button", { name: "Expand Customer objects" })).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(screen.getByRole("button", { name: "Expand Customer objects" }));
    fireEvent.click(screen.getByRole("tab", { name: /^Classes\s*1$/ }));
    fireEvent.click(await screen.findByRole("button", { name: /Customer/ }));
    expect(await screen.findByRole("heading", { name: "Customer" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Definition" })).toHaveValue("A customer.");
    expect(screen.getByRole("textbox", { name: "Preferred label" })).toHaveValue("Customer");
    expect(screen.getByRole("textbox", { name: "Definition" })).toBeInTheDocument();
    expect(screen.getByText("Technical details")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Hierarchy" }));
    expect(screen.getByRole("combobox", { name: "Direct superclass (one allowed)" })).toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "Add asserted type" })).not.toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "Schema" })).not.toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "Relationships" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Properties" }));
    expect(screen.getByRole("heading", { name: "Outgoing properties" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Outgoing" })).toHaveAttribute("aria-selected", "true");
    fireEvent.click(screen.getByRole("button", { name: "Add property" }));
    expect(screen.getByRole("dialog", { name: "Add outgoing property" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Domain class" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Property name" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Range class" })).toBeInTheDocument();
    expect(screen.getByRole("list", { name: "Selected domain class" })).toHaveTextContent("Customer");
    fireEvent.click(screen.getByRole("button", { name: "Close property dialog" }));
    fireEvent.click(screen.getByRole("tab", { name: "Incoming" }));
    expect(screen.getByRole("heading", { name: "Incoming properties" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Add property" }));
    expect(screen.getByRole("dialog", { name: "Add incoming property" })).toBeInTheDocument();
    expect(screen.getByRole("list", { name: "Selected range class" })).toHaveTextContent("Customer");
    fireEvent.click(screen.getByRole("button", { name: "Close property dialog" }));
    fireEvent.click(screen.getByRole("tab", { name: "Datatype" }));
    expect(screen.getByRole("heading", { name: "Datatype properties" })).toBeInTheDocument();
    const detailSections = screen.getByRole("tablist", { name: "Entity detail sections" });
    fireEvent.click(within(detailSections).getByRole("tab", { name: "Constraints" }));
    expect(await screen.findByText("No applicable constraints")).toBeInTheDocument();
    expect(screen.getByText("No applied SHACL shape targets this entity.")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Customer" })).toHaveAttribute("aria-selected", "true");
    fireEvent.change(screen.getByRole("textbox", { name: "Search entities by label" }), { target: { value: "Customer" } });
    expect(await screen.findByRole("heading", { name: "Search results" })).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: /^Classes\s*1$/ })).not.toBeInTheDocument();
    expect(await screen.findByRole("button", { name: /Shrey/ })).toBeInTheDocument();
    expect(screen.getByText("Object · Asserted Type")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Shrey/ }));
    const entityTabs = screen.getByRole("tablist", { name: "Open entities" });
    fireEvent.keyDown(within(entityTabs).getByRole("tab", { name: "Shrey" }), { key: "ArrowLeft", altKey: true });
    expect(within(entityTabs).getAllByRole("tab").map((tab) => tab.getAttribute("aria-label"))).toEqual(["Shrey", "Customer"]);
    fireEvent.click(screen.getByRole("button", { name: "Clear search" }));
    expect(screen.getByRole("tab", { name: /^Classes\s*1$/ })).toBeInTheDocument();
    fireEvent.contextMenu(screen.getByRole("button", { name: "Customer, Class" }));
    expect(screen.getByRole("menuitem", { name: "Edit details" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Copy IRI" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Delete" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("menuitem", { name: "Copy IRI" }));
    await vi.waitFor(() => expect(writeClipboard).toHaveBeenCalledWith("https://example.com/Customer"));
    expect(await screen.findByText("Customer IRI copied.")).toHaveAttribute("role", "status");
    fireEvent.contextMenu(screen.getByRole("button", { name: "Customer, Class" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Add subclass" }));
    expect(screen.getByRole("heading", { name: "Add subclass of Customer" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Superclass" })).toHaveValue("");
    expect(screen.getByRole("list", { name: "Selected superclass" })).toHaveTextContent("Customer");
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Expand navigation" }));
    fireEvent.click(screen.getByRole("button", { name: "Account" }));
    expect(screen.getByText("Alice Contributor")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Open settings" }));
    fireEvent.change(screen.getByRole("textbox", { name: "Display name" }), { target: { value: "Shrey Sharma" } });
    fireEvent.click(screen.getByRole("button", { name: "Save name" }));
    expect(await screen.findByText("Display name updated.")).toHaveAttribute("role", "status");
    fireEvent.click(screen.getByRole("button", { name: "Account" }));
    expect(screen.getByText("Shrey Sharma")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Validation" }));
    expect(screen.getByRole("heading", { name: "Manage constraints" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Add constraint" }));
    expect(screen.getByRole("dialog", { name: "Add constraint" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Create node shape/ })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Close constraint actions" }));
    fireEvent.click(screen.getByRole("button", { name: "Manage constraints" }));
    expect(screen.getByRole("button", { name: /View constraints/ })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /View constraints/ }));
    expect(screen.getByRole("dialog", { name: "View constraints" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Close constraint view" }));
    fireEvent.click(screen.getByRole("tab", { name: "Explore" }));
    fireEvent.click(screen.getByRole("button", { name: "Close Shrey" }));
    fireEvent.click(screen.getByRole("button", { name: "Close Customer" }));
    expect(await screen.findByRole("heading", { name: "Select an entity" })).toBeInTheDocument();
  }, 15_000);

  it("renders a clear project loading error", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("unavailable", { status: 503 })));

    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not load projects");
  });

  it("opens the proposal review queue from its authoritative route", async () => {
    window.history.pushState({}, "", "/projects/simple/changes?proposalId=proposal-1");
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.includes("/summary")) return json({
        apiVersion: "v1",
        project: { id: "simple", displayName: "Simple ontology", name: "simple-ontology" },
        sources: [{ id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], tripleCount: 4 }],
        symbolCount: 2,
        graphTripleCount: 4,
      });
      if (path.includes("/hierarchy")) return json({ apiVersion: "v1", sourceId: "simple", parentIri: null, page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
      if (path.includes("/outline")) return json({ apiVersion: "v1", sourceId: "simple", page: { items: [], offset: 0, limit: 100, total: 0, nextOffset: null } });
      if (path.endsWith("/staged")) return json({
        apiVersion: "v1",
        projectId: "simple",
        status: "READYFORREVIEW",
        entries: [{ id: "stage-1", order: 1, sourceId: "simple", summary: "add-definition · Account", editType: "add-definition", status: "STAGED", authorId: "alice", latestEditorId: "alice", comment: "Definition", normalizedValues: {}, generatedIris: [], validationMessages: [] }],
        proposal: { id: "proposal-1", status: "READYFORREVIEW", message: null, validationMessages: [], validationIssues: [], diff: [], shaclImpact: null },
      });
      throw new Error(`Unexpected request: ${path}`);
    }));

    render(<App />);

    expect(await screen.findByText("Review all staged edits together, then accept and apply them or reject the proposal.")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Proposal" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("heading", { name: "Review queue" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Accept" })).toBeInTheDocument();
  });

  it("opens a staged individual from the normal object outline", async () => {
    window.history.pushState({}, "", "/projects/simple");
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.includes("/summary")) return json({
        apiVersion: "v1",
        project: { id: "simple", displayName: "Simple ontology", name: "simple-ontology" },
        sources: [{ id: "simple", path: "ontology/simple.ttl", format: "turtle", roles: ["ontology"], tripleCount: 4 }],
        symbolCount: 2,
        graphTripleCount: 4,
      });
      if (path.includes("/hierarchy")) return json({ apiVersion: "v1", sourceId: "simple", parentIri: null, page: { items: [], offset: 0, limit: 50, total: 0, nextOffset: null } });
      if (path.includes("/outline")) return json({ apiVersion: "v1", sourceId: "simple", page: { items: [
        { iri: "https://example.com/entio/simple#Shrey", label: "Shrey", kind: "Individual", sourceId: "simple", directType: { iri: "https://example.com/entio/simple#Customer", label: "Customer", kind: "Class", sourceId: "simple" } },
      ], offset: 0, limit: 100, total: 1, nextOffset: null } });
      if (path.endsWith("/staged")) return json({
        apiVersion: "v1",
        projectId: "simple",
        status: "READY",
        entries: [{
          id: "stage-individual",
          order: 1,
          sourceId: "simple",
          summary: "create-individual · Account 101",
          editType: "create-individual",
          status: "STAGED",
          authorId: "bob",
          latestEditorId: "bob",
          comment: null,
          normalizedValues: {
            individualIri: "https://example.com/entio/simple#Account101",
            label: "Account 101",
            classIri: "https://example.com/entio/simple#Account",
            classLabel: "Account",
          },
          generatedIris: ["https://example.com/entio/simple#Account101"],
          validationMessages: [],
        }, {
          id: "rename-individual",
          order: 2,
          sourceId: "simple",
          summary: "set-entity-label · Primary Account",
          editType: "set-entity-label",
          status: "STAGED",
          authorId: "bob",
          latestEditorId: "bob",
          comment: null,
          normalizedValues: {
            resourceIri: "https://example.com/entio/simple#Account101",
            label: "Primary Account",
          },
          generatedIris: [],
          validationMessages: [],
        }],
        proposal: null,
      });
      if (path.includes("/sources")) return json({ items: [], offset: 0, limit: 50, total: 0, nextOffset: null });
      throw new Error(`Unexpected request: ${path}`);
    }));

    render(<App />);

    fireEvent.click(await screen.findByRole("tab", { name: /^Objects\s*2$/ }));
    const stagedIndividual = screen.getByRole("button", { name: "Primary Account, Object" });
    expect(stagedIndividual.closest("ul")).toHaveClass("object-group-items");
    expect(
      stagedIndividual.closest("section")?.querySelector(".object-group-heading .object-group-label"),
    ).toHaveTextContent("Account");
    fireEvent.click(stagedIndividual);
    expect(await screen.findByRole("heading", { name: "Primary Account" })).toBeInTheDocument();
    expect(screen.getByText(/pending proposal review/)).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("tab", { name: "Hierarchy" }).at(-1)!);
    expect(await screen.findByRole("list", { name: "Selected add asserted type" })).toHaveTextContent("Account");
  });
});

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
