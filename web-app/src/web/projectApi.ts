import { normalizePageRequest, type PageRequest, type WebPage } from "./contracts";

export interface WebProjectSummary {
  id: string;
  displayName: string;
  name: string;
}

export interface WebOntologySourceSummary {
  id: string;
  path: string;
  format: string;
  roles: string[];
  tripleCount: number;
}

export interface WebProjectSummaryResponse {
  apiVersion: "v1";
  project: WebProjectSummary;
  sources: WebOntologySourceSummary[];
  symbolCount: number;
  graphTripleCount: number;
}

export interface WebHierarchyItem {
  iri: string;
  label: string;
  kind: string;
  sourceId: string;
  childCount: number;
}

export interface WebHierarchyResponse {
  apiVersion: "v1";
  sourceId: string | null;
  parentIri: string | null;
  page: WebPage<WebHierarchyItem>;
}

export interface WebEntityReference {
  iri: string;
  label: string;
  kind: string | null;
  sourceId: string | null;
}

export interface WebTextValue {
  value: string;
  language: string | null;
  datatype: string | null;
}

export interface WebRdfValue {
  kind: string;
  value: string;
  label: string | null;
  datatype: string | null;
  language: string | null;
}

export interface WebAnnotation {
  property: WebEntityReference;
  value: WebRdfValue;
  sourceId: string;
}

export interface WebRelationship {
  direction: "incoming" | "outgoing";
  predicate: WebEntityReference;
  value: WebRdfValue;
  sourceId: string;
}

export interface WebEntityDetailResponse {
  apiVersion: "v1";
  iri: string;
  label: string;
  kind: string;
  sourceId: string;
  sourceOntologyId: string | null;
  locality: string;
  preferredLabelSource: string;
  alternateLabels: WebTextValue[];
  definitions: WebTextValue[];
  annotations: WebAnnotation[];
  directSuperclasses: WebEntityReference[];
  directSubclasses: WebEntityReference[];
  directlyTypedIndividuals: WebEntityReference[];
  assertedTypes: WebEntityReference[];
  domains: WebEntityReference[];
  ranges: WebEntityReference[];
  outgoingRelationships: WebRelationship[];
  incomingRelationships: WebRelationship[];
}

export interface WebSemanticSearchHit {
  iri: string;
  label: string;
  kind: string;
  sourceId: string;
  reason: string;
  rank: number;
  locality: string;
}

export interface WebSemanticSearchResponse {
  apiVersion: "v1";
  query: string;
  page: WebPage<WebSemanticSearchHit>;
}

export type WebFetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

export async function loadProjectSummary(
  projectId: string,
  fetcher: WebFetcher = fetch,
): Promise<WebProjectSummaryResponse> {
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/summary`, fetcher);
}

export async function loadProjectSources(
  projectId: string,
  request: PageRequest = {},
  fetcher: WebFetcher = fetch,
): Promise<WebPage<WebOntologySourceSummary>> {
  const page = normalizePageRequest(request);
  return getJson(
    `/api/v1/projects/${encodeURIComponent(projectId)}/sources?offset=${page.offset}&limit=${page.limit}`,
    fetcher,
  );
}

export async function loadHierarchy(
  projectId: string,
  options: { sourceId?: string; parentIri?: string } & PageRequest = {},
  fetcher: WebFetcher = fetch,
): Promise<WebHierarchyResponse> {
  const page = normalizePageRequest(options);
  const params = new URLSearchParams({ offset: String(page.offset), limit: String(page.limit) });
  if (options.sourceId) params.set("sourceId", options.sourceId);
  if (options.parentIri) params.set("parentIri", options.parentIri);
  return getJson(
    `/api/v1/projects/${encodeURIComponent(projectId)}/hierarchy?${params.toString()}`,
    fetcher,
  );
}

export async function loadEntityDetails(
  projectId: string,
  iri: string,
  sourceId?: string,
  fetcher: WebFetcher = fetch,
): Promise<WebEntityDetailResponse> {
  const params = new URLSearchParams({ iri });
  if (sourceId) params.set("sourceId", sourceId);
  return getJson(
    `/api/v1/projects/${encodeURIComponent(projectId)}/entities?${params.toString()}`,
    fetcher,
  );
}

export async function searchProject(
  projectId: string,
  text: string,
  options: { kind?: string; sourceId?: string; language?: string } & PageRequest = {},
  fetcher: WebFetcher = fetch,
): Promise<WebSemanticSearchResponse> {
  const page = normalizePageRequest(options);
  const params = new URLSearchParams({ q: text, offset: String(page.offset), limit: String(page.limit) });
  if (options.kind) params.set("kind", options.kind);
  if (options.sourceId) params.set("sourceId", options.sourceId);
  if (options.language) params.set("language", options.language);
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/search?${params.toString()}`, fetcher);
}

async function getJson<T>(path: string, fetcher: WebFetcher): Promise<T> {
  const response = await fetcher(path);
  if (!response.ok) {
    throw new Error(`Entio web request failed with status ${response.status}.`);
  }
  return response.json() as Promise<T>;
}
