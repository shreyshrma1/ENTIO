import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import AiCredentialSettings from "./AiCredentialSettings";

describe("AI credential settings", () => {
  it("keeps the credential input local and exposes lifecycle status only", async () => {
    const secret = "development-secret";
    let savedBody = "";
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.includes("credential-status")) return json({ configured: false, providerId: null, testStatus: "NOT_CONFIGURED" });
      if (init?.method === "PUT") {
        savedBody = String(init.body);
        return json({ configured: true, providerId: "openai", testStatus: "NOT_TESTED" });
      }
      if (init?.method === "POST") return json({ status: "PASSED", message: "Credential test passed." });
      if (init?.method === "DELETE") return json({ configured: false, providerId: null, testStatus: "NOT_CONFIGURED" });
      throw new Error(`Unexpected request: ${path}`);
    }));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    render(<QueryClientProvider client={client}><AiCredentialSettings /></QueryClientProvider>);

    const credential = await screen.findByLabelText("Credential");
    fireEvent.change(credential, { target: { value: secret } });
    fireEvent.click(screen.getByRole("button", { name: "Save credential" }));

    expect(await screen.findByText("NOT_TESTED")).toBeInTheDocument();
    expect(savedBody).toContain('"providerId":"openai"');
    expect(credential).toHaveValue("");
    expect(screen.queryByText(secret)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Test credential" }));
    expect(await screen.findByText("Credential test passed.")).toBeInTheDocument();
  });
});

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
