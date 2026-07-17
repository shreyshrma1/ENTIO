import {
  normalizePageRequest,
  type PageRequest,
  type WebPage,
  type WebProjectListResponse,
} from "./contracts";

export type WebFetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

const defaultFetcher: WebFetcher = (input, init) => globalThis.fetch(input, init);

export async function loadProjects(fetcher: WebFetcher = defaultFetcher): Promise<WebProjectListResponse> {
  return getJson("/api/v1/projects", fetcher);
}

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

export interface WebOutlineItem {
  iri: string;
  label: string;
  kind: string;
  sourceId: string;
}

export interface WebOutlineResponse {
  apiVersion: "v1";
  sourceId: string | null;
  page: WebPage<WebOutlineItem>;
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

export interface WebStageChangeRequest {
  sourceId: string;
  editType: string;
  classIri?: string;
  classLabel?: string;
  superclassIri?: string;
  superclassLabel?: string;
  propertyIri?: string;
  propertyLabel?: string;
  domainClassIri?: string;
  domainClassLabel?: string;
  rangeIri?: string;
  rangeLabel?: string;
  individualIri?: string;
  individualLabel?: string;
  resourceIri?: string;
  resourceLabel?: string;
  typeIri?: string;
  typeLabel?: string;
  subjectIri?: string;
  subjectLabel?: string;
  objectIri?: string;
  objectLabel?: string;
  targetIri?: string;
  targetLabel?: string;
  shapeIri?: string;
  shapeLabel?: string;
  targetClassIri?: string;
  targetClassLabel?: string;
  pathIri?: string;
  pathLabel?: string;
  constraintKind?: string;
  constraintValue?: string;
  severity?: string;
  validationMessage?: string;
  label?: string;
  value?: string;
  datatypeIri?: string;
  languageTag?: string;
  comment?: string;
  aiGenerated?: boolean;
  idempotencyKey?: string;
}

export interface WebStagedEntry {
  id: string;
  order: number;
  sourceId: string;
  summary: string;
  editType: string;
  status: string;
  authorId: string;
  latestEditorId: string;
  comment: string | null;
  aiGenerated: boolean;
  normalizedValues: Record<string, string>;
  generatedIris: string[];
  validationMessages: string[];
}

export interface WebDiffEntry {
  kind: string;
  subject: string;
  predicate: string | null;
  objectValue: string | null;
  description: string;
}

export interface WebProposalState {
  id: string;
  status: string;
  stagedChangeIds: string[];
  baselineProjectFingerprint: string | null;
  validationMessages: string[];
  diff: WebDiffEntry[];
  targetSourceIds: string[];
  shaclImpact: WebShaclImpact | null;
  message: string | null;
}

export interface WebShaclFindingSummary {
  resultId: string;
  severity: string;
  message: string;
  focusNode: string;
  path: string | null;
  shapeIri: string;
}

export interface WebShaclImpact {
  currentGraphFingerprint: string;
  previewGraphFingerprint: string;
  newFindings: WebShaclFindingSummary[];
  worsenedFindings: WebShaclFindingSummary[];
  unchangedFindings: WebShaclFindingSummary[];
  resolvedFindings: WebShaclFindingSummary[];
}

export interface WebStagingResponse {
  apiVersion: "v1";
  projectId: string;
  status: string;
  entries: WebStagedEntry[];
  proposal: WebProposalState | null;
}

export type WebJobKind = "reasoning" | "shacl";
export type WebJobScope = "applied" | "proposal";
export type WebJobMode = "asserted-only" | "asserted-and-inferred";
export type WebSemanticJobState = "Queued" | "Running" | "Completed" | "Failed" | "Cancelled" | "Incomplete" | "Stale";

export interface WebSemanticJobRequest {
  kind: WebJobKind;
  scope: WebJobScope;
  mode?: WebJobMode;
}

export interface WebSemanticJobStatus {
  apiVersion: "v1";
  id: string;
  projectId: string;
  kind: "Reasoning" | "Shacl";
  scope: "Applied" | "Proposal";
  status: WebSemanticJobState;
  phase: string;
  message: string | null;
  graphFingerprint: string;
  proposalFingerprint: string | null;
  queuedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  resultSummary: Record<string, unknown>;
  error: string | null;
}

export interface WebFiboModule {
  ontologyIri: string;
  label: string;
  domain: string;
  sourcePath: string;
  maturity: string;
  curated: boolean;
  elementCount: number;
}

export interface WebFiboElement {
  iri: string;
  label: string;
  kind: string;
  moduleIri: string;
  domain: string;
  maturity: string;
  catalogStatus: string;
  sourcePath: string;
  alternateLabels: string[];
  definitions: string[];
  parents: string[];
  domains: string[];
  ranges: string[];
}

export interface WebFiboDependency {
  category: string;
  requirement: string;
  visibility: string;
  selection: string;
  reason: string;
  externalIri: string | null;
  label: string | null;
}

export interface WebFiboDetailsResponse {
  apiVersion: "v1";
  element: WebFiboElement;
  dependencies: WebFiboDependency[];
}

export interface WebFiboProposalRequest {
  intentType: "reuse-class" | "reuse-object-property" | "reuse-datatype-property" | "create-local-subclass";
  sourceId: string;
  targetOntologyIri: string;
  externalIri: string;
  localClassIri?: string;
  selectedDependencyIris: string[];
  idempotencyKey?: string;
}

export type WebAiCredentialTestStatus = "NOT_CONFIGURED" | "NOT_TESTED" | "PASSED" | "FAILED";

export interface WebAiCredentialStatus {
  apiVersion: "v1";
  configured: boolean;
  providerId: string | null;
  testStatus: WebAiCredentialTestStatus;
}

export interface WebAiCredentialTestResponse {
  apiVersion: "v1";
  status: WebAiCredentialTestStatus;
  message: string;
}

export type WebAiOperation =
  | "EXPLAIN_ENTITY"
  | "EXPLAIN_INFERENCE"
  | "EXPLAIN_SHACL_RESULT"
  | "SEARCH_FIBO"
  | "SUGGEST_DEFINITION"
  | "SUGGEST_SUPERCLASS"
  | "SUGGEST_PROPERTY"
  | "SUGGEST_EXTERNAL_REUSE"
  | "SUMMARIZE_PROPOSAL";

export interface WebAiAssistantRequest {
  operation: WebAiOperation;
  entityIri?: string;
  question?: string;
  proposalId?: string;
}

export interface WebAiEvidence {
  category: string;
  label: string;
  value: string;
}

export interface WebAiTypedSuggestion {
  id: string;
  suggestionType: string;
  rationale: string;
  edit: WebStageChangeRequest;
}

export interface WebAiAssistantResponse {
  apiVersion: "v1";
  operation: WebAiOperation;
  answer: string;
  evidence: WebAiEvidence[];
  assertedFacts: string[];
  inferredFacts: string[];
  fiboResults: WebAiEvidence[];
  suggestions: WebAiTypedSuggestion[];
  uncertainty: string[];
  warnings: string[];
}

export async function loadStagedChanges(projectId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebStagingResponse> {
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/staged`, fetcher);
}

export async function stageChange(projectId: string, request: WebStageChangeRequest, fetcher: WebFetcher = defaultFetcher): Promise<WebStagingResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/staged`, "POST", request, fetcher);
}

export async function discardStagedChange(projectId: string, stagedId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebStagingResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/staged/${encodeURIComponent(stagedId)}`, "DELETE", undefined, fetcher);
}

export async function previewStagedChanges(projectId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebStagingResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/proposal/preview`, "POST", undefined, fetcher);
}

