import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  loadEntityDetails,
  loadShaclShapes,
  loadHierarchy,
  loadProjectOutline,
  loadProjectSources,
  loadProjectSummary,
  loadProjects,
  searchProject,
  loadStagedChanges,
  loadProjectActivity,
  stageChange,
  discardStagedChange,
  previewStagedChanges,
  approveProposal,
  rejectProposal,
  applyProposal,
  cancelSemanticJob,
  loadSemanticJob,
  loadSemanticJobDetails,
  submitSemanticJob,
  ensureAppliedReasoning,
  materializeInferenceFacts,
  type WebInferenceMaterializationRequest,
  type WebSemanticJobRequest,
  type WebSemanticJobStatus,
  loadFiboDetails,
  loadFiboModuleElements,
  loadFiboModules,
  searchFibo,
  stageFiboProposal,
  type WebFiboDetailsResponse,
  type WebFiboElement,
  type WebFiboModule,
  type WebFiboProposalRequest,
  loadAiProviderSettings,
  loadOntologyGraph,
  loadOntologyGraphNeighborhood,
  type OntologyGraphInitialOptions,
  type OntologyGraphNeighborhoodOptions,
  discoverAiModels,
  selectAiModel,
  retestAiModel,
  clearAiModelSelection,
  removeAiCredential,
  saveAiCredential,
  type WebEntityDetailResponse,
  type WebHierarchyResponse,
  type WebOutlineResponse,
  type WebProjectSummaryResponse,
  type WebSemanticSearchResponse,
  type WebShaclShapeListResponse,
  loadDocumentIngestionTasks,
  uploadDocuments,
  cancelDocumentIngestionTask,
  deleteDocumentIngestionTask,
  loadDocumentReview,
  loadDocumentEvidence,
  decideDocumentRecommendation,
  type WebDocumentReviewDecision,
  buildDocumentDraft,
  activateDomainOntology,
  deactivateDomainOntology,
  loadDomainFoundation,
  loadDomainOntologies,
  loadDomainOntologyStatus,
  loadDomainMigration,
  loadDomainRecommendation,
  planDomainFoundation,
  previewDomainActivation,
  previewDomainDeactivation,
  previewDomainMigration,
  previewDomainRecommendationDependencies,
  recommendDomainOntology,
  searchDomainOntology,
  stageDomainRecommendation,
  stageDomainRecommendationAction,
  type WebDomainRecommendationRequest,
  type WebDomainStageRequest,
} from "./projectApi";
import type {
  WebAiProviderSettings,
  WebPage,
} from "./contracts";

