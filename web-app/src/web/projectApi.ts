import {
  normalizePageRequest,
  type PageRequest,
  type WebAiProviderSettings,
  type WebPage,
  type WebProjectListResponse,
  type OntologyGraphExpansionCategory,
  type WebOntologyGraphResponse,
  normalizeOntologyGraphResponse,
} from "./contracts";
import { withDevelopmentIdentity } from "./session";

export type WebFetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

export class WebApiError extends Error {
  constructor(public readonly code: string, message: string) { super(message); this.name = "WebApiError"; }
}

const defaultFetcher: WebFetcher = (input, init) => globalThis.fetch(input, withDevelopmentIdentity(init));

export async function loadProjects(fetcher: WebFetcher = defaultFetcher): Promise<WebProjectListResponse> {
  return getJson("/api/v1/projects", fetcher);
}

export async function loadDocumentIngestionTasks(
  projectId: string,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebPage<WebDocumentIngestionTask>> {
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/document-ingestion/tasks?offset=0&limit=50`, fetcher);
}

export async function uploadDocuments(
  projectId: string,
  files: File[],
  metadata: { authorityStatus: string; businessArea?: string; jurisdiction?: string },
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebDocumentIngestionTask> {
  const form = new FormData();
  const documents = files.map((file, index) => ({
    clientDocumentId: `upload-${index + 1}`,
    filename: file.name,
    declaredMediaType: file.type || mediaTypeForFilename(file.name),
    language: "en",
    authorityStatus: metadata.authorityStatus,
    businessArea: metadata.businessArea || null,
    jurisdiction: metadata.jurisdiction || null,
  }));
  form.append("metadata", JSON.stringify({ documents }));
  files.forEach((file, index) => form.append(`document.upload-${index + 1}`, file, file.name));
  const response = await fetcher(`/api/v1/projects/${encodeURIComponent(projectId)}/document-ingestion/tasks`, {
    method: "POST",
    headers: { "Idempotency-Key": globalThis.crypto?.randomUUID?.() ?? `upload-${Date.now()}` },
    body: form,
  });
  if (!response.ok) throw await webRequestError(response);
  return response.json() as Promise<WebDocumentIngestionTask>;
}

export async function cancelDocumentIngestionTask(
  projectId: string,
  taskId: string,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebDocumentIngestionTask> {
  return sendJson(
    `/api/v1/projects/${encodeURIComponent(projectId)}/document-ingestion/tasks/${encodeURIComponent(taskId)}/cancel`,
    "POST",
    undefined,
    fetcher,
  );
}

export async function deleteDocumentIngestionTask(
  projectId: string,
  taskId: string,
  fetcher: WebFetcher = defaultFetcher,
): Promise<void> {
  const response = await fetcher(
    `/api/v1/projects/${encodeURIComponent(projectId)}/document-ingestion/tasks/${encodeURIComponent(taskId)}`,
    { method: "DELETE" },
  );
  if (!response.ok) throw await webRequestError(response);
}

export async function loadDocumentReview(
  projectId: string,
  taskId: string,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebDocumentReviewWorkspace> {
  return getJson(
    `/api/v1/projects/${encodeURIComponent(projectId)}/document-ingestion/tasks/${encodeURIComponent(taskId)}/review?offset=0&limit=100`,
    fetcher,
  );
}

export async function loadDocumentEvidence(
  projectId: string,
  taskId: string,
  evidenceId: string,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebDocumentEvidenceView> {
  return getJson(
    `/api/v1/projects/${encodeURIComponent(projectId)}/document-ingestion/tasks/${encodeURIComponent(taskId)}/evidence/${encodeURIComponent(evidenceId)}`,
    fetcher,
  );
}

export async function decideDocumentRecommendation(
  projectId: string,
  taskId: string,
  recommendationId: string,
  decision: WebDocumentReviewDecision,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebDocumentReviewWorkspace> {
  return sendJson(
    `/api/v1/projects/${encodeURIComponent(projectId)}/document-ingestion/tasks/${encodeURIComponent(taskId)}/recommendations/${encodeURIComponent(recommendationId)}/decision?offset=0&limit=100`,
    "POST",
    decision,
    fetcher,
  );
}

function mediaTypeForFilename(filename: string): string {
  const extension = filename.split(".").pop()?.toLowerCase();
  if (extension === "pdf") return "application/pdf";
  if (extension === "docx") return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  if (extension === "md") return "text/markdown";
  return "text/plain";
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

export interface WebDocumentIngestionProgress {
  stage: string;
  completedDocuments: number;
  totalDocuments: number;
  percent: number;
  message: string;
}

export interface WebDocumentIngestionDocument {
  documentId: string;
  safeFilename: string;
  mediaType: string;
  byteSize: number;
  checksumSha256: string;
  authorityStatus: string;
  status: string;
}

export interface WebDocumentIngestionTask {
  taskId: string;
  projectId: string;
  ownerUserId: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  documents: WebDocumentIngestionDocument[];
  progress: WebDocumentIngestionProgress;
}

export interface WebDocumentEvidenceSummary {
  evidenceId: string;
  evidenceType: string;
  documentId: string | null;
  pageNumber: number | null;
  extractionMethod: string | null;
  ocrConfidence: number | null;
  excerpt: string | null;
  priorRecordId: string | null;
}

export interface WebDocumentReviewRecommendation {
  id: string;
  category: "OntologyStructure" | "BusinessFact";
  type: string;
  action: string;
  proposedLabel: string | null;
  confidence: number;
  confidenceBand: "High" | "Medium" | "Low";
  rationale: string;
  reviewStatus: "Pending" | "Accepted" | "Rejected" | "NeedsClarification" | "Drafted";
  evidence: WebDocumentEvidenceSummary[];
  matches: Array<{ scope: string; entityIri: string; sourceId: string; preferredLabel: string | null; score: number; reason: string }>;
  selectedMatchIri: string | null;
  conflicts: Array<{ id: string; alternatives: string[]; affectedEntityIris: string[]; resolutionOptions: string[] }>;
  mandatoryClarificationReasons: string[];
  clarification: string | null;
  targetSourceId: string | null;
  reconsiderationCount: number;
  priorWorkflowProvenance: string[];
}

export interface WebDocumentReviewWorkspace {
  apiVersion: "v1";
  taskId: string;
  projectId: string;
  exactWorkKey: string;
  graphFingerprint: string;
  documents: Array<{
    documentId: string; safeFilename: string; mediaType: string; authorityStatus: string;
    pageCount: number | null; warningCount: number;
  }>;
  summaries: Array<{ documentId: string; purpose: string; highlights: string[] }>;
  recommendations: WebPage<WebDocumentReviewRecommendation>;
  draftImpact: { acceptedCount: number; pendingCount: number; blockedCount: number; maximumAcceptedEdits: number; readOnly: true };
}

export interface WebDocumentEvidenceView {
  apiVersion: "v1";
  evidenceId: string;
  documentId: string;
  safeFilename: string;
  pageNumber: number | null;
  sectionHeading: string | null;
  extractionMethod: string;
  ocrConfidence: number | null;
  text: string;
  highlightStart: number;
  highlightEnd: number;
  pageImageAvailable: boolean;
  truncated: boolean;
}

export interface WebDocumentReviewDecision {
  action: "accept" | "reject" | "clarify" | "edit" | "rematch" | "merge" | "reconsider";
  expectedWorkKey: string;
  expectedGraphFingerprint: string;
  proposedLabel?: string;
  selectedMatchIri?: string;
  targetSourceId?: string;
  clarification?: string;
  mergedRecommendationIds?: string[];
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
  inferredOverlays?: WebInferredFactsOverlay[];
}

export interface WebOutlineItem {
  iri: string;
  label: string;
  kind: string;
  sourceId: string;
  directType?: WebEntityReference | null;
  inferredState?: "Applied" | "Proposal";
}

export interface WebOutlineResponse {
  apiVersion: "v1";
  sourceId: string | null;
  page: WebPage<WebOutlineItem>;
  inferredOverlays?: WebInferredFactsOverlay[];
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
  entityKind?: string | null;
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
  inferredOverlays?: WebInferredFactsOverlay[];
}

export interface WebInferredFact {
  semanticFactKey: string;
  subject: string;
  predicate: string;
  objectValue: string;
  kind: "SubclassRelationship" | "IndividualType" | "ObjectPropertyAssertion" | "EffectiveDomain" | "EffectiveRange";
  placements: string[];
  graphState: "Applied" | "Proposal";
  sourceId: string | null;
}

export interface WebInferredFactsOverlay {
  graphState: "Applied" | "Proposal";
  state: "Off" | "Current" | "Updating" | "Unavailable" | "Failed";
  facts: WebInferredFact[];
  totalFactCount: number;
  truncated: boolean;
  graphFingerprint: string | null;
  proposalFingerprint: string | null;
  message: string | null;
}

export interface InferredReadOptions {
  includeAppliedInferred?: boolean;
  includeProposalInferred?: boolean;
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

export interface WebShaclConstraintSummary {
  kind: string;
  value: string | null;
  valueIri: string | null;
  valueLabel: string | null;
}

export interface WebShaclTargetSummary {
  kind: "TargetClass" | "TargetNode" | "TargetSubjectsOf" | "TargetObjectsOf";
  iri: string;
  label: string;
}

export interface WebShaclPropertyShapeSummary {
  iri: string;
  path: WebEntityReference;
  constraints: WebShaclConstraintSummary[];
  severity: string;
  message: string | null;
}

export interface WebShaclShapeSummary {
  iri: string;
  label: string;
  sourceId: string;
  targets: WebShaclTargetSummary[];
  constraints: WebShaclConstraintSummary[];
  propertyShapes: WebShaclPropertyShapeSummary[];
  closed: boolean;
  severity: string;
  message: string | null;
}

export interface WebShaclShapeListResponse {
  apiVersion: "v1";
  projectId: string;
  shapes: WebShaclShapeSummary[];
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
  existingValue?: string;
  datatypeIri?: string;
  languageTag?: string;
  dependencyKeys?: string[];
  comment?: string;
  replacesStagedId?: string;
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
  normalizedValues: Record<string, string>;
  generatedIris: string[];
  validationMessages: string[];
  materializationProvenance?: WebInferenceMaterializationProvenance | null;
}

export interface WebDeletionDependenciesRequest {
  sourceId: string;
  targetIri?: string;
  targetLabel?: string;
}

export interface WebDeletionDependency {
  key: string;
  kind: string;
  subject: string;
  subjectLabel: string;
  predicate: string;
  predicateLabel: string;
  objectValue: string;
  objectLabel: string;
}

export interface WebDeletionDependenciesResponse {
  apiVersion: "v1";
  projectId: string;
  targetIri: string;
  targetLabel: string;
  status: string;
  directStatements: WebDeletionDependency[];
  dependentStatements: WebDeletionDependency[];
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
  validationIssues: WebProposalValidationIssue[];
}

export interface WebProposalValidationIssue {
  code: string;
  message: string;
  stagedChangeId: string;
  remediations: WebProposalRemediation[];
}

export interface WebProposalRemediation {
  action: string;
  label: string;
  stagedChangeIds: string[];
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

export interface WebInferenceMaterializationProvenance {
  origin: "MaterializedFromReasoning";
  inferenceKind: "SubclassRelationship" | "IndividualType" | "ObjectPropertyAssertion";
  reasoningJobId: string;
  graphFingerprint: string;
  factId: string;
  stagedByUserId: string;
  stagedAt: string;
  targetSourceId: string;
  entailedBeforeAssertion: boolean;
  importDependence: "LocalOnly" | "Imported" | "Unknown";
  importSourceIds: string[];
}

export interface WebActivityEvent {
  eventId: string;
  projectId: string;
  collaborationSessionId: string;
  sequence: number;
  eventType: string;
  timestamp: string;
  userId: string | null;
  stagedChangeId: string | null;
  proposalId: string | null;
  jobId: string | null;
  data: Record<string, unknown>;
}

export interface WebActivitySnapshot {
  projectId: string;
  events: WebActivityEvent[];
  truncated: boolean;
}

export type WebAiProposalStatus = "QUEUED" | "RUNNING" | "READY" | "FAILED" | "CANCELLED" | "STAGED" | "REJECTED";
export type WebAiResponseMode = "ANSWER" | "PROPOSAL" | "CLARIFICATION";

export interface WebAiStatusUpdate {
  order: number;
  message: string;
  timestamp: string;
  details?: string[];
}

export interface WebAiEvidence {
  subject: string;
  predicate: string;
  objectKind: string;
  objectValue: string;
  source: string;
}

export interface WebAiProposalEdit {
  id: string;
  sourceId: string;
  operation: string;
  subject: string;
  predicate: string;
  objectKind: string;
  objectValue: string;
  datatype: string | null;
  language: string | null;
  summary: string;
  rationale: string | null;
}

export interface WebAiConversationMessage {
  role: string;
  content: string;
  timestamp: string;
  evidence?: WebAiEvidence[];
}

export interface WebAiProposalValidation {
  valid: boolean;
  messages: string[];
  diff: WebDiffEntry[];
}

export interface WebAiProposalRunResponse {
  apiVersion: "v1";
  runId: string;
  projectId: string;
  status: WebAiProposalStatus;
  responseMode: WebAiResponseMode;
  prompt: string | null;
  messages: WebAiConversationMessage[];
  summary: string | null;
  updates: WebAiStatusUpdate[];
  edits: WebAiProposalEdit[];
  validation: WebAiProposalValidation | null;
  message: string | null;
}

export interface WebAiChatSummary {
  apiVersion: "v1";
  runId: string;
  projectId: string;
  title: string;
  status: WebAiProposalStatus;
  updatedAt: string;
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

export interface WebSemanticJobFinding {
  resultId: string;
  severity: string;
  message: string;
  focusNode: string;
  path: string | null;
  shapeIri: string;
  shapeSourceId: string;
  constraint: string;
  value: string | null;
  sourceId: string | null;
}

export interface WebReasoningFact {
  kind: string;
  subject: string;
  subjectLabel?: string | null;
  predicate: string | null;
  predicateLabel?: string | null;
  objectValue: string;
  objectLabel?: string | null;
  origin: "Asserted" | "Inferred";
  sourceId: string | null;
}

export type WebInferenceStageability =
  | "Stageable"
  | "AlreadyAsserted"
  | "AlreadyStaged"
  | "Stale"
  | "UnsupportedType"
  | "UnsupportedTerm"
  | "MissingEntity"
  | "InvalidPredicate"
  | "NoWritableSource"
  | "AmbiguousSource"
  | "ImportDependencyUnsafe";

export interface WebInferenceMaterializationCandidate {
  factId: string;
  kind: "SubclassRelationship" | "IndividualType" | "ObjectPropertyAssertion";
  subject: string;
  subjectLabel: string;
  predicate: string;
  predicateLabel: string;
  objectValue: string;
  objectLabel: string;
  origin: "Inferred";
  stageability: WebInferenceStageability;
  reason: string;
  sourceCandidates: Array<{ sourceId: string; selected: boolean }>;
  selectedSourceId: string | null;
  existingStagedChangeId: string | null;
  importDependence: "LocalOnly" | "Imported" | "Unknown";
  importSourceIds: string[];
}

export interface WebSemanticJobDetails {
  apiVersion: "v1";
  job: WebSemanticJobStatus;
  facts: WebReasoningFact[];
  factOffset: number;
  factLimit: number;
  totalFactCount: number;
  nextFactOffset: number | null;
  materializationCandidates: WebInferenceMaterializationCandidate[];
  shaclFindings: WebSemanticJobFinding[];
  warnings: string[];
  errors: string[];
  truncated: boolean;
}

export interface WebInferenceMaterializationRequest {
  selections: Array<{ factId: string; targetSourceId?: string }>;
  idempotencyKey: string;
}

export interface WebInferenceMaterializationResponse {
  apiVersion: "v1";
  projectId: string;
  reasoningJobId: string;
  graphFingerprint: string;
  mappings: Array<{ factId: string; stagedChangeId: string }>;
  staging: WebStagingResponse;
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

export async function loadStagedChanges(projectId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebStagingResponse> {
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/staged`, fetcher);
}

