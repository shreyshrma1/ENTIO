import { describe, expect, it, vi } from "vitest";
import { loadWebSession } from "./session";

describe("web session client", () => {
  it("loads the server-owned current user", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          apiVersion: "v1",
          user: { id: "alice", displayName: "Alice Contributor", avatar: "AC", role: "CONTRIBUTOR" },
          permissions: ["BROWSE"],
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );

    await expect(loadWebSession(fetcher)).resolves.toMatchObject({ user: { id: "alice" } });
    expect(fetcher).toHaveBeenCalledWith("/api/v1/session");
  });

  it("surfaces failed session requests", async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 401 }));

    await expect(loadWebSession(fetcher)).rejects.toThrow("session-request-failed:401");
  });
});
