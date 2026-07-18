import type { WebSessionResponse } from "./contracts";

export const WEB_DEVELOPMENT_USER_ID = "bob";

export function withDevelopmentIdentity(init: RequestInit = {}): RequestInit {
  const headers = new Headers(init.headers);
  headers.set("X-Entio-User", WEB_DEVELOPMENT_USER_ID);
  return { ...init, headers };
}

export async function loadWebSession(
  fetcher: typeof fetch = fetch,
): Promise<WebSessionResponse> {
  const response = await fetcher("/api/v1/session");
  if (!response.ok) {
    throw new Error(`session-request-failed:${response.status}`);
  }
  return (await response.json()) as WebSessionResponse;
}
