import type { Webview } from "vscode";
import { EDIT_KINDS } from "./proposalPreview";

export function renderWorkbench(webview: Webview, nonce: string): string {
  const editKindOptions = EDIT_KINDS
    .map((editKind) => `<option value="${editKind}">${editKind}</option>`)
    .join("");
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-${nonce}';">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Entio Ontology Workbench</title>
  <style>
    body { color: var(--vscode-foreground); background: var(--vscode-editor-background); font-family: var(--vscode-font-family); padding: 16px; }
    button { color: var(--vscode-button-foreground); background: var(--vscode-button-background); border: 0; padding: 6px 12px; }
    button:hover { background: var(--vscode-button-hoverBackground); }
    button:disabled { opacity: 0.5; cursor: not-allowed; }
    main { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(240px, 2fr); gap: 20px; margin-top: 16px; }
    section { border-top: 1px solid var(--vscode-panel-border); padding-top: 12px; }
    ul { list-style: none; padding: 0; }
    li { margin: 4px 0; }
    .symbol-button { width: 100%; text-align: left; background: transparent; color: var(--vscode-foreground); }
    .symbol-button:hover, .symbol-button:focus { background: var(--vscode-list-hoverBackground); }
    #status { margin: 12px 0; white-space: pre-wrap; }
    #details { min-height: 100px; }
    form { display: grid; gap: 8px; max-width: 520px; }
    label { display: grid; gap: 4px; }
    .deletion-dependency { display: flex; align-items: flex-start; gap: 8px; }
    .deletion-dependency input { width: auto; margin: 2px 0 0; }
    [hidden] { display: none !important; }
    input, select { color: var(--vscode-input-foreground); background: var(--vscode-input-background); border: 1px solid var(--vscode-input-border); padding: 6px; }
    #edit-form, #preview { margin-top: 20px; }
    #preview-status { white-space: pre-wrap; }
    #preview-diff, #preview-validation, #preview-impact { margin: 8px 0; }
    #approval-state { margin-left: 8px; }
    #open-source { margin-left: 8px; }
    @media (max-width: 640px) { main { grid-template-columns: 1fr; } }
  </style>
</head>
<body>
  <h1>Entio Ontology Workbench</h1>
  <button id="refresh" type="button">Refresh</button>
  <div id="status" role="status">Loading project summary...</div>
  <section id="entity-search" aria-labelledby="entity-search-heading">
    <h2 id="entity-search-heading">Find an existing entity</h2>
    <p id="semantic-search-context">Search results are for inspection. Edit fields are verified when staged.</p>
    <form id="semantic-search-form">
      <label>Label or IRI <input id="semantic-search-query" type="search"></label>
      <label>Kind
        <select id="semantic-search-kind">
          <option value="">Any kind</option>
          <option value="Class">Class</option>
          <option value="ObjectProperty">Object property</option>
          <option value="DatatypeProperty">Datatype property</option>
          <option value="AnnotationProperty">Annotation property</option>
          <option value="Individual">Individual</option>
        </select>
      </label>
      <label>Source <select id="semantic-search-source"><option value="">All sources</option></select></label>
      <button id="semantic-search-submit" type="submit">Search</button>
    </form>
    <div id="semantic-search-results" aria-live="polite">No entity search requested.</div>
  </section>
  <main>
    <section aria-labelledby="sources-heading">
      <h2 id="sources-heading">Ontology Sources</h2>
      <ul id="sources"></ul>
    </section>
    <section aria-labelledby="symbols-heading">
      <h2 id="symbols-heading">Symbols</h2>
      <div id="symbol-groups"></div>
      <div id="details" aria-live="polite">Select a symbol to inspect its details.</div>
    </section>
  </main>
  <section id="edit-form" aria-labelledby="edit-heading">
    <h2 id="edit-heading">Preview Ontology Edit</h2>
    <form id="proposal-form">
      <label>Target source <select id="target-source" required></select></label>
      <label>Edit type <select id="edit-kind">${editKindOptions}</select></label>
      <div id="class-fields">
        <label>Class label <input id="class-label" type="text" required></label>
        <input id="class-iri" type="hidden">
      </div>
      <div id="property-fields" hidden>
        <div id="property-label-field" class="entity-picker">
          <label>Property label <input id="property-label" type="text"></label>
        </div>
        <input id="property-iri" type="hidden">
        <div id="property-domain-field" class="entity-picker">
          <label>Domain label <input id="property-domain-label" type="text"></label>
          <input id="property-domain-iri" type="hidden">
        </div>
        <div id="property-range-field" class="entity-picker">
          <label>Range label <input id="property-range-label" type="text"></label>
          <input id="property-range-iri" type="hidden">
        </div>
        <label id="property-datatype-field">Datatype
          <select id="property-datatype">
            <option value="">Select a datatype</option>
            <option value="http://www.w3.org/2001/XMLSchema#string">xsd:string</option>
            <option value="http://www.w3.org/2001/XMLSchema#boolean">xsd:boolean</option>
            <option value="http://www.w3.org/2001/XMLSchema#integer">xsd:integer</option>
            <option value="http://www.w3.org/2001/XMLSchema#decimal">xsd:decimal</option>
            <option value="http://www.w3.org/2001/XMLSchema#date">xsd:date</option>
            <option value="http://www.w3.org/2001/XMLSchema#dateTime">xsd:dateTime</option>
          </select>
        </label>
        <label><input id="property-replace" type="checkbox"> Replace existing domain or range</label>
      </div>
      <div id="individual-fields" hidden>
        <div id="individual-label-field" class="entity-picker">
          <label>Individual label <input id="individual-label" type="text"></label>
        </div>
        <input id="individual-iri" type="hidden">
        <div class="entity-picker">
          <label>Type label <input id="individual-type-label" type="text"></label>
          <input id="individual-type-iri" type="hidden">
        </div>
      </div>
      <div id="assertion-fields" hidden>
        <div class="entity-picker">
          <label>Subject label <input id="assertion-subject-label" type="text"></label>
          <input id="assertion-subject-iri" type="hidden">
        </div>
        <div class="entity-picker">
          <label>Property label <input id="assertion-property-label" type="text"></label>
          <input id="assertion-property-iri" type="hidden">
        </div>
        <div id="assertion-object-field" class="entity-picker">
          <label>Object label <input id="assertion-object-label" type="text"></label>
          <input id="assertion-object-iri" type="hidden">
        </div>
        <label id="assertion-value-field">Value <input id="assertion-value" type="text"></label>
        <label id="assertion-datatype-field">Datatype
          <select id="assertion-datatype">
            <option value="">Select a datatype</option>
            <option value="http://www.w3.org/2001/XMLSchema#string">xsd:string</option>
            <option value="http://www.w3.org/2001/XMLSchema#boolean">xsd:boolean</option>
            <option value="http://www.w3.org/2001/XMLSchema#integer">xsd:integer</option>
            <option value="http://www.w3.org/2001/XMLSchema#decimal">xsd:decimal</option>
            <option value="http://www.w3.org/2001/XMLSchema#date">xsd:date</option>
            <option value="http://www.w3.org/2001/XMLSchema#dateTime">xsd:dateTime</option>
          </select>
        </label>
        <label id="assertion-language-field">Language tag <input id="assertion-language" type="text"></label>
      </div>
      <div id="hierarchy-fields" hidden>
        <div class="entity-picker">
          <label>Class label <input id="hierarchy-class-label" type="text"></label>
          <input id="hierarchy-class-iri" type="hidden">
        </div>
        <div class="entity-picker">
          <label>Superclass label <input id="hierarchy-superclass-label" type="text"></label>
          <input id="hierarchy-superclass-iri" type="hidden">
        </div>
      </div>
      <div id="label-fields" hidden>
        <div class="entity-picker">
          <label>Entity label <input id="label-entity-label" type="text"></label>
          <input id="label-entity-iri" type="hidden">
        </div>
        <label>Label <input id="label-value" type="text"></label>
        <label>Language tag <input id="label-language" type="text"></label>
        <label><input id="label-replace" type="checkbox"> Replace existing labels</label>
      </div>
      <div id="semantic-fields" hidden>
        <div id="semantic-property-field" class="entity-picker">
          <label id="semantic-property-picker-label">Annotation property label <input id="semantic-property-label" type="text"></label>
          <input id="semantic-property-iri" type="hidden">
        </div>
        <div id="semantic-target-field" class="entity-picker">
          <label>Target label <input id="semantic-target-label" type="text"></label>
          <input id="semantic-target-iri" type="hidden">
        </div>
        <label id="semantic-label-field">Label <input id="semantic-label" type="text"></label>
        <label id="semantic-definition-field">Definition <input id="semantic-definition" type="text"></label>
        <label id="semantic-value-field">Value <input id="semantic-value" type="text"></label>
        <label id="semantic-existing-field">Existing value <input id="semantic-existing" type="text"></label>
        <label id="semantic-replacement-field">Replacement value <input id="semantic-replacement" type="text"></label>
      </div>
      <p id="edit-form-placeholder" hidden>Additional edit forms are provided by the workbench edit modes.</p>
      <button id="preview-submit" type="button">Stage change</button>
      <div id="edit-status" aria-live="polite">No edit staged.</div>
    </form>
  </section>
  <section id="deletion-review" aria-labelledby="deletion-heading">
    <h2 id="deletion-heading">Review deletion dependencies</h2>
    <form id="deletion-form">
      <label>Entity label <input id="deletion-label" type="text"></label>
      <input id="deletion-iri" type="hidden">
      <label>Kind
        <select id="deletion-kind">
          <option value="">Any kind</option>
          <option value="Class">Class</option>
          <option value="Property">Property</option>
          <option value="Individual">Individual</option>
        </select>
      </label>
      <button id="inspect-deletion" type="submit">Inspect dependencies</button>
    </form>
    <div id="deletion-dependencies" aria-live="polite">No deletion review requested.</div>
    <button id="preview-deletion" type="button" disabled>Stage deletion</button>
  </section>
  <section id="staged-changes" aria-labelledby="staged-heading">
    <h2 id="staged-heading">Staged changes</h2>
    <div id="staged-status" aria-live="polite">No changes staged.</div>
    <ul id="staged-list"></ul>
    <button id="cancel-staged-edit" type="button" hidden>Cancel staged edit</button>
    <button id="preview-all-changes" type="button" disabled>Preview all changes</button>
  </section>
  <section id="preview" aria-labelledby="preview-heading">
    <h2 id="preview-heading">Proposal Preview</h2>
    <div id="preview-status">No proposal preview requested.</div>
    <div id="preview-impact"></div>
    <div id="preview-diff"></div>
    <div id="preview-validation"></div>
    <button id="approve" type="button" disabled>Approve and apply</button>
    <button id="reject" type="button" disabled>Reject</button>
    <button id="open-source" type="button" disabled>Open changed source</button>
    <span id="approval-state">Preview a proposal to review approval.</span>
  </section>
  <script nonce="${nonce}">
    const vscode = acquireVsCodeApi();
    const status = document.getElementById("status");
    const sources = document.getElementById("sources");
    const symbolGroups = document.getElementById("symbol-groups");
    const details = document.getElementById("details");
    const semanticSearchForm = document.getElementById("semantic-search-form");
    const semanticSearchQuery = document.getElementById("semantic-search-query");
    const semanticSearchKind = document.getElementById("semantic-search-kind");
    const semanticSearchSource = document.getElementById("semantic-search-source");
    const semanticSearchResults = document.getElementById("semantic-search-results");
    const proposalForm = document.getElementById("proposal-form");
    const targetSource = document.getElementById("target-source");
    const editKind = document.getElementById("edit-kind");
    const classFields = document.getElementById("class-fields");
    const editFormPlaceholder = document.getElementById("edit-form-placeholder");
    const previewSubmit = document.getElementById("preview-submit");
    const editStatus = document.getElementById("edit-status");
    const classIri = document.getElementById("class-iri");
    const classLabel = document.getElementById("class-label");
    const propertyFields = document.getElementById("property-fields");
    const propertyIri = document.getElementById("property-iri");
    const propertyLabelField = document.getElementById("property-label-field");
    const propertyLabel = document.getElementById("property-label");
    const propertyDomainField = document.getElementById("property-domain-field");
    const propertyDomainIri = document.getElementById("property-domain-iri");
    const propertyDomainLabel = document.getElementById("property-domain-label");
    const propertyRangeField = document.getElementById("property-range-field");
    const propertyRangeIri = document.getElementById("property-range-iri");
    const propertyRangeLabel = document.getElementById("property-range-label");
    const propertyDatatypeField = document.getElementById("property-datatype-field");
    const propertyDatatype = document.getElementById("property-datatype");
    const propertyReplace = document.getElementById("property-replace");
    const individualFields = document.getElementById("individual-fields");
    const individualIri = document.getElementById("individual-iri");
    const individualLabelField = document.getElementById("individual-label-field");
    const individualTypeIri = document.getElementById("individual-type-iri");
    const individualTypeLabel = document.getElementById("individual-type-label");
    const individualLabel = document.getElementById("individual-label");
    const assertionFields = document.getElementById("assertion-fields");
    const assertionSubjectIri = document.getElementById("assertion-subject-iri");
    const assertionSubjectLabel = document.getElementById("assertion-subject-label");
    const assertionPropertyIri = document.getElementById("assertion-property-iri");
    const assertionPropertyLabel = document.getElementById("assertion-property-label");
    const assertionObjectField = document.getElementById("assertion-object-field");
    const assertionObjectIri = document.getElementById("assertion-object-iri");
    const assertionObjectLabel = document.getElementById("assertion-object-label");
    const assertionValueField = document.getElementById("assertion-value-field");
    const assertionValue = document.getElementById("assertion-value");
    const assertionDatatypeField = document.getElementById("assertion-datatype-field");
    const assertionDatatype = document.getElementById("assertion-datatype");
    const assertionLanguageField = document.getElementById("assertion-language-field");
    const assertionLanguage = document.getElementById("assertion-language");
    const hierarchyFields = document.getElementById("hierarchy-fields");
    const hierarchyClassIri = document.getElementById("hierarchy-class-iri");
    const hierarchyClassLabel = document.getElementById("hierarchy-class-label");
    const hierarchySuperclassIri = document.getElementById("hierarchy-superclass-iri");
    const hierarchySuperclassLabel = document.getElementById("hierarchy-superclass-label");
    const labelFields = document.getElementById("label-fields");
    const labelEntityIri = document.getElementById("label-entity-iri");
    const labelEntityLabel = document.getElementById("label-entity-label");
    const labelValue = document.getElementById("label-value");
    const labelLanguage = document.getElementById("label-language");
    const labelReplace = document.getElementById("label-replace");
    const semanticFields = document.getElementById("semantic-fields");
    const semanticPropertyField = document.getElementById("semantic-property-field");
    const semanticPropertyPickerLabel = document.getElementById("semantic-property-picker-label");
    const semanticPropertyIri = document.getElementById("semantic-property-iri");
    const semanticPropertyLabel = document.getElementById("semantic-property-label");
    const semanticTargetField = document.getElementById("semantic-target-field");
    const semanticTargetIri = document.getElementById("semantic-target-iri");
    const semanticTargetLabel = document.getElementById("semantic-target-label");
    const semanticLabelField = document.getElementById("semantic-label-field");
    const semanticLabel = document.getElementById("semantic-label");
    const semanticDefinitionField = document.getElementById("semantic-definition-field");
    const semanticDefinition = document.getElementById("semantic-definition");
    const semanticValueField = document.getElementById("semantic-value-field");
    const semanticValue = document.getElementById("semantic-value");
    const semanticExistingField = document.getElementById("semantic-existing-field");
    const semanticExisting = document.getElementById("semantic-existing");
    const semanticReplacementField = document.getElementById("semantic-replacement-field");
    const semanticReplacement = document.getElementById("semantic-replacement");
    const previewStatus = document.getElementById("preview-status");
    const previewImpact = document.getElementById("preview-impact");
    const previewDiff = document.getElementById("preview-diff");
    const previewValidation = document.getElementById("preview-validation");
    const approve = document.getElementById("approve");
    const reject = document.getElementById("reject");
    const openSource = document.getElementById("open-source");
    const approvalState = document.getElementById("approval-state");
    const deletionForm = document.getElementById("deletion-form");
    const deletionLabel = document.getElementById("deletion-label");
    const deletionIri = document.getElementById("deletion-iri");
    const deletionKind = document.getElementById("deletion-kind");
    const deletionDependencies = document.getElementById("deletion-dependencies");
    const previewDeletion = document.getElementById("preview-deletion");
    const stagedStatus = document.getElementById("staged-status");
    const stagedList = document.getElementById("staged-list");
    const cancelStagedEdit = document.getElementById("cancel-staged-edit");
    const previewAllChanges = document.getElementById("preview-all-changes");
    let currentRequest;
    let currentPreview;
    let changedSource;
    let stagedChanges = [];
    let stagedSequence = 1;
    let editingStagedEntry;
    let currentCombinedPreview;
    let currentDeletionReview;
    let selectedDeletionKeys = new Set();
    let currentModel;
    let selectedSemanticIri;
    let pendingIriGenerationKey;
    let pendingPreviewAfterIri = false;
    let pendingEntityResolution = false;
    document.getElementById("refresh").addEventListener("click", () => vscode.postMessage({ type: "refresh" }));

    [
      [classLabel, classIri],
      [propertyLabel, propertyIri],
      [propertyDomainLabel, propertyDomainIri],
      [propertyRangeLabel, propertyRangeIri],
      [individualLabel, individualIri],
      [individualTypeLabel, individualTypeIri],
      [assertionSubjectLabel, assertionSubjectIri],
      [assertionPropertyLabel, assertionPropertyIri],
      [assertionObjectLabel, assertionObjectIri],
      [semanticLabel, semanticPropertyIri],
      [semanticPropertyLabel, semanticPropertyIri],
      [semanticTargetLabel, semanticTargetIri],
      [hierarchyClassLabel, hierarchyClassIri],
      [hierarchySuperclassLabel, hierarchySuperclassIri],
      [labelEntityLabel, labelEntityIri],
    ].forEach(([labelInput, iriInput]) => {
      labelInput.addEventListener("input", () => {
        iriInput.value = "";
        pendingEntityResolution = false;
        pendingIriGenerationKey = undefined;
        pendingPreviewAfterIri = false;
      });
    });

    semanticSearchForm.addEventListener("submit", (event) => {
      event.preventDefault();
      const query = semanticSearchQuery.value.trim();
      if (query === "") {
        semanticSearchResults.textContent = "Enter text to search semantic descriptions.";
        return;
      }
      vscode.postMessage({
        type: "semantic-search",
        payload: {
          query,
          kind: semanticSearchKind.value || undefined,
          sourceId: semanticSearchSource.value || undefined,
        },
      });
      semanticSearchResults.textContent = "Searching semantic descriptions...";
    });

    function requestIriGeneration() {
      const kind = entityKindForEdit();
      const label = labelForEdit();
      if (!kind || !label) return false;
      pendingIriGenerationKey = editKind.value + "|" + label.trim();
      vscode.postMessage({ type: "generate-iri", payload: { label, kind } });
      editStatus.textContent = "Generating deterministic IRI...";
      return true;
    }

    function requestEntityResolutions(entries) {
      pendingEntityResolution = true;
      editStatus.textContent = "Verifying entity labels...";
      vscode.postMessage({
        type: "resolve-edit-entities",
        payload: {
          sourceId: targetSource.value,
          entries,
        },
      });
      return true;
    }

    deletionForm.addEventListener("submit", (event) => {
      event.preventDefault();
      if (deletionLabel.value === "" && deletionIri.value === "") {
        deletionDependencies.textContent = "Enter an entity label or IRI to inspect deletion dependencies.";
        return;
      }
      requestDeletionReview({
        label: deletionLabel.value || undefined,
        iri: deletionIri.value || undefined,
        kind: deletionKind.value || undefined,
        sourceId: targetSource.value || undefined,
      });
    });

    previewDeletion.addEventListener("click", () => {
      if (!currentDeletionReview || !canPreviewDeletion()) return;
      const target = currentDeletionReview.target;
      const request = {
        targetSourceId: target?.sourceId || targetSource.value,
        editKind: "delete-entity",
        entityIri: target?.iri || deletionIri.value,
        selectedDependencyKeys: Array.from(selectedDeletionKeys),
      };
      currentRequest = request;
      vscode.postMessage({ type: "deletion-preview", payload: request });
      previewStatus.textContent = "Requesting deletion proposal preview...";
      previewDeletion.disabled = true;
    });

    cancelStagedEdit.addEventListener("click", () => {
      if (editingStagedEntry) {
        stagedChanges = stagedChanges.concat(editingStagedEntry).sort((first, second) => first.order - second.order);
        editingStagedEntry = undefined;
        currentRequest = undefined;
        clearEditForm();
        renderStagedList();
      }
    });

    previewAllChanges.addEventListener("click", () => {
      if (stagedChanges.length === 0) return;
      vscode.postMessage({
        type: "combined-preview",
        payload: stagedChanges.map((entry) => entry.request),
      });
      previewStatus.textContent = "Requesting combined proposal preview...";
    });

    function entityKindForEdit() {
      if (editKind.value === "create-class") return "Class";
      if (["create-object-property", "create-datatype-property"].includes(editKind.value)) return "Property";
      if (editKind.value === "create-annotation-property") return "Property";
      if (editKind.value === "create-individual") return "Individual";
      return undefined;
    }

    function labelForEdit() {
      if (editKind.value === "create-class") return classLabel.value;
      if (["create-object-property", "create-datatype-property"].includes(editKind.value)) return propertyLabel.value;
      if (editKind.value === "create-annotation-property") return semanticLabel.value;
      if (editKind.value === "create-individual") return individualLabel.value;
      return undefined;
    }

    function clearEditForm() {
      const source = targetSource.value;
      pendingIriGenerationKey = undefined;
      pendingPreviewAfterIri = false;
      pendingEntityResolution = false;
      proposalForm.reset();
      targetSource.value = source;
      updateEditFormMode();
      editStatus.textContent = "No edit staged.";
    }

    function requestDeletionReview(selector) {
      if (selector.sourceId) targetSource.value = selector.sourceId;
      deletionLabel.value = selector.label || "";
      deletionIri.value = selector.iri || "";
      deletionKind.value = selector.kind || "";
      currentDeletionReview = undefined;
      selectedDeletionKeys = new Set();
      previewDeletion.disabled = true;
      vscode.postMessage({
        type: "deletion-dependencies",
        payload: selector,
      });
      deletionDependencies.textContent = "Inspecting deletion dependencies...";
    }

    function canPreviewDeletion() {
      if (!currentDeletionReview ||
          !["Safe", "RequiresExplicitDependencies"].includes(currentDeletionReview.status) ||
          currentDeletionReview.invalidSelectedDependencyKeys.length > 0) return false;
      return currentDeletionReview.dependentStatements.every((statement) =>
        typeof statement.dependencyKey === "string" && selectedDeletionKeys.has(statement.dependencyKey));
    }

    function updateDeletionPreviewState() {
      previewDeletion.disabled = !canPreviewDeletion();
    }

    function labelForIri(iri) {
      if (!iri) return "";
      const symbol = currentModel?.symbolGroups.flatMap((group) => group.symbols).find((candidate) => candidate.iri === iri);
      return symbol?.label || displayIri(iri);
    }

    function setEntityPicker(labelInput, iriInput, iri, label) {
      iriInput.value = iri || "";
      labelInput.value = iri ? (label || labelForIri(iri)) : "";
      labelInput.dataset.resolvedLabel = labelInput.value;
    }

    function restoreEditForm(request) {
      editKind.value = request.editKind;
      updateEditFormMode();
      targetSource.value = request.targetSourceId;
      classIri.value = request.classIri || "";
      classLabel.value = request.label || "";
      propertyIri.value = request.propertyIri || "";
      propertyLabel.value = request.label || labelForIri(request.propertyIri);
      setEntityPicker(propertyDomainLabel, propertyDomainIri, request.domainIri);
      setEntityPicker(propertyRangeLabel, propertyRangeIri, request.rangeIri);
      propertyDatatype.value = request.datatype || "";
      propertyReplace.checked = request.replaceExisting === true;
      individualIri.value = request.individualIri || "";
      setEntityPicker(individualTypeLabel, individualTypeIri, request.typeIri);
      individualLabel.value = request.label || labelForIri(request.individualIri);
      setEntityPicker(assertionSubjectLabel, assertionSubjectIri, request.subjectIri);
      setEntityPicker(assertionPropertyLabel, assertionPropertyIri, request.propertyIri);
      setEntityPicker(assertionObjectLabel, assertionObjectIri, request.objectIri);
      assertionValue.value = request.value || "";
      assertionDatatype.value = request.datatype || "";
      assertionLanguage.value = request.language || "";
      setEntityPicker(hierarchyClassLabel, hierarchyClassIri, request.classIri);
      setEntityPicker(hierarchySuperclassLabel, hierarchySuperclassIri, request.superclassIri);
      setEntityPicker(labelEntityLabel, labelEntityIri, request.entityIri);
      labelValue.value = request.label || "";
      labelLanguage.value = request.language || "";
      labelReplace.checked = request.replaceExisting === true;
      setEntityPicker(semanticPropertyLabel, semanticPropertyIri, request.propertyIri);
      setEntityPicker(semanticTargetLabel, semanticTargetIri, request.targetIri);
      semanticLabel.value = request.label || "";
      semanticDefinition.value = request.definition || "";
      semanticValue.value = request.value || "";
      semanticExisting.value = request.existing || "";
      semanticReplacement.value = request.replacement || "";
    }

    function stagePreview(preview) {
      if (!currentRequest || !preview.canApprove) return;
      const entry = {
        id: editingStagedEntry ? editingStagedEntry.id : "staged-" + stagedSequence++,
        order: editingStagedEntry ? editingStagedEntry.order : stagedChanges.length,
        request: currentRequest,
        preview,
        summary: preview.targetSourceId + " · " + currentRequest.editKind + " · " + (currentRequest.label || currentRequest.classIri || currentRequest.propertyIri || currentRequest.individualIri || currentRequest.entityIri || "edit"),
      };
      stagedChanges = editingStagedEntry
        ? stagedChanges.concat(entry).sort((first, second) => first.order - second.order)
        : stagedChanges.concat(entry);
      stagedChanges = stagedChanges.map((value, index) => ({ ...value, order: index }));
      editingStagedEntry = undefined;
      currentRequest = undefined;
      currentPreview = undefined;
      clearEditForm();
      renderStagedList();
      editStatus.textContent = "Change staged successfully.";
      previewStatus.textContent = "No combined proposal preview requested.";
      previewImpact.textContent = "The source file has not changed.";
      previewDiff.replaceChildren();
      previewValidation.replaceChildren();
      approve.disabled = true;
      reject.disabled = true;
      openSource.disabled = true;
      approvalState.textContent = "Review the staged list before the combined preview.";
    }

    function renderStagedList() {
      stagedList.replaceChildren();
      stagedStatus.textContent = stagedChanges.length === 0
        ? "No changes staged."
        : stagedChanges.length + " change(s) staged for combined review.";
      cancelStagedEdit.hidden = !editingStagedEntry;
      previewAllChanges.disabled = stagedChanges.length === 0 || Boolean(editingStagedEntry);
      stagedChanges.forEach((entry) => {
        const item = document.createElement("li");
        const summary = document.createElement("span");
        summary.textContent = entry.summary + " · " + entry.preview.validationStatus;
        const edit = document.createElement("button");
        edit.type = "button";
        edit.textContent = "Edit";
        edit.addEventListener("click", () => {
          editingStagedEntry = entry;
          stagedChanges = stagedChanges.filter((value) => value.id !== entry.id);
          restoreEditForm(entry.request);
          currentRequest = entry.request;
          stagedStatus.textContent = "Editing " + entry.summary + ". Re-preview to return it to the staged list.";
          renderStagedList();
        });
        const remove = document.createElement("button");
        remove.type = "button";
        remove.textContent = "Remove";
        remove.addEventListener("click", () => {
          stagedChanges = stagedChanges.filter((value) => value.id !== entry.id).map((value, index) => ({ ...value, order: index }));
          renderStagedList();
        });
        item.append(summary, edit, remove);
        stagedList.append(item);
      });
    }

    function updateEditFormMode() {
      const createClass = editKind.value === "create-class";
      const propertyMode = [
        "create-object-property",
        "create-datatype-property",
        "set-property-domain",
        "set-property-range",
      ].includes(editKind.value);
      const createProperty = editKind.value === "create-object-property" || editKind.value === "create-datatype-property";
      const datatypeMode = editKind.value === "create-datatype-property" || editKind.value === "set-property-range";
      const domainMode = createProperty || editKind.value === "set-property-domain";
      const rangeMode = createProperty || editKind.value === "set-property-range";
      const individualMode = editKind.value === "create-individual" || editKind.value === "assign-individual-type";
      const assertionMode = editKind.value === "add-object-property-assertion" || editKind.value === "add-datatype-property-assertion";
      const datatypeAssertion = editKind.value === "add-datatype-property-assertion";
      const hierarchyMode = editKind.value === "add-superclass" || editKind.value === "remove-superclass";
      const labelMode = editKind.value === "set-entity-label";
      const semanticMode = [
        "create-annotation-property", "add-definition", "replace-definition", "remove-definition",
        "add-alternate-label", "replace-alternate-label", "remove-alternate-label",
        "add-annotation", "remove-annotation",
      ].includes(editKind.value);
      const createAnnotationProperty = editKind.value === "create-annotation-property";
      const definitionMode = ["add-definition", "replace-definition", "remove-definition"].includes(editKind.value);
      const alternateLabelMode = ["add-alternate-label", "replace-alternate-label", "remove-alternate-label"].includes(editKind.value);
      const annotationMode = ["add-annotation", "remove-annotation"].includes(editKind.value);
      const replaceSemanticValue = ["replace-definition", "replace-alternate-label"].includes(editKind.value);
      const formMode = createClass || propertyMode || individualMode || assertionMode || hierarchyMode || labelMode || semanticMode;
      classFields.hidden = !createClass;
      propertyFields.hidden = !propertyMode;
      individualFields.hidden = !individualMode;
      assertionFields.hidden = !assertionMode;
      hierarchyFields.hidden = !hierarchyMode;
      labelFields.hidden = !labelMode;
      semanticFields.hidden = !semanticMode;
      editFormPlaceholder.hidden = formMode;
      previewSubmit.disabled = !formMode;
      classIri.required = false;
      classLabel.required = createClass;
      propertyIri.required = false;
      propertyLabel.required = createProperty;
      propertyLabelField.hidden = !propertyMode;
      propertyDomainField.hidden = !domainMode;
      propertyRangeField.hidden = !rangeMode;
      propertyDatatypeField.hidden = !datatypeMode;
      propertyReplace.parentElement.hidden = editKind.value !== "set-property-domain" && editKind.value !== "set-property-range";
      propertyDomainIri.required = false;
      propertyRangeIri.required = false;
      individualIri.required = false;
      individualLabel.required = editKind.value === "create-individual";
      individualLabelField.hidden = !individualMode;
      individualTypeIri.required = false;
      assertionSubjectIri.required = false;
      assertionPropertyIri.required = false;
      assertionObjectField.hidden = !assertionMode || datatypeAssertion;
      assertionValueField.hidden = !datatypeAssertion;
      assertionDatatypeField.hidden = !datatypeAssertion;
      assertionLanguageField.hidden = !datatypeAssertion;
      assertionObjectIri.required = false;
      assertionValue.required = datatypeAssertion;
      hierarchyClassIri.required = false;
      hierarchySuperclassIri.required = false;
      labelEntityIri.required = false;
      labelValue.required = labelMode;
      semanticPropertyField.hidden = !createAnnotationProperty && !annotationMode;
      semanticPropertyPickerLabel.hidden = createAnnotationProperty;
      semanticTargetField.hidden = !definitionMode && !alternateLabelMode && !annotationMode;
      semanticLabelField.hidden = !createAnnotationProperty;
      semanticDefinitionField.hidden = !createAnnotationProperty;
      semanticValueField.hidden = !definitionMode && !alternateLabelMode && !annotationMode;
      semanticExistingField.hidden = !replaceSemanticValue;
      semanticReplacementField.hidden = !replaceSemanticValue;
      semanticPropertyIri.required = false;
      semanticTargetIri.required = false;
      semanticLabel.required = false;
      semanticDefinition.required = false;
      semanticValue.required = (definitionMode || alternateLabelMode || annotationMode) &&
        !replaceSemanticValue;
      semanticExisting.required = replaceSemanticValue;
      semanticReplacement.required = replaceSemanticValue;
    }
    editKind.addEventListener("change", () => {
      updateEditFormMode();
      pendingIriGenerationKey = undefined;
      pendingPreviewAfterIri = false;
      pendingEntityResolution = false;
    });
    propertyDatatype.addEventListener("change", updateEditFormMode);
    assertionDatatype.addEventListener("change", updateEditFormMode);
    updateEditFormMode();

    function collectEntityResolutions(editState) {
      const {
        propertyMode,
        createProperty,
        hierarchyMode,
        labelMode,
        annotationMode,
        definitionMode,
        alternateLabelMode,
      } = editState;
      const entries = [];
      const missing = [];
      const add = (field, labelInput, iriInput, kind, description, required = true) => {
        if (iriInput.value) return;
        const label = labelInput.value.trim();
        if (!label) {
          if (required) missing.push(description);
          return;
        }
        entries.push({ field, label, kind, description });
      };
      const propertyExisting = propertyMode && !createProperty;
      if (propertyExisting) add("property-iri", propertyLabel, propertyIri, "Property", "property");
      if (editKind.value === "set-property-domain") add("property-domain-iri", propertyDomainLabel, propertyDomainIri, "Class", "domain");
      if (editKind.value === "set-property-range" || createProperty) {
        add("property-range-iri", propertyRangeLabel, propertyRangeIri, "Class", "range", editKind.value === "set-property-range" && !propertyDatatype.value);
      }
      if (createProperty) {
        add("property-domain-iri", propertyDomainLabel, propertyDomainIri, "Class", "domain", false);
      }
      if (editKind.value === "create-individual") add("individual-type-iri", individualTypeLabel, individualTypeIri, "Class", "individual type", false);
      if (editKind.value === "assign-individual-type") {
        add("individual-iri", individualLabel, individualIri, "Individual", "individual");
        add("individual-type-iri", individualTypeLabel, individualTypeIri, "Class", "individual type");
      }
      if (editKind.value === "add-object-property-assertion") {
        add("assertion-subject-iri", assertionSubjectLabel, assertionSubjectIri, undefined, "assertion subject");
        add("assertion-property-iri", assertionPropertyLabel, assertionPropertyIri, "Property", "assertion property");
        add("assertion-object-iri", assertionObjectLabel, assertionObjectIri, undefined, "assertion object");
      }
      if (editKind.value === "add-datatype-property-assertion") {
        add("assertion-subject-iri", assertionSubjectLabel, assertionSubjectIri, undefined, "assertion subject");
        add("assertion-property-iri", assertionPropertyLabel, assertionPropertyIri, "Property", "assertion property");
      }
      if (hierarchyMode) {
        add("hierarchy-class-iri", hierarchyClassLabel, hierarchyClassIri, "Class", "class");
        add("hierarchy-superclass-iri", hierarchySuperclassLabel, hierarchySuperclassIri, "Class", "superclass");
      }
      if (labelMode) add("label-entity-iri", labelEntityLabel, labelEntityIri, undefined, "entity");
      if (annotationMode) {
        add("semantic-property-iri", semanticPropertyLabel, semanticPropertyIri, "AnnotationProperty", "annotation property");
        add("semantic-target-iri", semanticTargetLabel, semanticTargetIri, undefined, "annotation target");
      } else if (definitionMode || alternateLabelMode) {
        add("semantic-target-iri", semanticTargetLabel, semanticTargetIri, undefined, "target entity");
      }
      return { entries, missing };
    }

    function submitProposalPreview() {
      const propertyMode = !propertyFields.hidden;
      if (editKind.value === "set-property-range" && propertyRangeIri.value === "" && propertyDatatype.value === "") {
        editStatus.textContent = "A range IRI or datatype is required.";
        return;
      }
      const individualMode = !individualFields.hidden;
      const assertionMode = !assertionFields.hidden;
      const hierarchyMode = !hierarchyFields.hidden;
      const labelMode = !labelFields.hidden;
      const semanticMode = !semanticFields.hidden;
      const createProperty = editKind.value === "create-object-property" || editKind.value === "create-datatype-property";
      const createAnnotationProperty = editKind.value === "create-annotation-property";
      const definitionMode = ["add-definition", "replace-definition", "remove-definition"].includes(editKind.value);
      const alternateLabelMode = ["add-alternate-label", "replace-alternate-label", "remove-alternate-label"].includes(editKind.value);
      const annotationMode = ["add-annotation", "remove-annotation"].includes(editKind.value);
      if (editKind.value === "add-datatype-property-assertion" && assertionValue.value === "") {
        editStatus.textContent = "A literal value is required.";
        return;
      }
      if (editKind.value !== "create-class" && !propertyMode && !individualMode && !assertionMode && !hierarchyMode && !labelMode && !semanticMode) return;
      if (editKind.value === "create-class" && !classIri.value) {
        pendingPreviewAfterIri = true;
        editStatus.textContent = "Generating an IRI before staging...";
        if (!requestIriGeneration()) pendingPreviewAfterIri = false;
        return;
      }
      if (createProperty && !propertyIri.value) {
        pendingPreviewAfterIri = true;
        editStatus.textContent = "Generating an IRI before staging...";
        if (!requestIriGeneration()) pendingPreviewAfterIri = false;
        return;
      }
      if (editKind.value === "set-property-range" && propertyRangeIri.value === "" && propertyDatatype.value === "") return;
      if (editKind.value === "create-individual" && !individualIri.value) {
        pendingPreviewAfterIri = true;
        editStatus.textContent = "Generating an IRI before staging...";
        if (!requestIriGeneration()) pendingPreviewAfterIri = false;
        return;
      }
      if (createAnnotationProperty && !semanticPropertyIri.value) {
        pendingPreviewAfterIri = true;
        editStatus.textContent = "Generating an IRI before staging...";
        if (!requestIriGeneration()) pendingPreviewAfterIri = false;
        return;
      }
      const entityResolutions = collectEntityResolutions({
        propertyMode,
        createProperty,
        hierarchyMode,
        labelMode,
        annotationMode,
        definitionMode,
        alternateLabelMode,
      });
      if (entityResolutions.missing.length > 0) {
        editStatus.textContent = "Enter a " + entityResolutions.missing[0] + " label before staging.";
        return;
      }
      if (entityResolutions.entries.length > 0) {
        if (!pendingEntityResolution) requestEntityResolutions(entityResolutions.entries);
        return;
      }
      const payload = editKind.value === "create-class"
        ? {
            targetSourceId: targetSource.value,
            editKind: editKind.value,
            classIri: classIri.value,
            label: classLabel.value,
          }
        : semanticMode
        ? {
            targetSourceId: targetSource.value,
            editKind: editKind.value,
            propertyIri: semanticPropertyIri.value,
            targetIri: semanticTargetIri.value,
            label: semanticLabel.value,
            definition: semanticDefinition.value,
            value: semanticValue.value,
            existing: semanticExisting.value,
            replacement: semanticReplacement.value,
          }
        : propertyMode
        ? {
            targetSourceId: targetSource.value,
            editKind: editKind.value,
            propertyIri: propertyIri.value,
            label: propertyLabel.value,
            domainIri: propertyDomainIri.value,
            rangeIri: propertyRangeIri.value,
            datatype: propertyDatatype.value,
            replaceExisting: propertyReplace.checked,
          }
        : individualMode
        ? {
            targetSourceId: targetSource.value,
            editKind: editKind.value,
            individualIri: individualIri.value,
            typeIri: individualTypeIri.value,
            label: individualLabel.value,
          }
        : hierarchyMode
        ? {
            targetSourceId: targetSource.value,
            editKind: editKind.value,
            classIri: hierarchyClassIri.value,
            superclassIri: hierarchySuperclassIri.value,
          }
        : labelMode
        ? {
            targetSourceId: targetSource.value,
            editKind: editKind.value,
            entityIri: labelEntityIri.value,
            label: labelValue.value,
            language: labelLanguage.value,
            replaceExisting: labelReplace.checked,
          }
        : {
            targetSourceId: targetSource.value,
            editKind: editKind.value,
            subjectIri: assertionSubjectIri.value,
            propertyIri: assertionPropertyIri.value,
            objectIri: assertionObjectIri.value,
            value: assertionValue.value,
            datatype: assertionDatatype.value,
            language: assertionLanguage.value,
          };
      currentRequest = payload;
      vscode.postMessage({
        type: semanticMode ? "semantic-preview" : "proposal-preview",
        payload,
      });
      editStatus.textContent = "Preparing staged change...";
    }

    previewSubmit.addEventListener("click", () => {
      try {
        submitProposalPreview();
      } catch (error) {
        renderPreviewError(error instanceof Error ? error.message : "The proposal preview could not be started.");
      }
    });

    approve.addEventListener("click", () => {
      if (currentCombinedPreview && currentCombinedPreview.canApprove) {
        vscode.postMessage({ type: "combined-action", action: "apply" });
        return;
      }
      if (currentPreview && currentPreview.canApprove && currentRequest) {
        vscode.postMessage({ type: "proposal-action", action: "apply", payload: currentRequest });
      }
    });

    reject.addEventListener("click", () => {
      if (currentCombinedPreview) {
        vscode.postMessage({ type: "combined-action", action: "reject" });
        return;
      }
      if (currentRequest) {
        vscode.postMessage({ type: "proposal-action", action: "reject", payload: currentRequest });
      }
    });

    openSource.addEventListener("click", () => {
      if (changedSource) {
        vscode.postMessage({ type: "open-source", path: changedSource });
      }
    });

    function renderList(container, items, emptyText) {
      container.replaceChildren();
      if (items.length === 0) {
        container.textContent = emptyText;
        return;
      }
      const list = document.createElement("ul");
      items.forEach((item) => {
        const entry = document.createElement("li");
        entry.textContent = item;
        list.append(entry);
      });
      container.append(list);
    }

    function renderPreview(preview) {
      currentPreview = preview;
      previewStatus.textContent = preview.status + " · " + preview.targetSourceId + " · " + preview.previewTripleCount + " graph triple(s)";
      previewImpact.textContent = "Affected files: " + (preview.affectedPaths.join(", ") || "none");
      renderList(previewDiff, preview.diffEntries.map((entry) => entry.description), "No semantic changes.");
      renderList(previewValidation, preview.validationIssues, preview.validationStatus);
      approve.disabled = !preview.canApprove;
      reject.disabled = false;
      openSource.disabled = true;
      changedSource = undefined;
      approvalState.textContent = preview.canApprove
        ? "Preview is valid; adding it to the staged list."
        : preview.approvalDisabledReason;
      stagePreview(preview);
    }

    function renderDeletionPreview(combined) {
      const deletionRequest = currentRequest;
      const preview = {
        proposalId: combined.proposalId || "deletion-preview",
        status: combined.status,
        targetSourceId: deletionRequest?.targetSourceId || targetSource.value,
        affectedPaths: combined.affectedPaths || [],
        previewTripleCount: combined.previewTripleCount || 0,
        diffEntries: combined.diffEntries || [],
        validationStatus: combined.validationStatus || "not run",
        validationOk: combined.validationOk,
        validationIssues: combined.validationIssues || [],
        semanticEquivalenceStatus: combined.semanticEquivalenceStatus,
        semanticEquivalenceReason: combined.semanticEquivalenceReason,
        canApprove: combined.canApprove,
        approvalDisabledReason: combined.canApprove
          ? undefined
          : "Approval is disabled because the deletion proposal is not ready.",
      };
      renderPreview(preview);
      if (combined.canApprove) {
        currentDeletionReview = undefined;
        selectedDeletionKeys = new Set();
        previewDeletion.disabled = true;
        deletionDependencies.textContent = "Deletion staged successfully. Review it in the staged changes list.";
      } else {
        updateDeletionPreviewState();
      }
    }

    function renderCombinedPreview(preview) {
      currentCombinedPreview = preview;
      currentPreview = undefined;
      previewStatus.textContent = preview.status + " · combined proposal · " + (preview.previewTripleCount || 0) + " graph triple(s)";
      previewImpact.textContent = "Affected files: " + (preview.affectedPaths.join(", ") || "none");
      renderList(previewDiff, preview.diffEntries.map((entry) => entry.description), "No semantic changes.");
      renderList(previewValidation, preview.validationIssues, preview.validationStatus || "not run");
      approve.disabled = !preview.canApprove;
      reject.disabled = false;
      openSource.disabled = true;
      approvalState.textContent = preview.canApprove
        ? "Combined preview is valid and ready for approval."
        : "Approval is disabled because the combined preview is not ready.";
    }

    function renderCombinedActionResult(result) {
      currentCombinedPreview = undefined;
      approve.disabled = true;
      reject.disabled = true;
      openSource.disabled = true;
      changedSource = result.changedFiles && result.changedFiles[0];
      if (result.ok && result.action === "apply") {
        stagedChanges = [];
        editingStagedEntry = undefined;
        renderStagedList();
        previewStatus.textContent = "Combined proposal applied successfully.";
        approvalState.textContent = "The project was refreshed after atomic application.";
        openSource.disabled = !changedSource;
        return;
      }
      if (result.ok && result.action === "reject") {
        currentRequest = undefined;
        currentPreview = undefined;
        changedSource = undefined;
        renderStagedList();
        previewStatus.textContent = "Combined proposal rejected; source files were not changed.";
        previewImpact.textContent = "No source files were changed.";
        previewDiff.replaceChildren();
        previewValidation.replaceChildren();
        approvalState.textContent = "The changes remain staged for correction or another review.";
        return;
      }
      previewStatus.textContent = result.reason || "Combined proposal action failed.";
      approvalState.textContent = result.rollbackStatus
        ? "Combined apply failed; rollback status: " + result.rollbackStatus
        : "The staged list remains available for correction.";
    }

    function renderPreviewError(message) {
      previewStatus.textContent = message;
      previewImpact.replaceChildren();
      previewDiff.replaceChildren();
      previewValidation.replaceChildren();
      approve.disabled = true;
      reject.disabled = true;
      openSource.disabled = true;
      currentPreview = undefined;
      approvalState.textContent = "Approval is disabled because preview failed.";
    }

    function renderGeneratedIri(result) {
      const currentKey = editKind.value + "|" + (labelForEdit() || "").trim();
      if (pendingIriGenerationKey !== currentKey) {
        pendingPreviewAfterIri = false;
        editStatus.textContent = "The label changed while the IRI was being generated. Click Stage change again.";
        return;
      }
      pendingIriGenerationKey = undefined;
      editStatus.textContent = "Generated IRI: " + result.iri + " · " + result.collision;
      if (editKind.value === "create-class") classIri.value = result.iri;
      if (["create-object-property", "create-datatype-property"].includes(editKind.value)) propertyIri.value = result.iri;
      if (editKind.value === "create-individual") individualIri.value = result.iri;
      if (editKind.value === "create-annotation-property") semanticPropertyIri.value = result.iri;
      if (pendingPreviewAfterIri) {
        pendingPreviewAfterIri = false;
        try {
          submitProposalPreview();
        } catch (error) {
          renderPreviewError(error instanceof Error ? error.message : "The proposal preview could not be started.");
        }
      }
    }

    function renderEntityResolutions(result) {
      pendingEntityResolution = false;
      Object.entries(result.entities || {}).forEach(([field, iri]) => {
        const input = document.getElementById(field);
        if (input && typeof iri === "string") input.value = iri;
      });
      try {
        submitProposalPreview();
      } catch (error) {
        renderPreviewError(error instanceof Error ? error.message : "The staged change could not be prepared.");
      }
    }

    function renderDeletionReview(result) {
      currentDeletionReview = result;
      selectedDeletionKeys = new Set(
        result.dependentStatements
          .filter((statement) => statement.selectedForRemoval && statement.dependencyKey)
          .map((statement) => statement.dependencyKey),
      );
      deletionDependencies.replaceChildren();
      const status = document.createElement("strong");
      status.textContent = formatDeletionStatus(result.status, result.safe);
      deletionDependencies.append(status);
      const list = document.createElement("ul");
      result.directStatements.forEach((statement) => {
        const item = document.createElement("li");
        item.textContent = statement.kind + " · " + displayStatementValue(statement.subjectLabel, statement.subject) + " · " +
          displayStatementValue(statement.predicateLabel, statement.predicate) + " · " +
          displayStatementValue(statement.objectLabel, statement.object);
        list.append(item);
      });
      result.dependentStatements.forEach((statement) => {
        const item = document.createElement("li");
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.checked = statement.selectedForRemoval;
        checkbox.disabled = !statement.dependencyKey;
        checkbox.addEventListener("change", () => {
          if (!statement.dependencyKey) return;
          if (checkbox.checked) selectedDeletionKeys.add(statement.dependencyKey);
          else selectedDeletionKeys.delete(statement.dependencyKey);
          statement.selectedForRemoval = checkbox.checked;
          updateDeletionPreviewState();
        });
        const label = document.createElement("label");
        label.className = "deletion-dependency";
        label.append(
          checkbox,
          document.createTextNode(" " + statement.kind + " · " + displayStatementValue(statement.subjectLabel, statement.subject) + " · " +
            displayStatementValue(statement.predicateLabel, statement.predicate) + " · " +
            displayStatementValue(statement.objectLabel, statement.object)),
        );
        item.append(label);
        list.append(item);
      });
      if (list.childElementCount > 0) deletionDependencies.append(list);
      if (!result.safe && canPreviewDeletion()) {
        const ready = document.createElement("p");
        ready.textContent = "All dependent statements are selected. Stage deletion is ready.";
        deletionDependencies.append(ready);
      } else if (!result.safe) {
        const blocker = document.createElement("p");
        blocker.textContent = result.invalidSelectedDependencyKeys.length > 0
          ? "Deletion remains blocked because one or more selected dependencies are invalid."
          : "Deletion remains blocked until dependent statements are explicitly selected.";
        deletionDependencies.append(blocker);
      }
      updateDeletionPreviewState();
    }

    function formatDeletionStatus(status, safe) {
      const labels = {
        Safe: "Safe",
        RequiresExplicitDependencies: "Requires explicit dependencies",
        Invalid: "Invalid deletion target",
        InvalidDependencySelection: "Invalid dependency selection",
      };
      return (labels[status] || status) + (safe ? " · safe to review" : " · explicit dependencies required");
    }

    function displayStatementValue(label, rawValue) {
      if (label) return label;
      const value = rawValue.startsWith("Iri(value=") && rawValue.endsWith(")")
        ? rawValue.slice("Iri(value=".length, -1)
        : rawValue;
      const separator = Math.max(value.lastIndexOf("#"), value.lastIndexOf("/"));
      return separator >= 0 && separator < value.length - 1 ? value.slice(separator + 1) : value;
    }

    function renderActionResult(result) {
      approve.disabled = true;
      reject.disabled = true;
      currentPreview = undefined;
      currentRequest = undefined;
      changedSource = result.changedFiles && result.changedFiles[0];
      openSource.disabled = !changedSource;
      if (result.ok) {
        previewStatus.textContent = result.action === "reject"
          ? "Proposal rejected; source files were not changed."
          : "Proposal applied successfully.";
        approvalState.textContent = result.action === "reject"
          ? "No files changed."
          : "The project was refreshed after application.";
        return;
      }
      previewStatus.textContent = result.reason;
      approvalState.textContent = result.rollbackStatus
        ? "Apply failed; rollback status: " + result.rollbackStatus
        : "Proposal action failed.";
    }

    function populateSources(model) {
      targetSource.replaceChildren();
      model.ontologySources.forEach((source) => {
        const option = document.createElement("option");
        option.value = source.id;
        option.textContent = source.id + " · " + source.path;
        targetSource.append(option);
      });
      const selectedSource = semanticSearchSource.value;
      semanticSearchSource.replaceChildren();
      const allSources = document.createElement("option");
      allSources.value = "";
      allSources.textContent = "All sources";
      semanticSearchSource.append(allSources);
      model.ontologySources.forEach((source) => {
        const option = document.createElement("option");
        option.value = source.id;
        option.textContent = source.id + " · " + source.path;
        semanticSearchSource.append(option);
      });
      semanticSearchSource.value = model.ontologySources.some((source) => source.id === selectedSource)
        ? selectedSource
        : "";
    }

    function renderDetails(symbol) {
      selectedSemanticIri = symbol.iri;
      details.replaceChildren();
      const heading = document.createElement("strong");
      heading.textContent = symbol.label || symbol.iri;
      const metadata = document.createElement("div");
      metadata.textContent = symbol.kind + " · " + symbol.sourceId;
      const iri = document.createElement("div");
      iri.textContent = symbol.iri;
      const deleteAction = document.createElement("button");
      deleteAction.id = "delete-symbol";
      deleteAction.type = "button";
      deleteAction.textContent = "Delete";
      deleteAction.disabled = !["Class", "Property", "Individual"].includes(symbol.kind);
      deleteAction.addEventListener("click", () => requestDeletionReview({
        label: symbol.label || undefined,
        iri: symbol.iri,
        kind: symbol.kind,
        sourceId: symbol.sourceId,
      }));
      details.append(heading, metadata, iri, deleteAction);
      renderRelationshipSection(
        "Types",
        symbol.relationships.filter((relationship) => relationship.kind === "type" && relationship.direction === "outgoing"),
      );
      renderRelationshipSection(
        "Outgoing properties",
        symbol.relationships.filter((relationship) => relationship.kind === "property" && relationship.direction === "outgoing"),
      );
      renderRelationshipSection(
        "Incoming relationships",
        symbol.relationships.filter((relationship) => relationship.direction === "incoming"),
      );
      const loading = document.createElement("p");
      loading.id = "semantic-details-status";
      loading.textContent = "Loading semantic details...";
      details.append(loading);
      vscode.postMessage({ type: "semantic-describe", payload: { iri: symbol.iri } });
    }

    function renderRelationshipSection(title, relationships) {
      const sectionHeading = document.createElement("h4");
      sectionHeading.textContent = title;
      const list = document.createElement("ul");
      if (relationships.length === 0) {
        const empty = document.createElement("li");
        empty.textContent = "None";
        list.append(empty);
      } else {
        relationships.forEach((relationship) => {
          const item = document.createElement("li");
          const predicate = relationship.predicateLabel || displayIri(relationship.predicate);
          const value = formatRelationshipValue(relationship);
          item.textContent = relationship.direction === "incoming"
            ? value + " → " + predicate
            : predicate + " → " + value;
          list.append(item);
        });
      }
      details.append(sectionHeading, list);
    }

    function formatRelationshipValue(relationship) {
      const value = relationship.value;
      if (value.kind === "literal") {
        let formatted = '"' + value.value + '"';
        if (value.language) formatted += "@" + value.language;
        if (value.datatype) formatted += "^^" + displayIri(value.datatype);
        return formatted;
      }
      return relationship.valueLabel
        ? relationship.valueLabel
        : displayIri(value.value);
    }

    function renderSemanticDescriptor(descriptor) {
      if (selectedSemanticIri && descriptor.iri !== selectedSemanticIri) return;
      const status = document.getElementById("semantic-details-status");
      if (status) status.remove();
      const existing = document.getElementById("semantic-details");
      if (existing) existing.remove();
      const section = document.createElement("section");
      section.id = "semantic-details";
      const heading = document.createElement("h4");
      heading.textContent = "Semantic details";
      section.append(heading);
      appendSemanticField(section, "Preferred label", descriptor.preferredLabel?.value || "None");
      appendSemanticField(section, "Label source", descriptor.preferredLabelSource);
      appendSemanticCollection(section, "Alternate labels", descriptor.alternateLabels.map(formatLocalizedText));
      appendSemanticCollection(section, "Definitions", descriptor.definitions.map(formatLocalizedText));
      appendSemanticCollection(section, "Annotations", descriptor.annotations.map(formatAnnotation));
      appendDescriptorStructure(section, descriptor);
      const technical = document.createElement("details");
      const technicalSummary = document.createElement("summary");
      technicalSummary.textContent = "Technical details";
      technical.append(technicalSummary);
      appendSemanticField(technical, "IRI", descriptor.iri);
      appendSemanticField(technical, "Kind", descriptor.kind);
      appendSemanticField(technical, "Source", descriptor.sourceId + " · " + descriptor.sourceOntologyId);
      appendSemanticField(technical, "Locality", descriptor.locality);
      section.append(technical);
      details.append(section);
    }

    function appendDescriptorStructure(container, descriptor) {
      if (descriptor.kind === "Class") {
        appendSemanticCollection(container, "Direct superclasses", descriptor.directSuperclasses.map(displayEntity));
        appendSemanticCollection(container, "Direct subclasses", descriptor.directSubclasses.map(displayEntity));
        appendSemanticCollection(container, "Directly typed individuals", descriptor.directlyTypedIndividuals.map(displayEntity));
      }
      if (["ObjectProperty", "DatatypeProperty"].includes(descriptor.kind)) {
        appendSemanticCollection(container, "Domains", descriptor.domains.map(displayEntity));
        appendSemanticCollection(
          container,
          descriptor.kind === "DatatypeProperty" ? "Datatype ranges" : "Ranges",
          (descriptor.kind === "DatatypeProperty" ? descriptor.datatypeRanges : descriptor.ranges).map(displayEntity),
        );
        appendSemanticCollection(container, "Direct assertions", descriptor.directAssertions.map(formatAssertion));
      }
      if (descriptor.kind === "AnnotationProperty") {
        appendSemanticCollection(container, "Statements using property", descriptor.statementsUsingProperty.map(formatAnnotation));
      }
      if (descriptor.kind === "Individual") {
        appendSemanticCollection(container, "Asserted types", descriptor.assertedTypes.map(displayEntity));
        appendSemanticCollection(container, "Object property assertions", descriptor.objectPropertyAssertions.map(formatAssertion));
        appendSemanticCollection(container, "Datatype property assertions", descriptor.datatypePropertyAssertions.map(formatAssertion));
      }
    }

    function appendSemanticField(container, label, value) {
      const field = document.createElement("p");
      const name = document.createElement("strong");
      name.textContent = label + ": ";
      field.append(name, document.createTextNode(value));
      container.append(field);
    }

    function appendSemanticCollection(container, title, values) {
      const heading = document.createElement("h5");
      heading.textContent = title;
      const list = document.createElement("ul");
      if (values.length === 0) {
        const empty = document.createElement("li");
        empty.textContent = "None";
        list.append(empty);
      } else {
        values.forEach((value) => {
          const item = document.createElement("li");
          item.textContent = value;
          list.append(item);
        });
      }
      container.append(heading, list);
    }

    function formatLocalizedText(text) {
      let value = text.value;
      if (text.language) value += " @" + text.language;
      if (text.datatype) value += " ^^ " + displayIri(text.datatype);
      return value;
    }

    function formatAnnotation(annotation) {
      return displayEntity(annotation.subject) + " · " + displayEntity(annotation.property) + " · " + formatRdfTerm(annotation.value);
    }

    function formatAssertion(assertion) {
      const value = typeof assertion.value === "string" ? displayEntity(assertion.value) : formatRdfTerm(assertion.value);
      return displayEntity(assertion.subject) + " · " + displayEntity(assertion.property) + " · " + value;
    }

    function formatRdfTerm(term) {
      if (term.kind !== "literal") return displayEntity(term.value);
      let value = '"' + term.value + '"';
      if (term.language) value += " @" + term.language;
      if (term.datatype) value += " ^^ " + displayIri(term.datatype);
      return value;
    }

    function displayEntity(iri) {
      const symbol = currentModel?.symbolGroups.flatMap((group) => group.symbols).find((candidate) => candidate.iri === iri);
      return symbol?.label || displayIri(iri);
    }

    function renderSemanticSearch(result) {
      semanticSearchResults.replaceChildren();
      if (result.results.length === 0) {
        semanticSearchResults.textContent = "No semantic descriptions matched '" + result.query + "'.";
        return;
      }
      const heading = document.createElement("p");
      heading.textContent = result.ambiguous
        ? "Multiple semantic descriptions matched; select one to inspect details."
        : "Semantic search result:";
      semanticSearchResults.append(heading);
      const list = document.createElement("ul");
      result.results.forEach((match) => {
        const item = document.createElement("li");
        const button = document.createElement("button");
        button.type = "button";
        button.className = "symbol-button";
        button.textContent = (match.descriptor.preferredLabel?.value || displayIri(match.descriptor.iri)) +
          " · " + match.descriptor.kind + " · " + formatSearchReason(match.reason);
        button.addEventListener("click", () => {
          selectedSemanticIri = match.descriptor.iri;
          renderSemanticDescriptor(match.descriptor);
          details.scrollIntoView({ block: "nearest" });
        });
        item.append(button);
        list.append(item);
      });
      semanticSearchResults.append(list);
    }

    function formatSearchReason(reason) {
      const labels = {
        PreferredLabel: "preferred label",
        AlternateLabel: "alternate label",
        Iri: "IRI",
        Annotation: "annotation",
      };
      return labels[reason] || reason;
    }

    function displayIri(value) {
      const separator = Math.max(value.lastIndexOf("#"), value.lastIndexOf("/"));
      return separator >= 0 && separator < value.length - 1 ? value.slice(separator + 1) : value;
    }

    function renderModel(model) {
      currentModel = model;
      status.textContent = model.projectName + " · " + model.graphTripleCount + " graph triple(s)";
      populateSources(model);
      sources.replaceChildren();
      model.ontologySources.forEach((source) => {
        const item = document.createElement("li");
        item.textContent = source.id + " · " + source.path;
        sources.append(item);
      });
      symbolGroups.replaceChildren();
      model.symbolGroups.forEach((group) => {
        const heading = document.createElement("h3");
        heading.textContent = group.kind;
        const list = document.createElement("ul");
        group.symbols.forEach((symbol) => {
          const item = document.createElement("li");
          const button = document.createElement("button");
          button.className = "symbol-button";
          button.type = "button";
          button.textContent = symbol.label || symbol.iri;
          button.addEventListener("click", () => renderDetails(symbol));
          item.append(button);
          list.append(item);
        });
        symbolGroups.append(heading, list);
      });
      if (model.selectedSymbol) {
        renderDetails(model.selectedSymbol);
      }
    }

    window.addEventListener("message", (event) => {
      const message = event.data;
      if (message.type === "error") {
        status.textContent = message.message;
        return;
      }
      if (message.type === "project-summary") {
        renderModel(message.payload);
      }
      if (message.type === "semantic-descriptor") {
        renderSemanticDescriptor(message.payload);
      }
      if (message.type === "semantic-descriptor-error") {
        const semanticStatus = document.getElementById("semantic-details-status");
        if (semanticStatus) semanticStatus.textContent = message.message;
      }
      if (message.type === "semantic-search") {
        renderSemanticSearch(message.payload);
      }
      if (message.type === "semantic-search-error") {
        semanticSearchResults.textContent = message.message;
      }
      if (message.type === "proposal-preview") {
        renderPreview(message.payload);
      }
      if (message.type === "combined-preview") {
        renderCombinedPreview(message.payload);
      }
      if (message.type === "deletion-preview") {
        renderDeletionPreview(message.payload);
      }
      if (message.type === "deletion-preview-error") {
        previewDeletion.disabled = false;
        previewStatus.textContent = message.message;
        approvalState.textContent = "Deletion preview failed; correct the dependency selection and try again.";
      }
      if (message.type === "combined-preview-error") {
        currentCombinedPreview = undefined;
        previewStatus.textContent = message.message;
        approve.disabled = true;
        reject.disabled = true;
      }
      if (message.type === "combined-action-result") {
        renderCombinedActionResult(message.payload);
      }
      if (message.type === "combined-action-error") {
        renderCombinedActionResult({ ok: false, action: message.action || "apply", reason: message.message, changedFiles: [] });
      }
      if (message.type === "generated-iri") {
        renderGeneratedIri(message.payload);
      }
      if (message.type === "generated-iri-error") {
        pendingPreviewAfterIri = false;
        pendingIriGenerationKey = undefined;
        editStatus.textContent = message.message;
      }
      if (message.type === "edit-entities-resolved") {
        renderEntityResolutions(message.payload);
      }
      if (message.type === "edit-entities-error") {
        pendingEntityResolution = false;
        editStatus.textContent = message.message;
      }
      if (message.type === "deletion-dependencies") {
        renderDeletionReview(message.payload);
      }
      if (message.type === "deletion-dependencies-error") {
        deletionDependencies.textContent = message.message;
      }
      if (message.type === "proposal-preview-error") {
        renderPreviewError(message.message);
      }
      if (message.type === "proposal-action-result") {
        renderActionResult(message.payload);
      }
      if (message.type === "proposal-action-error") {
        renderPreviewError(message.message);
      }
    });
    renderStagedList();
    vscode.postMessage({ type: "refresh" });
  </script>
</body>
</html>`;
}