export async function loadProjectActivity(projectId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebActivitySnapshot> {
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/activity?limit=1000`, fetcher);
}

export async function loadDeletionDependencies(
  projectId: string,
  request: WebDeletionDependenciesRequest,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebDeletionDependenciesResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/deletion-dependencies`, "POST", request, fetcher);
}

export async function stageChange(projectId: string, request: WebStageChangeRequest, fetcher: WebFetcher = defaultFetcher): Promise<WebStagingResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/staged`, "POST", request, fetcher);
}

export async function discardStagedChange(projectId: string, stagedId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebStagingResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/staged/${encodeURIComponent(stagedId)}`, "DELETE", undefined, fetcher);
}

export async function previewStagedChanges(projectId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebStagingResponse> {
  return sendJson(
    `/api/v1/projects/${encodeURIComponent(projectId)}/proposal/preview`,
    "POST",
    undefined,
    fetcher,
    {},
    30_000,
  );
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

export async function ensureAppliedReasoning(
  projectId: string,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebSemanticJobStatus> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/semantic-jobs/ensure-applied-reasoning`, "POST", undefined, fetcher);
}

export async function loadSemanticJob(
  projectId: string,
  jobId: string,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebSemanticJobStatus> {
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/semantic-jobs/${encodeURIComponent(jobId)}`, fetcher);
}

export async function loadSemanticJobDetails(
  projectId: string,
  jobId: string,
  options: { factOrigin?: "Asserted" | "Inferred"; factOffset?: number; factQuery?: string; limit?: number } = {},
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebSemanticJobDetails> {
  const params = new URLSearchParams();
  if (options.factOrigin) params.set("factOrigin", options.factOrigin);
  if (options.factOffset) params.set("factOffset", String(options.factOffset));
  if (options.factQuery) params.set("factQuery", options.factQuery);
  params.set("limit", String(options.limit ?? 50));
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/semantic-jobs/${encodeURIComponent(jobId)}/details?${params}`, fetcher);
}

export async function cancelSemanticJob(
  projectId: string,
  jobId: string,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebSemanticJobStatus> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/semantic-jobs/${encodeURIComponent(jobId)}`, "DELETE", undefined, fetcher);
}

export async function materializeInferenceFacts(
  projectId: string,
  jobId: string,
  request: WebInferenceMaterializationRequest,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebInferenceMaterializationResponse> {
  return sendJson(
    `/api/v1/projects/${encodeURIComponent(projectId)}/semantic-jobs/${encodeURIComponent(jobId)}/materializations`,
    "POST",
    request,
    fetcher,
  );
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

export async function loadAiProviderSettings(fetcher: WebFetcher = defaultFetcher): Promise<WebAiProviderSettings> {
  return getJson("/api/v1/ai/provider-settings", fetcher);
}

export async function saveAiCredential(providerId: string, apiKey: string, fetcher: WebFetcher = defaultFetcher): Promise<WebAiProviderSettings> {
  return sendJson("/api/v1/ai/credentials", "PUT", { providerId, apiKey }, fetcher);
}

export async function discoverAiModels(fetcher: WebFetcher = defaultFetcher): Promise<WebAiProviderSettings> {
  return sendJson("/api/v1/ai/models/discover", "POST", undefined, fetcher);
}

export async function selectAiModel(modelId: string, idempotencyKey: string, fetcher: WebFetcher = defaultFetcher): Promise<WebAiProviderSettings> {
  return sendJson("/api/v1/ai/model-selection", "PUT", { modelId }, fetcher, { "Idempotency-Key": idempotencyKey });
}

export async function retestAiModel(idempotencyKey: string, fetcher: WebFetcher = defaultFetcher): Promise<WebAiProviderSettings> {
  return sendJson("/api/v1/ai/model-selection/test", "POST", undefined, fetcher, { "Idempotency-Key": idempotencyKey });
}

export async function clearAiModelSelection(fetcher: WebFetcher = defaultFetcher): Promise<WebAiProviderSettings> {
  return sendJson("/api/v1/ai/model-selection", "DELETE", undefined, fetcher);
}

export async function testAiCredential(fetcher: WebFetcher = defaultFetcher): Promise<WebAiCredentialTestResponse> {
  return sendJson("/api/v1/ai/credentials/test", "POST", undefined, fetcher);
}

export async function removeAiCredential(fetcher: WebFetcher = defaultFetcher): Promise<WebAiProviderSettings> {
  return sendJson("/api/v1/ai/credentials", "DELETE", undefined, fetcher);
}

export async function startAiProposal(projectId: string, prompt: string, runId?: string | null, fetcher: WebFetcher = defaultFetcher): Promise<WebAiProposalRunResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/ai/proposals`, "POST", { prompt, ...(runId ? { runId } : {}) }, fetcher);
}

export async function loadAiProposal(projectId: string, runId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebAiProposalRunResponse> {
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/ai/proposals/${encodeURIComponent(runId)}`, fetcher);
}

export async function listAiChats(projectId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebAiChatSummary[]> {
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/ai/proposals`, fetcher);
}

export async function removeAiProposalEdit(projectId: string, runId: string, editId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebAiProposalRunResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/ai/proposals/${encodeURIComponent(runId)}/edits/${encodeURIComponent(editId)}/remove`, "POST", undefined, fetcher);
}

export async function stageAiProposal(projectId: string, runId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebAiProposalRunResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/ai/proposals/${encodeURIComponent(runId)}/stage`, "POST", undefined, fetcher);
}

export async function rejectAiProposal(projectId: string, runId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebAiProposalRunResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/ai/proposals/${encodeURIComponent(runId)}/reject`, "POST", undefined, fetcher);
}

