import { strict as assert } from "node:assert";
import { test } from "node:test";
import { detectEntioProject } from "../projectDetector";

test("detects a workspace containing entio.yaml", () => {
  const result = detectEntioProject("/workspace", (path) => path === "/workspace/entio.yaml");

  assert.deepEqual(result, {
    rootPath: "/workspace",
    configPath: "/workspace/entio.yaml",
  });
});

test("returns no project when entio.yaml is missing", () => {
  const result = detectEntioProject("/workspace", () => false);

  assert.equal(result, undefined);
});

test("returns no project when no workspace is open", () => {
  assert.equal(detectEntioProject(undefined), undefined);
});
