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
  aiProviderSettings: ["ai", "provider-settings"] as const,
  ontologyGraph: (projectId: string, options: Omit<OntologyGraphInitialOptions, "signal">) =>
    ["project", projectId, "ontology-graph", options] as const,
  ontologyGraphNeighborhood: (projectId: string, options: Omit<OntologyGraphNeighborhoodOptions, "signal">) =>
    ["project", projectId, "ontology-graph-neighborhood", options] as const,
};

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

export function useProjectOutline(projectId: string, sourceId?: string, applied = false, proposal = false) {
  return useQuery<WebOutlineResponse>({
    queryKey: queryKeys.outline(projectId, sourceId, applied, proposal),
    queryFn: () => loadProjectOutline(projectId, { sourceId, limit: 100, includeAppliedInferred: applied, includeProposalInferred: proposal }),
    enabled: projectId.length > 0,
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