export const queryKeys = {
  projects: ["projects"] as const,
  summary: (projectId: string) => ["project", projectId, "summary"] as const,
  sources: (projectId: string) => ["project", projectId, "sources"] as const,
  hierarchy: (projectId: string, sourceId?: string, parentIri?: string, applied = false, proposal = false) =>
    ["project", projectId, "hierarchy", sourceId ?? null, parentIri ?? null, applied, proposal] as const,
  outline: (projectId: string, sourceId?: string, applied = false, proposal = false) =>
    ["project", projectId, "outline", sourceId ?? null, applied, proposal] as const,
  entity: (projectId: string, iri: string, applied = false, proposal = false) =>
    ["project", projectId, "entity", iri, applied, proposal] as const,
  shaclShapes: (projectId: string) => ["project", projectId, "shacl", "shapes"] as const,
  search: (projectId: string, text: string) => ["project", projectId, "search", text] as const,
  staged: (projectId: string) => ["project", projectId, "staged"] as const,
  activity: (projectId: string) => ["project", projectId, "activity"] as const,
  semanticJob: (projectId: string, jobId: string) => ["project", projectId, "semantic-job", jobId] as const,
  fiboModules: (projectId: string) => ["project", projectId, "fibo", "modules"] as const,
  fiboElements: (projectId: string, moduleIri: string) => ["project", projectId, "fibo", "elements", moduleIri] as const,
  fiboSearch: (projectId: string, text: string) => ["project", projectId, "fibo", "search", text] as const,
  fiboDetails: (projectId: string, iri: string) => ["project", projectId, "fibo", "details", iri] as const,
  domainCatalog: ["domain-ontologies"] as const,
  domainStatus: (projectId: string) => ["project", projectId, "domain-ontology"] as const,
  domainMigration: (projectId: string) => ["project", projectId, "domain-migration"] as const,
  domainFoundation: (projectId: string) => ["project", projectId, "domain-ontology", "foundation"] as const,
  domainSearch: (projectId: string, text: string) => ["project", projectId, "domain-recommendations", text] as const,
  contextualDomainSearch: (projectId: string, request: WebDomainRecommendationRequest | null) =>
    ["project", projectId, "contextual-domain-recommendations", request] as const,
  domainRecommendation: (projectId: string, recommendationId: string) =>
    ["project", projectId, "domain-recommendation", recommendationId] as const,
  aiProviderSettings: ["ai", "provider-settings"] as const,
  documentTasks: (projectId: string) => ["project", projectId, "document-ingestion", "tasks"] as const,
  documentReview: (projectId: string, taskId: string) => ["project", projectId, "document-ingestion", taskId, "review"] as const,
  documentEvidence: (projectId: string, taskId: string, evidenceId: string) =>
    ["project", projectId, "document-ingestion", taskId, "evidence", evidenceId] as const,
  ontologyGraph: (projectId: string, options: Omit<OntologyGraphInitialOptions, "signal">) =>
    ["project", projectId, "ontology-graph", options] as const,
  ontologyGraphNeighborhood: (projectId: string, options: Omit<OntologyGraphNeighborhoodOptions, "signal">) =>
    ["project", projectId, "ontology-graph-neighborhood", options] as const,
};

export function useDocumentIngestionTasks(projectId: string) {
  return useQuery({
    queryKey: queryKeys.documentTasks(projectId),
    queryFn: () => loadDocumentIngestionTasks(projectId),
    enabled: Boolean(projectId),
    refetchInterval: (query) =>
      query.state.data?.items.some((task) => ACTIVE_DOCUMENT_TASK_STATUSES.has(task.status)) ? 750 : false,
  });
}

const ACTIVE_DOCUMENT_TASK_STATUSES = new Set([
  "uploaded",
  "extracting",
  "analyzing",
  "matching",
  "comparing",
  "preparing-recommendations",
  "building-draft",
  "validating",
]);

export function useUploadDocuments(projectId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ files, authorityStatus, businessArea, jurisdiction }: {
      files: File[]; authorityStatus: string; businessArea?: string; jurisdiction?: string;
    }) => uploadDocuments(projectId, files, { authorityStatus, businessArea, jurisdiction }),
    onSuccess: () => client.invalidateQueries({ queryKey: queryKeys.documentTasks(projectId) }),
  });
}

export function useCancelDocumentTask(projectId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (taskId: string) => cancelDocumentIngestionTask(projectId, taskId),
    onSuccess: () => client.invalidateQueries({ queryKey: queryKeys.documentTasks(projectId) }),
  });
}

export function useDeleteDocumentTask(projectId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (taskId: string) => deleteDocumentIngestionTask(projectId, taskId),
    onSuccess: () => client.invalidateQueries({ queryKey: queryKeys.documentTasks(projectId) }),
  });
}

export function useDocumentReview(projectId: string, taskId: string | null, ready: boolean = true) {
  return useQuery({
    queryKey: queryKeys.documentReview(projectId, taskId ?? ""),
    queryFn: () => loadDocumentReview(projectId, taskId!),
    enabled: Boolean(projectId && taskId && ready),
  });
}

export function useDocumentEvidence(projectId: string, taskId: string | null, evidenceId: string | null) {
  return useQuery({
    queryKey: queryKeys.documentEvidence(projectId, taskId ?? "", evidenceId ?? ""),
    queryFn: () => loadDocumentEvidence(projectId, taskId!, evidenceId!),
    enabled: Boolean(projectId && taskId && evidenceId),
  });
}

export function useDocumentReviewDecision(projectId: string, taskId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ recommendationId, decision }: { recommendationId: string; decision: WebDocumentReviewDecision }) =>
      decideDocumentRecommendation(projectId, taskId, recommendationId, decision),
    onSuccess: (workspace) => client.setQueryData(queryKeys.documentReview(projectId, taskId), workspace),
  });
}

