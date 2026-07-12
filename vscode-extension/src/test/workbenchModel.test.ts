import { strict as assert } from "node:assert";
import { test } from "node:test";
import { createWorkbenchModel, selectSymbol } from "../workbenchModel";

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
  });

  assert.ok(model);
  assert.equal(selectSymbol(model, "https://example.com/Customer").selectedSymbol?.label, "Customer");
  assert.equal(selectSymbol(model, "https://example.com/Missing").selectedSymbol, undefined);
});

test("rejects unsuccessful or malformed project summaries", () => {
  assert.equal(createWorkbenchModel({ ok: false }), undefined);
  assert.equal(createWorkbenchModel({ ok: true, project: { name: "missing-root" } }), undefined);
});
