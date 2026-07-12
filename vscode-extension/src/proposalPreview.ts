import type { EngineResponse } from "./engineCli";

export interface ProposalPreviewRequest {
  readonly targetSourceId: string;
  readonly editKind: "create-class";
  readonly classIri: string;
  readonly label?: string;
}

export interface ProposalDiffEntry {
  readonly kind: string;
  readonly description: string;
}

export interface ProposalPreviewModel {
  readonly proposalId: string;
  readonly status: string;
  readonly targetSourceId: string;
  readonly affectedPaths: readonly string[];
  readonly previewTripleCount: number;
  readonly diffEntries: readonly ProposalDiffEntry[];
  readonly validationStatus: string;
  readonly validationOk: boolean;
  readonly validationIssues: readonly string[];
  readonly semanticEquivalenceStatus: string;
  readonly semanticEquivalenceReason?: string;
  readonly canApprove: boolean;
  readonly approvalDisabledReason?: string;
}

export type ProposalAction = "apply" | "reject";

export interface ProposalActionResult {
  readonly action: ProposalAction;
  readonly ok: boolean;
  readonly status: string;
  readonly proposalId?: string;
  readonly changedFiles: readonly string[];
  readonly reason?: string;
  readonly rollbackStatus?: string;
  readonly rollbackReason?: string;
}

export function readProposalPreviewRequest(value: unknown): ProposalPreviewRequest | undefined {
  const request = asRecord(value);
  if (
    !request ||
    request.editKind !== "create-class" ||
    typeof request.targetSourceId !== "string" ||
    request.targetSourceId.trim() === "" ||
    typeof request.classIri !== "string" ||
    request.classIri.trim() === ""
  ) {
    return undefined;
  }

  return {
    targetSourceId: request.targetSourceId,
    editKind: "create-class",
    classIri: request.classIri,
    label: typeof request.label === "string" && request.label.trim() !== ""
      ? request.label
      : undefined,
  };
}

export function proposalPreviewCliArgs(request: ProposalPreviewRequest): readonly string[] {
  const args = [
    "proposal-preview",
    request.targetSourceId,
    "--edit",
    request.editKind,
    "--class-iri",
    request.classIri,
  ];
  if (request.label) {
    args.push("--label", request.label);
  }
  return args;
}

export function proposalPreviewInvocationArgs(
  projectRoot: string,
  request: ProposalPreviewRequest,
): readonly string[] {
  const [command, targetSourceId, ...options] = proposalPreviewCliArgs(request);
  return [command, projectRoot, targetSourceId, ...options];
}

export function proposalActionInvocationArgs(
  action: ProposalAction,
  projectRoot: string,
  request: ProposalPreviewRequest,
): readonly string[] {
  const [, targetSourceId, ...options] = proposalPreviewCliArgs(request);
  return [`proposal-${action}`, projectRoot, targetSourceId, ...options];
}

