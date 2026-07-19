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

export type WebAiCredentialStatus = "NOT_CONFIGURED" | "VALID" | "INVALID";
export type WebAiDiscoveryStatus = "NOT_STARTED" | "COMPLETED" | "NO_COMPATIBLE_MODELS" | "FAILED" | "STALE";
export type WebAiSelectionStatus = "NOT_SELECTED" | "READY" | "UNAVAILABLE" | "INCOMPATIBLE" | "VERIFICATION_FAILED";

export interface WebAiModelDescriptor {
  providerId: string;
  modelId: string;
  displayName: string;
  description: string;
  metadataKnown: boolean;
  recommended: boolean;
  capabilityTier: string | null;
  timeoutClass: string | null;
  relativeSpeed: string | null;
  relativeCost: string | null;
  verificationStatus: "NOT_VERIFIED" | "VERIFIED" | "FAILED";
  compatibilityStatus: string;
  policyVersion: string;
}

export interface WebAiProviderSettings {
  apiVersion: typeof WEB_API_VERSION;
  providerId: string | null;
  credentialStatus: WebAiCredentialStatus;
  discoveryStatus: WebAiDiscoveryStatus;
  discoveredAt: string | null;
  policyVersion: string;
  models: WebAiModelDescriptor[];
  unsupportedProviderModelCount: number;
  selectedModel: WebAiModelDescriptor | null;
  selectionStatus: WebAiSelectionStatus;
  selectedModelVerifiedAt: string | null;
  errorCode: string | null;
  availableActions: string[];
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

export interface WebAiConversationMessage {
  id: string;
  role: "USER" | "ASSISTANT" | "TOOL";
  content: string;
  operation: string | null;
  evidenceReferenceIds: string[];
  createdAt: string;
}

export interface WebAiConversation {
  id: string;
  projectId: string;
  messages: WebAiConversationMessage[];
  currentDraftId: string | null;
  modelId: string | null;
  status: "ACTIVE" | "CLOSED";
  createdAt: string;
  updatedAt: string;
}

export interface WebAiConversationResponse {
  apiVersion: typeof WEB_API_VERSION;
  conversation: WebAiConversation;
}

export interface WebAiConversationListResponse {
  apiVersion: typeof WEB_API_VERSION;
  conversations: WebAiConversation[];
}

export type WebAiConversationDecision =
  | "MESSAGE"
  | "CONFIRM_PLAN"
  | "REVISE_PLAN"
  | "ANSWER_CLARIFICATION"
  | "CANCEL";

export interface WebAiScreenContextRequest {
  screen: string;
  selectedEntityIri?: string;
  selectedSourceId?: string;
  selectedProposalId?: string;
}

export interface WebAiMessageRequest {
  message: string;
  decision?: WebAiConversationDecision;
  screenContext?: WebAiScreenContextRequest;
}

export interface WebAiPlan {
  request: string;
  steps: string[];
  openDecisions: string[];
  estimatedEditCount: number | null;
}

export interface WebAiLimit {
  kind: string;
  maximum: number;
  observed: number;
}

export interface WebAiRun {
  id: string;
  conversationId: string;
  projectId: string;
  status: string;
  capabilityCallCount: number;
  draftEditCount: number;
  correctionCycleCount: number;
  cancellationRequested: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface WebAiConversationTurnResponse {
  apiVersion: typeof WEB_API_VERSION;
  conversation: WebAiConversation;
  run: WebAiRun;
  intent: string;
  answer: string;
  plan: WebAiPlan | null;
  clarificationQuestion: string | null;
  draftId: string | null;
  limits: WebAiLimit[];
}

export interface WebAiRunResponse {
  apiVersion: typeof WEB_API_VERSION;
  run: WebAiRun;
}

export interface WebAiDraftItem {
  id: string;
  order: number;
  capabilityName: string;
  targetSourceId: string;
  summary: string;
  rationale: string;
  dependencyItemIds: string[];
  aiGenerated: boolean;
  acceptingUserId: string | null;
  runId: string | null;
}

export interface WebAiDraftRevision {
  revision: number;
  action: string;
  explanation: string;
  itemIds: string[];
  undoneRevision: number | null;
  createdAt: string;
}

export interface WebAiDraft {
  id: string;
  conversationId: string;
  projectId: string;
  baselineFingerprint: string;
  allowedSourceIds: string[];
  status: string;
  draftFingerprint: string | null;
  analysisReferenceIds: string[];
  items: WebAiDraftItem[];
  revisions: WebAiDraftRevision[];
  createdAt: string;
  updatedAt: string;
}

export interface WebAiDraftResponse {
  apiVersion: typeof WEB_API_VERSION;
  draft: WebAiDraft;
}

export interface WebAiAnalysisFinding {
  id: string;
  code: string;
  severity: string;
  message: string;
  source: string | null;
}

export interface WebAiDiffEntry {
  kind: string;
  subject: string;
  predicate: string | null;
  objectValue: string | null;
  description: string;
}

export interface WebAiAnalysisReference {
  stage: string;
  id: string;
}

export interface WebAiDraftAnalysis {
  id: string;
  draftId: string;
  revision: number;
  status: string;
  baselineFingerprint: string;
  draftFingerprint: string;
  previewGraphFingerprint: string | null;
  readyForReview: boolean;
  validationOk: boolean;
  findings: WebAiAnalysisFinding[];
  diff: WebAiDiffEntry[];
  references: WebAiAnalysisReference[];
  createdAt: string;
}

export interface WebAiDraftAnalysisResponse {
  apiVersion: typeof WEB_API_VERSION;
  analysis: WebAiDraftAnalysis;
}

export interface WebAiReviewSubmissionRequest {
  analysisId: string;
  runId: string;
  rationale: string;
  expectedBaselineFingerprint: string;
  expectedDraftFingerprint: string;
  expectedPreviewGraphFingerprint: string;
  expectedAnalysisReferenceIds: string[];
}

export interface WebAiReviewSubmissionResponse {
  apiVersion: typeof WEB_API_VERSION;
  submissionId: string;
  proposalId: string;
  reviewState: string;
  projectId: string;
  draftId: string;
  draftRevision: number;
  submittingUserId: string;
  conversationId: string;
  runId: string;
  rationale: string;
  diff: WebAiDiffEntry[];
  analysisReferenceIds: string[];
  reviewRoute: string;
}

export interface WebAiRunEvent {
  sequence: number;
  runId: string;
  type: string;
  message: string;
  referenceIds: string[];
  createdAt: string;
}

export interface WebAiResynchronization {
  apiVersion: typeof WEB_API_VERSION;
  runId: string;
  reason: string;
  authoritativeRunRoute: string;
  authoritativeConversationRoute: string;
}

export interface WebAiTaskCreateRequest {
  conversationId: string;
  objective: string;
  allowedSourceIds: string[];
}

export interface WebAiTaskCommandRequest {
  expectedRevision: number;
  message?: string;
  answer?: string;
  planRevision?: number;
}

export interface WebAiTask {
  id: string;
  conversationId: string;
  projectId: string;
  objective: string;
  type: string;
  size: string;
  status: string;
  revision: number;
  modelId: string;
  currentWorkPackageId: string | null;
  completedWorkPackageIds: string[];
  failedWorkPackageIds: string[];
  privateDraftId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface WebAiTaskResponse { apiVersion: typeof WEB_API_VERSION; task: WebAiTask }

export interface WebAiTaskWorkspace {
  task: WebAiTask;
  projectFingerprint: string;
  assumptions: string[];
  openQuestions: string[];
  selectedEntityIris: string[];
  planId: string | null;
  planRevision: number | null;
  analysisReferenceIds: string[];
  repairCycleCount: number;
  toolCallCount: number;
  pauseCode: string | null;
  limits: WebAiLimit[];
}

export interface WebAiTaskWorkspaceResponse { apiVersion: typeof WEB_API_VERSION; workspace: WebAiTaskWorkspace }

export interface WebAiTaskEvent {
  sequence: number;
  taskId: string;
  type: string;
  status: string;
  message: string;
  referenceIds: string[];
  createdAt: string;
}

export interface WebAiTaskResynchronization {
  apiVersion: typeof WEB_API_VERSION;
  taskId: string;
  reason: string;
  authoritativeTaskRoute: string;
  authoritativeWorkspaceRoute: string;
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
