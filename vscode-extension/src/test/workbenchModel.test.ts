import { strict as assert } from "node:assert";
import { test } from "node:test";
import {
  createSemanticDescriptorModel,
  createSemanticSearchModel,
  createProposalImpactModel,
  createReasoningModel,
  createShaclShapesModel,
  createShaclValidationModel,
  createWorkbenchModel,
  entitySelectorOptions,
  labelDisplay,
  selectSymbol,
} from "../workbenchModel";

test("normalizes project summaries into deterministic source and symbol groups", () => {
  const model = createWorkbenchModel({
    ok: true,
    project: {
      name: "simple-ontology",
      root: "/workspace",
      graphTripleCount: 4,
    },
    ontologySources: [
      { id: "z-source", path: "z.ttl", format: "turtle", tripleCount: 1 },
      { id: "a-source", path: "a.ttl", format: "turtle", tripleCount: 3 },
    ],
    symbols: [
      { iri: "https://example.com/Property", label: "owns", kind: "Property", sourceId: "a-source" },
      { iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "a-source" },
      { iri: "https://example.com/Account", label: "Account", kind: "Class", sourceId: "a-source" },
    ],
    symbolDetails: [],
  });

  assert.ok(model);
  assert.deepEqual(model.ontologySources.map((source) => source.id), ["a-source", "z-source"]);
  assert.deepEqual(model.symbolGroups.map((group) => group.kind), ["Class", "Property"]);
  assert.deepEqual(
    model.symbolGroups[0].symbols.map((symbol) => symbol.iri),
    ["https://example.com/Account", "https://example.com/Customer"],
  );
});