export async function approveProposal(projectId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebStagingResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/proposal/approve`, "POST", undefined, fetcher);
}

export async function rejectProposal(projectId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebStagingResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/proposal/reject`, "POST", undefined, fetcher);
}

export async function applyProposal(projectId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebStagingResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/proposal/apply`, "POST", undefined, fetcher);
}

export async function submitSemanticJob(
  projectId: string,
  request: WebSemanticJobRequest,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebSemanticJobStatus> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/semantic-jobs`, "POST", request, fetcher);
}

export async function loadSemanticJob(
  projectId: string,
  jobId: string,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebSemanticJobStatus> {
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/semantic-jobs/${encodeURIComponent(jobId)}`, fetcher);
}

export async function cancelSemanticJob(
  projectId: string,
  jobId: string,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebSemanticJobStatus> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/semantic-jobs/${encodeURIComponent(jobId)}`, "DELETE", undefined, fetcher);
}

export async function loadFiboModules(
  projectId: string,
  options: { curated?: boolean; offset?: number; limit?: number } = {},
  fetcher: WebFetcher = defaultFetcher,
): Promise<{ sourceId: string; release: string; page: WebPage<WebFiboModule> }> {
  const params = new URLSearchParams({
    curated: String(options.curated ?? true),
    offset: String(options.offset ?? 0),
    limit: String(options.limit ?? 15),
  });
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/external/fibo/modules?${params.toString()}`, fetcher);
}

export async function loadFiboModuleElements(
  projectId: string,
  moduleIri: string,
  options: { offset?: number; limit?: number } = {},
  fetcher: WebFetcher = defaultFetcher,
): Promise<{ moduleIri: string; page: WebPage<WebFiboElement> }> {
  const params = new URLSearchParams({ moduleIri, offset: String(options.offset ?? 0), limit: String(options.limit ?? 15) });
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/external/fibo/module-elements?${params.toString()}`, fetcher);
}