export function useBuildDocumentDraft(projectId: string, taskId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (request: { expectedWorkKey: string; expectedGraphFingerprint: string }) =>
      buildDocumentDraft(projectId, taskId, request),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: queryKeys.documentReview(projectId, taskId) });
      void client.invalidateQueries({ queryKey: queryKeys.staged(projectId) });
    },
  });
}

export function useOntologyGraph(projectId: string, options: Omit<OntologyGraphInitialOptions, "signal">, enabled = true) {
  return useQuery({
    queryKey: queryKeys.ontologyGraph(projectId, options),
    queryFn: ({ signal }) => loadOntologyGraph(projectId, { ...options, signal }),
    enabled: enabled && projectId.length > 0 && options.sourceIds.length > 0,
    retry: 1,
  });
}

export function useOntologyGraphNeighborhood(projectId: string, options: Omit<OntologyGraphNeighborhoodOptions, "signal">, enabled = true) {
  return useQuery({
    queryKey: queryKeys.ontologyGraphNeighborhood(projectId, options),
    queryFn: ({ signal }) => loadOntologyGraphNeighborhood(projectId, { ...options, signal }),
    enabled: enabled && projectId.length > 0 && options.sourceIds.length > 0,
    retry: 1,
  });
}

export function useProjects() {
  return useQuery({ queryKey: queryKeys.projects, queryFn: () => loadProjects() });
}

export function useProjectSummary(projectId: string) {
  return useQuery<WebProjectSummaryResponse>({
    queryKey: queryKeys.summary(projectId),
    queryFn: () => loadProjectSummary(projectId),
    enabled: projectId.length > 0,
  });
}

export function useProjectSources(projectId: string) {
  return useQuery<WebPage<{ id: string; path: string; format: string; roles: string[]; tripleCount: number }>>({
    queryKey: queryKeys.sources(projectId),
    queryFn: () => loadProjectSources(projectId),
    enabled: projectId.length > 0,
  });
}

export function useHierarchy(projectId: string, sourceId?: string, parentIri?: string, enabled = true, applied = false, proposal = false) {
  return useQuery<WebHierarchyResponse>({
    queryKey: queryKeys.hierarchy(projectId, sourceId, parentIri, applied, proposal),
    queryFn: () => loadHierarchy(projectId, { sourceId, parentIri, includeAppliedInferred: applied, includeProposalInferred: proposal }),
    enabled: enabled && projectId.length > 0,
  });
}

export function useProjectOutline(projectId: string, sourceId?: string, applied = false, proposal = false, enabled = true) {
  return useQuery<WebOutlineResponse>({
    queryKey: queryKeys.outline(projectId, sourceId, applied, proposal),
    queryFn: () => loadProjectOutline(projectId, { sourceId, limit: 100, includeAppliedInferred: applied, includeProposalInferred: proposal }),
    enabled: enabled && projectId.length > 0,
  });
}

export function useEntityDetails(projectId: string, iri: string, enabled = true, applied = false, proposal = false) {
  return useQuery<WebEntityDetailResponse>({
    queryKey: queryKeys.entity(projectId, iri, applied, proposal),
    queryFn: () => loadEntityDetails(projectId, iri, undefined, undefined, { includeAppliedInferred: applied, includeProposalInferred: proposal }),
    enabled: enabled && projectId.length > 0 && iri.length > 0,
  });
}

export function useShaclShapes(projectId: string) {
  return useQuery<WebShaclShapeListResponse>({
    queryKey: queryKeys.shaclShapes(projectId),
    queryFn: () => loadShaclShapes(projectId),
    enabled: projectId.length > 0,
  });
}

export function useProjectSearch(projectId: string, text: string) {
  return useQuery<WebSemanticSearchResponse>({
    queryKey: queryKeys.search(projectId, text),
    queryFn: () => searchProject(projectId, text),
    enabled: projectId.length > 0 && text.trim().length > 0,
  });
}

