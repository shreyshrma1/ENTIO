import { strict as assert } from "node:assert";
import { test } from "node:test";
import { renderWorkbench } from "../webview";

test("renders project browser and preview form controls", () => {
  const html = renderWorkbench({ cspSource: "vscode-resource:" } as never, "test-nonce");

  assert.match(html, /id="ontology-sources|id="sources/);
  assert.match(html, /id="symbol-groups"/);
  assert.match(html, /id="proposal-form"/);
  assert.match(html, /type: "proposal-preview"/);
  assert.match(html, /id="preview-validation"/);
  assert.match(html, /id="approve"/);
  assert.match(html, /Approve and apply/);
  assert.match(html, /id="reject"/);
  assert.match(html, /id="open-source"/);
  assert.match(html, /type: "proposal-action"/);
  assert.match(html, /type: "open-source"/);
});
