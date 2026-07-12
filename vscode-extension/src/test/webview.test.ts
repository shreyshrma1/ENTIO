import { strict as assert } from "node:assert";
import { test } from "node:test";
import { renderWorkbench } from "../webview";

test("renders project browser and preview form controls", () => {
  const html = renderWorkbench({ cspSource: "vscode-resource:" } as never, "test-nonce");

  assert.match(html, /id="ontology-sources|id="sources/);
  assert.match(html, /id="symbol-groups"/);
  assert.match(html, /id="proposal-form"/);
  assert.match(html, /id="edit-kind"/);
  assert.match(html, /create-object-property/);
  assert.match(html, /id="edit-form-placeholder"/);
  assert.match(html, /id="property-fields"/);
  assert.match(html, /id="property-domain-iri"/);
  assert.match(html, /id="property-range-iri"/);
  assert.match(html, /id="property-datatype"/);
  assert.match(html, /id="property-replace"/);
  assert.match(html, /id="individual-fields"/);
  assert.match(html, /id="individual-type-iri"/);
  assert.match(html, /id="assertion-fields"/);
  assert.match(html, /id="assertion-object-iri"/);
  assert.match(html, /id="assertion-value"/);
  assert.match(html, /type: "proposal-preview"/);
  assert.match(html, /id="preview-validation"/);
  assert.match(html, /id="approve"/);
  assert.match(html, /Approve and apply/);
  assert.match(html, /id="reject"/);
  assert.match(html, /id="open-source"/);
  assert.match(html, /type: "proposal-action"/);
  assert.match(html, /type: "open-source"/);
});
