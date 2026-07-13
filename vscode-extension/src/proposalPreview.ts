import type { EngineResponse } from "./engineCli";

export interface ProposalPreviewRequest {
  readonly targetSourceId: string;
  readonly editKind: EditKind;
  readonly classIri?: string;
  readonly label?: string;
  readonly propertyIri?: string;
  readonly domainIri?: string;
  readonly rangeIri?: string;
  readonly datatype?: string;
  readonly individualIri?: string;
  readonly typeIri?: string;
  readonly subjectIri?: string;
  readonly objectIri?: string;
  readonly value?: string;
  readonly language?: string;
  readonly superclassIri?: string;
  readonly entityIri?: string;
  readonly replaceExisting?: boolean;
  readonly selectedDependencyKeys?: readonly string[];
}

export type EditKind =
  | "create-class"
  | "create-object-property"
  | "create-datatype-property"
  | "set-property-domain"
  | "set-property-range"
  | "create-individual"
  | "assign-individual-type"
  | "add-object-property-assertion"
  | "add-datatype-property-assertion"
  | "add-superclass"
  | "remove-superclass"
  | "set-entity-label"
  | "delete-entity";

export const EDIT_KINDS: readonly EditKind[] = [
  "create-class",
  "create-object-property",
  "create-datatype-property",
  "set-property-domain",
  "set-property-range",
  "create-individual",
  "assign-individual-type",
  "add-object-property-assertion",
  "add-datatype-property-assertion",
  "add-superclass",
  "remove-superclass",
  "set-entity-label",
  "delete-entity",
];

export type EditFormField =
  | "classIri"
  | "label"
  | "propertyIri"
  | "domainIri"
  | "rangeIri"
  | "datatype"
  | "individualIri"
  | "typeIri"
  | "subjectIri"
  | "objectIri"
  | "value"
  | "language"
  | "superclassIri"
  | "entityIri";

export interface EditFormState {
  readonly targetSourceId: string;
  readonly editKind: EditKind;
  readonly values: Readonly<Partial<Record<EditFormField, string>>>;
}

export function createEditFormState(targetSourceId = ""): EditFormState {
  return {
    targetSourceId,
    editKind: "create-class",
    values: {},
  };
}

export function selectEditKind(state: EditFormState, editKind: EditKind): EditFormState {
  return { ...state, editKind, values: {} };
}