export async function cancelAiProposal(projectId: string, runId: string, fetcher: WebFetcher = defaultFetcher): Promise<WebAiProposalRunResponse> {
  return sendJson(`/api/v1/projects/${encodeURIComponent(projectId)}/ai/proposals/${encodeURIComponent(runId)}/cancel`, "POST", undefined, fetcher);
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

export interface OntologyGraphInitialOptions {
  sourceIds: string[];
  seed?: { sourceId: string; entityIri: string };
  expectedFingerprint?: string;
  continuation?: string;
  signal?: AbortSignal;
  includeAppliedInferred?: boolean;
  includeProposalInferred?: boolean;
}

export interface OntologyGraphNeighborhoodOptions {
  sourceIds: string[];
  entity: { sourceId: string; entityIri: string };
  categories: OntologyGraphExpansionCategory[];
  expectedFingerprint: string;
  continuation?: string;
  signal?: AbortSignal;
  includeAppliedInferred?: boolean;
  includeProposalInferred?: boolean;
}

export async function loadOntologyGraph(
  projectId: string,
  options: OntologyGraphInitialOptions,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebOntologyGraphResponse> {
  const params = graphParams(options.sourceIds, options.expectedFingerprint, options.continuation, options);
  if (options.seed) {
    params.set("seedSourceId", options.seed.sourceId);
    params.set("seedIri", options.seed.entityIri);
  }
  return getGraphJson(`/api/v1/projects/${encodeURIComponent(projectId)}/graph?${params}`, options.signal, fetcher);
}

export async function loadOntologyGraphNeighborhood(
  projectId: string,
  options: OntologyGraphNeighborhoodOptions,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebOntologyGraphResponse> {
  const params = graphParams(options.sourceIds, options.expectedFingerprint, options.continuation, options);
  params.set("entitySourceId", options.entity.sourceId);
  params.set("entityIri", options.entity.entityIri);
  options.categories.forEach((category) => params.append("category", category));
  return getGraphJson(`/api/v1/projects/${encodeURIComponent(projectId)}/graph/neighborhood?${params}`, options.signal, fetcher);
}

function graphParams(sourceIds: string[], fingerprint?: string, continuation?: string, inferred: InferredReadOptions = {}): URLSearchParams {
  const params = new URLSearchParams();
  sourceIds.forEach((sourceId) => params.append("sourceId", sourceId));
  if (fingerprint) params.set("expectedFingerprint", fingerprint);
  if (continuation) params.set("continuation", continuation);
  if (inferred.includeAppliedInferred) params.set("includeAppliedInferred", "true");
  if (inferred.includeProposalInferred) params.set("includeProposalInferred", "true");
  return params;
}

async function getGraphJson(path: string, signal: AbortSignal | undefined, fetcher: WebFetcher): Promise<WebOntologyGraphResponse> {
  const response = await fetcher(path, { signal });
  if (!response.ok) throw await webRequestError(response);
  return normalizeOntologyGraphResponse(await response.json());
}

export async function loadHierarchy(
  projectId: string,
  options: { sourceId?: string; parentIri?: string } & PageRequest & InferredReadOptions = {},
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebHierarchyResponse> {
  const page = normalizePageRequest(options);
  const params = new URLSearchParams({ offset: String(page.offset), limit: String(page.limit) });
  if (options.sourceId) params.set("sourceId", options.sourceId);
  if (options.parentIri) params.set("parentIri", options.parentIri);
  if (options.includeAppliedInferred) params.set("includeAppliedInferred", "true");
  if (options.includeProposalInferred) params.set("includeProposalInferred", "true");
  return getJson(
    `/api/v1/projects/${encodeURIComponent(projectId)}/hierarchy?${params.toString()}`,
    fetcher,
  );
}

export async function loadProjectOutline(
  projectId: string,
  options: { sourceId?: string } & PageRequest & InferredReadOptions = {},
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebOutlineResponse> {
  const page = normalizePageRequest(options);
  const params = new URLSearchParams({ offset: String(page.offset), limit: String(page.limit) });
  if (options.sourceId) params.set("sourceId", options.sourceId);
  if (options.includeAppliedInferred) params.set("includeAppliedInferred", "true");
  if (options.includeProposalInferred) params.set("includeProposalInferred", "true");
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
  inferred: InferredReadOptions = {},
): Promise<WebEntityDetailResponse> {
  const params = new URLSearchParams({ iri });
  if (sourceId) params.set("sourceId", sourceId);
  if (inferred.includeAppliedInferred) params.set("includeAppliedInferred", "true");
  if (inferred.includeProposalInferred) params.set("includeProposalInferred", "true");
  return getJson(
    `/api/v1/projects/${encodeURIComponent(projectId)}/entities?${params.toString()}`,
    fetcher,
  );
}

export async function loadShaclShapes(
  projectId: string,
  fetcher: WebFetcher = defaultFetcher,
): Promise<WebShaclShapeListResponse> {
  return getJson(`/api/v1/projects/${encodeURIComponent(projectId)}/shacl/shapes`, fetcher);
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
    throw await webRequestError(response);
  }
  return response.json() as Promise<T>;
}

async function sendJson<T>(
  path: string,
  method: string,
  body: unknown,
  fetcher: WebFetcher,
  extraHeaders: Record<string, string> = {},
  timeoutMs?: number,
  signal?: AbortSignal,
): Promise<T> {
  const controller = timeoutMs === undefined ? null : new AbortController();
  const timeout = controller === null ? null : window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetcher(path, {
      method,
      headers: body === undefined ? extraHeaders : { "Content-Type": "application/json", ...extraHeaders },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: signal ?? controller?.signal,
    });
    if (!response.ok) {
      throw await webRequestError(response);
    }
    return response.json() as Promise<T>;
  } catch (error) {
    if (controller?.signal.aborted) {
      throw new Error("Proposal preparation timed out. Retry when the semantic engine is available.");
    }
    throw error;
  } finally {
    if (timeout !== null) window.clearTimeout(timeout);
  }
}

async function webRequestError(response: Response): Promise<Error> {
  const error = await response.json().catch(() => null) as { code?: string; message?: string } | null;
  return new WebApiError(error?.code ?? "web-request-failed", error?.message ?? `Entio web request failed with status ${response.status}.`);
}