export function useStagedChanges(projectId: string) {
  return useQuery({ queryKey: queryKeys.staged(projectId), queryFn: () => loadStagedChanges(projectId), enabled: projectId.length > 0 });
}

export function useProjectActivity(projectId: string) {
  return useQuery({
    queryKey: queryKeys.activity(projectId),
    queryFn: () => loadProjectActivity(projectId),
    enabled: projectId.length > 0,
    refetchInterval: 5_000,
  });
}

export function useStagingActions(projectId: string) {
  const queryClient = useQueryClient();
  const refresh = (data: Awaited<ReturnType<typeof loadStagedChanges>>) => {
    queryClient.setQueryData(queryKeys.staged(projectId), data);
    void queryClient.invalidateQueries({ queryKey: queryKeys.summary(projectId) });
  };
  const refreshApplied = async (data: Awaited<ReturnType<typeof loadStagedChanges>>) => {
    queryClient.setQueryData(queryKeys.staged(projectId), data);
    await queryClient.invalidateQueries({ queryKey: ["project", projectId] });
    await queryClient.invalidateQueries({ queryKey: queryKeys.shaclShapes(projectId) });
  };
  return {
    stage: useMutation({ mutationFn: (request: Parameters<typeof stageChange>[1]) => stageChange(projectId, request), onSuccess: refresh }),
    discard: useMutation({ mutationFn: (id: string) => discardStagedChange(projectId, id), onSuccess: refresh }),
    preview: useMutation({ mutationFn: () => previewStagedChanges(projectId), onSuccess: refresh }),
    approve: useMutation({ mutationFn: () => approveProposal(projectId), onSuccess: refresh }),
    reject: useMutation({ mutationFn: () => rejectProposal(projectId), onSuccess: refresh }),
    apply: useMutation({ mutationFn: () => applyProposal(projectId), onSuccess: refreshApplied }),
    accept: useMutation({
      mutationFn: async () => {
        const current = queryClient.getQueryData<Awaited<ReturnType<typeof loadStagedChanges>>>(queryKeys.staged(projectId));
        if (current?.proposal?.status !== "APPROVED") {
          const approved = await approveProposal(projectId);
          queryClient.setQueryData(queryKeys.staged(projectId), approved);
        }
        return applyProposal(projectId);
      },
      onSuccess: refreshApplied,
    }),
  };
}

export function useSemanticJob(projectId: string, jobId: string | null) {
  return useQuery<WebSemanticJobStatus>({
    queryKey: queryKeys.semanticJob(projectId, jobId ?? ""),
    queryFn: () => loadSemanticJob(projectId, jobId!),
    enabled: projectId.length > 0 && Boolean(jobId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && ["Completed", "Failed", "Cancelled", "Incomplete", "Stale"].includes(status) ? false : 750;
    },
  });
}

export function useSemanticJobDetails(projectId: string, jobId: string | null, options: { factOrigin?: "Asserted" | "Inferred"; factOffset?: number; factQuery?: string; limit?: number } = {}) {
  return useQuery({
    queryKey: [...queryKeys.semanticJob(projectId, jobId ?? ""), "details", options],
    queryFn: () => loadSemanticJobDetails(projectId, jobId!, options),
    enabled: projectId.length > 0 && Boolean(jobId),
    refetchInterval: (query) => {
      const status = query.state.data?.job.status;
      return status && ["Completed", "Failed", "Cancelled", "Incomplete", "Stale"].includes(status) ? false : 750;
    },
  });
}

export function useSemanticJobActions(projectId: string) {
  const queryClient = useQueryClient();
  const refresh = (status: WebSemanticJobStatus) => {
    queryClient.setQueryData(queryKeys.semanticJob(projectId, status.id), status);
  };
  return {
    submit: useMutation({ mutationFn: (request: WebSemanticJobRequest) => submitSemanticJob(projectId, request), onSuccess: refresh }),
    cancel: useMutation({ mutationFn: (jobId: string) => cancelSemanticJob(projectId, jobId), onSuccess: refresh }),
  };
}

export function useEnsureAppliedReasoning(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: ["project", projectId, "ensure-applied-reasoning"],
    mutationFn: () => ensureAppliedReasoning(projectId),
    onSuccess: (status) => {
      queryClient.setQueryData(queryKeys.semanticJob(projectId, status.id), status);
    },
  });
}