export function updateEditFormField(
  state: EditFormState,
  field: EditFormField,
  value: string,
): EditFormState {
  return { ...state, values: { ...state.values, [field]: value } };
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

export interface StagedChangeEntry {
  readonly id: string;
  readonly order: number;
  readonly request: ProposalPreviewRequest;
  readonly preview: ProposalPreviewModel;
  readonly summary: string;
}

export interface StagedChangeSession {
  readonly entries: readonly StagedChangeEntry[];
  readonly editing?: StagedChangeEntry;
  readonly nextSequence: number;
}

export interface CombinedProposalRequest {
  readonly schemaVersion: 1;
  readonly proposalId: string;
  readonly title: string;
  readonly targetSourceId: string;
  readonly edits: readonly Record<string, string | boolean | readonly string[] | undefined>[];
}

export interface CombinedProposalModel {
  readonly ok: boolean;
  readonly action: string;
  readonly status: string;
  readonly proposalId?: string;
  readonly previewTripleCount?: number;
  readonly diffEntries: readonly ProposalDiffEntry[];
  readonly validationStatus?: string;
  readonly validationOk: boolean;
  readonly validationIssues: readonly string[];
  readonly semanticEquivalenceStatus: string;
  readonly semanticEquivalenceReason?: string;
  readonly affectedPaths: readonly string[];
  readonly canApprove: boolean;
  readonly changedFiles: readonly string[];
  readonly rollbackStatus?: string;
}

export interface EditStagedChangeResult {
  readonly session: StagedChangeSession;
  readonly request: ProposalPreviewRequest;
}

export function createStagedChangeSession(): StagedChangeSession {
  return { entries: [], nextSequence: 1 };
}

export function stagePreview(
  session: StagedChangeSession,
  request: ProposalPreviewRequest,
  preview: ProposalPreviewModel,
): StagedChangeSession | undefined {
  if (!preview.canApprove) return undefined;
  const editing = session.editing;
  const entry: StagedChangeEntry = {
    id: editing?.id ?? `staged-${session.nextSequence}`,
    order: editing?.order ?? session.entries.length,
    request,
    preview,
    summary: stagedChangeSummary(request),
  };
  const entries = editing
    ? [...session.entries, entry].sort((first, second) => first.order - second.order)
    : [...session.entries, entry];
  return {
    entries: entries.map((value, index) => ({ ...value, order: index })),
    nextSequence: editing ? session.nextSequence : session.nextSequence + 1,
  };
}

export function editStagedChange(
  session: StagedChangeSession,
  id: string,
): EditStagedChangeResult | undefined {
  const entry = session.entries.find((value) => value.id === id);
  if (!entry) return undefined;
  return {
    session: {
      entries: session.entries.filter((value) => value.id !== id),
      editing: entry,
      nextSequence: session.nextSequence,
    },
    request: entry.request,
  };
}

export function cancelStagedEdit(session: StagedChangeSession): StagedChangeSession {
  const editing = session.editing;
  if (!editing) return session;
  const entries = [...session.entries, editing]
    .sort((first, second) => first.order - second.order)
    .map((value, index) => ({ ...value, order: index }));
  return { entries, nextSequence: session.nextSequence };
}

export function removeStagedChange(session: StagedChangeSession, id: string): StagedChangeSession {
  return {
    entries: session.entries
      .filter((entry) => entry.id !== id)
      .map((entry, index) => ({ ...entry, order: index })),
    editing: session.editing?.id === id ? undefined : session.editing,
    nextSequence: session.nextSequence,
  };
}

export function stagedChangeSummary(request: ProposalPreviewRequest): string {
  const identity = request.label || request.classIri || request.propertyIri || request.individualIri || request.entityIri || request.subjectIri || "edit";
  return `${request.editKind} · ${identity}`;
}

export function readCombinedProposalRequests(value: unknown): readonly ProposalPreviewRequest[] | undefined {
  if (!Array.isArray(value) || value.length === 0) return undefined;
  const requests = value.map(readProposalPreviewRequest);
  return requests.every(isDefined) ? requests : undefined;
}

export function createCombinedProposalRequest(
  requests: readonly ProposalPreviewRequest[],
  proposalId = "vscode-combined-proposal",
  title = "VS Code combined ontology proposal",
): CombinedProposalRequest | undefined {
  if (requests.length === 0 || requests.some((request) => request.targetSourceId !== requests[0].targetSourceId)) {
    return undefined;
  }
  return {
    schemaVersion: 1,
    proposalId,
    title,
    targetSourceId: requests[0].targetSourceId,
    edits: requests.map((request) => {
      const { targetSourceId: _targetSourceId, editKind, ...fields } = request;
      return { kind: editKind, ...fields };
    }),
  };
}

export function combinedProposalInvocationArgs(
  projectRoot: string,
  requestFile: string,
  action: "preview" | "validate" | "diff" | "apply" | "reject" = "preview",
): readonly string[] {
  return ["proposal-combined", projectRoot, "--request-file", requestFile, "--action", action];
}

export function createCombinedProposalModel(response: EngineResponse): CombinedProposalModel | undefined {
  if (typeof response.action !== "string" || typeof response.status !== "string") return undefined;
  const proposal = asRecord(response.proposal);
  const preview = asRecord(response.preview);
  const diff = asRecord(response.diff);
  const validation = asRecord(response.validation);
  const equivalence = asRecord(response.semanticEquivalence);
  if (!preview || !diff || !validation || !equivalence) return undefined;
  const diffEntries = Array.isArray(diff.entries) ? diff.entries.map(readDiffEntry).filter(isDefined) : undefined;
  const validationIssues = Array.isArray(validation.issues)
    ? validation.issues.map(readValidationIssue).filter(isDefined)
    : undefined;
  const previewTripleCount = numberValue(preview.tripleCount);
  const equivalenceStatus = typeof equivalence.status === "string" ? equivalence.status : undefined;
  if (!diffEntries || !validationIssues || equivalenceStatus === undefined) return undefined;
  const validationOk = validation.ok === true;
  const canApprove = response.action === "preview" && validationOk && equivalenceStatus === "equivalent" && diffEntries.length > 0;
  return {
    ok: response.ok,
    action: response.action,
    status: response.status,
    proposalId: proposal && typeof proposal.id === "string" ? proposal.id : undefined,
    previewTripleCount,
    diffEntries,
    validationStatus: typeof validation.status === "string" ? validation.status : undefined,
    validationOk,
    validationIssues,
    semanticEquivalenceStatus: equivalenceStatus,
    semanticEquivalenceReason: typeof equivalence.reason === "string" ? equivalence.reason : undefined,
    affectedPaths: readStringArray(response.sourceFileImpact, "affectedPaths"),
    canApprove,
    changedFiles: readStringArray(response, "changedFiles"),
    rollbackStatus: readString(response.rollback, "status"),
  };
}

export interface EntitySelectorRequest {
  readonly label?: string;
  readonly iri?: string;
  readonly kind?: string;
  readonly sourceId?: string;
}

export interface EntityCandidateModel {
  readonly iri: string;
  readonly label: string | null;
  readonly kind: string;
  readonly sourceId: string;
}

export interface EntityResolutionModel {
  readonly status: string;
  readonly candidate?: EntityCandidateModel;
  readonly candidates: readonly EntityCandidateModel[];
  readonly message?: string;
}

export interface GeneratedIriModel {
  readonly iri: string;
  readonly localName: string;
  readonly collision: string;
  readonly normalizationVersion: string;
}

export interface DeletionDependencyModel {
  readonly status: string;
  readonly target?: EntityCandidateModel;
  readonly directStatements: readonly DeletionStatementModel[];
  readonly dependentStatements: readonly DeletionStatementModel[];
  readonly safe: boolean;
  readonly invalidSelectedDependencyKeys: readonly string[];
}

export interface DeletionStatementModel {
  readonly kind: string;
  readonly sourceId: string;
  readonly dependencyKey?: string;
  readonly selectedForRemoval: boolean;
  readonly subject: string;
  readonly subjectLabel?: string;
  readonly predicate: string;
  readonly predicateLabel?: string;
  readonly object: string;
  readonly objectLabel?: string;
}

export function readEntitySelectorRequest(value: unknown): EntitySelectorRequest | undefined {
  const request = asRecord(value);
  if (!request) return undefined;
  const selector: EntitySelectorRequest = {
    label: textValue(request.label),
    iri: textValue(request.iri),
    kind: textValue(request.kind),
    sourceId: textValue(request.sourceId),
  };
  return selector.label || selector.iri ? selector : undefined;
}

export function entityResolutionInvocationArgs(
  projectRoot: string,
  selector: EntitySelectorRequest,
): readonly string[] {
  return [
    "resolve-label",
    projectRoot,
    ...(selector.label ? ["--label", selector.label] : []),
    ...(selector.iri ? ["--iri", selector.iri] : []),
    ...(selector.kind ? ["--kind", selector.kind] : []),
    ...(selector.sourceId ? ["--source-id", selector.sourceId] : []),
  ];
}

export function generatedIriInvocationArgs(
  projectRoot: string,
  label: string,
  kind: string,
  distinct = false,
): readonly string[] {
  return [
    "generate-iri",
    projectRoot,
    "--label",
    label,
    "--kind",
    kind,
    ...(distinct ? ["--distinct"] : []),
  ];
}

export function deletionDependenciesInvocationArgs(
  projectRoot: string,
  sourceId: string,
  selector: EntitySelectorRequest,
  selectedDependencyKeys: readonly string[] = [],
): readonly string[] {
  return [
    "deletion-dependencies",
    projectRoot,
    sourceId,
    ...(selector.label ? ["--label", selector.label] : []),
    ...(selector.iri ? ["--iri", selector.iri] : []),
    ...(selector.kind ? ["--kind", selector.kind] : []),
    ...selectedDependencyKeys.flatMap((key) => ["--selected-dependency-key", key]),
  ];
}

export function createEntityResolutionModel(response: EngineResponse): EntityResolutionModel | undefined {
  const resolution = asRecord(response.resolution);
  if (!resolution || typeof resolution.status !== "string") return undefined;
  const candidate = readEntityCandidate(resolution.candidate);
  const candidates = Array.isArray(resolution.candidates)
    ? resolution.candidates.map(readEntityCandidate).filter(isDefined)
    : [];
  return {
    status: resolution.status,
    candidate,
    candidates,
    message: textValue(resolution.message),
  };
}

export function createGeneratedIriModel(response: EngineResponse): GeneratedIriModel | undefined {
  const generated = asRecord(response.generated);
  if (
    !generated ||
    typeof generated.iri !== "string" ||
    typeof generated.localName !== "string" ||
    typeof generated.collision !== "string" ||
    typeof generated.normalizationVersion !== "string"
  ) {
    return undefined;
  }
  return {
    iri: generated.iri,
    localName: generated.localName,
    collision: generated.collision,
    normalizationVersion: generated.normalizationVersion,
  };
}

export function createDeletionDependencyModel(response: EngineResponse): DeletionDependencyModel | undefined {
  if (typeof response.status !== "string") return undefined;
  const target = readEntityCandidate(response.target);
  const directStatements = readDeletionStatements(response.directStatements);
  const dependentStatements = readDeletionStatements(response.dependentStatements);
  return {
    status: response.status,
    target,
    directStatements,
    dependentStatements,
    safe: response.ok === true,
    invalidSelectedDependencyKeys: readStringArray(response, "invalidSelectedDependencyKeys"),
  };
}

export function readProposalPreviewRequest(value: unknown): ProposalPreviewRequest | undefined {
  const request = asRecord(value);
  if (
    !request ||
    typeof request.targetSourceId !== "string" ||
    request.targetSourceId.trim() === "" ||
    !isEditKind(request.editKind)
  ) {
    return undefined;
  }

  const normalized: ProposalPreviewRequest = {
    targetSourceId: request.targetSourceId,
    editKind: request.editKind,
  };
  const fields = editFields(request);
  const rawSelectedDependencyKeys = request.selectedDependencyKeys;
  const selectedDependencyKeys = Array.isArray(rawSelectedDependencyKeys)
    ? rawSelectedDependencyKeys.filter((key): key is string => typeof key === "string" && key.trim() !== "")
    : undefined;
  if (rawSelectedDependencyKeys !== undefined &&
      (!Array.isArray(rawSelectedDependencyKeys) || selectedDependencyKeys === undefined ||
        selectedDependencyKeys.length !== rawSelectedDependencyKeys.length)) {
    return undefined;
  }
  if (!hasRequiredEditFields(request.editKind, fields)) {
    return undefined;
  }
  return selectedDependencyKeys === undefined
    ? { ...normalized, ...fields }
    : { ...normalized, ...fields, selectedDependencyKeys };
}

export function editFormStateRequest(state: EditFormState): ProposalPreviewRequest | undefined {
  return readProposalPreviewRequest({
    targetSourceId: state.targetSourceId,
    editKind: state.editKind,
    ...state.values,
  });
}

export function proposalPreviewCliArgs(request: ProposalPreviewRequest): readonly string[] {
  const args = [
    "proposal-preview",
    request.targetSourceId,
    "--edit",
    request.editKind,
  ];
  appendEditArgs(args, request);
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

function editFields(request: Record<string, unknown>): Partial<ProposalPreviewRequest> {
  const fields: Record<string, string | boolean> = {};
  const names: readonly EditFormField[] = [
    "classIri", "label", "propertyIri", "domainIri", "rangeIri", "datatype", "individualIri",
    "typeIri", "subjectIri", "objectIri", "value", "language", "superclassIri", "entityIri",
  ];
  names.forEach((name) => {
    const value = request[name];
    if (typeof value === "string" && value.trim() !== "") {
      fields[name] = value;
    }
  });
  if (request.replaceExisting === true) {
    fields.replaceExisting = true;
  }
  return Array.isArray(request.selectedDependencyKeys)
    ? { ...fields, selectedDependencyKeys: request.selectedDependencyKeys }
    : fields as Partial<ProposalPreviewRequest>;
}

function hasRequiredEditFields(editKind: EditKind, fields: Partial<ProposalPreviewRequest>): boolean {
  const present = (field: keyof ProposalPreviewRequest): boolean => {
    const value = fields[field];
    return typeof value === "string" && value.trim() !== "";
  };
  switch (editKind) {
    case "create-class": return present("classIri");
    case "create-object-property":
    case "create-datatype-property":
      return present("propertyIri");
    case "set-property-domain": return present("propertyIri") && present("domainIri");
    case "set-property-range": return present("propertyIri") && (present("rangeIri") || present("datatype"));
    case "create-individual": return present("individualIri");
    case "assign-individual-type": return present("individualIri") && present("typeIri");
    case "add-object-property-assertion":
      return present("subjectIri") && present("propertyIri") && present("objectIri");
    case "add-datatype-property-assertion": return present("subjectIri") && present("propertyIri") && present("value");
    case "add-superclass":
    case "remove-superclass": return present("classIri") && present("superclassIri");
    case "set-entity-label": return present("entityIri") && present("label");
    case "delete-entity": return present("entityIri");
  }
}

function appendEditArgs(args: string[], request: ProposalPreviewRequest): void {
  const append = (option: string, value: string | undefined): void => {
    if (value) args.push(option, value);
  };
  switch (request.editKind) {
    case "create-class":
      append("--class-iri", request.classIri);
      append("--label", request.label);
      break;
    case "create-object-property":
    case "create-datatype-property":
      append("--property-iri", request.propertyIri);
      append("--label", request.label);
      append("--domain-iri", request.domainIri);
      append("--range-iri", request.rangeIri);
      append("--datatype", request.datatype);
      break;
    case "set-property-domain":
      append("--property-iri", request.propertyIri);
      append("--domain-iri", request.domainIri);
      break;
    case "set-property-range":
      append("--property-iri", request.propertyIri);
      append("--range-iri", request.rangeIri);
      append("--datatype", request.datatype);
      break;
    case "create-individual":
      append("--individual-iri", request.individualIri);
      append("--type-iri", request.typeIri);
      append("--label", request.label);
      break;
    case "assign-individual-type":
      append("--individual-iri", request.individualIri);
      append("--type-iri", request.typeIri);
      break;
    case "add-object-property-assertion":
      append("--subject-iri", request.subjectIri);
      append("--property-iri", request.propertyIri);
      append("--object-iri", request.objectIri);
      break;
    case "add-datatype-property-assertion":
      append("--subject-iri", request.subjectIri);
      append("--property-iri", request.propertyIri);
      append("--value", request.value);
      append("--datatype", request.datatype);
      append("--language", request.language);
      break;
    case "add-superclass":
    case "remove-superclass":
      append("--class-iri", request.classIri);
      append("--superclass-iri", request.superclassIri);
      break;
    case "set-entity-label":
      append("--entity-iri", request.entityIri);
      append("--label", request.label);
      append("--language", request.language);
      break;
    case "delete-entity":
      append("--entity-iri", request.entityIri);
      break;
  }
  if (request.replaceExisting) args.push("--replace-existing");
}

function isEditKind(value: unknown): value is EditKind {
  return typeof value === "string" && EDIT_KINDS.includes(value as EditKind);
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

function readString(value: unknown, key: string): string | undefined {
  const record = asRecord(value);
  return record && typeof record[key] === "string" ? record[key] as string : undefined;
}

function readStringArray(value: unknown, key: string): readonly string[] {
  const record = asRecord(value);
  return record && Array.isArray(record[key])
    ? record[key].filter((entry): entry is string => typeof entry === "string")
    : [];
}

function textValue(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() !== "" ? value : undefined;
}

function readEntityCandidate(value: unknown): EntityCandidateModel | undefined {
  const candidate = asRecord(value);
  return candidate && typeof candidate.iri === "string" &&
    (candidate.label === null || typeof candidate.label === "string") &&
    typeof candidate.kind === "string" && typeof candidate.sourceId === "string"
    ? {
        iri: candidate.iri,
        label: typeof candidate.label === "string" ? candidate.label : null,
        kind: candidate.kind,
        sourceId: candidate.sourceId,
      }
    : undefined;
}

function readDeletionStatements(value: unknown): readonly DeletionStatementModel[] {
  return Array.isArray(value)
    ? value.map((statement) => {
        const record = asRecord(statement);
        return record && typeof record.kind === "string" && typeof record.sourceId === "string" &&
          typeof record.selectedForRemoval === "boolean" && typeof record.subject === "string" &&
          typeof record.predicate === "string" && typeof record.object === "string"
          ? {
              kind: record.kind,
              sourceId: record.sourceId,
              dependencyKey: typeof record.dependencyKey === "string" ? record.dependencyKey : undefined,
              selectedForRemoval: record.selectedForRemoval,
              subject: record.subject,
              subjectLabel: typeof record.subjectLabel === "string" ? record.subjectLabel : undefined,
              predicate: record.predicate,
              predicateLabel: typeof record.predicateLabel === "string" ? record.predicateLabel : undefined,
              object: record.object,
              objectLabel: typeof record.objectLabel === "string" ? record.objectLabel : undefined,
            }
          : undefined;
      }).filter(isDefined)
    : [];
}

function numberValue(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function isDefined<T>(value: T | undefined): value is T {
  return value !== undefined;
}
