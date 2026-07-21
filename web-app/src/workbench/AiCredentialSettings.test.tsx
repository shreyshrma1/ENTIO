import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import AiCredentialSettings from "./AiCredentialSettings";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("AI provider settings", () => {
  it("clears the credential field and requires explicit model selection", async () => {
    const secret = "development-secret";
    let settings = providerSettings(false);
    let savedBody = "";
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith("/ai/provider-settings") && !init?.method) return json(settings);
      if (path.endsWith("/ai/credentials") && init?.method === "PUT") {
        savedBody = String(init.body);
        settings = providerSettings(true);
        return json(settings);
      }
      if (path.endsWith("/ai/model-selection") && init?.method === "PUT") {
        settings = providerSettings(true, true);
        return json(settings);
      }
      throw new Error(`Unexpected request: ${path}`);
    }));
    renderSettings();

    const credential = await screen.findByLabelText("Credential");
    fireEvent.change(credential, { target: { value: secret } });
    fireEvent.click(screen.getByRole("button", { name: "Save credential" }));

    expect(await screen.findByText(/Credential valid. Select and test/i)).toBeInTheDocument();
    expect(savedBody).toContain('"providerId":"openai"');
    expect(credential).toHaveValue("");
    expect(screen.queryByText(secret)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Select and test GPT-5.2" }));
    expect(await screen.findByText("Ready with GPT-5.2.")).toBeInTheDocument();
  });

  it("saves the credential when Enter is pressed", async () => {
    let saved = false;
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).endsWith("/ai/provider-settings") && !init?.method) return json(providerSettings(false));
      if (String(input).endsWith("/ai/credentials") && init?.method === "PUT") {
        saved = true;
        return json(providerSettings(true));
      }
      return json(providerSettings(false));
    }));
    renderSettings();
    const credential = await screen.findByLabelText("Credential");
    fireEvent.change(credential, { target: { value: "enter-secret" } });
    fireEvent.keyDown(credential, { key: "Enter" });
    await screen.findByText(/Credential valid/i);
    expect(saved).toBe(true);
  });

  it("never writes credentials or provider settings to browser storage", async () => {
    const localWrite = vi.spyOn(Storage.prototype, "setItem");
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).endsWith("/ai/provider-settings") && !init?.method) return json(providerSettings(false));
      return json(providerSettings(true));
    }));
    renderSettings();
    const credential = await screen.findByLabelText("Credential");
    fireEvent.change(credential, { target: { value: "storage-test-secret" } });
    fireEvent.click(screen.getByRole("button", { name: "Save credential" }));
    expect(await screen.findByText(/Credential valid/i)).toBeInTheDocument();
    expect(localWrite).not.toHaveBeenCalled();
    expect(credential).toHaveValue("");
  });

  it("clears a rejected credential from the form", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).endsWith("/ai/provider-settings") && !init?.method) return json(providerSettings(false));
      return new Response(JSON.stringify({ message: "Credential rejected" }), { status: 401, headers: { "Content-Type": "application/json" } });
    }));
    renderSettings();
    const credential = await screen.findByLabelText("Credential");
    fireEvent.change(credential, { target: { value: "bad-secret" } });
    fireEvent.click(screen.getByRole("button", { name: "Save credential" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not save the key");
    expect(credential).toHaveValue("");
  });

  it.each([
    [{ credentialStatus: "INVALID" }, /credential is invalid/i],
    [{ discoveryStatus: "NO_COMPATIBLE_MODELS" }, /No compatible models/i],
    [{ discoveryStatus: "STALE" }, /model list is stale/i],
    [{ selectionStatus: "UNAVAILABLE" }, /model is no longer available/i],
    [{ selectionStatus: "VERIFICATION_FAILED" }, /Model verification failed/i],
    [{ discoveryStatus: "FAILED", errorCode: "AI_PROVIDER_RATE_LIMITED" }, /rate-limited/i],
    [{ discoveryStatus: "FAILED", errorCode: "AI_PROVIDER_TIMEOUT" }, /did not respond in time/i],
    [{ discoveryStatus: "FAILED", errorCode: "AI_PROVIDER_UNAVAILABLE" }, /currently unavailable/i],
  ])("announces provider state %#", async (overrides, expected) => {
    vi.stubGlobal("fetch", vi.fn(async () => json({ ...providerSettings(true), ...overrides })));
    renderSettings();
    expect((await screen.findAllByText(expected)).length).toBeGreaterThan(0);
  });
});

function renderSettings() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(<QueryClientProvider client={client}><AiCredentialSettings /></QueryClientProvider>);
}

function providerSettings(configured: boolean, ready = false) {
  const model = { providerId: "openai", modelId: "gpt-5.2", displayName: "GPT-5.2", description: "Balanced model", capabilityTier: "ADVANCED", relativeSpeed: "Medium", relativeCost: "Medium", recommended: true, capabilities: ["RESPONSES", "TOOLS"] };
  return { apiVersion: "v1", providerId: "openai", credentialStatus: configured ? "VALID" : "NOT_CONFIGURED", discoveryStatus: configured ? "COMPLETED" : "NOT_REQUESTED", discoveredAt: null, policyVersion: "phase-7.5-compatibility-v1", models: configured ? [model] : [], unsupportedProviderModelCount: 0, selectedModel: ready ? model : null, selectionStatus: ready ? "READY" : configured ? "NOT_SELECTED" : "NOT_CONFIGURED", selectedModelVerifiedAt: null, errorCode: null, availableActions: [] };
}

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });
}
