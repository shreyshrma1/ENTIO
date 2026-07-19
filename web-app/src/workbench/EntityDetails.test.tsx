import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { WebEntityDetailResponse, WebEntityReference, WebStagedEntry } from "../web/projectApi";
import EntityDetails from "./EntityDetails";

describe("entity details editing", () => {
  beforeEach(() => vi.restoreAllMocks());
  afterEach(() => cleanup());

  it("synchronizes a changed field and removes it when restored to its applied value", async () => {
    let entries: WebStagedEntry[] = [];
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/sources")) return json(page([]));
      if (path.endsWith("/staged") && init?.method === "POST") {
        const request = JSON.parse(String(init.body)) as Record<string, string>;
        entries = [{
          id: request.replacesStagedId ?? "stage-1",
          order: 1,
          sourceId: "simple",
          summary: `${request.editType} · Customer`,
          editType: request.editType,
          status: "STAGED",
          authorId: "bob",
          latestEditorId: "bob",
          comment: null,
          aiGenerated: false,
          normalizedValues: { resourceIri: request.resourceIri, label: request.label },
          generatedIris: [],
          validationMessages: [],
        }];
        return json(staging(entries));
      }
      if (path.endsWith("/staged/stage-1") && init?.method === "DELETE") {
        entries = [];
        return json(staging(entries));
      }
      if (path.endsWith("/staged")) return json(staging(entries));
      if (path.endsWith("/summary")) return json({});
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);

    renderEntity(classEntity());

    const label = screen.getByRole("textbox", { name: "Preferred label" });
    expect(screen.queryByRole("button", { name: /Stage label/i })).not.toBeInTheDocument();
    fireEvent.change(label, { target: { value: "Client" } });
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged",
      expect.objectContaining({ method: "POST", body: expect.stringContaining('"label":"Client"') }),
    ));

    fireEvent.change(label, { target: { value: "Customer" } });
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged/stage-1",
      expect.objectContaining({ method: "DELETE" }),
    ));
  });

  it("does not expose a schema tab for objects and limits relationship targets to individuals", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/sources")) return json(page([]));
      if (path.endsWith("/staged")) return json(staging([]));
      if (path.includes("/search")) return json({
        apiVersion: "v1",
        query: "Invoice",
        page: page([
          { iri: "https://example.com/Invoice", label: "Invoice", kind: "Class", sourceId: "simple", reason: "PreferredLabel", rank: 1, locality: "Local" },
          { iri: "https://example.com/Invoice20874", label: "Invoice 20874", kind: "Individual", sourceId: "simple", reason: "PreferredLabel", rank: 1, locality: "Local" },
          { iri: "https://example.com/receivedInvoice", label: "received invoice", kind: "ObjectProperty", sourceId: "simple", reason: "PreferredLabel", rank: 1, locality: "Local" },
        ]),
      });
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderEntity({ ...classEntity(), iri: "https://example.com/Shrey", label: "Shrey", kind: "Individual", assertedTypes: [{ iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple" }] });

    expect(await screen.findByRole("tab", { name: "Relationships" })).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "Schema" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Relationships" }));
    expect(screen.getByRole("heading", { name: "Outgoing relationships" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Outgoing" })).toHaveAttribute("aria-selected", "true");
    fireEvent.change(screen.getByRole("combobox", { name: "Object" }), { target: { value: "Invoice" } });
    fireEvent.click(await screen.findByRole("option", { name: /Invoice 20874/ }));
    expect(screen.getByRole("combobox", { name: "Object" })).toHaveValue("Invoice 20874");
    expect(screen.queryByRole("list", { name: "Selected object" })).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole("combobox", { name: "Object" }), { target: { value: "Invoice" } });
    const options = screen.getByRole("listbox");
    expect(within(options).queryByRole("option", { name: /Invoice 20874/ })).not.toBeInTheDocument();
    expect(options).not.toHaveTextContent("received invoice");

    fireEvent.click(screen.getByRole("tab", { name: "Incoming" }));
    expect(screen.getByRole("heading", { name: "Incoming relationships" })).toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "Object" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "Datatype" }));
    expect(screen.getByRole("heading", { name: "Datatype values" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /Value/ })).toBeInTheDocument();
  });

  it("hides hierarchy for properties and shows direct entity facts and annotations as consistent rows", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/sources")) return json(page([]));
      if (path.endsWith("/staged")) return json(staging([]));
      throw new Error(`Unexpected request: ${path}`);
    }));

    const account101 = { iri: "https://example.com/Account101", label: "Account 101", kind: "Individual", sourceId: "simple" };
    renderEntity({
      ...classEntity(),
      annotations: [{
        property: { iri: "https://example.com/dateOpened", label: "date opened", kind: "DatatypeProperty", sourceId: "simple" },
        value: {
          kind: "Literal",
          value: "2026-07-17",
          label: null,
          datatype: "http://www.w3.org/2001/XMLSchema#date",
          language: null,
        },
        sourceId: "simple",
      }],
      directlyTypedIndividuals: [account101],
    });

    expect(await screen.findByText("Direct objects")).toBeInTheDocument();
    expect(screen.getByText("Account 101")).toHaveClass("entity-reference-value");
    expect(screen.getByText("date opened: 2026-07-17")).toHaveClass("entity-reference-value");

    cleanup();
    renderEntity(propertyEntity());

    expect(await screen.findByRole("tab", { name: "Schema" })).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "Hierarchy" })).not.toBeInTheDocument();

    cleanup();
    const directType = { iri: "https://example.com/Account", label: "Account", kind: "Class", sourceId: "simple" };
    renderEntity({ ...classEntity(), iri: account101.iri, label: account101.label, kind: "Individual" }, { directType });

    expect(await screen.findByText("Direct class type")).toBeInTheDocument();
    expect(screen.getByText("Account")).toBeInTheDocument();
  });

  it("stages datatype values with the selected XSD datatype", async () => {
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/sources")) return json(page([]));
      if (path.includes("/search")) return json({
        apiVersion: "v1",
        query: "balance",
        page: page([
          { iri: "https://example.com/balance", label: "balance", kind: "DatatypeProperty", sourceId: "simple", reason: "PreferredLabel", rank: 1, locality: "Local" },
        ]),
      });
      if (path.endsWith("/staged") && init?.method === "POST") {
        const request = JSON.parse(String(init.body)) as Record<string, string>;
        return json(staging([{
          id: "stage-datatype",
          order: 1,
          sourceId: "simple",
          summary: "add-datatype-property-assertion · Account 101",
          editType: request.editType,
          status: "STAGED",
          authorId: "bob",
          latestEditorId: "bob",
          comment: null,
          aiGenerated: false,
          normalizedValues: request,
          generatedIris: [],
          validationMessages: [],
        }]));
      }
      if (path.endsWith("/staged")) return json(staging([]));
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);

    renderEntity({ ...classEntity(), iri: "https://example.com/Account101", label: "Account 101", kind: "Individual" });
    fireEvent.click(screen.getByRole("tab", { name: "Relationships" }));
    fireEvent.click(screen.getByRole("tab", { name: "Datatype" }));
    const propertyPicker = screen.getByRole("combobox", { name: "Datatype property" });
    fireEvent.change(propertyPicker, { target: { value: "balance" } });
    fireEvent.click(await screen.findByRole("option", { name: /balance/ }));
    fireEvent.change(screen.getByRole("combobox", { name: "Datatype" }), { target: { value: "http://www.w3.org/2001/XMLSchema#integer" } });
    const valueInput = screen.getByRole("spinbutton", { name: "Value" });
    fireEvent.change(valueInput, { target: { value: "42" } });

    expect(fetcher).not.toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged",
      expect.objectContaining({ method: "POST" }),
    );
    fireEvent.keyDown(valueInput, { key: "Enter" });

    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged",
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining('"datatypeIri":"http://www.w3.org/2001/XMLSchema#integer"'),
      }),
    ));
    await vi.waitFor(() => {
      expect(propertyPicker).toHaveValue("");
      expect(valueInput).toHaveValue("");
      expect(screen.getByRole("combobox", { name: "Datatype" })).toHaveValue("http://www.w3.org/2001/XMLSchema#string");
    });
  });

  it("waits for Enter before staging a completed object relationship", async () => {
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/sources")) return json(page([]));
      if (path.includes("/search")) {
        const query = new URL(path, "http://localhost").searchParams.get("q");
        const item = query === "owns account"
          ? { iri: "https://example.com/ownsAccount", label: "owns account", kind: "ObjectProperty", sourceId: "simple", reason: "PreferredLabel", rank: 1, locality: "Local" }
          : { iri: "https://example.com/Invoice20874", label: "Invoice 20874", kind: "Individual", sourceId: "simple", reason: "PreferredLabel", rank: 1, locality: "Local" };
        return json({ apiVersion: "v1", query, page: page([item]) });
      }
      if (path.endsWith("/staged") && init?.method === "POST") {
        const request = JSON.parse(String(init.body)) as Record<string, string>;
        return json(staging([{
          id: "stage-relationship",
          order: 1,
          sourceId: "simple",
          summary: "add-object-property-assertion · Account 101",
          editType: request.editType,
          status: "STAGED",
          authorId: "bob",
          latestEditorId: "bob",
          comment: null,
          aiGenerated: false,
          normalizedValues: request,
          generatedIris: [],
          validationMessages: [],
        }]));
      }
      if (path.endsWith("/staged")) return json(staging([]));
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);

    renderEntity({ ...classEntity(), iri: "https://example.com/Account101", label: "Account 101", kind: "Individual" });
    fireEvent.click(screen.getByRole("tab", { name: "Relationships" }));
    const property = screen.getByRole("combobox", { name: "Object property" });
    fireEvent.change(property, { target: { value: "owns account" } });
    fireEvent.click(await screen.findByRole("option", { name: /owns account/ }));
    const object = screen.getByRole("combobox", { name: "Object" });
    fireEvent.change(object, { target: { value: "Invoice 20874" } });
    fireEvent.click(await screen.findByRole("option", { name: /Invoice 20874/ }));

    expect(fetcher).not.toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged",
      expect.objectContaining({ method: "POST" }),
    );
    fireEvent.keyDown(object, { key: "Enter" });

    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged",
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining('"editType":"add-object-property-assertion"'),
      }),
    ));
    await vi.waitFor(() => {
      expect(property).toHaveValue("");
      expect(object).toHaveValue("");
    });
  });

  it("removes a staged property domain when the selection is cleared", async () => {
    let entries: WebStagedEntry[] = [];
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/sources")) return json(page([]));
      if (path.includes("/search")) return json({
        apiVersion: "v1",
        query: "Account",
        page: page([
          { iri: "https://example.com/Account", label: "Account", kind: "Class", sourceId: "simple", reason: "PreferredLabel", rank: 1, locality: "Local" },
        ]),
      });
      if (path.endsWith("/staged") && init?.method === "POST") {
        const request = JSON.parse(String(init.body)) as Record<string, string>;
        entries = [{
          id: "stage-domain",
          order: 1,
          sourceId: "simple",
          summary: "set-property-domain · owns account",
          editType: "set-property-domain",
          status: "STAGED",
          authorId: "bob",
          latestEditorId: "bob",
          comment: null,
          aiGenerated: false,
          normalizedValues: {
            propertyIri: request.propertyIri,
            propertyLabel: request.propertyLabel,
            domainClassIri: request.domainClassIri,
            domainClassLabel: request.domainClassLabel,
          },
          generatedIris: [],
          validationMessages: [],
        }];
        return json(staging(entries));
      }
      if (path.endsWith("/staged/stage-domain") && init?.method === "DELETE") {
        entries = [];
        return json(staging(entries));
      }
      if (path.endsWith("/staged")) return json(staging(entries));
      if (path.endsWith("/summary")) return json({});
      throw new Error(`Unexpected request: ${init?.method ?? "GET"} ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);

    renderEntity(propertyEntity());
    fireEvent.click(await screen.findByRole("tab", { name: "Schema" }));
    fireEvent.change(screen.getByRole("combobox", { name: "Set domain" }), { target: { value: "Account" } });
    fireEvent.click(await screen.findByRole("option", { name: /Account/ }));
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged",
      expect.objectContaining({ method: "POST", body: expect.stringContaining('"editType":"set-property-domain"') }),
    ));

    fireEvent.click(screen.getByRole("button", { name: "Remove Account" }));
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged/stage-domain",
      expect.objectContaining({ method: "DELETE" }),
    ));
    expect(entries).toEqual([]);
    expect(await screen.findByText("This field's staged changes were removed. The review queue is now empty.")).toBeInTheDocument();
  });

  it("shows staged property domains in the class property workspace", async () => {
    const stagedDomain: WebStagedEntry = {
      id: "stage-checking-account-domain",
      order: 1,
      sourceId: "simple",
      summary: "set-property-domain · owns account",
      editType: "set-property-domain",
      status: "STAGED",
      authorId: "bob",
      latestEditorId: "bob",
      comment: null,
      aiGenerated: false,
      normalizedValues: {
        propertyIri: "https://example.com/ownsAccount",
        propertyLabel: "owns account",
        domainClassIri: "https://example.com/CheckingAccount",
        domainClassLabel: "Checking Account",
      },
      generatedIris: [],
      validationMessages: [],
    };
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/sources")) return json(page([]));
      if (path.endsWith("/staged")) return json(staging([stagedDomain]));
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderEntity({
      ...classEntity(),
      iri: "https://example.com/CheckingAccount",
      label: "Checking Account",
      locality: "Staged",
      directSuperclasses: [{ iri: "https://example.com/Account", label: "Account", kind: "Class", sourceId: "simple" }],
    });

    fireEvent.click(screen.getByRole("tab", { name: "Hierarchy" }));
    expect(screen.getByRole("heading", { name: "Superclass" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Direct superclass (one allowed)" })).toBeInTheDocument();
    expect(screen.getByText("Account")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "Properties" }));
    expect(await screen.findByRole("heading", { name: "Outgoing properties" })).toBeInTheDocument();
    expect(await screen.findByText("owns account")).toBeInTheDocument();
    expect(screen.getByText("Object property · Staged")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Outgoing" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("tab", { name: "Incoming" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Datatype" })).toBeInTheDocument();
  });

  it("keeps the active editor section when the same entity is refreshed", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/sources")) return json(page([]));
      if (path.endsWith("/staged")) return json(staging([]));
      throw new Error(`Unexpected request: ${path}`);
    }));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    const entity = propertyEntity();
    const view = render(
      <QueryClientProvider client={client}>
        <EntityDetails projectId="simple" iri={entity.iri} stagedEntity={entity} />
      </QueryClientProvider>,
    );

    fireEvent.click(await screen.findByRole("tab", { name: "Schema" }));
    expect(screen.getByRole("tab", { name: "Schema" })).toHaveAttribute("aria-selected", "true");

    view.rerender(
      <QueryClientProvider client={client}>
        <EntityDetails
          projectId="simple"
          iri={entity.iri}
          stagedEntity={{ ...entity, domains: [{ iri: "https://example.com/Account", label: "Account", kind: "Class", sourceId: "simple" }] }}
        />
      </QueryClientProvider>,
    );

    expect(screen.getByRole("tab", { name: "Schema" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("combobox", { name: "Set domain" })).toBeInTheDocument();
  });

  it("shows class constraints in a list and stages add, update, and remove edits from dialogs", async () => {
    const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.includes("/sources?")) return json(page([
        { id: "shapes", path: "ontology/shapes.ttl", format: "turtle", roles: ["shapes"], tripleCount: 8 },
      ]));
      if (path.endsWith("/staged") && init?.method === "POST") return json(staging([]));
      if (path.endsWith("/staged")) return json(staging([]));
      if (path.endsWith("/shacl/shapes")) return json(shaclShapes());
      if (path.includes("/search?")) return json({
        apiVersion: "v1",
        query: "owns account",
        page: page([{
          iri: "https://example.com/ownsAccount",
          label: "owns account",
          kind: "ObjectProperty",
          sourceId: "simple",
          reason: "PreferredLabel",
          rank: 1,
          locality: "Local",
        }]),
      });
      throw new Error(`Unexpected request: ${path}`);
    });
    vi.stubGlobal("fetch", fetcher);

    renderEntity(classEntity());
    fireEvent.click(await screen.findByRole("tab", { name: "Constraints" }));

    expect(await screen.findByRole("heading", { name: "Class constraints" })).toBeInTheDocument();
    expect(screen.getByText("Customer invoice requirement")).toBeInTheDocument();
    expect(screen.getByText("received invoice")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Add property constraint" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Add constraint" }));
    expect(screen.getByRole("dialog", { name: "Add constraint" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add to review queue" })).toBeDisabled();
    const propertyPath = screen.getByRole("combobox", { name: "Property path" });
    fireEvent.change(propertyPath, { target: { value: "owns account" } });
    fireEvent.click(await screen.findByRole("option", { name: /owns account/ }));
    expect(propertyPath).toHaveValue("owns account");
    expect(screen.queryByRole("list", { name: "Selected property path" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Close constraint dialog" }));

    fireEvent.click(screen.getByRole("button", { name: /Customer invoice requirement/ }));
    const dialog = screen.getByRole("dialog", { name: "Customer invoice requirement" });
    const shapeName = within(dialog).getByRole("textbox", { name: "Shape name" });
    fireEvent.change(shapeName, { target: { value: "Customer account requirement" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "Stage update" }));
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged",
      expect.objectContaining({ method: "POST", body: expect.stringContaining('"editType":"shacl-update-shape-label"') }),
    ));
    expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged",
      expect.objectContaining({ method: "POST", body: expect.stringContaining('"label":"Customer account requirement"') }),
    );

    fireEvent.click(screen.getByRole("button", { name: /Customer invoice requirement/ }));
    const valueDialog = screen.getByRole("dialog", { name: "Customer invoice requirement" });
    fireEvent.change(within(valueDialog).getByRole("textbox", { name: "Constraint value" }), { target: { value: "2" } });
    fireEvent.click(within(valueDialog).getByRole("button", { name: "Stage update" }));
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged",
      expect.objectContaining({ method: "POST", body: expect.stringContaining('"editType":"shacl-update-constraint"') }),
    ));

    fireEvent.click(screen.getByRole("button", { name: /Customer invoice requirement/ }));
    fireEvent.click(within(screen.getByRole("dialog", { name: "Customer invoice requirement" })).getByRole("button", { name: "Remove constraint" }));
    await vi.waitFor(() => expect(fetcher).toHaveBeenCalledWith(
      "/api/v1/projects/simple/staged",
      expect.objectContaining({ method: "POST", body: expect.stringContaining('"editType":"shacl-remove-constraint"') }),
    ));
  });

  it("shows inherited validation rules for individuals without contextual authoring controls", async () => {
    const openEntity = vi.fn();
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.includes("/sources?")) return json(page([]));
      if (path.endsWith("/staged")) return json(staging([]));
      if (path.endsWith("/shacl/shapes")) return json(shaclShapes());
      throw new Error(`Unexpected request: ${path}`);
    }));

    renderEntity({
      ...classEntity(),
      iri: "https://example.com/Shrey",
      label: "Shrey",
      kind: "Individual",
      assertedTypes: [{ iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple" }],
    }, { onOpenEntity: openEntity });
    fireEvent.click(await screen.findByRole("tab", { name: "Validation" }));

    expect(await screen.findByRole("heading", { name: "Applicable validation rules" })).toBeInTheDocument();
    expect(screen.getByText("Customer invoice requirement")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Add property constraint" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Customer invoice requirement/ }));
    const dialog = screen.getByRole("dialog", { name: "Customer invoice requirement" });
    expect(within(dialog).queryByRole("button", { name: "Remove constraint" })).not.toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "Stage update" })).not.toBeInTheDocument();
    expect(within(dialog).getByText(/To edit or remove this constraint/)).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole("button", { name: "Customer" }));
    expect(openEntity).toHaveBeenCalledWith(
      { iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple" },
      "shacl",
    );
  });
});

function renderEntity(entity: WebEntityDetailResponse, options: {
  directType?: WebEntityReference | null;
  onOpenEntity?: (entity: WebEntityReference, section?: "overview" | "shacl") => void;
} = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(<QueryClientProvider client={client}><EntityDetails
    projectId="simple"
    iri={entity.iri}
    stagedEntity={entity}
    directType={options.directType}
    onOpenEntity={options.onOpenEntity}
  /></QueryClientProvider>);
}

function classEntity(): WebEntityDetailResponse {
  return {
    apiVersion: "v1",
    iri: "https://example.com/Customer",
    label: "Customer",
    kind: "Class",
    sourceId: "simple",
    sourceOntologyId: "simple",
    locality: "Local",
    preferredLabelSource: "RdfsLabel",
    alternateLabels: [],
    definitions: [],
    annotations: [],
    directSuperclasses: [],
    directSubclasses: [],
    directlyTypedIndividuals: [],
    assertedTypes: [],
    domains: [],
    ranges: [],
    outgoingRelationships: [],
    incomingRelationships: [],
  };
}

function propertyEntity(): WebEntityDetailResponse {
  return {
    ...classEntity(),
    iri: "https://example.com/ownsAccount",
    label: "owns account",
    kind: "ObjectProperty",
  };
}

function staging(entries: WebStagedEntry[]) {
  return { apiVersion: "v1", projectId: "simple", status: "READY", entries, proposal: null };
}

function page<T>(items: T[]) {
  return { items, offset: 0, limit: 50, total: items.length, nextOffset: null };
}

function shaclShapes() {
  return {
    apiVersion: "v1",
    projectId: "simple",
    shapes: [{
      iri: "https://example.com/CustomerShape",
      label: "Customer invoice requirement",
      sourceId: "shapes",
      targets: [{ kind: "TargetClass", iri: "https://example.com/Customer", label: "Customer" }],
      constraints: [],
      propertyShapes: [{
        iri: "_:received-invoice",
        path: { iri: "https://example.com/receivedInvoice", label: "received invoice", kind: "ObjectProperty", sourceId: "simple" },
        constraints: [{ kind: "MinCount", value: "1", valueIri: null, valueLabel: "1" }],
        severity: "Violation",
        message: "A customer must receive at least one invoice.",
      }],
      closed: false,
      severity: "Violation",
      message: null,
    }],
  };
}

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