export function useInferenceMaterialization(projectId: string, jobId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: WebInferenceMaterializationRequest) =>
      materializeInferenceFacts(projectId, jobId, request),
    onSuccess: async (result) => {
      queryClient.setQueryData(queryKeys.staged(projectId), result.staging);
      await queryClient.invalidateQueries({ queryKey: [...queryKeys.semanticJob(projectId, jobId), "details"] });
      await queryClient.invalidateQueries({ queryKey: queryKeys.summary(projectId) });
    },
  });
}

export function useFiboModules(projectId: string) {
  return useQuery<{ sourceId: string; release: string; page: WebPage<WebFiboModule> }>({
    queryKey: queryKeys.fiboModules(projectId),
    queryFn: () => loadFiboModules(projectId),
    enabled: projectId.length > 0,
  });
}

export function useFiboModuleElements(projectId: string, moduleIri: string | null) {
  return useQuery<{ moduleIri: string; page: WebPage<WebFiboElement> }>({
    queryKey: queryKeys.fiboElements(projectId, moduleIri ?? ""),
    // Module cards advertise their full catalog size, so load the complete
    // page rather than the compact 15-item default used by the API helper.
    queryFn: () => loadFiboModuleElements(projectId, moduleIri!, { limit: 100 }),
    enabled: projectId.length > 0 && Boolean(moduleIri),
  });
}

export function useFiboSearch(projectId: string, text: string) {
  return useQuery<{ query: string; page: WebPage<WebFiboElement> }>({
    queryKey: queryKeys.fiboSearch(projectId, text),
    queryFn: () => searchFibo(projectId, text),
    enabled: projectId.length > 0 && text.trim().length > 0,
  });
}

export function useFiboDetails(projectId: string, iri: string | null) {
  return useQuery<WebFiboDetailsResponse>({
    queryKey: queryKeys.fiboDetails(projectId, iri ?? ""),
    queryFn: () => loadFiboDetails(projectId, iri!),
    enabled: projectId.length > 0 && Boolean(iri),
  });
}

export function useFiboActions(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: WebFiboProposalRequest) => stageFiboProposal(projectId, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.staged(projectId) }),
  });
}

export function useDomainOntology(projectId: string) {
  const queryClient = useQueryClient();
  const catalog = useQuery({ queryKey: queryKeys.domainCatalog, queryFn: () => loadDomainOntologies() });
  const status = useQuery({ queryKey: queryKeys.domainStatus(projectId), queryFn: () => loadDomainOntologyStatus(projectId), enabled: Boolean(projectId) });
  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: queryKeys.domainStatus(projectId) });
    await queryClient.invalidateQueries({ queryKey: queryKeys.domainMigration(projectId) });
    await queryClient.invalidateQueries({ queryKey: queryKeys.summary(projectId) });
  };
  return {
    catalog,
    status,
    previewActivation: useMutation({ mutationFn: () => previewDomainActivation(projectId) }),
    activate: useMutation({ mutationFn: (token: string) => activateDomainOntology(projectId, token), onSuccess: refresh }),
    previewDeactivation: useMutation({ mutationFn: () => previewDomainDeactivation(projectId) }),
    deactivate: useMutation({ mutationFn: (token: string) => deactivateDomainOntology(projectId, token), onSuccess: refresh }),
    migration: useQuery({
      queryKey: queryKeys.domainMigration(projectId),
      queryFn: () => loadDomainMigration(projectId),
      enabled: Boolean(projectId) && status.data?.status.availability === "Inactive",
      retry: false,
    }),
    previewMigration: useMutation({ mutationFn: () => previewDomainMigration(projectId) }),
  };
}

export function useDomainOntologyStatus(projectId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.domainStatus(projectId),
    queryFn: () => loadDomainOntologyStatus(projectId),
    enabled: enabled && Boolean(projectId),
    retry: false,
  });
}