export async function searchFibo(
  projectId: string,
  text: string,
  options: { curated?: boolean; offset?: number; limit?: number } = {},
  fetcher: WebFetcher = defaultFetcher,
): Promise<{ query: string; page: WebPage<WebFiboElement> }> {
  const params = new URLSearchParams({ q: text, curated: String(options.curated ?? false), offset: String(options.offset ?? 0), limit: String(options.limit ?? 15) });
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/external/fibo/search?${params.toString()}`, fetcher);
}

export async function loadFiboDetails(projectId: string, iri: string, fetcher: WebFetcher = defaultFetcher): Promise<WebFiboDetailsResponse> {
  const params = new URLSearchParams({ iri });
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/external/fibo/details?${params.toString()}`, fetcher);
}

export async function stageFiboProposal(projectId: string, request: WebFiboProposalRequest, fetcher: WebFetcher = defaultFetcher): Promise<WebStagingResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/external/fibo/proposals`, "POST", request, fetcher);
}

export async function loadAiCredentialStatus(fetcher: WebFetcher = defaultFetcher): Promise<WebAiCredentialStatus> {
  return getJson("/api/v1/ai/credential-status", fetcher);
}

export async function saveAiCredential(providerId: string, apiKey: string, fetcher: WebFetcher = defaultFetcher): Promise<WebAiCredentialStatus> {
  return sendJson("/api/v1/ai/credentials", "PUT", { providerId, apiKey }, fetcher);
}

export async function testAiCredential(fetcher: WebFetcher = defaultFetcher): Promise<WebAiCredentialTestResponse> {
  return sendJson("/api/v1/ai/credentials/test", "POST", undefined, fetcher);
}

export async function askAiAssistant(
  projectId: string,
  request: WebAiAssistantRequest,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebAiAssistantResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/ai/assistant`, "POST", request, fetcher);
}

export async function removeAiCredential(fetcher: WebFetcher = defaultFetcher): Promise<WebAiCredentialStatus> {
  return sendJson("/api/v1/ai/credentials", "DELETE", undefined, fetcher);
}

export async function loadProjectSummary(
  projectId: string,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebProjectSummaryResponse> {
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/summary`, fetcher);
}

export async function loadProjectSources(
  projectId: string,
  request: PageRequest = {},
  fetcher: WebFetcher = defaultFetcher,
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
  fetcher: WebFetcher = defaultFetcher,
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

export async function loadProjectOutline(
  projectId: string,
  options: { sourceId?: string } & PageRequest = {},
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebOutlineResponse> {
  const page = normalizePageRequest(options);
  const params = new URLSearchParams({ offset: String(page.offset), limit: String(page.limit) });
  if (options.sourceId) params.set("sourceId", options.sourceId);
  return getJson(
    `/api/v1/projects/${encodeURIComponent(projectId)}/outline?${params.toString()}`,
    fetcher,
  );
}

export async function loadEntityDetails(
  projectId: string,
  iri: string,
  sourceId?: string,
  fetcher: WebFetcher = defaultFetcher,
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
  fetcher: WebFetcher = defaultFetcher,
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

async function sendJson<T>(path: string, method: string, body: unknown, fetcher: WebFetcher): Promise<T> {
  const response = await fetcher(path, {
    method,
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(error?.message ?? `Entio web request failed with status ${response.status}.`);
  }
  return response.json() as Promise<T>;
}