export function createProposalPreviewModel(
  response: EngineResponse,
): ProposalPreviewModel | undefined {
  if (!response.ok) {
    return undefined;
  }

  const proposal = asRecord(response.proposal);
  const impact = proposal ? asRecord(proposal.sourceFileImpact) : undefined;
  const preview = proposal ? asRecord(proposal.preview) : undefined;
  const diff = proposal ? asRecord(proposal.diff) : undefined;
  const validation = proposal ? asRecord(proposal.validation) : undefined;
  const equivalence = proposal ? asRecord(proposal.semanticEquivalence) : undefined;

  if (
    !proposal ||
    typeof proposal.id !== "string" ||
    typeof proposal.status !== "string" ||
    typeof proposal.targetSourceId !== "string" ||
    !preview ||
    !diff ||
    !validation ||
    !equivalence
  ) {
    return undefined;
  }

  const previewTripleCount = numberValue(preview.tripleCount);
  const diffEntries = Array.isArray(diff.entries)
    ? diff.entries.map(readDiffEntry).filter(isDefined)
    : undefined;
  const validationIssues = Array.isArray(validation.issues)
    ? validation.issues.map(readValidationIssue).filter(isDefined)
    : undefined;
  const validationOk = validation.ok === true;
  const validationStatus = typeof validation.status === "string" ? validation.status : undefined;
  const equivalenceStatus = typeof equivalence.status === "string" ? equivalence.status : undefined;
  const diffAvailable = diffEntries !== undefined;

  if (
    previewTripleCount === undefined ||
    diffEntries === undefined ||
    validationIssues === undefined ||
    validationStatus === undefined ||
    equivalenceStatus === undefined
  ) {
    return undefined;
  }

  const canApprove = validationOk && diffAvailable && equivalenceStatus === "equivalent";
  const approvalDisabledReason = canApprove
    ? undefined
    : !validationOk
      ? "Approval is disabled because proposal validation failed."
      : equivalenceStatus !== "equivalent"
        ? "Approval is disabled because semantic equivalence verification failed."
        : "Approval is disabled because no semantic diff is available.";

  return {
    proposalId: proposal.id,
    status: proposal.status,
    targetSourceId: proposal.targetSourceId,
    affectedPaths: impact && Array.isArray(impact.affectedPaths)
      ? impact.affectedPaths.filter((path): path is string => typeof path === "string")
      : [],
    previewTripleCount,
    diffEntries,
    validationStatus,
    validationOk,
    validationIssues,
    semanticEquivalenceStatus: equivalenceStatus,
    semanticEquivalenceReason: typeof equivalence.reason === "string" ? equivalence.reason : undefined,
    canApprove,
    approvalDisabledReason,
  };
}

export function proposalPreviewError(response: EngineResponse): string {
  const error = asRecord(response.error);
  return error && typeof error.message === "string"
    ? error.message
    : "Entio could not generate a proposal preview.";
}

export function createProposalActionResult(
  action: ProposalAction,
  response: EngineResponse,
): ProposalActionResult | undefined {
  const proposal = asRecord(response.proposal);
  const validation = proposal ? asRecord(proposal.validation) : undefined;
  const rollback = asRecord(response.rollback);
  const proposalId = typeof response.proposalId === "string"
    ? response.proposalId
    : proposal && typeof proposal.id === "string"
      ? proposal.id
      : undefined;
  const changedFiles = Array.isArray(response.changedFiles)
    ? response.changedFiles.filter((path): path is string => typeof path === "string")
    : [];
  const rawStatus = typeof response.status === "string"
    ? response.status
    : proposal && typeof proposal.status === "string"
      ? proposal.status
      : action === "reject"
        ? "rejected"
        : "failed";
  const reason = typeof response.reason === "string"
    ? response.reason
    : validation && validation.ok === false
      ? "Proposal validation failed."
      : undefined;
  const status = rawStatus === "apply-failed" && reason?.toLowerCase().includes("stale")
    ? "stale"
    : validation?.ok === false
      ? "validation-failed"
      : rawStatus;

  if (response.ok) {
    return {
      action,
      ok: true,
      status,
      proposalId,
      changedFiles,
      rollbackStatus: rollback && typeof rollback.status === "string" ? rollback.status : undefined,
    };
  }

  const error = asRecord(response.error);
  return {
    action,
    ok: false,
    status,
    proposalId,
    changedFiles,
    reason: error && typeof error.message === "string"
      ? error.message
      : (reason ?? "Entio could not complete the proposal action."),
    rollbackStatus: rollback && typeof rollback.status === "string" ? rollback.status : undefined,
    rollbackReason: rollback && typeof rollback.reason === "string" ? rollback.reason : undefined,
  };
}

function readDiffEntry(value: unknown): ProposalDiffEntry | undefined {
  const entry = asRecord(value);
  return entry && typeof entry.kind === "string" && typeof entry.description === "string"
    ? { kind: entry.kind, description: entry.description }
    : undefined;
}

function readValidationIssue(value: unknown): string | undefined {
  const issue = asRecord(value);
  return issue && typeof issue.message === "string" ? issue.message : undefined;
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null
    ? value as Record<string, unknown>
    : undefined;
}

function numberValue(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function isDefined<T>(value: T | undefined): value is T {
  return value !== undefined;
}