export function useDomainFoundation(projectId: string, active: boolean) {
  const queryClient = useQueryClient();
  return {
    foundation: useQuery({
      queryKey: queryKeys.domainFoundation(projectId),
      queryFn: () => loadDomainFoundation(projectId),
      enabled: active && Boolean(projectId),
    }),
    plan: useMutation({
      mutationFn: (request: { elementIds?: string[]; selectAll?: boolean }) => planDomainFoundation(projectId, request),
      onSuccess: (response) => queryClient.setQueryData(
        ["project", projectId, "domain-ontology", "foundation-plan", response.plan.planId],
        response,
      ),
    }),
  };
}

export function useDomainSearch(projectId: string, text: string) {
  const queryClient = useQueryClient();
  return {
    search: useQuery({
      queryKey: queryKeys.domainSearch(projectId, text),
      queryFn: () => searchDomainOntology(projectId, text),
      enabled: Boolean(projectId && text.trim()),
      retry: false,
    }),
    stage: useMutation({
      mutationFn: ({ recommendationId, acknowledged }: { recommendationId: string; acknowledged: boolean }) =>
        stageDomainRecommendation(projectId, recommendationId, acknowledged),
      onSuccess: async () => queryClient.invalidateQueries({ queryKey: queryKeys.staged(projectId) }),
    }),
  };
}

export function useDomainRecommendation(projectId: string, recommendationId: string | null) {
  return useQuery({
    queryKey: queryKeys.domainRecommendation(projectId, recommendationId ?? ""),
    queryFn: () => loadDomainRecommendation(projectId, recommendationId!),
    enabled: Boolean(projectId && recommendationId),
    retry: false,
  });
}

export function useContextualDomainRecommendations(projectId: string, request: WebDomainRecommendationRequest | null) {
  const queryClient = useQueryClient();
  const status = useDomainOntologyStatus(projectId, Boolean(request));
  const active = status.data?.status?.availability === "Active";
  return {
    status,
    search: useQuery({
      queryKey: queryKeys.contextualDomainSearch(projectId, request),
      queryFn: ({ signal }) => recommendDomainOntology(projectId, request!, signal),
      enabled: active && Boolean(request?.draftLabel.trim()),
      retry: false,
    }),
    stage: useMutation({
      mutationFn: ({ recommendationId, stageRequest }: { recommendationId: string; stageRequest: WebDomainStageRequest }) =>
        stageDomainRecommendationAction(projectId, recommendationId, stageRequest),
      onSuccess: async () => queryClient.invalidateQueries({ queryKey: queryKeys.staged(projectId) }),
    }),
  };
}

export function useDomainDependencyPreview(projectId: string, recommendationId: string | null, action: string | null) {
  return useQuery({
    queryKey: ["project", projectId, "domain-recommendation", recommendationId, "dependency-preview", action],
    queryFn: ({ signal }) => previewDomainRecommendationDependencies(projectId, recommendationId!, signal),
    enabled: Boolean(projectId && recommendationId && action && action !== "ContinueLocally"),
    retry: false,
  });
}

export function useAiProviderSettings() {
  return useQuery<WebAiProviderSettings>({
    queryKey: queryKeys.aiProviderSettings,
    queryFn: () => loadAiProviderSettings(),
  });
}

export function useAiProviderActions() {
  const queryClient = useQueryClient();
  const refresh = (status: WebAiProviderSettings) => queryClient.setQueryData(queryKeys.aiProviderSettings, status);
  return {
    save: useMutation({ mutationFn: ({ providerId, apiKey }: { providerId: string; apiKey: string }) => saveAiCredential(providerId, apiKey), onSuccess: refresh }),
    discover: useMutation({ mutationFn: () => discoverAiModels(), onSuccess: refresh }),
    select: useMutation({ mutationFn: ({ modelId, idempotencyKey }: { modelId: string; idempotencyKey: string }) => selectAiModel(modelId, idempotencyKey), onSuccess: refresh }),
    retest: useMutation({ mutationFn: (idempotencyKey: string) => retestAiModel(idempotencyKey), onSuccess: refresh }),
    clear: useMutation({ mutationFn: () => clearAiModelSelection(), onSuccess: refresh }),
    remove: useMutation({ mutationFn: () => removeAiCredential(), onSuccess: refresh }),
  };
}