test("selects a symbol for deterministic detail rendering", () => {
  const model = createWorkbenchModel({
    ok: true,
    project: { name: "simple", root: "/workspace", graphTripleCount: 1 },
    ontologySources: [],
    symbols: [
      { iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple" },
    ],
    symbolDetails: [
      {
        iri: "https://example.com/Customer",
        label: "Customer",
        kind: "Class",
        sourceId: "simple",
        relationships: [
          {
            direction: "outgoing",
            kind: "property",
            predicate: "https://example.com/owns",
            predicateLabel: "owns",
            value: { kind: "iri", value: "https://example.com/Account", datatype: null, language: null },
            valueLabel: "Account",
            sourceId: "simple",
          },
          {
            direction: "outgoing",
            kind: "property",
            predicate: "https://example.com/name",
            predicateLabel: "name",
            value: { kind: "literal", value: "Shrey", datatype: null, language: null },
            valueLabel: null,
            sourceId: "simple",
          },
          {
            direction: "incoming",
            kind: "property",
            predicate: "https://example.com/receivedBy",
            predicateLabel: "received by",
            value: { kind: "iri", value: "https://example.com/Invoice", datatype: null, language: null },
            valueLabel: "Invoice",
            sourceId: "simple",
          },
        ],
      },
    ],
  });

  assert.ok(model);
  assert.equal(selectSymbol(model, "https://example.com/Customer").selectedSymbol?.label, "Customer");
  assert.equal(selectSymbol(model, "https://example.com/Customer").selectedSymbol?.relationships.length, 3);
  assert.equal(
    selectSymbol(model, "https://example.com/Customer").selectedSymbol?.relationships
      .find((relationship) => relationship.direction === "incoming")?.valueLabel,
    "Invoice",
  );
  assert.equal(selectSymbol(model, "https://example.com/Missing").selectedSymbol, undefined);
});

test("rejects unsuccessful or malformed project summaries", () => {
  assert.equal(createWorkbenchModel({ ok: false }), undefined);
  assert.equal(createWorkbenchModel({ ok: true, project: { name: "missing-root" } }), undefined);
});

test("provides source and kind filtered display options without resolving labels locally", () => {
  const model = createWorkbenchModel({
    ok: true,
    project: { name: "simple", root: "/workspace", graphTripleCount: 2 },
    ontologySources: [],
    symbols: [
      { iri: "https://example.com/Customer", label: "Customer", kind: "Class", sourceId: "simple" },
      { iri: "https://example.com/customer", label: "Customer", kind: "Individual", sourceId: "simple" },
      { iri: "https://other.example/Customer", label: "Customer", kind: "Class", sourceId: "other" },
    ],
    symbolDetails: [],
  });

  assert.ok(model);
  const options = entitySelectorOptions(model, "Class", "simple");
  assert.equal(options.length, 1);
  assert.equal(labelDisplay(options[0]), "Customer · Class · simple");
  assert.equal(labelDisplay({
    iri: "https://example.com/CustomerShape",
    label: null,
    kind: "Shape",
    sourceId: "shapes",
    relationships: [],
  }), "Customer Shape · Shape · shapes");
});

test("normalizes Kotlin semantic descriptors without interpreting RDF locally", () => {
  const descriptor = createSemanticDescriptorModel({
    command: "descriptor",
    ok: true,
    descriptor: {
      iri: "https://example.com/Customer",
      kind: "Class",
      sourceId: "simple",
      sourceOntologyId: "simple",
      locality: "Local",
      preferredLabelSource: "ExplicitLabel",
      preferredLabel: { value: "Customer", language: "en", datatype: null },
      ambiguousPreferredLabelLanguages: [],
      alternateLabels: [{ value: "Client", language: "en", datatype: null }],
      definitions: [{ value: "A customer.", language: null, datatype: null }],
      annotations: [
        {
          subject: "https://example.com/Customer",
          property: "https://example.com/note",
          value: { kind: "literal", value: "Trusted", datatype: null, language: null },
          sourceId: "simple",
        },
      ],
      directSuperclasses: [],
      directSubclasses: ["https://example.com/BusinessCustomer"],
      directlyTypedIndividuals: ["https://example.com/Shrey"],
    },
  });

  assert.ok(descriptor);
  assert.equal(descriptor.preferredLabel?.value, "Customer");
  assert.equal(descriptor.alternateLabels[0].value, "Client");
  assert.equal(descriptor.annotations[0].value.value, "Trusted");
  assert.deepEqual(descriptor.directSubclasses, ["https://example.com/BusinessCustomer"]);
});

test("normalizes semantic search results and preserves Kotlin match reasons", () => {
  const search = createSemanticSearchModel({
    command: "search",
    ok: true,
    query: "client",
    ambiguous: false,
    results: [
      {
        reason: "AlternateLabel",
        rank: 1,
        descriptor: {
          iri: "https://example.com/Customer",
          kind: "Class",
          sourceId: "simple",
          sourceOntologyId: "simple",
          locality: "Local",
          preferredLabelSource: "ExplicitLabel",
          preferredLabel: { value: "Customer", language: null, datatype: null },
          alternateLabels: [{ value: "Client", language: null, datatype: null }],
          definitions: [],
          annotations: [],
        },
      },
    ],
  });

  assert.ok(search);
  assert.equal(search.results[0].reason, "AlternateLabel");
  assert.equal(search.results[0].descriptor.preferredLabel?.value, "Customer");
});

test("normalizes Phase 4 reasoning, SHACL, and proposal impact results", () => {
  const reasoning = createReasoningModel({
    ok: true,
    reasoning: {
      status: "completed",
      consistency: "consistent",
      importClosureComplete: true,
      fingerprints: { graph: "graph-1", imports: "imports-1", configuration: "config-1" },
      classRelationships: [{ subject: "https://example.com/Child", objectClass: "https://example.com/Parent", origin: "inferred", sourceId: "simple" }],
      individualTypes: [],
      propertyRelationships: [],
      unsatisfiableClasses: [],
      unsupportedFeatures: [{ feature: "OWL 2 DL profile", support: "unsupported", affectsCompleteness: true, message: "Partial profile coverage." }],
      warnings: [],
      errors: [],
    },
  });
  assert.ok(reasoning);
  assert.equal(reasoning.classRelationships[0].origin, "inferred");
  assert.equal(reasoning.fingerprints.graph, "graph-1");

  const validation = createShaclValidationModel({
    ok: false,
    validation: {
      status: "completed",
      mode: "asserted-only",
      graphIdentity: { dataGraphFingerprint: "data-1", shapesGraphFingerprint: "shapes-1" },
      results: [{
        resultId: "result-1",
        severity: "violation",
        message: "code is required",
        focusNode: "https://example.com/Account1",
        path: { kind: "direct-property", iri: "https://example.com/code" },
        shape: "https://example.com/AccountShape",
        constraint: "mincount",
        value: null,
        sourceId: "simple",
      }],
      warnings: [],
      errors: [],
    },
  });
  assert.ok(validation);
  assert.equal(validation.results[0].path, "https://example.com/code");

  const shapes = createShaclShapesModel({
    ok: true,
    shapes: [{
      iri: "https://example.com/AccountShape",
      label: "Account shape",
      sourceId: "simple",
      targets: [{ kind: "target-class", iri: "https://example.com/Account" }],
      propertyShapes: [{ path: { kind: "direct-property", iri: "https://example.com/code" } }],
      constraints: [{ kind: "mincount", value: "1" }],
    }],
  });
  assert.ok(shapes);
  assert.equal(shapes.shapes[0].label, "Account shape");
  assert.deepEqual(shapes.shapes[0].propertyShapes, ["https://example.com/code"]);

  const impact = createProposalImpactModel({
    ok: true,
    impact: {
      status: "safe",
      explicitDiff: { entryCount: 1, entries: [{}] },
      reasoningImpact: { addedInferences: [], removedInferences: [] },
      shaclImpact: { newResults: [], worsenedResults: [], unchangedResults: [], resolvedResults: [] },
      blockingMessages: [],
    },
  });
  assert.ok(impact);
  assert.equal(impact.explicitDiffCount, 1);
  assert.equal(impact.status, "safe");
});
