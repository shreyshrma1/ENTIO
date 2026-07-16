import type { WebSessionResponse } from "./contracts";

export async function loadWebSession(
  fetcher: typeof fetch = fetch,
): Promise<WebSessionResponse> {
  const response = await fetcher("/api/v1/session");
  if (!response.ok) {
    throw new Error(`session-request-failed:${response.status}`);
  }
  return (await response.json()) as WebSessionResponse;
}
