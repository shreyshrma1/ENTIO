import { describe, expect, it } from "vitest";
import { cancelSemanticJob, ensureAppliedReasoning, loadSemanticJob, submitSemanticJob } from "./projectApi";

function response(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}

describe("semantic job API", () => {
  it("starts, polls, and cancels a job through the web boundary", async () => {
    const paths: string[] = [];
    const methods: string[] = [];
    const fetcher = async (input: RequestInfo | URL, init?: RequestInit) => {
      paths.push(String(input));
      methods.push(init?.method ?? "GET");
      return response({ id: "job-1", status: "Queued", graphFingerprint: "fingerprint" });
    };

    await submitSemanticJob("simple", { kind: "reasoning", scope: "applied" }, fetcher);
    await loadSemanticJob("simple", "job-1", fetcher);
    await cancelSemanticJob("simple", "job-1", fetcher);

    expect(paths[0]).toContain("/api/v1/projects/simple/semantic-jobs");
    expect(paths[1]).toContain("/semantic-jobs/job-1");
    expect(methods).toEqual(["POST", "GET", "DELETE"]);
  });

  it("ensures background applied reasoning through its idempotent startup boundary", async () => {
    let requestedPath = "";
    let requestedMethod = "";
    const status = await ensureAppliedReasoning("simple", async (input, init) => {
      requestedPath = String(input);
      requestedMethod = init?.method ?? "GET";
      return response({ id: "job-background", kind: "Reasoning", scope: "Applied", status: "Queued" });
    });

    expect(requestedPath).toContain("/api/v1/projects/simple/semantic-jobs/ensure-applied-reasoning");
    expect(requestedMethod).toBe("POST");
    expect(status.id).toBe("job-background");
  });
});
