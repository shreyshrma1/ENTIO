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

export type OntologyGraphNodeKind = "Class" | "ObjectProperty" | "DatatypeProperty" | "Individual";
export type OntologyGraphEdgeKind = "SubclassOf" | "Domain" | "Range" | "Type" | "ObjectAssertion";
export type OntologyGraphExpansionCategory = "ClassHierarchy" | "PropertySchema" | "AssertedTypes" | "ObjectAssertions";

export interface WebOntologyGraphNodeId { id: string; sourceId: string; entityIri: string }
export interface WebOntologyGraphNodeSummary {
  directSuperclassLabels: string[]; domainLabels: string[]; rangeLabels: string[];
  assertedTypeLabels: string[]; datatypeRangeLabels: string[];
  loadedRelationshipCount: number; availableRelationshipCount: number;
}
export interface WebOntologyGraphNode {
  identity: WebOntologyGraphNodeId; kind: OntologyGraphNodeKind; label: string;
  definitionExcerpt: string | null; summary: WebOntologyGraphNodeSummary;
}
export interface WebOntologyGraphEdge {
  id: string; kind: OntologyGraphEdgeKind; sourceNodeId: string; targetNodeId: string;
  label: string; predicateIri: string | null; provenance: "Asserted";
}
export interface WebOntologyGraphResponse {
  apiVersion: typeof WEB_API_VERSION; projectId: string; graphFingerprint: string;
  sources: Array<{ id: string; displayName: string }>;
  loadKind: "RootOverview" | "EntityCentered" | "Neighborhood";
  seed: WebOntologyGraphNodeId | null; nodes: WebOntologyGraphNode[]; edges: WebOntologyGraphEdge[];
  limits: { nodeLimit: number; edgeLimit: number }; totalNodeCount: number; totalEdgeCount: number;
  continuation: string | null; ambiguousCrossSourceRelationshipCount: number;
}

export function normalizeOntologyGraphResponse(value: unknown): WebOntologyGraphResponse {
  if (!value || typeof value !== "object") throw new Error("malformed-graph-response");
  const response = value as Partial<WebOntologyGraphResponse>;
  if (response.apiVersion !== WEB_API_VERSION || !Array.isArray(response.nodes) || !Array.isArray(response.edges)) {
    throw new Error("malformed-graph-response");
  }
  if (response.nodes.some((node) => typeof node?.identity?.id !== "string")) {
    throw new Error("malformed-graph-reference");
  }
  const ids = new Set(response.nodes.map((node) => node.identity.id));
  if (response.edges.some((edge) => !ids.has(edge.sourceNodeId) || !ids.has(edge.targetNodeId))) {
    throw new Error("malformed-graph-reference");
  }
  return response as WebOntologyGraphResponse;
}
