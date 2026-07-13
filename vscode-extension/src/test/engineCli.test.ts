import { strict as assert } from "node:assert";
import { EventEmitter } from "node:events";
import { test } from "node:test";
import { PassThrough } from "node:stream";
import { cliFailureMessage, EntioEngineClient, type SpawnProcess } from "../engineCli";

test("prefers a structured CLI error over logging noise", () => {
  const message = cliFailureMessage(
    JSON.stringify({
      command: "generate-iri",
      ok: false,
      error: { message: "An iriNamespace is required to generate an identifier." },
    }),
    "SLF4J(W): No SLF4J providers were found.",
    1,
  );

  assert.equal(message, "An iriNamespace is required to generate an identifier.");
});

test("falls back to stderr for non-JSON CLI failures", () => {
  assert.equal(
    cliFailureMessage("not-json", "process failed", 2),
    "process failed",
  );
});

test("returns structured results when a command uses a nonzero review status", async () => {
  const spawnProcess = (() => {
    const child = new EventEmitter() as EventEmitter & {
      stdout: PassThrough;
      stderr: PassThrough;
    };
    child.stdout = new PassThrough();
    child.stderr = new PassThrough();
    queueMicrotask(() => {
      child.stdout.end(JSON.stringify({
        command: "deletion-dependencies",
        ok: false,
        status: "RequiresExplicitDependencies",
        dependentStatements: [{ subject: "Shrey", predicate: "recievedInvoice", object: "20874" }],
      }));
      child.stderr.end("SLF4J(W): No SLF4J providers were found.");
      child.emit("close", 1);
    });
    return child;
  }) as unknown as SpawnProcess;

  const response = await new EntioEngineClient("entio", spawnProcess).run(["deletion-dependencies"]);

  assert.equal(response.ok, false);
  assert.equal(response.status, "RequiresExplicitDependencies");
});
