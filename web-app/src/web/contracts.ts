export const WEB_API_VERSION = "v1" as const;

export type WebRole = "CONTRIBUTOR" | "REVIEWER";

export interface WebSessionUser {
  id: string;
  displayName: string;
  avatar: string;
  role: WebRole;
}

export interface WebSessionResponse {
  apiVersion: typeof WEB_API_VERSION;
  user: WebSessionUser;
  permissions: string[];
}

export interface RegisteredProject {
  id: string;
  displayName: string;
}

export interface WebProjectListResponse {
  apiVersion: typeof WEB_API_VERSION;
  projects: RegisteredProject[];
}

export interface WebErrorResponse {
  apiVersion: typeof WEB_API_VERSION;
  requestId: string;
  code: string;
  message: string;
  details: Record<string, string>;
}

export interface WebPage<T> {
  items: T[];
  offset: number;
  limit: number;
  total: number;
  nextOffset: number | null;
}

export interface PageRequest {
  offset?: number;
  limit?: number;
}

export function encodeWebIri(iri: string): string {
  return encodeURIComponent(iri);
}

export function normalizePageRequest(request: PageRequest = {}): Required<PageRequest> {
  const offset = request.offset ?? 0;
  const limit = request.limit ?? 50;

  if (!Number.isInteger(offset) || offset < 0) {
    throw new Error("offset-must-not-be-negative");
  }
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
    throw new Error("limit-must-be-between-1-and-100");
  }

  return { offset, limit };
}
